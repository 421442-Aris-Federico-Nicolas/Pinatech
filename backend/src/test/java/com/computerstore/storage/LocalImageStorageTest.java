package com.computerstore.storage;

import com.computerstore.common.exception.FileStorageException;
import com.computerstore.common.exception.InvalidRequestException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.mock.web.MockMultipartFile;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class LocalImageStorageTest {
    @TempDir
    Path directory;

    @Test
    void storesDecodedImageUsingUuidAndDetectedContentType() throws Exception {
        LocalImageStorage storage = new LocalImageStorage(directory.toString());
        MockMultipartFile file = new MockMultipartFile("file", "../user-name.png", "text/plain", image("png", 20, 10));

        LocalImageStorage.StoredImage stored = storage.store(file);

        assertEquals(stored.storageKey(), UUID.fromString(stored.storageKey()).toString());
        assertEquals("user-name.png", stored.originalFilename());
        assertEquals("image/png", stored.contentType());
        assertTrue(Files.isRegularFile(directory.resolve(stored.storageKey())));
    }

    @Test
    void storesPublicImagesAsWebpWithOriginalDimensions() throws Exception {
        LocalImageStorage storage = new LocalImageStorage(directory.toString());
        MockMultipartFile file = new MockMultipartFile("file", "../hero.png", "image/png", image("png", 80, 40));

        LocalImageStorage.StoredImage stored = storage.storeWebp(file);

        assertEquals("hero.webp", stored.originalFilename());
        assertEquals("image/webp", stored.contentType());
        assertEquals(80, stored.width());
        assertEquals(40, stored.height());
        try (var input = ImageIO.createImageInputStream(storage.load(stored.storageKey()).toFile())) {
            var reader = ImageIO.getImageReaders(input).next();
            try {
                assertEquals("WebP", reader.getFormatName());
            } finally {
                reader.dispose();
            }
        }
    }

    @Test
    void rejectsWebpInputForOriginalAndPublicUploads() throws Exception {
        LocalImageStorage storage = new LocalImageStorage(directory.toString());
        var converted = storage.storeWebp(
                new MockMultipartFile("file", "source.png", "image/png", image("png", 20, 10)));
        byte[] webp = Files.readAllBytes(storage.load(converted.storageKey()));

        assertAll(
                () -> assertThrows(InvalidRequestException.class,
                        () -> storage.store(new MockMultipartFile("file", "image.webp", "image/webp", webp))),
                () -> assertThrows(InvalidRequestException.class,
                        () -> storage.storeWebp(new MockMultipartFile("file", "image.webp", "image/webp", webp))));
    }

    @ParameterizedTest
    @CsvSource({
            "2,80,40,green,red,yellow,blue",
            "3,80,40,yellow,blue,green,red",
            "4,80,40,blue,yellow,red,green",
            "5,40,80,red,blue,green,yellow",
            "6,40,80,blue,red,yellow,green",
            "7,40,80,yellow,green,blue,red",
            "8,40,80,green,yellow,red,blue"
    })
    void publicWebpAppliesEveryExifOrientation(int orientation, int expectedWidth, int expectedHeight,
            String topLeft, String topRight, String bottomLeft, String bottomRight) throws Exception {
        LocalImageStorage storage = new LocalImageStorage(directory.toString());
        var stored = storage.storeWebp(new MockMultipartFile("file", "oriented.jpg", "image/jpeg",
                orientedJpeg(orientation)));
        BufferedImage decoded = ImageIO.read(storage.load(stored.storageKey()).toFile());

        assertEquals(expectedWidth, stored.width());
        assertEquals(expectedHeight, stored.height());
        assertEquals(expectedWidth, decoded.getWidth());
        assertEquals(expectedHeight, decoded.getHeight());
        assertColor(color(topLeft), decoded.getRGB(decoded.getWidth() / 4, decoded.getHeight() / 4));
        assertColor(color(topRight), decoded.getRGB(decoded.getWidth() * 3 / 4, decoded.getHeight() / 4));
        assertColor(color(bottomLeft), decoded.getRGB(decoded.getWidth() / 4, decoded.getHeight() * 3 / 4));
        assertColor(color(bottomRight), decoded.getRGB(decoded.getWidth() * 3 / 4,
                decoded.getHeight() * 3 / 4));
    }

    @Test
    void rejectsClaimedImageWithInvalidBytes() {
        LocalImageStorage storage = new LocalImageStorage(directory.toString());
        MockMultipartFile file = new MockMultipartFile("file", "fake.png", "image/png", "not-an-image".getBytes());

        assertThrows(InvalidRequestException.class, () -> storage.store(file));
    }

    @Test
    void rejectsTruncatedImageData() throws Exception {
        LocalImageStorage storage = new LocalImageStorage(directory.toString());
        byte[] valid = image("png", 20, 10);
        MockMultipartFile file = new MockMultipartFile("file", "truncated.png", "image/png",
                java.util.Arrays.copyOf(valid, valid.length / 2));

        assertThrows(InvalidRequestException.class, () -> storage.store(file));
    }

    @Test
    void rejectsOversizedFilesAndUnsafeKeys() {
        LocalImageStorage storage = new LocalImageStorage(directory.toString());
        MockMultipartFile oversized = new MockMultipartFile("file", "large.png", "image/png",
                new byte[(int) LocalImageStorage.MAX_FILE_SIZE + 1]);

        assertThrows(InvalidRequestException.class, () -> storage.store(oversized));
        assertThrows(InvalidRequestException.class, () -> storage.load("../outside.png"));
    }

    @Test
    void rejectsExcessiveImageDimensions() throws Exception {
        LocalImageStorage storage = new LocalImageStorage(directory.toString());
        MockMultipartFile file = new MockMultipartFile("file", "wide.png", "image/png", image("png", 6001, 1));

        assertThrows(InvalidRequestException.class, () -> storage.store(file));
    }

    @Test
    void rejectsImagesAbovePixelCap() throws Exception {
        LocalImageStorage storage = new LocalImageStorage(directory.toString());
        BufferedImage oversized = new BufferedImage(4000, 3001, BufferedImage.TYPE_BYTE_BINARY);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        assertTrue(ImageIO.write(oversized, "png", output));
        MockMultipartFile file = new MockMultipartFile("file", "large.png", "image/png", output.toByteArray());

        assertEquals(12_000_000L, LocalImageStorage.MAX_PIXELS);
        assertThrows(InvalidRequestException.class, () -> storage.store(file));
    }

    @Test
    void thumbnailFlattensTransparencyOntoWhite() throws Exception {
        var storage = new LocalImageStorage(directory.toString());
        var output = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(10, 10, BufferedImage.TYPE_INT_ARGB), "png", output);
        var stored = storage.store(new MockMultipartFile("file", "alpha.png", "image/png", output.toByteArray()));
        var decoded = ImageIO.read(storage.thumbnail(stored.storageKey()).toFile());
        assertEquals(0xffffff, decoded.getRGB(5, 5) & 0xffffff);
    }

    @ParameterizedTest
    @CsvSource({"png,1280,800,640,400", "jpeg,800,1280,400,640", "png,1000,1000,640,640",
            "png,20,10,20,10", "png,6000,1,640,1"})
    void thumbnailPreservesAspectRatioAndOriginal(String format, int width, int height,
                                                  int expectedWidth, int expectedHeight) throws Exception {
        var storage = new LocalImageStorage(directory.toString());
        byte[] original = image(format, width, height);
        var stored = storage.store(new MockMultipartFile("file", "image." + format, "image/" + format, original));
        Path thumbnail = storage.thumbnail(stored.storageKey());
        try (var input = ImageIO.createImageInputStream(thumbnail.toFile())) {
            var reader = ImageIO.getImageReaders(input).next();
            try {
                assertEquals("WebP", reader.getFormatName());
                reader.setInput(input);
                assertEquals(expectedWidth, reader.getWidth(0));
                assertEquals(expectedHeight, reader.getHeight(0));
            } finally {
                reader.dispose();
            }
        }
        assertArrayEquals(original, Files.readAllBytes(storage.load(stored.storageKey())));
    }

    @Test
    void publicWebpConvertsExistingImagesOnceAndDeletesTheDerivative() throws Exception {
        var storage = new LocalImageStorage(directory.toString());
        var stored = storage.store(new MockMultipartFile("file", "image.png", "image/png", image("png", 1280, 800)));
        var first = storage.publicWebp(stored.storageKey());
        byte[] cached = Files.readAllBytes(first.path());
        var modified = Files.getLastModifiedTime(first.path());

        Files.writeString(storage.load(stored.storageKey()), "not an image anymore");
        var second = new LocalImageStorage(directory.toString()).publicWebp(stored.storageKey());

        assertEquals(first.path(), second.path());
        assertEquals(modified, Files.getLastModifiedTime(second.path()));
        assertArrayEquals(cached, Files.readAllBytes(second.path()));
        assertEquals(cached.length, second.sizeBytes());
        storage.delete(stored.storageKey());
        assertFalse(Files.exists(first.path()));
        assertFalse(Files.exists(directory.resolve(stored.storageKey())));
    }

    @Test
    void thumbnailCacheSurvivesStorageRestartWithoutReadingOriginalAndDeletesBothFiles() throws Exception {
        var storage = new LocalImageStorage(directory.toString());
        var stored = storage.store(new MockMultipartFile("file", "image.png", "image/png", image("png", 1280, 800)));
        Path thumbnail = storage.thumbnail(stored.storageKey());
        byte[] cached = Files.readAllBytes(thumbnail);
        var modified = Files.getLastModifiedTime(thumbnail);
        // An unreadable original proves a cache hit does not attempt to decode it again.
        Files.writeString(storage.load(stored.storageKey()), "not an image anymore");
        var restarted = new LocalImageStorage(directory.toString());
        assertEquals(thumbnail, restarted.thumbnail(stored.storageKey()));
        assertEquals(modified, Files.getLastModifiedTime(thumbnail));
        assertArrayEquals(cached, Files.readAllBytes(thumbnail));
        restarted.delete(stored.storageKey());
        assertFalse(Files.exists(thumbnail));
        assertFalse(Files.exists(directory.resolve(stored.storageKey())));
    }

    @Test
    void concurrentColdRequestsPublishOneCompleteCachedThumbnail() throws Exception {
        var storage = new LocalImageStorage(directory.toString());
        var secondStorage = new LocalImageStorage(directory.toString());
        var stored = storage.store(new MockMultipartFile("file", "image.png", "image/png", image("png", 1280, 800)));
        var start = new java.util.concurrent.CountDownLatch(1);
        try (var executor = java.util.concurrent.Executors.newFixedThreadPool(8)) {
            var futures = new java.util.ArrayList<java.util.concurrent.Future<Path>>();
            for (int index = 0; index < 16; index++) {
                var instance = index % 2 == 0 ? storage : secondStorage;
                futures.add(executor.submit(() -> {
                    start.await();
                    Path result = instance.thumbnail(stored.storageKey());
                    assertEquals(640, ImageIO.read(result.toFile()).getWidth());
                    return result;
                }));
            }
            start.countDown();
            Path expected = futures.getFirst().get(10, java.util.concurrent.TimeUnit.SECONDS);
            for (var future : futures) {
                assertEquals(expected, future.get(10, java.util.concurrent.TimeUnit.SECONDS));
            }
        }
        try (var files = Files.list(directory)) {
            assertEquals(2, files.count());
        }
    }

    @ParameterizedTest
    @CsvSource({"6001,1", "1,6001", "4000,3001"})
    void thumbnailValidatesExistingFilesBeforeDecoding(int width, int height) throws Exception {
        var storage = new LocalImageStorage(directory.toString());
        String key = UUID.randomUUID().toString();
        Files.write(directory.resolve(key), image("png", width, height));
        assertThrows(InvalidRequestException.class, () -> storage.thumbnail(key));
        try (var files = Files.list(directory)) {
            assertEquals(1, files.count());
        }
    }

    @Test
    void thumbnailRejectsOversizedInvalidMissingAndUnsafeOriginals() throws Exception {
        var storage = new LocalImageStorage(directory.toString());
        String key = UUID.randomUUID().toString();
        Files.write(directory.resolve(key), new byte[(int) LocalImageStorage.MAX_FILE_SIZE + 1]);
        assertThrows(InvalidRequestException.class, () -> storage.thumbnail(key));
        Files.writeString(directory.resolve(key), "invalid");
        assertThrows(InvalidRequestException.class, () -> storage.thumbnail(key));
        assertThrows(InvalidRequestException.class, () -> storage.thumbnail("../escape"));
        assertThrows(com.computerstore.common.exception.ResourceNotFoundException.class,
                () -> storage.thumbnail(UUID.randomUUID().toString()));
        try (var files = Files.list(directory)) {
            assertEquals(1, files.count());
        }
    }

    @Test
    void failsInitializationWhenStorageRootCannotBeWritten() throws Exception {
        Path regularFile = Files.createFile(directory.resolve("not-a-directory"));

        assertThrows(FileStorageException.class, () -> new LocalImageStorage(regularFile.toString()));
    }

    private byte[] image(String format, int width, int height) throws Exception {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        assertTrue(ImageIO.write(image, format, output));
        return output.toByteArray();
    }

    private byte[] orientedJpeg(int orientation) throws Exception {
        BufferedImage image = new BufferedImage(80, 40, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setColor(Color.RED);
            graphics.fillRect(0, 0, 40, 20);
            graphics.setColor(Color.GREEN);
            graphics.fillRect(40, 0, 40, 20);
            graphics.setColor(Color.BLUE);
            graphics.fillRect(0, 20, 40, 20);
            graphics.setColor(Color.YELLOW);
            graphics.fillRect(40, 20, 40, 20);
        } finally {
            graphics.dispose();
        }
        ByteArrayOutputStream jpegOutput = new ByteArrayOutputStream();
        assertTrue(ImageIO.write(image, "jpeg", jpegOutput));
        byte[] jpeg = jpegOutput.toByteArray();
        byte[] exif = {
                'E', 'x', 'i', 'f', 0, 0,
                'I', 'I', 42, 0, 8, 0, 0, 0,
                1, 0,
                0x12, 0x01, 3, 0, 1, 0, 0, 0, (byte) orientation, 0, 0, 0,
                0, 0, 0, 0
        };
        ByteArrayOutputStream oriented = new ByteArrayOutputStream();
        oriented.write(jpeg, 0, 2);
        oriented.write(0xff);
        oriented.write(0xe1);
        oriented.write(0);
        oriented.write(exif.length + 2);
        oriented.write(exif);
        oriented.write(jpeg, 2, jpeg.length - 2);
        return oriented.toByteArray();
    }

    private Color color(String name) {
        return switch (name) {
            case "red" -> Color.RED;
            case "green" -> Color.GREEN;
            case "blue" -> Color.BLUE;
            case "yellow" -> Color.YELLOW;
            default -> throw new IllegalArgumentException("Unknown color: " + name);
        };
    }

    private void assertColor(Color expected, int rgb) {
        Color actual = new Color(rgb);
        assertAll(
                () -> assertEquals(expected.getRed(), actual.getRed(), 50),
                () -> assertEquals(expected.getGreen(), actual.getGreen(), 50),
                () -> assertEquals(expected.getBlue(), actual.getBlue(), 50));
    }
}

package com.computerstore.storage;

import com.computerstore.common.exception.FileStorageException;
import com.computerstore.common.exception.InvalidRequestException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.mock.web.MockMultipartFile;

import javax.imageio.ImageIO;
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
                assertEquals("JPEG", reader.getFormatName());
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
}

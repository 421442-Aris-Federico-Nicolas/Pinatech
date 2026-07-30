package com.computerstore.storage;

import com.computerstore.common.exception.FileStorageException;
import com.computerstore.common.exception.InvalidRequestException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
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

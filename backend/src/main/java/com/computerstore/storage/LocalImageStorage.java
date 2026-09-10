package com.computerstore.storage;

import com.computerstore.common.exception.FileStorageException;
import com.computerstore.common.exception.InvalidRequestException;
import com.computerstore.common.exception.ResourceNotFoundException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Iterator;
import java.util.Locale;
import java.util.UUID;

@Service
public class LocalImageStorage {
    public static final long MAX_FILE_SIZE = 5L * 1024 * 1024;
    static final int MAX_WIDTH = 6000;
    static final int MAX_HEIGHT = 6000;
    static final long MAX_PIXELS = 12_000_000L;
    private static final int THUMBNAIL_SIZE = 640;
    private static final String THUMBNAIL_SUFFIX = ".thumbnail-v1.jpg";
    // Bounded locks also coordinate storage instances sharing a root in this JVM.
    private static final Object[] IMAGE_LOCKS = java.util.stream.IntStream.range(0, 64)
            .mapToObj(index -> new Object()).toArray();

    private final Path root;

    public LocalImageStorage(@Value("${app.storage.root:./uploads}") String configuredRoot) {
        root = Path.of(configuredRoot).toAbsolutePath().normalize();
        try {
            Files.createDirectories(root);
            Path writeProbe = Files.createTempFile(root, ".write-probe-", ".tmp");
            Files.delete(writeProbe);
        } catch (IOException exception) {
            throw new FileStorageException("Could not initialize file storage.", exception);
        }
    }

    public StoredImage store(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new InvalidRequestException("An image file is required.");
        }

        Path temporary = null;
        try {
            temporary = Files.createTempFile(root, ".upload-", ".tmp");
            long size = copyLimited(file, temporary);
            ImageMetadata metadata = inspect(temporary);
            String storageKey = UUID.randomUUID().toString();
            Path destination = resolveKey(storageKey);
            move(temporary, destination);
            temporary = null;
            return new StoredImage(storageKey, originalFilename(file.getOriginalFilename()), metadata.contentType(), size,
                    metadata.decoded().getWidth(), metadata.decoded().getHeight());
        } catch (InvalidRequestException exception) {
            throw exception;
        } catch (IOException exception) {
            throw new FileStorageException("Could not store image file.", exception);
        } finally {
            if (temporary != null) {
                try {
                    Files.deleteIfExists(temporary);
                } catch (IOException ignored) {
                    // The original storage error remains the actionable failure.
                }
            }
        }
    }

    public Path load(String storageKey) {
        Path path = resolveKey(storageKey);
        try {
            if (!Files.isRegularFile(path)) {
                throw new ResourceNotFoundException("Image content not found.");
            }
            Path realRoot = root.toRealPath();
            Path realFile = path.toRealPath();
            if (!realFile.startsWith(realRoot)) {
                throw new InvalidRequestException("Invalid storage key.");
            }
            return realFile;
        } catch (ResourceNotFoundException | InvalidRequestException exception) {
            throw exception;
        } catch (IOException exception) {
            throw new FileStorageException("Could not read image file.", exception);
        }
    }

    public Path thumbnail(String storageKey) {
        Path original = resolveKey(storageKey);
        Path destination = root.resolve(storageKey + THUMBNAIL_SUFFIX);
        synchronized (IMAGE_LOCKS[Math.floorMod(original.hashCode(), IMAGE_LOCKS.length)]) {
            if (Files.isRegularFile(destination, LinkOption.NOFOLLOW_LINKS)) {
                return destination;
            }
            Path temporary = null;
            try {
                Path source = load(storageKey);
                if (Files.size(source) > MAX_FILE_SIZE) {
                    throw new InvalidRequestException("Image files must not exceed 5 MiB.");
                }
                BufferedImage decoded = inspect(source).decoded();
                double scale = Math.min(1.0, (double) THUMBNAIL_SIZE / Math.max(decoded.getWidth(), decoded.getHeight()));
                int width = Math.max(1, (int) Math.round(decoded.getWidth() * scale));
                int height = Math.max(1, (int) Math.round(decoded.getHeight() * scale));
                BufferedImage thumbnail = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
                Graphics2D graphics = thumbnail.createGraphics();
                try {
                    graphics.setColor(Color.WHITE);
                    graphics.fillRect(0, 0, width, height);
                    graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                    graphics.drawImage(decoded, 0, 0, width, height, null);
                } finally {
                    graphics.dispose();
                }
                temporary = Files.createTempFile(root, ".thumbnail-", ".tmp");
                if (!ImageIO.write(thumbnail, "jpeg", temporary.toFile())) {
                    throw new IOException("JPEG encoder unavailable.");
                }
                // Never expose partial JPEGs, including to other processes sharing the filesystem.
                // Fail safely if atomic publication is unsupported by the storage volume.
                try {
                    Files.move(temporary, destination, StandardCopyOption.ATOMIC_MOVE);
                    temporary = null;
                } catch (FileAlreadyExistsException exception) {
                    if (!Files.isRegularFile(destination, LinkOption.NOFOLLOW_LINKS)) {
                        throw exception;
                    }
                }
                return destination;
            } catch (IOException exception) {
                throw new FileStorageException("Could not create image thumbnail.", exception);
            } finally {
                if (temporary != null) {
                    try {
                        Files.deleteIfExists(temporary);
                    } catch (IOException ignored) {
                        // Preserve the actionable storage failure.
                    }
                }
            }
        }
    }

    public void delete(String storageKey) {
        Path path = resolveKey(storageKey);
        synchronized (IMAGE_LOCKS[Math.floorMod(path.hashCode(), IMAGE_LOCKS.length)]) {
            try {
                Files.deleteIfExists(root.resolve(storageKey + THUMBNAIL_SUFFIX));
                if (!Files.exists(path)) {
                    return;
                }
                Path realRoot = root.toRealPath();
                Path realFile = path.toRealPath();
                if (!realFile.startsWith(realRoot)) {
                    throw new InvalidRequestException("Invalid storage key.");
                }
                Files.delete(realFile);
            } catch (InvalidRequestException exception) {
                throw exception;
            } catch (IOException exception) {
                throw new FileStorageException("Could not delete image file.", exception);
            }
        }
    }

    private long copyLimited(MultipartFile file, Path target) throws IOException {
        long total = 0;
        byte[] buffer = new byte[8192];
        try (InputStream input = file.getInputStream(); OutputStream output = Files.newOutputStream(target)) {
            int read;
            while ((read = input.read(buffer)) != -1) {
                total += read;
                if (total > MAX_FILE_SIZE) {
                    throw new InvalidRequestException("Image files must not exceed 5 MiB.");
                }
                output.write(buffer, 0, read);
            }
        }
        if (total == 0) {
            throw new InvalidRequestException("An image file is required.");
        }
        return total;
    }

    private ImageMetadata inspect(Path path) {
        try (ImageInputStream input = ImageIO.createImageInputStream(path.toFile())) {
            if (input == null) {
                throw new InvalidRequestException("The uploaded file is not a valid JPEG or PNG image.");
            }
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) {
                throw new InvalidRequestException("The uploaded file is not a valid JPEG or PNG image.");
            }
            ImageReader reader = readers.next();
            try {
                String format = reader.getFormatName().toUpperCase(Locale.ROOT);
                String contentType = switch (format) {
                    case "JPEG", "JPG" -> "image/jpeg";
                    case "PNG" -> "image/png";
                    default -> throw new InvalidRequestException("Only JPEG and PNG images are allowed.");
                };
                reader.setInput(input, true, true);
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                if (width <= 0 || height <= 0 || width > MAX_WIDTH || height > MAX_HEIGHT
                        || (long) width * height > MAX_PIXELS) {
                    throw new InvalidRequestException("Image dimensions exceed the allowed limit.");
                }
                BufferedImage decoded = reader.read(0);
                if (decoded == null) {
                    throw new InvalidRequestException("The uploaded image could not be decoded.");
                }
                return new ImageMetadata(contentType, decoded);
            } finally {
                reader.dispose();
            }
        } catch (InvalidRequestException exception) {
            throw exception;
        } catch (IOException exception) {
            throw new InvalidRequestException("The uploaded file is not a valid JPEG or PNG image.");
        } catch (RuntimeException exception) {
            throw new InvalidRequestException("The uploaded file is not a valid JPEG or PNG image.");
        }
    }

    private Path resolveKey(String storageKey) {
        if (storageKey == null) {
            throw new InvalidRequestException("Invalid storage key.");
        }
        try {
            UUID uuid = UUID.fromString(storageKey);
            if (!uuid.toString().equals(storageKey)) {
                throw new IllegalArgumentException();
            }
        } catch (IllegalArgumentException exception) {
            throw new InvalidRequestException("Invalid storage key.");
        }
        Path resolved = root.resolve(storageKey).normalize();
        if (!resolved.startsWith(root) || !resolved.getParent().equals(root)) {
            throw new InvalidRequestException("Invalid storage key.");
        }
        return resolved;
    }

    private String originalFilename(String value) {
        if (value == null || value.isBlank()) {
            return "image";
        }
        String normalized = value.replace('\\', '/');
        normalized = normalized.substring(normalized.lastIndexOf('/') + 1)
                .replaceAll("[\\p{Cntrl}]", "")
                .trim();
        if (normalized.isEmpty()) {
            return "image";
        }
        return normalized.length() <= 255 ? normalized : normalized.substring(normalized.length() - 255);
    }

    private void move(Path source, Path destination) throws IOException {
        try {
            Files.move(source, destination, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(source, destination);
        }
    }

    private record ImageMetadata(String contentType, BufferedImage decoded) {}

    public record StoredImage(String storageKey, String originalFilename, String contentType, long sizeBytes,
            int width, int height) {}
}

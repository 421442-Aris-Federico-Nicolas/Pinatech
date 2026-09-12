package com.computerstore.storage;

import com.computerstore.common.exception.FileStorageException;
import com.computerstore.common.exception.InvalidRequestException;
import com.computerstore.common.exception.ResourceNotFoundException;
import com.drew.imaging.ImageMetadataReader;
import com.drew.imaging.ImageProcessingException;
import com.drew.metadata.Metadata;
import com.drew.metadata.exif.ExifIFD0Directory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import javax.imageio.IIOImage;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriter;
import javax.imageio.stream.FileImageOutputStream;
import javax.imageio.stream.ImageInputStream;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
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
import java.util.concurrent.Semaphore;

@Service
public class LocalImageStorage {
    public static final long MAX_FILE_SIZE = 5L * 1024 * 1024;
    static final int MAX_WIDTH = 6000;
    static final int MAX_HEIGHT = 6000;
    static final long MAX_PIXELS = 12_000_000L;
    private static final int THUMBNAIL_SIZE = 640;
    private static final float WEBP_QUALITY = 0.85f;
    private static final String PUBLIC_WEBP_SUFFIX = ".public-v1.webp";
    private static final String THUMBNAIL_SUFFIX = ".thumbnail-v2.webp";
    private static final String LEGACY_THUMBNAIL_SUFFIX = ".thumbnail-v1.jpg";
    private static final Semaphore IMAGE_PROCESSING_PERMITS = new Semaphore(2, true);
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
        return store(file, false);
    }

    public StoredImage storeWebp(MultipartFile file) {
        return store(file, true);
    }

    private StoredImage store(MultipartFile file, boolean convertToWebp) {
        if (file == null || file.isEmpty()) {
            throw new InvalidRequestException("An image file is required.");
        }

        Path temporary = null;
        Path encoded = null;
        boolean processingPermit = false;
        try {
            temporary = Files.createTempFile(root, ".upload-", ".tmp");
            copyLimited(file, temporary);
            if (isWebp(temporary)) {
                throw new InvalidRequestException("Only JPEG and PNG images are allowed.");
            }
            acquireProcessingPermit();
            processingPermit = true;
            ImageMetadata metadata = inspect(temporary);
            if ("image/webp".equals(metadata.contentType())) {
                throw new InvalidRequestException("Only JPEG and PNG images are allowed.");
            }

            Path source = temporary;
            String filename = originalFilename(file.getOriginalFilename());
            String contentType = metadata.contentType();
            if (convertToWebp) {
                encoded = Files.createTempFile(root, ".webp-", ".tmp");
                writeWebp(metadata.decoded(), encoded);
                source = encoded;
                filename = webpFilename(filename);
                contentType = "image/webp";
            }

            String storageKey = UUID.randomUUID().toString();
            Path destination = resolveKey(storageKey);
            move(source, destination);
            if (source.equals(temporary)) temporary = null;
            else encoded = null;
            return new StoredImage(storageKey, filename, contentType, Files.size(destination),
                    metadata.decoded().getWidth(), metadata.decoded().getHeight());
        } catch (InvalidRequestException exception) {
            throw exception;
        } catch (IOException exception) {
            throw new FileStorageException("Could not store image file.", exception);
        } finally {
            if (processingPermit) {
                IMAGE_PROCESSING_PERMITS.release();
            }
            if (temporary != null) {
                try {
                    Files.deleteIfExists(temporary);
                } catch (IOException ignored) {
                    // The original storage error remains the actionable failure.
                }
            }
            if (encoded != null) {
                try {
                    Files.deleteIfExists(encoded);
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
            boolean processingPermit = false;
            try {
                Path source = load(storageKey);
                if (Files.size(source) > MAX_FILE_SIZE) {
                    throw new InvalidRequestException("Image files must not exceed 5 MiB.");
                }
                acquireProcessingPermit();
                processingPermit = true;
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
                writeWebp(thumbnail, temporary);
                // Never expose partial derivatives, including to other processes sharing the filesystem.
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
                if (processingPermit) {
                    IMAGE_PROCESSING_PERMITS.release();
                }
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

    public StoredContent publicWebp(String storageKey) {
        Path original = resolveKey(storageKey);
        synchronized (IMAGE_LOCKS[Math.floorMod(original.hashCode(), IMAGE_LOCKS.length)]) {
            Path destination = root.resolve(storageKey + PUBLIC_WEBP_SUFFIX);
            if (Files.isRegularFile(destination, LinkOption.NOFOLLOW_LINKS)) {
                return content(destination);
            }
            Path source = load(storageKey);
            if (isWebp(source)) {
                return content(source);
            }
            Path temporary = null;
            boolean processingPermit = false;
            try {
                acquireProcessingPermit();
                processingPermit = true;
                ImageMetadata metadata = inspect(source);
                temporary = Files.createTempFile(root, ".webp-", ".tmp");
                writeWebp(metadata.decoded(), temporary);
                publishDerived(temporary, destination);
                Files.deleteIfExists(temporary);
                temporary = null;
            } catch (IOException exception) {
                throw new FileStorageException("Could not create WebP image.", exception);
            } finally {
                if (processingPermit) {
                    IMAGE_PROCESSING_PERMITS.release();
                }
                if (temporary != null) {
                    try {
                        Files.deleteIfExists(temporary);
                    } catch (IOException ignored) {
                        // Preserve the actionable storage failure.
                    }
                }
            }
            return content(destination);
        }
    }

    public void delete(String storageKey) {
        Path path = resolveKey(storageKey);
        synchronized (IMAGE_LOCKS[Math.floorMod(path.hashCode(), IMAGE_LOCKS.length)]) {
            try {
                Files.deleteIfExists(root.resolve(storageKey + PUBLIC_WEBP_SUFFIX));
                Files.deleteIfExists(root.resolve(storageKey + THUMBNAIL_SUFFIX));
                Files.deleteIfExists(root.resolve(storageKey + LEGACY_THUMBNAIL_SUFFIX));
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
                    case "WEBP" -> "image/webp";
                    default -> throw new InvalidRequestException("Only JPEG, PNG and WebP images are allowed.");
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
                if ("image/jpeg".equals(contentType)) {
                    decoded = applyExifOrientation(path, decoded);
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

    private String webpFilename(String filename) {
        int dot = filename.lastIndexOf('.');
        String basename = dot > 0 ? filename.substring(0, dot) : filename;
        if (basename.length() > 250) basename = basename.substring(0, 250);
        return basename + ".webp";
    }

    private boolean isWebp(Path path) {
        try (InputStream input = Files.newInputStream(path)) {
            byte[] header = input.readNBytes(12);
            return header.length == 12
                    && header[0] == 'R' && header[1] == 'I' && header[2] == 'F' && header[3] == 'F'
                    && header[8] == 'W' && header[9] == 'E' && header[10] == 'B' && header[11] == 'P';
        } catch (IOException exception) {
            throw new FileStorageException("Could not read image file.", exception);
        }
    }

    private StoredContent content(Path path) {
        try {
            ImageDimensions dimensions = dimensions(path);
            return new StoredContent(path, Files.size(path), dimensions.width(), dimensions.height());
        } catch (IOException exception) {
            throw new FileStorageException("Could not read WebP image.", exception);
        }
    }

    private BufferedImage applyExifOrientation(Path path, BufferedImage source) {
        int orientation = 1;
        try {
            Metadata metadata = ImageMetadataReader.readMetadata(path.toFile());
            ExifIFD0Directory directory = metadata.getFirstDirectoryOfType(ExifIFD0Directory.class);
            if (directory != null) {
                Integer value = directory.getInteger(ExifIFD0Directory.TAG_ORIENTATION);
                if (value != null) orientation = value;
            }
        } catch (ImageProcessingException | IOException ignored) {
            return source;
        }
        if (orientation < 2 || orientation > 8) return source;

        int width = source.getWidth();
        int height = source.getHeight();
        boolean swapDimensions = orientation >= 5;
        BufferedImage result = new BufferedImage(swapDimensions ? height : width,
                swapDimensions ? width : height,
                source.getColorModel().hasAlpha() ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB);
        AffineTransform transform = switch (orientation) {
            case 2 -> new AffineTransform(-1, 0, 0, 1, width, 0);
            case 3 -> new AffineTransform(-1, 0, 0, -1, width, height);
            case 4 -> new AffineTransform(1, 0, 0, -1, 0, height);
            case 5 -> new AffineTransform(0, 1, 1, 0, 0, 0);
            case 6 -> new AffineTransform(0, 1, -1, 0, height, 0);
            case 7 -> new AffineTransform(0, -1, -1, 0, height, width);
            case 8 -> new AffineTransform(0, -1, 1, 0, 0, width);
            default -> new AffineTransform();
        };
        Graphics2D graphics = result.createGraphics();
        try {
            graphics.drawImage(source, transform, null);
        } finally {
            graphics.dispose();
        }
        return result;
    }

    private ImageDimensions dimensions(Path path) throws IOException {
        try (ImageInputStream input = ImageIO.createImageInputStream(path.toFile())) {
            if (input == null) throw new IOException("Could not read image dimensions.");
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) throw new IOException("Could not read image dimensions.");
            ImageReader reader = readers.next();
            try {
                reader.setInput(input, true, true);
                return new ImageDimensions(reader.getWidth(0), reader.getHeight(0));
            } finally {
                reader.dispose();
            }
        }
    }

    private void acquireProcessingPermit() {
        try {
            IMAGE_PROCESSING_PERMITS.acquire();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new FileStorageException("Image processing was interrupted.", exception);
        }
    }

    private void writeWebp(BufferedImage image, Path target) throws IOException {
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByMIMEType("image/webp");
        if (!writers.hasNext()) {
            throw new IOException("WebP encoder unavailable.");
        }
        ImageWriter writer = writers.next();
        try (FileImageOutputStream output = new FileImageOutputStream(target.toFile())) {
            ImageWriteParam parameters = writer.getDefaultWriteParam();
            parameters.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            String[] compressionTypes = parameters.getCompressionTypes();
            if (compressionTypes != null && compressionTypes.length > 0) {
                parameters.setCompressionType(compressionTypes[0]);
            }
            parameters.setCompressionQuality(WEBP_QUALITY);
            writer.setOutput(output);
            writer.write(null, new IIOImage(image, null, null), parameters);
        } finally {
            writer.dispose();
        }
    }

    private void publishDerived(Path source, Path destination) throws IOException {
        try {
            Files.move(source, destination, StandardCopyOption.ATOMIC_MOVE);
        } catch (FileAlreadyExistsException exception) {
            if (!Files.isRegularFile(destination, LinkOption.NOFOLLOW_LINKS)) throw exception;
        }
    }

    private void move(Path source, Path destination) throws IOException {
        try {
            Files.move(source, destination, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(source, destination);
        }
    }

    private record ImageMetadata(String contentType, BufferedImage decoded) {}

    private record ImageDimensions(int width, int height) {}

    public record StoredImage(String storageKey, String originalFilename, String contentType, long sizeBytes,
            int width, int height) {}

    public record StoredContent(Path path, long sizeBytes, int width, int height) {
        public StoredContent(Path path, long sizeBytes) {
            this(path, sizeBytes, -1, -1);
        }
    }
}

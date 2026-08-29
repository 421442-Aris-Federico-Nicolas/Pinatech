package com.computerstore.storage;

import com.computerstore.common.exception.FileStorageException;
import com.computerstore.common.exception.InvalidRequestException;
import com.computerstore.common.exception.ResourceNotFoundException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

@Service
public class LocalPrivateDocumentStorage implements PrivateDocumentStorage {
    private final Path root;

    public LocalPrivateDocumentStorage(@Value("${app.storage.root:./uploads}") String storageRoot,
            @Value("${app.storage.private-documents-subroot:bank-transfer-proofs}") String subroot) {
        Path base = Path.of(storageRoot).toAbsolutePath().normalize();
        if (subroot == null || !subroot.matches("[A-Za-z0-9_-]+")) {
            throw new FileStorageException("Invalid private document storage subroot.", null);
        }
        root = base.resolve(subroot).normalize();
        if (!root.startsWith(base)) throw new FileStorageException("Invalid private document storage subroot.", null);
        try {
            Files.createDirectories(root);
            Path probe = Files.createTempFile(root, ".write-probe-", ".tmp");
            Files.delete(probe);
        } catch (IOException exception) {
            throw new FileStorageException("Could not initialize private document storage.", exception);
        }
    }

    @Override
    public StoredDocument store(byte[] content) {
        if (content == null || content.length == 0) throw new InvalidRequestException("Document content is required.");
        String key = UUID.randomUUID().toString();
        Path temporary = null;
        try {
            temporary = Files.createTempFile(root, ".document-", ".tmp");
            Files.write(temporary, content, StandardOpenOption.TRUNCATE_EXISTING);
            Path destination = resolve(key);
            try {
                Files.move(temporary, destination, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(temporary, destination);
            }
            temporary = null;
            return new StoredDocument(key, content.length, sha256(content));
        } catch (IOException exception) {
            throw new FileStorageException("Could not store private document.", exception);
        } finally {
            if (temporary != null) try { Files.deleteIfExists(temporary); } catch (IOException ignored) {}
        }
    }

    @Override
    public Path load(String storageKey) {
        Path path = resolve(storageKey);
        if (!Files.isRegularFile(path)) throw new ResourceNotFoundException("Private document not found.");
        try {
            Path real = path.toRealPath();
            if (!real.startsWith(root.toRealPath())) throw new InvalidRequestException("Invalid storage key.");
            return real;
        } catch (IOException exception) {
            throw new FileStorageException("Could not load private document.", exception);
        }
    }

    @Override
    public void delete(String storageKey) {
        try {
            Files.deleteIfExists(resolve(storageKey));
        } catch (IOException exception) {
            throw new FileStorageException("Could not delete private document.", exception);
        }
    }

    @Override
    public List<StoredFile> filesOlderThan(Instant cutoff) {
        try (Stream<Path> paths = Files.list(root)) {
            return paths.filter(Files::isRegularFile).map(path -> {
                try {
                    return new StoredFile(path.getFileName().toString(), Files.getLastModifiedTime(path).toInstant());
                } catch (IOException exception) {
                    throw new FileStorageException("Could not inspect private storage.", exception);
                }
            }).filter(file -> file.lastModified().isBefore(cutoff)).toList();
        } catch (IOException exception) {
            throw new FileStorageException("Could not inspect private storage.", exception);
        }
    }

    private Path resolve(String key) {
        try {
            UUID uuid = UUID.fromString(key);
            if (!uuid.toString().equals(key)) throw new IllegalArgumentException();
        } catch (RuntimeException exception) {
            throw new InvalidRequestException("Invalid storage key.");
        }
        Path path = root.resolve(key).normalize();
        if (!path.getParent().equals(root)) throw new InvalidRequestException("Invalid storage key.");
        return path;
    }

    private String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable.", exception);
        }
    }
}

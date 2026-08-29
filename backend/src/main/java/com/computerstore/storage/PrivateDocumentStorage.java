package com.computerstore.storage;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

public interface PrivateDocumentStorage {
    StoredDocument store(byte[] content);
    Path load(String storageKey);
    void delete(String storageKey);
    List<StoredFile> filesOlderThan(Instant cutoff);

    record StoredDocument(String storageKey, long sizeBytes, String sha256) {}
    record StoredFile(String storageKey, Instant lastModified) {}
}

package com.itilms.file.storage;

import java.io.IOException;
import java.io.InputStream;

/**
 * Where bytes actually live. Two implementations: {@link LocalStorageBackend}
 * for a single-server install, {@link MinioStorageBackend} for production
 * (Doc S17: object storage survives a container being replaced and scales
 * past one machine's disk). The rest of the service never checks which one
 * is active.
 */
public interface StorageBackend {

    void store(String key, InputStream content, long size, String contentType) throws IOException;

    InputStream load(String key) throws IOException;

    void delete(String key) throws IOException;
}

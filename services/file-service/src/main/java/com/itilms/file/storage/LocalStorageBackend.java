package com.itilms.file.storage;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

import com.itilms.file.config.FileProperties;

/** Writes to a mounted volume. Fine for development and a single-server install. */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "itilms.storage", name = "backend", havingValue = "local", matchIfMissing = true)
public class LocalStorageBackend implements StorageBackend {

    private final FileProperties props;

    @Override
    public void store(String key, InputStream content, long size, String contentType) throws IOException {
        Path target = resolve(key);
        Files.createDirectories(target.getParent());
        Files.copy(content, target, StandardCopyOption.REPLACE_EXISTING);
    }

    @Override
    public InputStream load(String key) throws IOException {
        Path path = resolve(key);
        if (!Files.exists(path)) {
            throw new FileNotFoundException(key);
        }
        return Files.newInputStream(path);
    }

    @Override
    public void delete(String key) throws IOException {
        Files.deleteIfExists(resolve(key));
    }

    /** Confines every read and write inside the configured root, even if a key were ever malformed. */
    private Path resolve(String key) throws IOException {
        Path base = Path.of(props.getLocalPath()).toAbsolutePath().normalize();
        Path target = base.resolve(key).normalize();
        if (!target.startsWith(base)) {
            throw new IOException("Rejected storage key outside the storage root: " + key);
        }
        return target;
    }
}

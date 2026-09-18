package com.itilms.file.exception;

/**
 * The storage backend (disk or MinIO) failed on a read, write or delete.
 * Not an {@code ApiException}: the caller did nothing wrong, so the global
 * handler reports it as an unexpected 500 with a correlation id.
 */
public class StorageException extends RuntimeException {

    public StorageException(String message, Throwable cause) {
        super(message, cause);
    }
}

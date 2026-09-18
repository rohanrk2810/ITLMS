package com.itilms.file.storage;

import java.io.IOException;
import java.io.InputStream;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.itilms.file.config.FileProperties;

import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import lombok.extern.slf4j.Slf4j;

/**
 * The production choice (Doc S17): object storage that survives a container
 * being replaced and scales past one machine's disk. Not wired into
 * {@code docker-compose.yml} yet - "local" is what the dev stack runs - but
 * switching {@code STORAGE_BACKEND=minio} and pointing at a real server is
 * all a deployment needs to do.
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "itilms.storage", name = "backend", havingValue = "minio")
public class MinioStorageBackend implements StorageBackend {

    private final MinioClient client;
    private final String bucket;

    public MinioStorageBackend(FileProperties props) {
        this.bucket = props.getMinio().getBucket();
        this.client = MinioClient.builder()
                .endpoint(props.getMinio().getEndpoint())
                .credentials(props.getMinio().getAccessKey(), props.getMinio().getSecretKey())
                .build();
        ensureBucket();
    }

    private void ensureBucket() {
        try {
            boolean exists = client.bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
            if (!exists) {
                client.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
                log.info("Created MinIO bucket {}", bucket);
            }
        } catch (Exception ex) {
            throw new IllegalStateException("Could not prepare MinIO bucket " + bucket, ex);
        }
    }

    @Override
    public void store(String key, InputStream content, long size, String contentType) throws IOException {
        try {
            client.putObject(PutObjectArgs.builder().bucket(bucket).object(key)
                    .stream(content, size, -1).contentType(contentType).build());
        } catch (Exception ex) {
            throw new IOException("Could not write %s to MinIO".formatted(key), ex);
        }
    }

    @Override
    public InputStream load(String key) throws IOException {
        try {
            return client.getObject(GetObjectArgs.builder().bucket(bucket).object(key).build());
        } catch (Exception ex) {
            throw new IOException("Could not read %s from MinIO".formatted(key), ex);
        }
    }

    @Override
    public void delete(String key) throws IOException {
        try {
            client.removeObject(RemoveObjectArgs.builder().bucket(bucket).object(key).build());
        } catch (Exception ex) {
            throw new IOException("Could not delete %s from MinIO".formatted(key), ex);
        }
    }
}

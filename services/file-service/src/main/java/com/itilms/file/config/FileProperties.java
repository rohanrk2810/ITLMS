package com.itilms.file.config;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;

import com.itilms.file.entity.FileCategory;

import lombok.Getter;
import lombok.Setter;

/**
 * Where uploads are written and what is accepted (Doc S12: "validate
 * uploads; restrict MIME type and size"). An allow-list, not a block-list -
 * see {@code config-repo/file-service.yml} for why.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "itilms.storage")
public class FileProperties {

    private Backend backend = Backend.LOCAL;

    private String localPath = "./storage";

    private Minio minio = new Minio();

    private List<String> allowedContentTypes = new ArrayList<>();

    private Map<FileCategory, DataSize> maxSizeByCategory = new EnumMap<>(FileCategory.class);

    public enum Backend {
        LOCAL, MINIO
    }

    @Getter
    @Setter
    public static class Minio {
        private String endpoint;
        private String accessKey;
        private String secretKey;
        private String bucket = "itilms";
    }
}

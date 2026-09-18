package com.itilms.file.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.util.unit.DataSize;

import com.itilms.common.exception.BusinessRuleException;
import com.itilms.file.config.FileProperties;
import com.itilms.file.entity.FileCategory;

/** Doc S12: an allow-list of MIME types, and a size ceiling that depends on the category. */
class UploadValidatorTest {

    private FileProperties props;

    @BeforeEach
    void setUp() {
        props = new FileProperties();
        props.setAllowedContentTypes(List.of("application/pdf", "image/png"));
        props.setMaxSizeByCategory(Map.of(
                FileCategory.AVATAR, DataSize.ofMegabytes(2),
                FileCategory.DOCUMENT, DataSize.ofMegabytes(10)));
    }

    @Test
    void acceptsAnAllowedTypeWithinItsLimit() {
        assertThatCode(() -> UploadValidator.validate(props, "image/png", DataSize.ofMegabytes(1).toBytes(),
                FileCategory.AVATAR)).doesNotThrowAnyException();
    }

    @Test
    void rejectsAnEmptyFile() {
        assertThatThrownBy(() -> UploadValidator.validate(props, "image/png", 0, FileCategory.AVATAR))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("empty");
    }

    @Test
    void rejectsAContentTypeNotOnTheAllowList() {
        assertThatThrownBy(() -> UploadValidator.validate(props, "application/x-msdownload", 100,
                FileCategory.DOCUMENT))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("not accepted");
    }

    @Test
    void rejectsAFileOverItsCategoryLimit() {
        long tooBig = DataSize.ofMegabytes(3).toBytes();
        assertThatThrownBy(() -> UploadValidator.validate(props, "image/png", tooBig, FileCategory.AVATAR))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("may not exceed");
    }

    @Test
    void categoryWithNoConfiguredLimitIsUnbounded() {
        assertThatCode(() -> UploadValidator.validate(props, "application/pdf", DataSize.ofMegabytes(50).toBytes(),
                FileCategory.LESSON_RESOURCE)).doesNotThrowAnyException();
    }
}

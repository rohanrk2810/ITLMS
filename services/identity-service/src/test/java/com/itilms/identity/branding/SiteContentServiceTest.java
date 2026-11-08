package com.itilms.identity.branding;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.itilms.common.event.EventPublisher;
import com.itilms.common.exception.BusinessRuleException;
import com.itilms.common.exception.ResourceNotFoundException;

@ExtendWith(MockitoExtension.class)
class SiteContentServiceTest {

    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 13, 10, 26, 10, 0, 0};

    @Mock InstituteSettingsRepository settings;
    @Mock SiteImageRepository images;
    @Mock EventPublisher events;

    SiteContentService service;
    InstituteSettings row;

    @BeforeEach
    void setUp() {
        service = new SiteContentService(settings, images, events);
        row = new InstituteSettings();
        row.setName("IT Institute LMS");
    }

    @Test
    void anEmptyWebsiteReadsAsAnEmptyObject() {
        when(settings.findById((short) 1)).thenReturn(Optional.of(row));

        assertThat(service.get().config()).isEqualTo("{}");
    }

    @Test
    void savingStoresTheJsonAndAuditsIt() {
        when(settings.findById((short) 1)).thenReturn(Optional.of(row));

        SiteConfigResponse saved = service.set("{\"en\":{\"heroTitle\":\"Hello\"}}", 7L);

        assertThat(saved.config()).contains("Hello");
        assertThat(row.getSiteConfig()).contains("Hello");
        assertThat(row.getUpdatedBy()).isEqualTo(7L);
        verify(settings).save(row);
    }

    @Test
    void somethingThatIsNotAJsonObjectIsRefused() {
        assertThatThrownBy(() -> service.set("[1,2]", 7L)).isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> service.set("not json", 7L)).isInstanceOf(BusinessRuleException.class);
        verify(settings, never()).save(any());
    }

    @Test
    void anUploadedPictureIsStoredWithItsRealType() {
        when(images.count()).thenReturn(0L);
        when(images.save(any(SiteImage.class))).thenAnswer(call -> {
            SiteImage image = call.getArgument(0);
            image.setId(42L);
            return image;
        });

        SiteImageResponse response = service.upload(PNG, 7L);

        assertThat(response.id()).isEqualTo(42L);
        assertThat(response.url()).isEqualTo("/api/public/branding/site-images/42");
    }

    @Test
    void aFileThatIsNotAnImageIsRefused() {
        assertThatThrownBy(() -> service.upload("<svg onload=alert(1)/>".getBytes(), 7L))
                .isInstanceOf(BusinessRuleException.class);
        verify(images, never()).save(any());
    }

    @Test
    void thePictureLimitStopsAnotherUpload() {
        when(images.count()).thenReturn((long) SiteContentService.MAX_IMAGES);

        assertThatThrownBy(() -> service.upload(PNG, 7L)).isInstanceOf(BusinessRuleException.class);
        verify(images, never()).save(any());
    }

    @Test
    void removingAMissingPictureIsNotFound() {
        when(images.findById(9L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(9L, 7L)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void aMissingPictureReadsAsNull() {
        when(images.findById(9L)).thenReturn(Optional.empty());

        assertThat(service.image(9L)).isNull();
    }
}

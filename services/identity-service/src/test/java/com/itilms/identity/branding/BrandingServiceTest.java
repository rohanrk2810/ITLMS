package com.itilms.identity.branding;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
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

@ExtendWith(MockitoExtension.class)
class BrandingServiceTest {

    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 13, 10, 26, 10, 0, 0};

    @Mock InstituteSettingsRepository repository;
    @Mock EventPublisher events;

    BrandingService service;
    InstituteSettings row;

    @BeforeEach
    void setUp() {
        service = new BrandingService(repository, events);
        row = new InstituteSettings();
        row.setName("IT Institute LMS");
        when(repository.findById((short) 1)).thenReturn(Optional.of(row));
    }

    private static UpdateBrandingRequest rename(String name) {
        return new UpdateBrandingRequest(name, null, "#112233", null, null, null, null, "Dr. Rao", "Director");
    }

    @Test
    void renamingChangesWhatEveryoneSeesAndBumpsTheVersion() {
        BrandingResponse response = service.update(rename("  ABC Computer Institute "), 7L);

        assertThat(response.name()).isEqualTo("ABC Computer Institute");
        assertThat(response.signatoryName()).isEqualTo("Dr. Rao");
        assertThat(response.version()).isEqualTo(2);
        verify(events).audit(eq("identity-service"), eq("BRANDING_UPDATED"), eq("InstituteSettings"),
                anyLong(), eq(7L), any());
    }

    @Test
    void publicResponseNeverCarriesImageBytesOnlyAVersionedUrl() {
        service.setLogo(PNG, 7L);

        BrandingResponse response = service.get();

        assertThat(response.logoUrl()).isEqualTo("/api/public/branding/logo?v=2");
        assertThat(response.faviconUrl()).isNull();
        assertThat(service.logo().contentType()).isEqualTo("image/png");
    }

    @Test
    void contentTypeComesFromTheBytesNotFromTheClient() {
        byte[] svg = "<svg xmlns=\"http://www.w3.org/2000/svg\"><script>alert(1)</script></svg>".getBytes();

        assertThatThrownBy(() -> service.setLogo(svg, 7L)).isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void oversizedImageIsRefused() {
        byte[] big = new byte[BrandingService.MAX_IMAGE_BYTES + 1];
        System.arraycopy(PNG, 0, big, 0, PNG.length);

        assertThatThrownBy(() -> service.setFavicon(big, 7L)).isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void removingTheLogoClearsIt() {
        service.setLogo(PNG, 7L);
        service.clearLogo(7L);

        assertThat(service.logo()).isNull();
        assertThat(service.get().logoUrl()).isNull();
    }
}

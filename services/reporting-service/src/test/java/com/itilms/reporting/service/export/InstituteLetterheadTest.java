package com.itilms.reporting.service.export;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.itilms.reporting.client.BrandingClient;

@ExtendWith(MockitoExtension.class)
class InstituteLetterheadTest {

    @Mock BrandingClient client;

    @Test
    void theNameAddressAndContactLineComeFromTheBranding() {
        when(client.branding()).thenReturn(new BrandingClient.Branding(
                " GH Test College ", "Jalgaon", "1 College Road", "0257 111", "office@college.test", null));

        Letterhead letterhead = new InstituteLetterhead(client).get();

        assertThat(letterhead.name()).isEqualTo("GH Test College");
        assertThat(letterhead.tagline()).isEqualTo("Jalgaon");
        assertThat(letterhead.lines()).containsExactly("1 College Road", "0257 111  |  office@college.test");
        assertThat(letterhead.isEmpty()).isFalse();
    }

    @Test
    void blankPartsAreLeftOut() {
        when(client.branding()).thenReturn(new BrandingClient.Branding("Plain Institute", "  ", null, "", null, " "));

        Letterhead letterhead = new InstituteLetterhead(client).get();

        assertThat(letterhead.tagline()).isNull();
        assertThat(letterhead.lines()).isEmpty();
    }

    @Test
    void identityBeingDownGivesNoLetterheadAndIsTriedAgainNextTime() {
        when(client.branding()).thenReturn(null);
        InstituteLetterhead institute = new InstituteLetterhead(client);

        assertThat(institute.get().isEmpty()).isTrue();
        assertThat(institute.get()).isSameAs(Letterhead.NONE);
        verify(client, times(2)).branding(); // a failure is not remembered
    }

    @Test
    void anExceptionFromTheCallIsTreatedTheSameWay() {
        when(client.branding()).thenThrow(new IllegalStateException("down"));

        assertThat(new InstituteLetterhead(client).get().isEmpty()).isTrue();
    }

    @Test
    void aGoodAnswerIsKeptForAWhileSoEveryExportDoesNotCallIdentity() {
        when(client.branding()).thenReturn(new BrandingClient.Branding("GH Test College", null, null, null, null, null));
        InstituteLetterhead institute = new InstituteLetterhead(client);

        institute.get();
        institute.get();
        institute.get();

        verify(client, times(1)).branding();
        assertThat(institute.get().lines()).isEqualTo(List.of());
    }

    @Test
    void anAnswerWithNoNameIsNoLetterhead() {
        when(client.branding()).thenReturn(new BrandingClient.Branding("  ", "x", "y", null, null, null));

        assertThat(new InstituteLetterhead(client).get().isEmpty()).isTrue();
    }
}

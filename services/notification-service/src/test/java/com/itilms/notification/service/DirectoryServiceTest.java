package com.itilms.notification.service;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.itilms.common.event.UserCreatedEvent;
import com.itilms.notification.repository.BatchMemberRepository;
import com.itilms.notification.repository.RecipientRepository;

/** A notification to a role must reach the people who hold it as an extra duty, so the directory has to know them. */
class DirectoryServiceTest {

    private final RecipientRepository recipients = mock(RecipientRepository.class);
    private final DirectoryService service = new DirectoryService(recipients, mock(BatchMemberRepository.class));

    @Test
    void theExtraDutiesAreKeptAsACommaSeparatedList() {
        service.userChanged(UserCreatedEvent.withDuties(7L, "r@x.in", "9800000001", "Ravi", "TRAINER",
                List.of("COORDINATOR", "FINANCE")));

        verify(recipients).upsertProfile(7L, "r@x.in", "Ravi", "TRAINER", "COORDINATOR,FINANCE");
    }

    @Test
    void withdrawingTheDutiesClearsThem() {
        service.userChanged(UserCreatedEvent.withDuties(7L, "r@x.in", "9800000001", "Ravi", "TRAINER", List.of()));

        verify(recipients).upsertProfile(7L, "r@x.in", "Ravi", "TRAINER", null);
    }

    @Test
    void anEventSentBeforeDutiesExistedMeansNone() {
        // The old shape of the message has no extraRoles at all.
        UserCreatedEvent old = new UserCreatedEvent("ev", Instant.now(), 8L, "c@x.in", "9", "Chetan", "COORDINATOR", false);

        service.userChanged(old);

        verify(recipients).upsertProfile(8L, "c@x.in", "Chetan", "COORDINATOR", null);
    }
}

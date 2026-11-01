package com.itilms.identity.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.itilms.common.event.EventPublisher;
import com.itilms.common.event.KafkaTopics;
import com.itilms.common.exception.BusinessRuleException;
import com.itilms.common.security.AppPrincipal;
import com.itilms.identity.entity.User;
import com.itilms.identity.entity.UserRole;
import com.itilms.identity.entity.UserStatus;
import com.itilms.identity.repository.PasswordResetTokenRepository;
import com.itilms.identity.repository.RefreshTokenRepository;
import com.itilms.identity.repository.UserRepository;

/** Deleting a user is a soft delete: signed out, wiped, and the phone and e-mail are free to be used again. */
class UserDeleteTest {

    private static final long ADMIN_ID = 1L;
    private static final long STUDENT_ID = 3L;

    private final UserRepository users = mock(UserRepository.class);
    private final RefreshTokenRepository tokens = mock(RefreshTokenRepository.class);
    private final PasswordResetTokenRepository resets = mock(PasswordResetTokenRepository.class);
    private final PasswordEncoder encoder = mock(PasswordEncoder.class);
    private final EventPublisher events = mock(EventPublisher.class);
    private final UserServiceImpl service = new UserServiceImpl(users, tokens, resets, encoder, events, mock(com.itilms.identity.service.RolesVersionStore.class));

    private User student;

    @BeforeEach
    void setUp() {
        AppPrincipal admin = new AppPrincipal(ADMIN_ID, "admin@x", "Admin", "ADMIN", null);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(admin, null, admin.authorities()));
        when(encoder.encode(any())).thenReturn("unusable-hash");
        student = User.builder().id(STUDENT_ID).firstName("Raj").lastName("Bhagwat")
                .email("raj@gmail.com").phone("8010342585").passwordHash("old").role(UserRole.STUDENT)
                .status(UserStatus.INACTIVE).build();
        when(users.findById(STUDENT_ID)).thenReturn(Optional.of(student));
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void theAccountIsWipedSoItsPhoneAndEmailCanBeUsedAgain() {
        service.delete(STUDENT_ID, "test account");

        assertThat(student.getStatus()).isEqualTo(UserStatus.DELETED);
        assertThat(student.getPhone()).isNull();
        assertThat(student.getEmail()).isEqualTo("deleted-3@deleted.invalid");
        assertThat(student.getFirstName()).isEqualTo("Deleted");
        assertThat(student.getPasswordHash()).isEqualTo("unusable-hash");
        assertThat(student.getDeletedAt()).isNotNull();
        assertThat(student.getStatus().canSignIn()).isFalse();
        verify(users).save(student);
    }

    @Test
    void sessionsAndResetCodesEnd() {
        service.delete(STUDENT_ID, "left the institute");

        verify(tokens).revokeAllForUser(eq(STUDENT_ID), any());
        verify(resets).invalidateAllForUser(eq(STUDENT_ID), any());
    }

    @Test
    void otherServicesAreToldToForgetTheDetailsAndStopNotifying() {
        service.delete(STUDENT_ID, "left the institute");

        verify(events).publishAfterCommit(eq(KafkaTopics.USER_UPDATED), any());
        verify(events).publishAfterCommit(eq(KafkaTopics.USER_STATUS_CHANGED), any());
    }

    @Test
    void theAuditKeepsTheReasonButNotTheWipedEmailOrPhone() {
        service.delete(STUDENT_ID, "left the institute");

        verify(events).audit(eq("identity-service"), eq("USER_DELETED"), eq("User"), eq(STUDENT_ID),
                org.mockito.ArgumentMatchers.argThat(m -> !m.toString().contains("raj@gmail.com")
                        && !m.toString().contains("8010342585")),
                org.mockito.ArgumentMatchers.argThat(m -> m.toString().contains("left the institute")));
    }

    @Test
    void deletingTwiceIsNotAnError() {
        service.delete(STUDENT_ID, "first");
        org.mockito.Mockito.clearInvocations(users, tokens, events);

        service.delete(STUDENT_ID, "again");

        verify(users, never()).save(any());
        verify(events, never()).publishAfterCommit(any(), any());
    }

    @Test
    void anAdministratorCannotDeleteThemselves() {
        User self = User.builder().id(ADMIN_ID).firstName("A").lastName("B").email("admin@x").passwordHash("h")
                .role(UserRole.ADMIN).status(UserStatus.ACTIVE).build();
        when(users.findById(ADMIN_ID)).thenReturn(Optional.of(self));

        assertThatThrownBy(() -> service.delete(ADMIN_ID, "oops")).isInstanceOf(BusinessRuleException.class);
        assertThat(self.getStatus()).isEqualTo(UserStatus.ACTIVE);
    }

    @Test
    void theLastActiveAdministratorCannotBeDeleted() {
        User other = User.builder().id(9L).firstName("O").lastName("A").email("o@x").passwordHash("h")
                .role(UserRole.ADMIN).status(UserStatus.ACTIVE).build();
        when(users.findById(9L)).thenReturn(Optional.of(other));
        when(users.countByRoleAndStatus(UserRole.ADMIN, UserStatus.ACTIVE)).thenReturn(1L);

        assertThatThrownBy(() -> service.delete(9L, "cleanup")).isInstanceOf(BusinessRuleException.class);
        verify(users, never()).save(any());
    }

    @Test
    void anotherAdministratorCanBeDeletedWhenThereIsMoreThanOne() {
        User other = User.builder().id(9L).firstName("O").lastName("A").email("o@x").passwordHash("h")
                .role(UserRole.ADMIN).status(UserStatus.ACTIVE).build();
        when(users.findById(9L)).thenReturn(Optional.of(other));
        when(users.countByRoleAndStatus(UserRole.ADMIN, UserStatus.ACTIVE)).thenReturn(2L);

        service.delete(9L, "left");

        assertThat(other.getStatus()).isEqualTo(UserStatus.DELETED);
        verify(users).save(other);
    }

    @Test
    void anUnknownUserIsNotFound() {
        when(users.findById(anyLong())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(404L, "x"))
                .isInstanceOf(com.itilms.common.exception.ResourceNotFoundException.class);
    }
}

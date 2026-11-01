package com.itilms.identity.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.itilms.common.event.EventPublisher;
import com.itilms.common.exception.BusinessRuleException;
import com.itilms.common.exception.ResourceNotFoundException;
import com.itilms.identity.entity.User;
import com.itilms.identity.entity.UserRole;
import com.itilms.identity.entity.UserStatus;
import com.itilms.identity.repository.PasswordResetTokenRepository;
import com.itilms.identity.repository.RefreshTokenRepository;
import com.itilms.identity.repository.UserRepository;
import com.itilms.identity.security.JwtIssuer;
import com.itilms.identity.service.RolesVersionStore;

import com.itilms.common.security.JwtProperties;
import com.itilms.common.security.JwtVerifier;

/** An administrator can give a person extra duties (a trainer who also coordinates), and take them away again. */
class UserDutiesTest {

    private final UserRepository users = mock(UserRepository.class);
    private final EventPublisher events = mock(EventPublisher.class);
    private final RolesVersionStore versions = mock(RolesVersionStore.class);
    private final UserServiceImpl service = new UserServiceImpl(users, mock(RefreshTokenRepository.class),
            mock(PasswordResetTokenRepository.class), mock(PasswordEncoder.class), events, versions);

    private User trainer;

    @BeforeEach
    void setUp() {
        trainer = User.builder().id(7L).firstName("Ravi").lastName("Kulkarni").email("ravi@x.test").phone("9800000001")
                .passwordHash("h").role(UserRole.TRAINER).status(UserStatus.ACTIVE).profileId(3L).build();
        when(users.findById(7L)).thenReturn(Optional.of(trainer));
        when(versions.publish(anyLong(), anyInt())).thenReturn(true);
    }

    @Test
    void aTrainerCanAlsoBeGivenTheCoordinatorsDuty() {
        var response = service.updateDuties(7L, List.of("COORDINATOR"));

        assertThat(response.role()).isEqualTo("TRAINER");           // the main role does not change
        assertThat(response.extraRoles()).containsExactly("COORDINATOR");
        assertThat(trainer.extraRoleList()).containsExactly("COORDINATOR");
        assertThat(trainer.getProfileId()).isEqualTo(3L);            // the trainer profile stays
    }

    @Test
    void theGatewayIsToldAtOnceWithTheNewVersionAndThePersonIsNotified() {
        service.updateDuties(7L, List.of("COORDINATOR"));

        assertThat(trainer.getRolesVersion()).isEqualTo(1);
        verify(versions).publish(7L, 1);
        verify(events).notifyUsers(eq(List.of(7L)), eq("DUTIES"), eq("Your duties changed"), any(), any());
        verify(events).audit(eq("identity-service"), eq("USER_DUTIES_CHANGED"), eq("User"), eq(7L), any(), any());
    }

    @Test
    void withdrawingTheDutyEmptiesTheListAndMovesTheVersionOnAgain() {
        service.updateDuties(7L, List.of("COORDINATOR", "FINANCE"));
        var response = service.updateDuties(7L, List.of());

        assertThat(response.extraRoles()).isEmpty();
        assertThat(trainer.getExtraRoles()).isNull();
        assertThat(trainer.getRolesVersion()).isEqualTo(2);
        verify(versions).publish(7L, 2);
    }

    @Test
    void theDutiesAreKeptInAFixedOrderWithoutDuplicates() {
        var response = service.updateDuties(7L, List.of("placement", "COORDINATOR", "coordinator", " finance "));

        assertThat(response.extraRoles()).containsExactly("COORDINATOR", "FINANCE", "PLACEMENT");
    }

    @Test
    void sayingTheSameThingAgainChangesNothingAndTellsNoOne() {
        service.updateDuties(7L, List.of("COORDINATOR"));
        org.mockito.Mockito.clearInvocations(versions, events);

        service.updateDuties(7L, List.of("COORDINATOR"));

        assertThat(trainer.getRolesVersion()).isEqualTo(1);
        verify(versions, never()).publish(anyLong(), anyInt());
        verify(events, never()).notifyUsers(any(), any(), any(), any(), any());
    }

    @Test
    void adminStudentAndTrainerCannotBeGivenAsAnExtraDuty() {
        for (String forbidden : List.of("ADMIN", "STUDENT", "TRAINER", "SUPERUSER", "")) {
            assertThatThrownBy(() -> service.updateDuties(7L, List.of(forbidden)))
                    .as(forbidden).isInstanceOf(BusinessRuleException.class);
        }
        assertThat(trainer.extraRoleList()).isEmpty();
        assertThat(trainer.getRolesVersion()).isZero();
    }

    @Test
    void theMainRoleCannotBeRepeatedAsAnExtraOne() {
        User coordinator = User.builder().id(8L).firstName("Chetan").lastName("Joshi").email("c@x.test")
                .passwordHash("h").role(UserRole.COORDINATOR).status(UserStatus.ACTIVE).build();
        when(users.findById(8L)).thenReturn(Optional.of(coordinator));

        assertThatThrownBy(() -> service.updateDuties(8L, List.of("COORDINATOR")))
                .isInstanceOf(BusinessRuleException.class);
        assertThat(service.updateDuties(8L, List.of("FINANCE")).extraRoles()).containsExactly("FINANCE");
    }

    @Test
    void aStudentAndAnAdministratorCannotHaveExtraDuties() {
        User student = User.builder().id(9L).firstName("Asha").lastName("Patil").email("a@x.test").passwordHash("h")
                .role(UserRole.STUDENT).status(UserStatus.ACTIVE).build();
        User admin = User.builder().id(10L).firstName("Anita").lastName("Rao").email("n@x.test").passwordHash("h")
                .role(UserRole.ADMIN).status(UserStatus.ACTIVE).build();
        when(users.findById(9L)).thenReturn(Optional.of(student));
        when(users.findById(10L)).thenReturn(Optional.of(admin));

        assertThatThrownBy(() -> service.updateDuties(9L, List.of("COORDINATOR"))).isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> service.updateDuties(10L, List.of("COORDINATOR"))).isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void aDeletedAccountAndAMissingOneAreRefused() {
        trainer.setDeletedAt(java.time.Instant.now());
        assertThatThrownBy(() -> service.updateDuties(7L, List.of("COORDINATOR"))).isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> service.updateDuties(404L, List.of("COORDINATOR"))).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void ifTheGatewayCannotBeToldTheChangeIsStillSavedAndTheAuditSaysSo() {
        when(versions.publish(anyLong(), anyInt())).thenReturn(false);

        service.updateDuties(7L, List.of("FINANCE"));

        assertThat(trainer.extraRoleList()).containsExactly("FINANCE");
        verify(events).audit(any(), eq("USER_DUTIES_CHANGED"), any(), any(), any(),
                org.mockito.ArgumentMatchers.argThat(v -> v.toString().contains("gatewayTold=false")));
    }

    @Test
    void theTokenCarriesTheDutiesAndTheVersionSoTheGatewayCanTellItIsStale() {
        JwtProperties props = new JwtProperties();
        props.setSecret("0123456789abcdef0123456789abcdef0123456789abcdef");
        JwtIssuer issuer = new JwtIssuer(props);
        trainer.changeExtraRoles(List.of("COORDINATOR", "FINANCE"));

        JwtVerifier verifier = new JwtVerifier(props);
        var principal = verifier.verifyAccessToken(issuer.issueAccessToken(trainer));

        assertThat(principal).isNotNull();
        assertThat(principal.role()).isEqualTo("TRAINER");
        assertThat(principal.extraRoles()).containsExactly("COORDINATOR", "FINANCE");
        assertThat(principal.isStaff()).isTrue();
        assertThat(principal.trainerIdOrNull()).isEqualTo(3L);
        var claims = verifier.parse(issuer.issueAccessToken(trainer));
        assertThat(claims.get(JwtVerifier.CLAIM_ROLES_VERSION, Integer.class)).isEqualTo(1);
    }

    @Test
    void aTokenOfSomeoneWithNoExtraDutiesCarriesNone() {
        JwtProperties props = new JwtProperties();
        props.setSecret("0123456789abcdef0123456789abcdef0123456789abcdef");
        var principal = new JwtVerifier(props).verifyAccessToken(new JwtIssuer(props).issueAccessToken(trainer));

        assertThat(principal.extraRoles()).isEmpty();
        assertThat(principal.isStaff()).isFalse();
    }
}

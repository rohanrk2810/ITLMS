package com.itilms.common.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

class AppPrincipalTest {

    private static AppPrincipal trainerAlso(String... extras) {
        return new AppPrincipal(7L, "ravi@x.test", "Ravi Kulkarni", Roles.TRAINER, 3L, List.of(extras));
    }

    @Test
    void aTrainerWithoutExtraDutiesIsOnlyATrainer() {
        AppPrincipal p = new AppPrincipal(7L, "ravi@x.test", "Ravi", Roles.TRAINER, 3L);

        assertThat(p.extraRoles()).isEmpty();
        assertThat(p.isTrainer()).isTrue();
        assertThat(p.isStaff()).isFalse();
        assertThat(p.hasRole(Roles.COORDINATOR)).isFalse();
        assertThat(p.authorities()).extracting(Object::toString).containsExactly("ROLE_TRAINER");
    }

    @Test
    void aTrainerWhoAlsoCoordinatesIsBothAndKeepsTheTrainerProfile() {
        AppPrincipal p = trainerAlso(Roles.COORDINATOR);

        assertThat(p.isTrainer()).isTrue();
        assertThat(p.isStaff()).isTrue();
        assertThat(p.hasRole(Roles.COORDINATOR)).isTrue();
        assertThat(p.trainerIdOrNull()).isEqualTo(3L);
        assertThat(p.studentIdOrNull()).isNull();
        assertThat(p.isAdmin()).isFalse();
    }

    @Test
    void everyDutyIsAnAuthoritySoPreAuthorizeLetsThePersonInThroughAny() {
        AppPrincipal p = trainerAlso(Roles.COORDINATOR, Roles.FINANCE);

        assertThat(p.authorities()).extracting(Object::toString)
                .containsExactlyInAnyOrder("ROLE_TRAINER", "ROLE_COORDINATOR", "ROLE_FINANCE");
    }

    @Test
    void anExtraDutyOtherThanCoordinatorDoesNotMakeSomeoneStaff() {
        AppPrincipal p = new AppPrincipal(8L, "f@x.test", "Farah", Roles.PLACEMENT, null, List.of(Roles.FINANCE));

        assertThat(p.isStaff()).isFalse();
        assertThat(p.hasRole(Roles.FINANCE)).isTrue();
        assertThat(p.hasRole(Roles.PLACEMENT)).isTrue();
    }

    @Test
    void aCoordinatorWithExtraFinanceDutyIsStaffAndIsNotATrainer() {
        AppPrincipal p = new AppPrincipal(9L, "c@x.test", "Chetan", Roles.COORDINATOR, null, List.of(Roles.FINANCE));

        assertThat(p.isStaff()).isTrue();
        assertThat(p.isTrainer()).isFalse();
        assertThat(p.trainerIdOrNull()).isNull();
    }

    @Test
    void aMissingListIsTreatedAsNoExtraDutiesAndTheListCannotBeChangedAfterwards() {
        AppPrincipal none = new AppPrincipal(1L, "a@x.test", "A", Roles.TRAINER, 1L, null);
        assertThat(none.extraRoles()).isEmpty();

        java.util.ArrayList<String> mutable = new java.util.ArrayList<>(List.of(Roles.COORDINATOR));
        AppPrincipal copy = new AppPrincipal(1L, "a@x.test", "A", Roles.TRAINER, 1L, mutable);
        mutable.add(Roles.ADMIN);
        assertThat(copy.hasRole(Roles.ADMIN)).isFalse();
    }

    @Test
    void asksAboutANullRoleAreFalse() {
        assertThat(trainerAlso(Roles.COORDINATOR).hasRole(null)).isFalse();
    }
}

package com.itilms.liveclass.service;

import org.springframework.stereotype.Component;

import com.itilms.common.exception.ForbiddenOperationException;
import com.itilms.common.security.AppPrincipal;
import com.itilms.common.security.SecurityUtils;
import com.itilms.liveclass.client.BatchClient;
import com.itilms.liveclass.entity.LiveSession;

import lombok.RequiredArgsConstructor;

/**
 * Who may run a live class: the class trainer, and staff (administrators and coordinators).
 *
 * <p>Every host-level action - ending the class, removing someone, switching microphones off, asking a question -
 * goes through here, in the service and not only in the controller's role annotation: a role says "some trainer",
 * and only this check knows whether it is the trainer of THIS class.
 */
@Component
@RequiredArgsConstructor
public class HostAccess {

    private final BatchClient batchClient;

    /** Staff pass; a trainer must teach the batch; anyone else is refused. */
    public AppPrincipal requireHostOf(LiveSession session) {
        AppPrincipal caller = SecurityUtils.requirePrincipal();
        if (caller.isStaff()) {
            return caller;
        }
        if (!caller.isTrainer()) {
            throw new ForbiddenOperationException("Only the class trainer or staff can do this.");
        }
        requireTeaches(caller, session);
        return caller;
    }

    /**
     * The named trainer on the session is checked first because it needs no network call. Co-trainers are then
     * confirmed against the caller's own batch list, which batch-service scopes by their token, and which comes back
     * empty - refusing them - if batch-service cannot be reached.
     */
    public void requireTeaches(AppPrincipal trainer, LiveSession session) {
        if (trainer.profileId() != null && trainer.profileId().equals(session.getTrainerId())) {
            return;
        }
        var mine = batchClient.myBatches();
        boolean coTrainer = mine != null && mine.stream().anyMatch(batch -> session.getBatchId().equals(batch.id()));
        if (!coTrainer) {
            throw new ForbiddenOperationException("This class belongs to a batch you do not teach.");
        }
    }

    /** A host of the class, or a student enrolled in it - the two audiences a recording is for. */
    public void requireHostOrEnrolled(LiveSession session) {
        AppPrincipal caller = SecurityUtils.requirePrincipal();
        if (caller.isStaff() || caller.isTrainer()) {
            requireHostOf(session);
            return;
        }
        if (caller.isStudent()) {
            requireEnrolled(caller, session);
            return;
        }
        throw new ForbiddenOperationException("You cannot view this class.");
    }

    /** A student must hold an active place in the batch, checked now rather than remembered. */
    public void requireEnrolled(AppPrincipal student, LiveSession session) {
        if (student.profileId() == null) {
            throw new ForbiddenOperationException("Your account is not linked to a student profile yet.");
        }
        var check = batchClient.isEnrolled(session.getBatchId(), student.profileId());
        if (check == null || !check.enrolled()) {
            throw new ForbiddenOperationException("You are not enrolled in the batch this class belongs to.");
        }
    }
}
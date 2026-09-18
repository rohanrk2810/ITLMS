package com.itilms.common.event;

/**
 * Every Kafka topic in IT-ILMS, named in one place.
 *
 * <p>Naming: {@code itilms.<context>.<event>}. Topics are per-event rather than
 * per-service so a consumer subscribes to exactly the facts it cares about and
 * a producer can be split later without renaming anything.
 */
public final class KafkaTopics {

    private KafkaTopics() {
    }

    /** identity-service -> everyone who caches user display data. */
    public static final String USER_CREATED = "itilms.identity.user-created";
    public static final String USER_UPDATED = "itilms.identity.user-updated";
    public static final String USER_STATUS_CHANGED = "itilms.identity.user-status-changed";

    /** admission-service -> finance (fee plan), notification (welcome). */
    public static final String STUDENT_ADMITTED = "itilms.admission.student-admitted";
    public static final String LEAD_CONVERTED = "itilms.admission.lead-converted";
    /** admission-service -> identity, so the access token can carry the profile id. */
    public static final String PROFILE_LINKED = "itilms.admission.profile-linked";

    /** batch-service -> course (create progress rows), notification, finance. */
    public static final String ENROLLMENT_CREATED = "itilms.batch.enrollment-created";
    public static final String ENROLLMENT_CLOSED = "itilms.batch.enrollment-closed";
    public static final String SESSION_SCHEDULED = "itilms.batch.session-scheduled";
    public static final String SESSION_RESCHEDULED = "itilms.batch.session-rescheduled";
    /** batch-service -> liveclass, so a called-off class does not leave an open room. */
    public static final String SESSION_CANCELLED = "itilms.batch.session-cancelled";
    public static final String ATTENDANCE_MARKED = "itilms.batch.attendance-marked";

    /** course-service -> notification, certificate. */
    public static final String COURSE_PUBLISHED = "itilms.course.published";
    public static final String LESSON_PUBLISHED = "itilms.course.lesson-published";
    public static final String COURSE_PROGRESS_UPDATED = "itilms.course.progress-updated";

    /** assessment-service -> notification, certificate, reporting. */
    public static final String ASSIGNMENT_CREATED = "itilms.assessment.assignment-created";
    public static final String SUBMISSION_EVALUATED = "itilms.assessment.submission-evaluated";
    public static final String QUIZ_PUBLISHED = "itilms.assessment.quiz-published";
    public static final String QUIZ_ATTEMPT_COMPLETED = "itilms.assessment.quiz-attempt-completed";

    /** finance-service -> notification, reporting. */
    public static final String PAYMENT_RECORDED = "itilms.finance.payment-recorded";
    public static final String FEE_PLAN_CREATED = "itilms.finance.fee-plan-created";
    public static final String INSTALLMENT_OVERDUE = "itilms.finance.installment-overdue";

    /** certificate-service -> notification. */
    public static final String CERTIFICATE_ISSUED = "itilms.certificate.issued";

    /** placement-service -> notification. */
    public static final String JOB_POSTED = "itilms.placement.job-posted";
    public static final String APPLICATION_STAGE_CHANGED = "itilms.placement.application-stage-changed";

    /** liveclass-service -> batch (auto attendance), notification. */
    public static final String LIVE_CLASS_STARTED = "itilms.liveclass.started";
    public static final String LIVE_CLASS_ENDED = "itilms.liveclass.ended";
    public static final String LIVE_PARTICIPANT_JOINED = "itilms.liveclass.participant-joined";
    public static final String LIVE_PARTICIPANT_LEFT = "itilms.liveclass.participant-left";
    public static final String LIVE_ATTENDANCE_COMPUTED = "itilms.liveclass.attendance-computed";

    /** Any service -> notification-service. The generic fan-out channel. */
    public static final String NOTIFICATION_REQUESTED = "itilms.notification.requested";

    /** Any service -> reporting-service, which owns the audit log store. */
    public static final String AUDIT_RECORDED = "itilms.audit.recorded";
}

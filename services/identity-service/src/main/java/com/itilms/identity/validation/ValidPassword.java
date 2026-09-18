package com.itilms.identity.validation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

/**
 * Applies the institute's password policy.
 *
 * <p>The rules are configuration, not constants: an institute that has to meet
 * a particular standard can tighten them without a code change, and the error
 * message always describes the policy actually in force rather than a hard-coded
 * sentence that has drifted out of date.
 */
@Documented
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = PasswordPolicyValidator.class)
public @interface ValidPassword {

    String message() default "Password does not meet the required policy";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}

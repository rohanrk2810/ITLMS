package com.itilms.identity.validation;

import java.util.ArrayList;
import java.util.List;

import com.itilms.identity.config.IdentityProperties;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import lombok.RequiredArgsConstructor;

/**
 * Checks a password against the configured policy and, when it fails, says
 * exactly which rules were missed.
 *
 * <p>"Password is invalid" makes a user guess. Listing the unmet rules — and
 * only the unmet ones — lets them fix it on the next attempt, which in practice
 * produces stronger passwords than a vague error that pushes people toward the
 * shortest thing that finally works.
 */
@RequiredArgsConstructor
public class PasswordPolicyValidator implements ConstraintValidator<ValidPassword, String> {

    private final IdentityProperties properties;

    @Override
    public boolean isValid(String password, ConstraintValidatorContext context) {
        if (password == null || password.isBlank()) {
            return fail(context, "Password is required");
        }

        var policy = properties.getSecurity().getPassword();
        List<String> unmet = new ArrayList<>();

        if (password.length() < policy.getMinLength()) {
            unmet.add("at least " + policy.getMinLength() + " characters");
        }
        if (policy.isRequireUpper() && password.chars().noneMatch(Character::isUpperCase)) {
            unmet.add("an uppercase letter");
        }
        if (policy.isRequireLower() && password.chars().noneMatch(Character::isLowerCase)) {
            unmet.add("a lowercase letter");
        }
        if (policy.isRequireDigit() && password.chars().noneMatch(Character::isDigit)) {
            unmet.add("a digit");
        }
        if (policy.isRequireSymbol()
                && password.chars().noneMatch(ch -> !Character.isLetterOrDigit(ch) && !Character.isWhitespace(ch))) {
            unmet.add("a special character");
        }

        // BCrypt silently ignores bytes past 72; a longer password would appear
        // accepted while the tail contributed nothing to the hash.
        if (password.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72) {
            return fail(context, "Password must be at most 72 bytes long");
        }

        if (unmet.isEmpty()) {
            return true;
        }
        return fail(context, "Password must contain " + String.join(", ", unmet));
    }

    private boolean fail(ConstraintValidatorContext context, String message) {
        context.disableDefaultConstraintViolation();
        context.buildConstraintViolationWithTemplate(message).addConstraintViolation();
        return false;
    }
}

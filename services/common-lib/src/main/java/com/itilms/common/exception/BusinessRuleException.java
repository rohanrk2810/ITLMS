package com.itilms.common.exception;

import org.springframework.http.HttpStatus;

/**
 * 422 - the request is well-formed but violates a rule from Doc Section 14,
 * for example issuing a certificate before completion criteria are satisfied,
 * or enrolling a student into a batch that is already full.
 */
public class BusinessRuleException extends ApiException {

    public BusinessRuleException(String message) {
        super(HttpStatus.UNPROCESSABLE_ENTITY, "BUSINESS_RULE_VIOLATION", message);
    }

    public BusinessRuleException(String code, String message) {
        super(HttpStatus.UNPROCESSABLE_ENTITY, code, message);
    }
}

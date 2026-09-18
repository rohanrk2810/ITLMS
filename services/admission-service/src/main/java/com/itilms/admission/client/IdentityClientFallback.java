package com.itilms.admission.client;

import java.util.List;

import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import com.itilms.common.exception.ApiException;
import com.itilms.common.exception.BusinessRuleException;

import lombok.extern.slf4j.Slf4j;

/**
 * What to do when identity-service cannot be reached.
 *
 * <p>The two calls get opposite treatment, because the consequence of failure is
 * opposite:
 *
 * <ul>
 *   <li><b>createUser fails loudly.</b> There is no sensible partial admission —
 *       a student profile pointing at an account that was never created is
 *       corrupt data that someone has to untangle by hand later. Better to
 *       refuse the admission now and let the coordinator retry.</li>
 *   <li><b>lookup degrades quietly.</b> It only decorates a list with names. A
 *       roster missing display names is mildly worse than usual; a roster that
 *       fails to load because a supporting service is restarting is much
 *       worse.</li>
 * </ul>
 */
@Slf4j
@Component
public class IdentityClientFallback implements FallbackFactory<IdentityClient> {

    @Override
    public IdentityClient create(Throwable cause) {
        return new IdentityClient() {

            @Override
            public CreatedUser createUser(CreateUserPayload payload) {
                log.error("identity-service unreachable while creating an account for {}",
                        payload.email(), cause);

                // Pass a 4xx through unchanged: "email already registered" is a
                // real answer the user can act on, not an outage.
                if (cause instanceof ApiException apiException) {
                    throw apiException;
                }
                throw new BusinessRuleException("IDENTITY_UNAVAILABLE",
                        "The account service is not responding, so the admission was not completed. "
                                + "No partial record has been created. Please try again shortly.");
            }

            @Override
            public List<UserSummary> lookup(List<Long> userIds) {
                log.warn("identity-service unreachable during bulk user lookup of {} id(s); "
                        + "continuing without display names", userIds.size());
                return List.of();
            }
        };
    }
}

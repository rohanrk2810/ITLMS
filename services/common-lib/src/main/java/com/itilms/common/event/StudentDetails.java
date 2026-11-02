package com.itilms.common.event;

import java.time.LocalDate;

/**
 * What a student typed on the registration form besides their name, email and phone.
 *
 * <p>Travels on {@link UserCreatedEvent} so admission-service can open the profile
 * complete, rather than empty. Null on sign-ups that could not ask (Google, WhatsApp).
 */
public record StudentDetails(
        LocalDate dateOfBirth,
        String gender,
        String highestEducation,
        String college,
        Integer graduationYear,
        String addressLine,
        String city,
        String state,
        String pincode,
        String guardianName,
        String guardianPhone,
        String emergencyContact
) {
}

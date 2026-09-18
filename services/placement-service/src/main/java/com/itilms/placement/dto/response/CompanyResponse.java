package com.itilms.placement.dto.response;

import com.itilms.placement.entity.Company;

public record CompanyResponse(Long id, String name, String industry, String website, String location,
                              String contactName, String contactEmail, String contactPhone, String notes,
                              boolean active) {

    public static CompanyResponse from(Company c) {
        return new CompanyResponse(c.getId(), c.getName(), c.getIndustry(), c.getWebsite(), c.getLocation(),
                c.getContactName(), c.getContactEmail(), c.getContactPhone(), c.getNotes(), c.isActive());
    }
}

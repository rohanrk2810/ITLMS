package com.itilms.identity.branding;

import java.time.Instant;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * The institute's name and look. Exactly one row (id = 1): each institute runs its own
 * deployment and database, so there is nothing to key it by. Images are stored here
 * rather than in file-service because the login page needs them before anyone has a token.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "institute_settings")
public class InstituteSettings {

    public static final short ROW_ID = 1;

    @Id
    private Short id = ROW_ID;

    private String name;
    private String tagline;

    @Column(name = "primary_color")
    private String primaryColor;

    @Column(name = "contact_email")
    private String contactEmail;

    @Column(name = "contact_phone")
    private String contactPhone;

    private String website;
    private String address;

    @Column(name = "signatory_name")
    private String signatoryName;

    @Column(name = "signatory_title")
    private String signatoryTitle;

    @JdbcTypeCode(SqlTypes.VARBINARY)
    @Column(columnDefinition = "bytea")
    private byte[] logo;

    @Column(name = "logo_type")
    private String logoType;

    @JdbcTypeCode(SqlTypes.VARBINARY)
    @Column(columnDefinition = "bytea")
    private byte[] favicon;

    @Column(name = "favicon_type")
    private String faviconType;

    @JdbcTypeCode(SqlTypes.VARBINARY)
    @Column(name = "login_background", columnDefinition = "bytea")
    private byte[] loginBackground;

    @Column(name = "login_background_type")
    private String loginBackgroundType;

    /** Bumped on every change; part of the image URLs so browsers refetch after an edit. */
    private long version = 1;

    @Column(name = "updated_at")
    private Instant updatedAt = Instant.now();

    @Column(name = "updated_by")
    private Long updatedBy;
}

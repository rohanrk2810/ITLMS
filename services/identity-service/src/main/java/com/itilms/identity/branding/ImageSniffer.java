package com.itilms.identity.branding;

import com.itilms.common.exception.BusinessRuleException;

/**
 * Decides an upload's real type from its first bytes, ignoring what the client claimed.
 * SVG is refused on purpose: it can carry script, and these images are served from the
 * application's own origin to everyone, including the login page.
 */
final class ImageSniffer {

    private ImageSniffer() {
    }

    static String detect(byte[] b) {
        if (b == null || b.length == 0) {
            throw new BusinessRuleException("The image is empty");
        }
        if (b.length > BrandingService.MAX_IMAGE_BYTES) {
            throw new BusinessRuleException("The image is larger than 1 MB");
        }
        if (b.length >= 8 && (b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G') {
            return "image/png";
        }
        if (b.length >= 3 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF) {
            return "image/jpeg";
        }
        if (b.length >= 12 && b[0] == 'R' && b[1] == 'I' && b[2] == 'F' && b[3] == 'F'
                && b[8] == 'W' && b[9] == 'E' && b[10] == 'B' && b[11] == 'P') {
            return "image/webp";
        }
        if (b.length >= 4 && b[0] == 0 && b[1] == 0 && b[2] == 1 && b[3] == 0) {
            return "image/x-icon";
        }
        throw new BusinessRuleException("Use a PNG, JPEG, WebP or ICO image (SVG is not accepted)");
    }
}

package storage.dto;

import storage.model.ShareLink;

import java.time.Instant;

public record ShareLinkResponse(
        String token,
        String url,
        Instant expiresAt
) {
    public static ShareLinkResponse from(ShareLink link, String baseUrl) {
        return new ShareLinkResponse(
                link.getToken(),
                baseUrl + "/api/files/shared/" + link.getToken(),
                link.getExpiresAt()
        );
    }
}

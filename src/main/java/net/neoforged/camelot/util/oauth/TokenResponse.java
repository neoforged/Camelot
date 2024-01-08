package net.neoforged.camelot.util.oauth;

import java.time.Instant;

/**
 * A token returned by the OAuth flow.
 *
 * @param accessToken the token
 * @param expiration the time of expiration of the tokens
 */
public record TokenResponse(
        String accessToken,
        Instant expiration
) {
}

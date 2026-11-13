package hu.elte.ik.thesis.cinegrade.domain.token;

import lombok.*;

import java.time.Instant;

@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class RefreshToken {
    private int id;
    // [AI-GENERATED] Plain value in memory only; RefreshTokenDao encrypts it with DPAPI on disk.
    // No userId here: user_session.refresh_token_id points at this row (one direction only, like
    // sessions -> users on the backend), because the cookie arrives before /users/me says who the user is.
    private String refreshToken;
    private Instant createdAt;
    private Instant lastUsedAt;
}

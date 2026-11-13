// [AI-GENERATED]
package hu.elte.ik.thesis.cinegrade.domain.user;

import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
public class User {

    private final int id;
    private final String username;
    private final String email;
    private String avatarUrl;
    // refresh_tokens.id of the session this user came from; deleting that row deletes this one
    private int refreshTokenId;
    private Instant lastLogin;
    private final Instant createdAt;

    public User(int id, String username, String email, String avatarUrl, int refreshTokenId, Instant lastLogin, Instant createdAt) {
        this.id = id;
        this.username = username;
        this.email = email;
        this.avatarUrl = avatarUrl;
        this.refreshTokenId = refreshTokenId;
        this.lastLogin = lastLogin;
        this.createdAt = createdAt;
    }
}

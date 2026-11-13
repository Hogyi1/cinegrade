// [CONTAINS AI-GENERATED CODE: load, insert, save, mapRowToToken, encrypt, decrypt]
package hu.elte.ik.thesis.cinegrade.infra.database.dao.token;

import com.sun.jna.platform.win32.Crypt32Util;
import com.sun.jna.platform.win32.Win32Exception;
import hu.elte.ik.thesis.cinegrade.domain.token.RefreshToken;
import hu.elte.ik.thesis.cinegrade.infra.database.AppDatabase;

import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;

public class RefreshTokenDao {

    private final Connection connection;

    public RefreshTokenDao() {
        this.connection = AppDatabase.getInstance().getConnection();
    }

    // [AI-GENERATED METHOD]
    // The newest row. A value that can't be decrypted (another Windows user, a copied database,
    // an old plain-text row) is thrown away, which simply means "logged out".
    public Optional<RefreshToken> load() throws SQLException {
        String sql = "SELECT id, refresh_token, last_used_at, created_at FROM refresh_tokens ORDER BY id DESC LIMIT 1";

        try (PreparedStatement pstmt = connection.prepareStatement(sql);
             ResultSet rs = pstmt.executeQuery()) {
            if (rs.next()) {
                return Optional.of(mapRowToToken(rs));
            }
        } catch (Win32Exception | IllegalArgumentException ex) {
            deleteToken();
        }
        return Optional.empty();
    }

    public void updateLastUsedAt(int id, Instant newDate) throws SQLException {
        String sql = "UPDATE refresh_tokens SET last_used_at = ? WHERE id = ?";

        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setTimestamp(1, Timestamp.from(newDate));
            pstmt.setInt(2, id);
            pstmt.executeUpdate();
        }
    }


    public void updateToken(int id, String refreshToken) throws SQLException {
        String sql = "UPDATE refresh_tokens SET refresh_token = ? WHERE id = ?";

        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, encrypt(refreshToken));
            pstmt.setInt(2, id);
            pstmt.executeUpdate();
        }
    }

    // [AI-GENERATED METHOD]
    // Returns the new id and also sets it on the token, so user_session can point at it.
    public int insert(RefreshToken token) throws SQLException {
        String sql = "INSERT INTO refresh_tokens (refresh_token) VALUES (?)";

        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, encrypt(token.getRefreshToken()));
            pstmt.executeUpdate();
        }
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT last_insert_rowid()")) {
            rs.next();
            token.setId(rs.getInt(1));
            return token.getId();
        }
    }

    private RefreshToken mapRowToToken(ResultSet rs) throws SQLException {
        Timestamp lastUsedTs = rs.getTimestamp("last_used_at");
        Timestamp createdTs = rs.getTimestamp("created_at");

        Instant lastUsedAt = (lastUsedTs != null) ? lastUsedTs.toInstant() : null;
        Instant createdAt = (createdTs != null) ? createdTs.toInstant() : null;

        return new RefreshToken(
                rs.getInt("id"),
                decrypt(rs.getString("refresh_token")),
                createdAt,
                lastUsedAt
        );
    }

    // Also removes the user_session row that points at it (ON DELETE CASCADE)
    public boolean deleteToken() throws SQLException {
        String sql = "DELETE FROM refresh_tokens";

        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.executeUpdate();
            return true;
        }
    }

    // [AI-GENERATED METHOD]
    // One session per desktop: the old row (and with it the cached user) goes, the new one comes in.
    // null only clears, which is a logout.
    public Optional<RefreshToken> save(String raw) throws SQLException {
        deleteToken();
        if (raw == null) {
            return Optional.empty();
        }
        Instant now = Instant.now();
        RefreshToken token = RefreshToken.builder().refreshToken(raw).createdAt(now).lastUsedAt(now).build();
        insert(token);
        return Optional.of(token);
    }

    // [AI-GENERATED METHOD]
    // DPAPI ties the ciphertext to the current Windows user, so the database file alone is useless to anyone else
    private static String encrypt(String raw) {
        return Base64.getEncoder().encodeToString(Crypt32Util.cryptProtectData(raw.getBytes(StandardCharsets.UTF_8)));
    }

    // [AI-GENERATED METHOD]
    private static String decrypt(String stored) {
        return new String(Crypt32Util.cryptUnprotectData(Base64.getDecoder().decode(stored)), StandardCharsets.UTF_8);
    }
}

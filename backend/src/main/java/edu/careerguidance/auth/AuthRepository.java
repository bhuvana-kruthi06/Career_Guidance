package edu.careerguidance.auth;

import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

@Repository
@ConditionalOnProperty(prefix = "career-guidance.auth", name = "enabled", havingValue = "true")
public class AuthRepository {
    private final JdbcTemplate jdbc;
    public AuthRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public Optional<UserAccount> findUserByEmail(String email) {
        try {
            return Optional.of(jdbc.queryForObject("SELECT id,email,password_hash,role,is_active,full_name,account_status,last_active_at FROM users WHERE email=?",
                    (rs, row) -> new UserAccount(rs.getLong("id"), rs.getString("email"),
                            rs.getString("password_hash"), rs.getString("role"), rs.getBoolean("is_active"), rs.getString("full_name"), rs.getString("account_status"),
                            rs.getTimestamp("last_active_at") == null ? null : rs.getTimestamp("last_active_at").toInstant()), email));
        } catch (EmptyResultDataAccessException ex) { return Optional.empty(); }
    }

    public Optional<UserAccount> findUserById(long id) {
        try {
            return Optional.of(jdbc.queryForObject("SELECT id,email,password_hash,role,is_active,full_name,account_status,last_active_at FROM users WHERE id=?",
                    (rs, row) -> new UserAccount(rs.getLong("id"), rs.getString("email"),
                            rs.getString("password_hash"), rs.getString("role"), rs.getBoolean("is_active"), rs.getString("full_name"), rs.getString("account_status"),
                            rs.getTimestamp("last_active_at") == null ? null : rs.getTimestamp("last_active_at").toInstant()), id));
        } catch (EmptyResultDataAccessException ex) { return Optional.empty(); }
    }

    public UserAccount createUser(String email, String passwordHash, String fullName) {
        GeneratedKeyHolder keys = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO users(email,password_hash,role,is_active,full_name,account_status) VALUES(?,?,'USER',TRUE,?,'ACTIVE')", new String[] {"id"});
            statement.setString(1, email);
            statement.setString(2, passwordHash);
            statement.setString(3, fullName);
            return statement;
        }, keys);
        Number id = keys.getKey();
        if (id == null) throw new IllegalStateException("Database did not return a generated account id.");
        return findUserById(id.longValue()).orElseThrow();
    }

    public UserAccount createAdmin(String email, String passwordHash, String fullName) {
        GeneratedKeyHolder keys = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO users(email,password_hash,role,is_active,full_name,account_status) VALUES(?,?,'ADMIN',TRUE,?,'ACTIVE')", new String[] {"id"});
            statement.setString(1, email); statement.setString(2, passwordHash); statement.setString(3, fullName); return statement;
        }, keys);
        Number id = keys.getKey();
        if (id == null) throw new IllegalStateException("Database did not return a generated admin id.");
        return findUserById(id.longValue()).orElseThrow();
    }

    public void touchLastActive(long userId) { jdbc.update("UPDATE users SET last_active_at=CURRENT_TIMESTAMP WHERE id=?", userId); }

    public Optional<RefreshTokenRecord> findRefreshToken(String hash) {
        try {
            return Optional.of(jdbc.queryForObject("SELECT id,user_id,token_hash,expires_at,revoked_at FROM refresh_tokens WHERE token_hash=?",
                    (rs, row) -> new RefreshTokenRecord(rs.getLong("id"), rs.getLong("user_id"), rs.getString("token_hash"),
                            rs.getTimestamp("expires_at").toInstant(), rs.getTimestamp("revoked_at") == null ? null : rs.getTimestamp("revoked_at").toInstant()), hash));
        } catch (EmptyResultDataAccessException ex) { return Optional.empty(); }
    }

    public void storeRefreshToken(long userId, String hash, Instant expiresAt) {
        jdbc.update("INSERT INTO refresh_tokens(user_id,token_hash,expires_at) VALUES(?,?,?)",
                userId, hash, Timestamp.from(expiresAt));
    }

    public boolean revokeRefreshToken(long id, Instant replacementExpiry, String replacementHash) {
        return jdbc.update("UPDATE refresh_tokens SET revoked_at=CURRENT_TIMESTAMP,replaced_by_hash=? WHERE id=? AND revoked_at IS NULL AND expires_at>?",
                replacementHash, id, Timestamp.from(replacementExpiry)) == 1;
    }

    public void revokeToken(long id) {
        jdbc.update("UPDATE refresh_tokens SET revoked_at=CURRENT_TIMESTAMP WHERE id=? AND revoked_at IS NULL", id);
    }

    public void revokeAllRefreshTokens(long userId) {
        jdbc.update("UPDATE refresh_tokens SET revoked_at=CURRENT_TIMESTAMP WHERE user_id=? AND revoked_at IS NULL", userId);
    }

    public int deleteExpiredTokens(Instant before) {
        return jdbc.update("DELETE FROM refresh_tokens WHERE expires_at<? OR revoked_at<?", Timestamp.from(before), Timestamp.from(before));
    }
}

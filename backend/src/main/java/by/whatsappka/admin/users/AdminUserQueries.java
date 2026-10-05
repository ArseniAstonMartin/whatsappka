package by.whatsappka.admin.users;

import by.whatsappka.platform.web.ApiException;
import by.whatsappka.platform.web.CursorPage;
import by.whatsappka.platform.web.PageSize;
import by.whatsappka.search.SearchRules;
import java.nio.charset.StandardCharsets;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Список пользователей для администратора: поиск по логину и имени, страницы по логину. */
@Service
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class AdminUserQueries {

    public record UserRow(UUID id, String username, String displayName, String status, boolean verified, List<String> roles) {
    }

    private final JdbcTemplate jdbc;

    public AdminUserQueries(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public CursorPage<UserRow> list(String rawQuery, String cursor, Integer limit) {
        int size = PageSize.limit(limit);
        Keyset key = decode(cursor);
        StringBuilder sql = new StringBuilder("""
                SELECT u.id, u.username, p.display_name, u.status, (p.verified_at IS NOT NULL) AS verified,
                       ARRAY(SELECT ur.role_code FROM user_roles ur WHERE ur.user_id = u.id ORDER BY ur.role_code) AS roles
                FROM users u JOIN user_profiles p ON p.user_id = u.id
                WHERE 1 = 1
                """);
        List<Object> params = new ArrayList<>();
        String query = rawQuery == null ? "" : SearchRules.normalize(rawQuery);
        if (!query.isEmpty()) {
            sql.append(" AND (whatsappka_search_norm(u.username) LIKE ? ESCAPE '!' OR whatsappka_search_norm(p.display_name) LIKE ? ESCAPE '!')");
            String contains = SearchRules.pattern(query, false);
            params.add(contains);
            params.add(contains);
        }
        if (key != null) {
            sql.append(" AND (u.username, u.id) > (CAST(? AS text), CAST(? AS uuid))");
            params.add(key.username());
            params.add(key.id());
        }
        sql.append(" ORDER BY u.username, u.id LIMIT ?");
        params.add(size + 1);
        List<UserRow> rows = jdbc.query(sql.toString(), (rs, n) -> row(rs), params.toArray());
        boolean more = rows.size() > size;
        List<UserRow> shown = new ArrayList<>(more ? rows.subList(0, size) : rows);
        String next = more ? encode(shown.get(shown.size() - 1).username(), shown.get(shown.size() - 1).id()) : null;
        return new CursorPage<>(shown, next, more);
    }

    private static UserRow row(ResultSet rs) throws SQLException {
        String[] roles = (String[]) rs.getArray("roles").getArray();
        return new UserRow(
                UUID.fromString(rs.getString("id")),
                rs.getString("username"),
                rs.getString("display_name"),
                rs.getString("status"),
                rs.getBoolean("verified"),
                List.of(roles));
    }

    private record Keyset(String username, UUID id) {
    }

    static String encode(String username, UUID id) {
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString((username + "|" + id).getBytes(StandardCharsets.UTF_8));
    }

    static Keyset decode(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return null;
        }
        try {
            String raw = new String(Base64.getUrlDecoder().decode(cursor.trim()), StandardCharsets.UTF_8);
            int separator = raw.lastIndexOf('|');
            return new Keyset(raw.substring(0, separator), UUID.fromString(raw.substring(separator + 1)));
        } catch (RuntimeException malformed) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "invalid_cursor", "Курсор страницы некорректен", List.of(), null);
        }
    }
}

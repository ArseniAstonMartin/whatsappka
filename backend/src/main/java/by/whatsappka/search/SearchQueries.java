package by.whatsappka.search;

import by.whatsappka.platform.web.ApiException;
import by.whatsappka.platform.web.CursorPage;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Поиск людей и сообществ страницами по 20. Курсор — ранг, название и id последнего показанного результата. */
@Service
public class SearchQueries {

    public record UserHit(UUID id, String username, String displayName) {
    }

    /** У приватной группы restricted = true: описания и аватара в ответе нет. */
    public record GroupHit(UUID id, String slug, String name, String visibility, boolean restricted, UUID avatarMediaId) {
    }

    private final JdbcTemplate jdbc;

    public SearchQueries(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public CursorPage<UserHit> users(UUID viewer, String rawQuery, String cursor, Integer limit) {
        String query = SearchRules.requireQuery(rawQuery);
        int size = SearchRules.pageSize(limit);
        Cursor key = decode(cursor);
        String prefix = SearchRules.pattern(query, true);
        String contains = SearchRules.pattern(query, false);
        List<Object> params = new ArrayList<>(List.of(prefix, prefix, viewer, contains, contains, viewer, viewer));
        String sql;
        if (key == null) {
            sql = SearchSql.USERS_FIRST;
        } else {
            sql = SearchSql.USERS_AFTER;
            params.addAll(List.of(key.rank(), key.name(), key.id()));
        }
        params.add(size + 1);
        List<Hit> rows = jdbc.query(sql, (rs, n) -> new Hit(
                UUID.fromString(rs.getString("id")),
                rs.getInt("hit_rank"),
                rs.getString("username"),
                rs.getString("display_name"),
                null,
                null,
                false), params.toArray());
        boolean more = rows.size() > size;
        List<Hit> shown = more ? rows.subList(0, size) : rows;
        List<UserHit> items = shown.stream().map(h -> new UserHit(h.id(), h.username(), h.displayName())).toList();
        String next = more ? encode(shown.get(shown.size() - 1)) : null;
        return new CursorPage<>(items, next, more);
    }

    @Transactional(readOnly = true)
    public CursorPage<GroupHit> groups(String rawQuery, String cursor, Integer limit) {
        String query = SearchRules.requireQuery(rawQuery);
        int size = SearchRules.pageSize(limit);
        Cursor key = decode(cursor);
        String prefix = SearchRules.pattern(query, true);
        String contains = SearchRules.pattern(query, false);
        // Параметры по порядку: ранги (префикс названия, префикс slug, подстрока названия и slug), затем отбор по описанию.
        List<Object> params = new ArrayList<>(List.of(prefix, prefix, contains, contains, contains, contains, contains));
        String sql;
        if (key == null) {
            sql = SearchSql.GROUPS_FIRST;
        } else {
            sql = SearchSql.GROUPS_AFTER;
            params.addAll(List.of(key.rank(), key.name(), key.id()));
        }
        params.add(size + 1);
        List<Hit> rows = jdbc.query(sql, (rs, n) -> new Hit(
                UUID.fromString(rs.getString("id")),
                rs.getInt("hit_rank"),
                null,
                rs.getString("name"),
                rs.getString("slug"),
                rs.getString("visibility"),
                !"PUBLIC".equals(rs.getString("visibility"))), params.toArray());
        boolean more = rows.size() > size;
        List<Hit> shown = more ? rows.subList(0, size) : rows;
        List<GroupHit> items = shown.stream()
                .map(h -> new GroupHit(h.id(), h.slug(), h.name(), h.visibility(), h.restricted(), null))
                .toList();
        String next = more ? encode(shown.get(shown.size() - 1)) : null;
        return new CursorPage<>(items, next, more);
    }

    /** Внутренняя строка страницы; курсор строится из ранга, названия и id. */
    private record Hit(UUID id, int rank, String username, String displayName, String slug, String visibility,
                       boolean restricted) {
        String name() {
            return displayName;
        }
    }

    private record Cursor(int rank, String name, UUID id) {
    }

    static String encode(Hit last) {
        String name = last.username() != null ? last.username() : last.name();
        String raw = last.rank() + "|" + name + "|" + last.id();
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    static Cursor decode(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return null;
        }
        try {
            String raw = new String(Base64.getUrlDecoder().decode(cursor.trim()), StandardCharsets.UTF_8);
            int first = raw.indexOf('|');
            int last = raw.lastIndexOf('|');
            return new Cursor(Integer.parseInt(raw.substring(0, first)), raw.substring(first + 1, last),
                    UUID.fromString(raw.substring(last + 1)));
        } catch (RuntimeException malformed) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "invalid_cursor", "Курсор страницы некорректен",
                    List.of(), null);
        }
    }
}

package by.whatsappka.identity.security;

import java.util.List;
import java.util.UUID;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

/** Принципал запроса: роли считаны из БД на этом запросе, а не из токена. */
public record AuthenticatedUser(UUID userId, UUID sessionId, String username, List<String> roles) {

    public AuthenticatedUser {
        roles = List.copyOf(roles);
    }

    public List<GrantedAuthority> authorities() {
        return roles.stream()
                .<GrantedAuthority>map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                .toList();
    }
}

package by.whatsappka.identity.security;

import by.whatsappka.platform.web.ApiResponses;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Правила доступа на сервере. Гость видит только явно перечисленные маршруты; роли читаются из БД на каждом запросе.
 */
@Configuration
@EnableWebSecurity
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class SecurityConfiguration {

    @Bean
    public SecurityFilterChain apiSecurityFilterChain(HttpSecurity http, AccessService access, ObjectMapper mapper)
            throws Exception {
        return http
                // Bearer-токены браузер не прикладывает сам. Cookie-операции защищены SameSite=Lax и проверкой Origin.
                .csrf(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.POST,
                                "/api/v1/auth/register",
                                "/api/v1/auth/login",
                                "/api/v1/auth/refresh",
                                "/api/v1/auth/logout").permitAll()
                        .requestMatchers(HttpMethod.GET,
                                "/api/v1/auth/google/start",
                                "/api/v1/auth/google/callback").permitAll()
                        .requestMatchers("/error", "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
                        .requestMatchers("/api/v1/moderation/**").hasAnyRole("MODERATOR", "ADMIN")
                        .anyRequest().authenticated())
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint((request, response, ex) ->
                                write(request, response, mapper, HttpStatus.UNAUTHORIZED, "unauthorized",
                                        "Нужна действующая сессия"))
                        .accessDeniedHandler((request, response, ex) ->
                                write(request, response, mapper, HttpStatus.FORBIDDEN, "forbidden",
                                        "Действие запрещено")))
                .addFilterBefore(new BearerAuthenticationFilter(access), UsernamePasswordAuthenticationFilter.class)
                .build();
    }

    /** Отключает пароль, который Boot иначе генерирует и пишет в журнал. Пользователи здесь не входят. */
    @Bean
    public UserDetailsService noFormUsers() {
        return username -> {
            throw new UsernameNotFoundException("Вход по имени пользователя не поддерживается");
        };
    }

    private static void write(
            HttpServletRequest request,
            HttpServletResponse response,
            ObjectMapper mapper,
            HttpStatus status,
            String code,
            String detail
    ) throws IOException {
        response.setStatus(status.value());
        response.setContentType(ApiResponses.PROBLEM_JSON.toString());
        mapper.writeValue(response.getOutputStream(), ApiResponses.of(
                request, status, code, detail, List.of(), null).getBody());
    }
}

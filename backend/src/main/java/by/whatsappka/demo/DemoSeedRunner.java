package by.whatsappka.demo;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.env.Environment;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Демо-данные для локальной проверки. Работает только с профилем demo и явным флагом DEMO_ENABLED=true,
 * никогда не в production и не в процессе worker. Пароль берётся из DEMO_PASSWORD и в журнал не попадает.
 * Повторный запуск безопасен: seed использует фиксированные идентификаторы и ON CONFLICT DO NOTHING.
 */
@Component
@ConditionalOnProperty(prefix = "whatsappka.demo", name = "enabled", havingValue = "true")
@ConditionalOnProperty(prefix = "whatsappka.jobs", name = "worker-enabled", havingValue = "false", matchIfMissing = true)
public class DemoSeedRunner implements ApplicationRunner {

    static final String PLACEHOLDER = "{{DEMO_PASSWORD_HASH}}";
    static final int MIN_PASSWORD = 10;

    private static final Logger log = LoggerFactory.getLogger(DemoSeedRunner.class);

    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private final PasswordEncoder passwordEncoder;
    private final Environment environment;
    private final String password;

    public DemoSeedRunner(JdbcTemplate jdbc, PlatformTransactionManager transactions, PasswordEncoder passwordEncoder,
                          Environment environment, @Value("${whatsappka.demo.password:}") String password) {
        this.jdbc = jdbc;
        this.tx = new TransactionTemplate(transactions);
        this.passwordEncoder = passwordEncoder;
        this.environment = environment;
        this.password = password;
    }

    @Override
    public void run(ApplicationArguments args) throws IOException {
        if (java.util.Arrays.asList(environment.getActiveProfiles()).contains("production")) {
            throw new IllegalStateException("Демо-данные не загружаются в production");
        }
        if (password == null || password.length() < MIN_PASSWORD) {
            throw new IllegalStateException("Задайте DEMO_PASSWORD локально: не короче " + MIN_PASSWORD + " символов");
        }
        String sql = new ClassPathResource("demo/seed.sql")
                .getContentAsString(StandardCharsets.UTF_8)
                .replace(PLACEHOLDER, passwordEncoder.encode(password));
        tx.executeWithoutResult(status -> jdbc.execute(sql));
        log.info("Демо-данные проверены и загружены; повторный запуск безопасен");
    }
}

package by.whatsappka.identity;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class BootstrapAdminRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(BootstrapAdminRunner.class);

    private final BootstrapAdminService bootstrap;
    private final BootstrapAdminProperties properties;

    public BootstrapAdminRunner(BootstrapAdminService bootstrap, BootstrapAdminProperties properties) {
        this.bootstrap = bootstrap;
        this.properties = properties;
    }

    @Override
    public void run(ApplicationArguments args) {
        // Без настроек первого администратора транзакция не открывается: иначе API не стартует при недоступном PostgreSQL.
        if (!properties.requested()) {
            return;
        }
        // Пароль и email в журнал не пишутся.
        if (bootstrap.createIfAbsent(properties)) {
            log.info("Создан первый администратор из настроек окружения");
        }
    }
}

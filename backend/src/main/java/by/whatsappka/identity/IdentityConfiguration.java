package by.whatsappka.identity;

import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@EnableConfigurationProperties({IdentityProperties.class, BootstrapAdminProperties.class})
public class IdentityConfiguration {

    @Bean
    public PasswordEncoder passwordEncoder(IdentityProperties properties) {
        return new BCryptPasswordEncoder(properties.bcryptStrength());
    }
}

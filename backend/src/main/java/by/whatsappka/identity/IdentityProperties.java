package by.whatsappka.identity;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("whatsappka.security")
public record IdentityProperties(
        @NotBlank @Size(min = 32) String jwtSecret,
        @DefaultValue("true") boolean refreshCookieSecure,
        @DefaultValue("10") @Min(4) @Max(31) int bcryptStrength,
        @NotEmpty List<String> allowedOrigins
) {
}

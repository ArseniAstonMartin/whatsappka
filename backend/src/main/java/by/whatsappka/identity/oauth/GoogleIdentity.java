package by.whatsappka.identity.oauth;

/** Утверждения из проверенного ID token, нужные для входа: стабильный subject и подтверждённый email. */
public record GoogleIdentity(String subject, String email, boolean emailVerified) {
}

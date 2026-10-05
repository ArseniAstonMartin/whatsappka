package by.whatsappka.identity.oauth;

import java.util.UUID;

/** Состояние одного обращения к Google: state и nonce защищают от подмены, verifier — PKCE. */
public record OAuthState(String state, String nonce, String codeVerifier, Mode mode, UUID userId) {

    public enum Mode {
        /** Вход или создание аккаунта. */
        LOGIN,
        /** Явная привязка Google к уже вошедшему аккаунту. */
        LINK
    }
}

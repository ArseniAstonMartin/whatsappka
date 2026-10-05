package by.whatsappka.identity.recovery;

/**
 * SQL одноразовых токенов аккаунта. Проверка и отметка использования идут под блокировкой строки,
 * поэтому два параллельных запроса с одной ссылкой не пройдут оба.
 */
final class AccountTokenSql {

    private AccountTokenSql() {
    }

    /** При выпуске нового токена предыдущие того же назначения перестают действовать. */
    static final String RETIRE_OPEN = """
            UPDATE account_tokens SET consumed_at = ?
            WHERE user_id = ? AND purpose = ? AND consumed_at IS NULL
            """;

    static final String INSERT = """
            INSERT INTO account_tokens (id, user_id, purpose, token_hash, expires_at, created_at)
            VALUES (?, ?, ?, ?, ?, ?)
            """;

    static final String FIND_FOR_UPDATE = """
            SELECT user_id, expires_at, consumed_at FROM account_tokens
            WHERE token_hash = ? AND purpose = ?
            FOR UPDATE
            """;

    static final String MARK_CONSUMED = """
            UPDATE account_tokens SET consumed_at = ? WHERE token_hash = ? AND purpose = ?
            """;
}

package by.whatsappka.platform.idempotency;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.UUID;

@Embeddable
public class IdempotencyRecordId implements Serializable {

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "operation", nullable = false, length = 100)
    private String operation;

    @Column(name = "key", nullable = false, length = 255)
    private String key;

    protected IdempotencyRecordId() {
    }

    public IdempotencyRecordId(UUID userId, String operation, String key) {
        this.userId = userId;
        this.operation = operation;
        this.key = key;
    }

    public UUID userId() {
        return userId;
    }

    public String operation() {
        return operation;
    }

    public String key() {
        return key;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof IdempotencyRecordId that)) {
            return false;
        }
        return userId.equals(that.userId) && operation.equals(that.operation) && key.equals(that.key);
    }

    @Override
    public int hashCode() {
        int result = userId.hashCode();
        result = 31 * result + operation.hashCode();
        result = 31 * result + key.hashCode();
        return result;
    }
}

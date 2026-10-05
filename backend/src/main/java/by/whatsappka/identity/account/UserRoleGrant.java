package by.whatsappka.identity.account;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "user_roles")
public class UserRoleGrant {

    @EmbeddedId
    private UserRoleId id;

    @Column(name = "granted_at", nullable = false)
    private Instant grantedAt;

    protected UserRoleGrant() {
    }

    public UserRoleGrant(UserRoleId id, Instant grantedAt) {
        this.id = id;
        this.grantedAt = grantedAt;
    }
}

package by.whatsappka.identity.session;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AuthSessionRepository extends JpaRepository<AuthSession, UUID> {

    @Query("""
            select s from AuthSession s
            where s.userId = :userId and s.revokedAt is null and s.absoluteExpiresAt > :now
            order by s.lastUsedAt desc
            """)
    List<AuthSession> findActiveByUserId(@Param("userId") UUID userId, @Param("now") Instant now);

    Optional<AuthSession> findByIdAndUserId(UUID id, UUID userId);
}

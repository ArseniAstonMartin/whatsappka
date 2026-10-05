package by.whatsappka.identity.account;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserAccountRepository extends JpaRepository<UserAccount, UUID> {

    /** Сравнение без учёта регистра использует функциональные уникальные индексы из V3. */
    @Query("select u from UserAccount u where lower(u.email) = lower(:email)")
    Optional<UserAccount> findByEmailIgnoreCase(@Param("email") String email);

    @Query("select u from UserAccount u where lower(u.username) = lower(:username)")
    Optional<UserAccount> findByUsernameIgnoreCase(@Param("username") String username);
}

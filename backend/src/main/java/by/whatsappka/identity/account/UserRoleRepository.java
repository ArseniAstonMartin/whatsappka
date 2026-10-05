package by.whatsappka.identity.account;

import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRoleRepository extends JpaRepository<UserRoleGrant, UserRoleId> {
}

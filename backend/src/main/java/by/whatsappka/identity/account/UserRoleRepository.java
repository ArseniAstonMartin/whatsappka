package by.whatsappka.identity.account;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRoleRepository extends JpaRepository<UserRoleGrant, UserRoleId> {

    @Query("select r.id.roleCode from UserRoleGrant r where r.id.userId = :userId")
    List<String> findRoleCodes(@Param("userId") UUID userId);

    @Query("select count(r) from UserRoleGrant r where r.id.roleCode = :roleCode")
    long countByRoleCode(@Param("roleCode") String roleCode);
}

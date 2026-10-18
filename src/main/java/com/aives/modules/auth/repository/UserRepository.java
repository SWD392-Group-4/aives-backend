package com.aives.modules.auth.repository;

import com.aives.modules.auth.entity.User;
import com.aives.modules.auth.enums.RoleName;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

@Repository
public interface UserRepository extends JpaRepository<User, String>, JpaSpecificationExecutor<User> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    // Tai khoan duoc tao som nhat cua 1 role (dung khi tao du lieu mau)
    Optional<User> findFirstByRole_NameOrderByCreatedAtAsc(RoleName name);

    long countByRole_Name(RoleName name);
}

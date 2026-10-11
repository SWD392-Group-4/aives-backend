package com.aives.modules.auth.repository;

import com.aives.modules.auth.entity.Role;
import com.aives.modules.auth.enums.RoleName;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface RoleRepository extends JpaRepository<Role, String> {

    Optional<Role> findByName(RoleName name);
}

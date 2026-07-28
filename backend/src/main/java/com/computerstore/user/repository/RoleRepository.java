package com.computerstore.user.repository;

import java.util.Optional;

import com.computerstore.user.domain.Role;
import com.computerstore.user.domain.RoleName;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RoleRepository extends JpaRepository<Role, Long> {

    Optional<Role> findByName(RoleName name);
}

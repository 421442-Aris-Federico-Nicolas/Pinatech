package com.computerstore.user.repository;

import java.util.Optional;

import com.computerstore.user.domain.UserAccount;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserAccountRepository extends JpaRepository<UserAccount, Long> {

    Optional<UserAccount> findByEmailIgnoreCase(String email);

    Optional<UserAccount> findByIdAndActiveTrue(Long id);
}

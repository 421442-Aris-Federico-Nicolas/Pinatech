package com.computerstore.user.repository;

import java.util.Optional;

import com.computerstore.user.domain.UserAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

public interface UserAccountRepository extends JpaRepository<UserAccount, Long> {

    Optional<UserAccount> findByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCase(String email);

    @Query(value = "select 1 from pg_advisory_xact_lock(hashtextextended(concat('account-email:', :normalizedEmail), 0))",
            nativeQuery = true)
    Integer lockNormalizedEmail(@Param("normalizedEmail") String normalizedEmail);

    Optional<UserAccount> findByIdAndActiveTrue(Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select user from UserAccount user where user.id = :id")
    Optional<UserAccount> findByIdForUpdate(@Param("id") Long id);
}

package com.computerstore.user.repository;

import java.time.Instant;
import java.util.Optional;

import com.computerstore.user.domain.AccountActionPurpose;
import com.computerstore.user.domain.AccountActionToken;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AccountActionTokenRepository extends JpaRepository<AccountActionToken, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select token from AccountActionToken token where token.tokenHash = :tokenHash")
    Optional<AccountActionToken> findByTokenHashForUpdate(@Param("tokenHash") String tokenHash);

    @Modifying(flushAutomatically = true)
    @Query("""
            update AccountActionToken token set token.consumedAt = :now, token.updatedAt = :now
            where token.user.id = :userId and token.purpose = :purpose and token.consumedAt is null
            """)
    int invalidateActive(@Param("userId") Long userId,
                         @Param("purpose") AccountActionPurpose purpose,
                         @Param("now") Instant now);

    @Modifying(flushAutomatically = true)
    @Query("""
            update AccountActionToken token set token.consumedAt = :now, token.updatedAt = :now
            where token.user.id = :userId and token.consumedAt is null
            """)
    int invalidateAllActive(@Param("userId") Long userId, @Param("now") Instant now);
}

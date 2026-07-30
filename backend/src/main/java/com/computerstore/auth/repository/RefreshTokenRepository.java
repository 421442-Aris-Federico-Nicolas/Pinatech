package com.computerstore.auth.repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import com.computerstore.auth.domain.RefreshToken;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    @Query("select refreshToken.familyId from RefreshToken refreshToken where refreshToken.tokenHash = :tokenHash")
    Optional<UUID> findFamilyIdByTokenHash(@Param("tokenHash") String tokenHash);

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select refreshToken from RefreshToken refreshToken
            where refreshToken.familyId = :familyId
              and refreshToken.id = (
                  select min(familyToken.id) from RefreshToken familyToken
                  where familyToken.familyId = :familyId
              )
            """)
    Optional<RefreshToken> lockFamily(@Param("familyId") UUID familyId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update RefreshToken refreshToken
            set refreshToken.revokedAt = :revokedAt
            where refreshToken.familyId = :familyId and refreshToken.revokedAt is null
            """)
    int revokeFamily(@Param("familyId") UUID familyId, @Param("revokedAt") Instant revokedAt);
}

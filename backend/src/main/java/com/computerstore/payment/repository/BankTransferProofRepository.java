package com.computerstore.payment.repository;

import com.computerstore.payment.domain.BankTransferProof;
import com.computerstore.payment.domain.BankTransferProofStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BankTransferProofRepository extends JpaRepository<BankTransferProof, UUID> {
    Optional<BankTransferProof> findByOrderId(Long orderId);
    @EntityGraph(attributePaths = {
            "order.user.firstName", "order.user.lastName", "order.user.email", "order.shipment", "previews"})
    List<BankTransferProof> findByStatusOrderBySubmittedAtAsc(BankTransferProofStatus status);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select proof from BankTransferProof proof where proof.id = :id")
    Optional<BankTransferProof> findByIdForUpdate(@Param("id") UUID id);
    boolean existsByBankReference(String bankReference);
    @Query("select proof from BankTransferProof proof where proof.storageKey is not null and proof.retainUntil <= :now")
    List<BankTransferProof> findDueForDeletion(@Param("now") Instant now);
    @Query("select proof.storageKey from BankTransferProof proof where proof.storageKey is not null")
    List<String> findAllRawStorageKeys();
    @Query("select preview.storageKey from BankTransferProofPreview preview")
    List<String> findAllPreviewStorageKeys();

    @Query("select p.storageKey from BankTransferProofPreview p where p.proof.id = :proofId and p.previewIndex = :index")
    Optional<String> findPreviewStorageKey(@Param("proofId") UUID proofId, @Param("index") int index);
}

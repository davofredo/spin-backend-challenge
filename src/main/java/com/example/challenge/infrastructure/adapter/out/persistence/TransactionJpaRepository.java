package com.example.challenge.infrastructure.adapter.out.persistence;

import com.example.challenge.domain.model.TransactionStatus;
import com.example.challenge.domain.model.TransactionType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data repository over {@link TransactionJpaEntity}. Never used outside this package.
 */
public interface TransactionJpaRepository extends JpaRepository<TransactionJpaEntity, UUID> {

    Optional<TransactionJpaEntity> findByIdempotencyKey(String idempotencyKey);

    @Query("""
            select t from TransactionJpaEntity t
            where (:accountId is null or t.accountId = :accountId)
              and (:status is null or t.status = :status)
              and (:type is null or t.type = :type)
            """)
    Page<TransactionJpaEntity> search(
            @Param("accountId") String accountId,
            @Param("status") TransactionStatus status,
            @Param("type") TransactionType type,
            Pageable pageable);
}

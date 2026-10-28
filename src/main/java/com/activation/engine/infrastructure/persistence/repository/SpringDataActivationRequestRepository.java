package com.activation.engine.infrastructure.persistence.repository;

import com.activation.engine.domain.model.RequestStatus;
import com.activation.engine.infrastructure.persistence.entity.ActivationRequestEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SpringDataActivationRequestRepository extends JpaRepository<ActivationRequestEntity, UUID> {

    Optional<ActivationRequestEntity> findByExternalId(String externalId);

    @Query("SELECT r FROM ActivationRequestEntity r WHERE r.status IN ('PENDING', 'PROCESSING') " +
           "AND r.updatedAt <= :updatedBefore AND r.retryCount < :maxRetries ORDER BY r.updatedAt ASC")
    List<ActivationRequestEntity> findEligibleForReconciliation(
            @Param("updatedBefore") Instant updatedBefore,
            @Param("maxRetries") int maxRetries,
            Pageable pageable);

    @Query("SELECT r FROM ActivationRequestEntity r WHERE " +
           "(:status IS NULL OR r.status = :status) AND " +
           "(:customerId IS NULL OR r.customerId = :customerId) " +
           "ORDER BY r.createdAt DESC")
    Page<ActivationRequestEntity> findAllFiltered(
            @Param("status") RequestStatus status,
            @Param("customerId") String customerId,
            Pageable pageable);

    @Query("SELECT COUNT(r) FROM ActivationRequestEntity r WHERE " +
           "(:status IS NULL OR r.status = :status) AND " +
           "(:customerId IS NULL OR r.customerId = :customerId)")
    long countFiltered(
            @Param("status") RequestStatus status,
            @Param("customerId") String customerId);
}

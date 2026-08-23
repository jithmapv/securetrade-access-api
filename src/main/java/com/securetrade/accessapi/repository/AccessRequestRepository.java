package com.securetrade.accessapi.repository;

import com.securetrade.accessapi.entity.AccessRequestEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface AccessRequestRepository extends JpaRepository<AccessRequestEntity, UUID> {

    @Override
    @EntityGraph(attributePaths = {"agent", "agent.user"})
    Optional<AccessRequestEntity> findById(UUID id);

    @Query(
            value = """
                    select request
                    from AccessRequestEntity request
                    join fetch request.agent agent
                    where agent.id = :agentId
                    """,
            countQuery = """
                    select count(request)
                    from AccessRequestEntity request
                    where request.agent.id = :agentId
                    """)
    Page<AccessRequestEntity> findByAgentId(
            @Param("agentId") UUID agentId,
            Pageable pageable);

    @Query("""
            select request
            from AccessRequestEntity request
            join fetch request.agent agent
            where agent.id = :agentId
              and request.idempotencyKey = :idempotencyKey
            """)
    Optional<AccessRequestEntity> findByAgentIdAndIdempotencyKey(
            @Param("agentId") UUID agentId,
            @Param("idempotencyKey") String idempotencyKey);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select request from AccessRequestEntity request where request.id = :requestId")
    Optional<AccessRequestEntity> findByIdForUpdate(
            @Param("requestId") UUID requestId);
}

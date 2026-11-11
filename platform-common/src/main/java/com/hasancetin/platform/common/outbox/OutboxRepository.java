package com.hasancetin.platform.common.outbox;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OutboxRepository extends JpaRepository<OutboxEvent, Long> {

    /**
     * Locks a batch of pending rows. {@code SKIP LOCKED} lets several service instances poll the same table
     * without blocking each other or publishing the same row twice at the same time.
     */
    @Query(value = """
            select * from outbox_event
            where status = 'PENDING'
            order by id
            limit :limit
            for update skip locked
            """, nativeQuery = true)
    List<OutboxEvent> lockPending(@Param("limit") int limit);
}

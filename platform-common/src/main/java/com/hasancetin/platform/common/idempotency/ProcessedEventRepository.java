package com.hasancetin.platform.common.idempotency;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProcessedEventRepository extends JpaRepository<ProcessedEvent, UUID> {

    /** Atomic "insert if absent": returns 1 for the first delivery and 0 for every duplicate. */
    @Modifying
    @Query(value = """
            insert into processed_event (event_id, processed_at)
            values (:eventId, now())
            on conflict (event_id) do nothing
            """, nativeQuery = true)
    int insertIfAbsent(@Param("eventId") UUID eventId);
}

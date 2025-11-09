package com.starscape.rapidupload.common.outbox;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface OutboxEventRepository extends JpaRepository<OutboxEvent, String> {
    
    @Query("SELECT e FROM OutboxEvent e WHERE e.processedAt IS NULL ORDER BY e.createdAt ASC")
    List<OutboxEvent> findUnprocessedEvents();
    
    @Query(value = "SELECT * FROM outbox_events WHERE processed_at IS NULL ORDER BY created_at ASC LIMIT :limit", nativeQuery = true)
    List<OutboxEvent> findUnprocessedEventsWithLimit(int limit);
}


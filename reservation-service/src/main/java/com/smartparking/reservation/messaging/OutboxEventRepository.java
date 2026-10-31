package com.smartparking.reservation.messaging;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {

	/**
	 * The oldest unsent events, locked for this transaction. {@code SKIP LOCKED}: a second relay run (another
	 * instance, or the poller and the after-commit kick at the same moment) takes other rows instead of
	 * waiting for these or sending them twice.
	 */
	@Query(value = """
			SELECT * FROM outbox_event WHERE sent_at IS NULL
			ORDER BY created_at LIMIT :limit FOR UPDATE SKIP LOCKED""", nativeQuery = true)
	List<OutboxEvent> lockUnsent(int limit);

	long countBySentAtIsNull();

	@Modifying
	@Query("delete from OutboxEvent e where e.sentAt < :before")
	int deleteSentBefore(Instant before);

}

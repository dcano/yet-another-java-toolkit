package io.twba.tk.cdc.outbox.jpa;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface OutboxMessageRepositoryJpaHelper extends JpaRepository<OutboxMessageEntity, String> {
}

package io.twba.tk.cdc.outbox.jpa;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface OutboxMessageRepositoryJpaHelper extends JpaRepository<OutboxMessageEntity, String> {

    @Modifying
    @Query("DELETE FROM OutboxMessageEntity o WHERE o.uuid = :uuid")
    void deleteByUuidJpql(@Param("uuid") String uuid);

}

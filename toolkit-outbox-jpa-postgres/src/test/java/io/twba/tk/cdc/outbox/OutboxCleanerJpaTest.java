package io.twba.tk.cdc.outbox;

import io.twba.tk.cdc.outbox.jpa.OutboxMessageRepositoryJpaHelper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class OutboxCleanerJpaTest {

    @Mock
    OutboxMessageRepositoryJpaHelper helper;

    @InjectMocks
    OutboxCleanerJpa cleaner;

    @Test
    void deletesRowByUuid() {
        cleaner.deleteByUuid("some-uuid");

        verify(helper).deleteByUuidJpql("some-uuid");
    }

    @Test
    void doesNotThrowWhenRowAbsent() {
        // deleteByUuidJpql is a JPQL DELETE: zero rows affected is fine, no exception
        assertThatCode(() -> cleaner.deleteByUuid("non-existent-uuid")).doesNotThrowAnyException();
    }
}

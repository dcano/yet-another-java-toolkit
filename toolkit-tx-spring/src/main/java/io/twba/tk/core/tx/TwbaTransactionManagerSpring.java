package io.twba.tk.core.tx;


import io.twba.tk.core.TwbaTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.DefaultTransactionDefinition;

public class TwbaTransactionManagerSpring implements TwbaTransactionManager {

    private final DefaultTransactionDefinition transactionDefinition;
    private final PlatformTransactionManager platformTransactionManager;
    private final ThreadLocal<TransactionStatus> transactionStatus = new ThreadLocal<>();

    public TwbaTransactionManagerSpring(PlatformTransactionManager platformTransactionManager) {
        this.platformTransactionManager = platformTransactionManager;
        transactionDefinition = new DefaultTransactionDefinition();
        transactionDefinition.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        transactionDefinition.setTimeout(10);
    }

    @Override
    public void begin() {
        transactionStatus.set(platformTransactionManager.getTransaction(transactionDefinition));
    }

    @Override
    public void commit() {
        platformTransactionManager.commit(transactionStatus.get());
        transactionStatus.remove();
    }

    @Override
    public void rollback() {
        platformTransactionManager.rollback(transactionStatus.get());
        transactionStatus.remove();
    }
}

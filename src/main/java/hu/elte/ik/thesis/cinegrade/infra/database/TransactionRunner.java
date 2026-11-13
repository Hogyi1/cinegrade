package hu.elte.ik.thesis.cinegrade.infra.database;

import hu.elte.ik.thesis.cinegrade.domain.enums.ErrorCode;
import hu.elte.ik.thesis.cinegrade.domain.exceptions.CineGradeException;
import lombok.Getter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public final class TransactionRunner implements AutoCloseable {

    private static final Logger logger = LogManager.getLogger(TransactionRunner.class);
    @Getter
    private final Connection connection;
    private final String name;
    private final ExecutorService dbThread;
    private volatile Thread owner;
    private boolean inTransaction; // only touched on the db thread

    public TransactionRunner(Connection connection, String name) {
        this.connection = connection;
        this.name = name;
        this.dbThread = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "cinegrade-db-" + name);
            t.setDaemon(true);
            owner = t;
            return t;
        });
    }

    public <T> CompletableFuture<T> inTransaction(SqlWork<T> work) {
        if (Thread.currentThread() == owner) {
            try {
                return CompletableFuture.completedFuture(runInTransaction(work));
            } catch (Exception ex) {
                return CompletableFuture.failedFuture(ex);
            }
        }
        return CompletableFuture.supplyAsync(() -> runInTransaction(work), dbThread);
    }

    public CompletableFuture<Void> execute(SqlAction action) {
        return inTransaction(c -> {
            action.run(c);
            return null;
        });
    }

    public <T> CompletableFuture<T> read(SqlWork<T> work) {
        if (Thread.currentThread() == owner) {
            try {
                return CompletableFuture.completedFuture(runPlain(work));
            } catch (Exception ex) {
                return CompletableFuture.failedFuture(ex);
            }
        }
        return CompletableFuture.supplyAsync(() -> runPlain(work), dbThread);
    }

    private <T> T runInTransaction(SqlWork<T> work) {
        if (inTransaction) {
            return runPlain(work);
        }

        inTransaction = true;
        try {
            connection.setAutoCommit(false);
            T result = work.run(connection);
            connection.commit();
            return result;
        } catch (Exception ex) {
            rollbackQuietly();
            if (ex instanceof CineGradeException cgex) {
                throw cgex;
            }
            throw new CineGradeException(ErrorCode.DB_TRANSACTION_FAILED, ex, ex.getMessage());
        } finally {
            inTransaction = false;
            try {
                connection.setAutoCommit(true);
            } catch (SQLException ex) {
                logger.warn("[{}] Could not restore auto-commit", name, ex);
            }
        }
    }

    private <T> T runPlain(SqlWork<T> work) {
        try {
            return work.run(connection);
        } catch (Exception ex) {
            if (ex instanceof CineGradeException cgex) {
                throw cgex;
            }
            throw new CineGradeException(ErrorCode.DB_EXECUTION_FAILED, ex, ex.getMessage());
        }
    }

    private void rollbackQuietly() {
        try {
            connection.rollback();
        } catch (SQLException ex) {
            throw new CineGradeException(ErrorCode.DB_EXECUTION_FAILED, ex, "Rollback failed");
        }
    }

    @Override
    public void close() {
        dbThread.shutdown();
        try {
            if (!dbThread.awaitTermination(10, TimeUnit.SECONDS)) {
                logger.warn("[{}] DB thread did not finish in time, forcing shutdown", name);
                dbThread.shutdownNow();
            }
        } catch (InterruptedException ex) {
            dbThread.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    @FunctionalInterface
    public interface SqlWork<T> {
        T run(Connection connection) throws SQLException, IOException;
    }

    @FunctionalInterface
    public interface SqlAction {
        void run(Connection connection) throws SQLException;
    }
}
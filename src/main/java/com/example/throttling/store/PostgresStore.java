package com.example.throttling.store;

import java.util.function.Function;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * A persistent {@link Store} that keeps each state as a row of the {@code rate_limit_state}
 * table in PostgreSQL.
 *
 * <p>State survives application restarts and is shared by every instance that uses the same
 * database. Atomicity comes from a transaction that locks the key's row with
 * {@code SELECT ... FOR UPDATE} while the update function runs, so concurrent updates of the same
 * key are serialized.
 *
 * <p>The table is created on construction if it does not exist. The number of rows is bounded by
 * clients times endpoints, so no cleanup is needed.
 */
public class PostgresStore implements Store {

    private final JdbcTemplate jdbc;
    private final TransactionTemplate transaction;

    public PostgresStore(JdbcTemplate jdbc, PlatformTransactionManager transactionManager) {
        this.jdbc = jdbc;
        this.transaction = new TransactionTemplate(transactionManager);

        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS rate_limit_state (
                    limiter_key TEXT PRIMARY KEY,
                    state       TEXT
                )
                """);
    }

    @Override
    public <T> T update(String key, Function<String, Outcome<T>> updater) {
        return transaction.execute(status -> {
            jdbc.update("""
                    INSERT INTO rate_limit_state (limiter_key, state) VALUES (?, NULL)
                    ON CONFLICT (limiter_key) DO NOTHING
                    """, key);

            String current = jdbc.queryForObject(
                    "SELECT state FROM rate_limit_state WHERE limiter_key = ? FOR UPDATE",
                    String.class, key);

            Outcome<T> outcome = updater.apply(current);

            jdbc.update("UPDATE rate_limit_state SET state = ? WHERE limiter_key = ?",
                    outcome.newState(), key);

            return outcome.result();
        });
    }
}
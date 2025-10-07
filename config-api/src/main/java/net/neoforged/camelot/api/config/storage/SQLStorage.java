package net.neoforged.camelot.api.config.storage;

import org.jdbi.v3.core.Jdbi;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.function.Function;

class SQLStorage<G> implements ConfigStorage<G> {
    private final Jdbi jdbi;
    private final String tableName;
    private final Function<G, Object> identifier;

    SQLStorage(Jdbi jdbi, String tableName, Function<G, Object> identifier) {
        this.jdbi = jdbi;
        this.tableName = tableName;
        this.identifier = identifier;
    }

    @Override
    public void store(String key, G target, String value) {
        jdbi.withHandle(handle -> handle.createUpdate("insert or replace into " + tableName + "(target, key, value) values (?, ?, ?)")
                .bind(0, identifier.apply(target))
                .bind(1, key)
                .bind(2, value)
                .execute());
    }

    @Override
    @Nullable
    public Optional<String> read(String key, G target) {
        return jdbi.withHandle(handle -> handle.createQuery("select value from " + tableName + " where target = ? and key = ?")
                .bind(0, identifier.apply(target))
                .bind(1, key)
                .execute((statementSupplier, ctx) -> {
                    var rs = statementSupplier.get().getResultSet();
                    if (rs.next()) {
                        return Optional.ofNullable(rs.getString(1));
                    }
                    return null;
                }));
    }
}

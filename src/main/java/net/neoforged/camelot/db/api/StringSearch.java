package net.neoforged.camelot.db.api;

@FunctionalInterface
public interface StringSearch {
    String asQuery();

    static StringSearch contains(String value) {
        return () -> "%" + value + "%";
    }
}

package com.glancesync.common.db;

import java.util.Arrays;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * What {@link SchemaInitializer} does to the database at startup, named after Hibernate's
 * {@code hibernate.hbm2ddl.auto} values.
 */
public enum DdlAuto {

    /** Leave the database alone. */
    NONE("none"),
    /** Fail startup when the database is missing a table or column that schema.sql defines. */
    VALIDATE("validate"),
    /** Apply schema.sql as-is: it only adds what is missing and never drops data. */
    UPDATE("update"),
    /** Drop everything, then apply schema.sql to an empty database. */
    CREATE("create"),
    /** Like {@link #CREATE}, and drop everything again on shutdown. */
    CREATE_DROP("create-drop");

    private final String value;

    DdlAuto(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }

    public static DdlAuto parse(String raw) {
        String wanted = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
        return Arrays.stream(values())
                .filter(mode -> mode.value.equals(wanted))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Unknown glancesync.db.ddl-auto (DB_DDL_AUTO) '" + raw
                        + "'. Use one of: " + Arrays.stream(values()).map(DdlAuto::value)
                        .collect(Collectors.joining(", "))));
    }
}

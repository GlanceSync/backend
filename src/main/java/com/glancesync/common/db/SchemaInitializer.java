package com.glancesync.common.db;

import io.micronaut.context.annotation.Value;
import io.micronaut.context.event.ShutdownEvent;
import io.micronaut.context.event.StartupEvent;
import io.micronaut.runtime.event.annotation.EventListener;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Brings the database in line with schema.sql at startup, the way Hibernate's ddl-auto does:
 * {@code glancesync.db.ddl-auto} (DB_DDL_AUTO) is one of none, validate, update, create or
 * create-drop; see {@link DdlAuto}. Separately, {@code glancesync.db.seed} (DB_SEED) loads the
 * 500 test profiles, which only ever adds rows that are missing.
 */
@Singleton
public class SchemaInitializer {

    private static final Logger LOG = LoggerFactory.getLogger(SchemaInitializer.class);

    private static final String DROP_ALL = "DROP SCHEMA public CASCADE; CREATE SCHEMA public;";

    private final Database database;
    private final DdlAuto mode;
    private final boolean seed;

    public SchemaInitializer(Database database,
                             @Value("${glancesync.db.ddl-auto:update}") String mode,
                             @Value("${glancesync.db.seed:false}") boolean seed) {
        this.database = database;
        this.mode = DdlAuto.parse(mode);
        this.seed = seed;
    }

    @EventListener
    void onStartup(StartupEvent event) {
        switch (mode) {
            case NONE -> LOG.info("ddl-auto=none: database schema left untouched");
            case VALIDATE -> validate();
            case UPDATE -> database.execute(readResource("schema.sql"));
            case CREATE, CREATE_DROP -> {
                LOG.warn("ddl-auto={}: dropping every table and all data", mode.value());
                database.execute(DROP_ALL);
                database.execute(readResource("schema.sql"));
            }
        }
        if (mode != DdlAuto.NONE && mode != DdlAuto.VALIDATE) {
            LOG.info("ddl-auto={}: database schema applied", mode.value());
        }

        if (seed) {
            if (mode == DdlAuto.NONE || mode == DdlAuto.VALIDATE) {
                LOG.warn("glancesync.db.seed is ignored with ddl-auto={}, which never writes", mode.value());
            } else {
                database.execute(readResource("seed-test-users.sql"));
                LOG.info("Seed data applied");
            }
        }
    }

    @EventListener
    void onShutdown(ShutdownEvent event) {
        if (mode == DdlAuto.CREATE_DROP) {
            LOG.warn("ddl-auto=create-drop: dropping every table and all data");
            database.execute(DROP_ALL);
        }
    }

    /**
     * Applies schema.sql to a scratch schema inside a transaction that is rolled back, then
     * compares its columns with the real ones. Comparing against what schema.sql actually
     * builds, rather than a hand-kept list, means the check can never drift from the schema.
     */
    private void validate() {
        String scratch = "ddl_validate_" + UUID.randomUUID().toString().replace("-", "");
        List<String> problems = database.inRolledBackTransaction(connection -> {
            try (Statement statement = connection.createStatement()) {
                statement.execute("CREATE SCHEMA " + scratch);
                // public stays on the path so PostGIS types such as geography still resolve.
                statement.execute("SET LOCAL search_path TO " + scratch + ", public");
                statement.execute(readResource("schema.sql"));
            }
            String sql = "SELECT e.table_name, e.column_name, e.udt_name, a.udt_name AS actual_udt, "
                    + "EXISTS (SELECT 1 FROM information_schema.tables t "
                    + "        WHERE t.table_schema = 'public' AND t.table_name = e.table_name) AS table_exists "
                    + "FROM information_schema.columns e "
                    + "LEFT JOIN information_schema.columns a ON a.table_schema = 'public' "
                    + "  AND a.table_name = e.table_name AND a.column_name = e.column_name "
                    + "WHERE e.table_schema = ? AND (a.column_name IS NULL OR a.udt_name <> e.udt_name) "
                    + "ORDER BY e.table_name, e.ordinal_position";
            List<String> found = new ArrayList<>();
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, scratch);
                try (ResultSet rs = statement.executeQuery()) {
                    while (rs.next()) {
                        String table = rs.getString("table_name");
                        String column = rs.getString("column_name");
                        if (!rs.getBoolean("table_exists")) {
                            String missing = "missing table " + table;
                            if (!found.contains(missing)) {
                                found.add(missing);
                            }
                        } else if (rs.getString("actual_udt") == null) {
                            found.add("missing column " + table + "." + column);
                        } else {
                            found.add("wrong type for " + table + "." + column + ": expected "
                                    + rs.getString("udt_name") + ", found " + rs.getString("actual_udt"));
                        }
                    }
                }
            }
            return found;
        });

        if (!problems.isEmpty()) {
            throw new IllegalStateException("ddl-auto=validate: the database does not match schema.sql ("
                    + String.join("; ", problems) + "). Run once with DB_DDL_AUTO=update to fix it.");
        }
        LOG.info("ddl-auto=validate: database schema matches schema.sql");
    }

    private String readResource(String name) {
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(name)) {
            if (in == null) {
                throw new IllegalStateException("Missing resource: " + name);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Could not read " + name, e);
        }
    }
}

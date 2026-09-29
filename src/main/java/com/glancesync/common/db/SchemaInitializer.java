package com.glancesync.common.db;

import io.micronaut.context.event.StartupEvent;
import io.micronaut.runtime.event.annotation.EventListener;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/** Applies schema.sql, then seed-interests.sql, at startup. Every statement in both is idempotent. */
@Singleton
public class SchemaInitializer {

    private static final Logger LOG = LoggerFactory.getLogger(SchemaInitializer.class);

    private final Database database;

    public SchemaInitializer(Database database) {
        this.database = database;
    }

    @EventListener
    void onStartup(StartupEvent event) {
        database.execute(readResource("schema.sql"));
        database.execute(readResource("seed-interests.sql"));
        LOG.info("Database schema and seed data applied");
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

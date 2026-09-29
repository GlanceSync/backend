package com.glancesync.common.db;

import io.micronaut.context.event.StartupEvent;
import io.micronaut.runtime.event.annotation.EventListener;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/** Applies schema.sql at startup. Every statement in it is idempotent. */
@Singleton
public class SchemaInitializer {

    private static final Logger LOG = LoggerFactory.getLogger(SchemaInitializer.class);

    private final Database database;

    public SchemaInitializer(Database database) {
        this.database = database;
    }

    @EventListener
    void onStartup(StartupEvent event) {
        String sql = readResource("schema.sql");
        database.execute(sql);
        LOG.info("Database schema applied");
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

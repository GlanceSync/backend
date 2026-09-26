package com.glancesync;

import io.github.cdimascio.dotenv.Dotenv;
import io.micronaut.runtime.Micronaut;

public class Application {

    public static void main(String[] args) {
        loadDotEnv();
        Micronaut.run(Application.class, args);
    }

    /**
     * Credentials live in a .env file at the repository root, which is git-ignored.
     * They are promoted to system properties so Micronaut's ${VAR} placeholders resolve.
     * Real environment variables always win, so deployments can override the file.
     */
    private static void loadDotEnv() {
        for (String dir : new String[]{".", "..", "../.."}) {
            Dotenv dotenv = Dotenv.configure()
                    .directory(dir)
                    .ignoreIfMalformed()
                    .ignoreIfMissing()
                    .load();
            dotenv.entries().forEach(entry -> {
                if (System.getenv(entry.getKey()) == null
                        && System.getProperty(entry.getKey()) == null) {
                    System.setProperty(entry.getKey(), entry.getValue());
                }
            });
        }
    }
}

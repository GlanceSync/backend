package com.glancesync.common.config;

import io.github.cdimascio.dotenv.Dotenv;
import io.micronaut.context.ApplicationContextBuilder;
import io.micronaut.context.ApplicationContextConfigurer;
import io.micronaut.context.annotation.ContextConfigurer;


@ContextConfigurer
public class DotEnvConfigurer implements ApplicationContextConfigurer {

    @Override
    public void configure(ApplicationContextBuilder builder) {
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

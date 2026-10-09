package com.glancesync.common.config;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.glancesync.modules.mail.config.MailConfig;
import com.glancesync.modules.oauth.service.ProviderAuthService;
import com.glancesync.modules.oauth.serviceImpl.FacebookOAuthServiceImpl;
import com.glancesync.modules.oauth.serviceImpl.FtOAuthServiceImpl;
import com.glancesync.modules.oauth.serviceImpl.GoogleOAuthServiceImpl;
import com.glancesync.web.Value;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import javax.sql.DataSource;
import java.lang.reflect.Constructor;
import java.lang.reflect.Parameter;
import java.lang.reflect.ParameterizedType;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Tiny constructor-injection container. It supplies infrastructure only; application logic stays explicit. */
public final class AppContainer implements AutoCloseable {
    private final AppConfig config;
    private final Map<Class<?>, Object> singletons = new HashMap<>();
    private final HikariDataSource dataSource;
    private final ExecutorService ioExecutor = Executors.newCachedThreadPool();

    public AppContainer(AppConfig config) {
        this.config = config;
        ObjectMapper mapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .enable(MapperFeature.ACCEPT_CASE_INSENSITIVE_ENUMS)
                .setSerializationInclusion(JsonInclude.Include.NON_NULL);
        singletons.put(AppConfig.class, config);
        singletons.put(ObjectMapper.class, mapper);
        singletons.put(ExecutorService.class, ioExecutor);

        HikariConfig hikari = new HikariConfig();
        hikari.setJdbcUrl(config.get("datasources.default.url"));
        hikari.setUsername(config.get("datasources.default.username"));
        hikari.setPassword(config.get("datasources.default.password"));
        hikari.setDriverClassName(config.get("datasources.default.driver-class-name"));
        hikari.setMaximumPoolSize(config.getInt("datasources.default.maximum-pool-size"));
        dataSource = new HikariDataSource(hikari);
        singletons.put(DataSource.class, dataSource);
        singletons.put(HikariDataSource.class, dataSource);
        singletons.put(MailConfig.class, mailConfig());
    }

    public ObjectMapper objectMapper() { return get(ObjectMapper.class); }

    public synchronized <T> T get(Class<T> type) {
        Object existing = singletons.get(type);
        if (existing != null) return type.cast(existing);
        if (type.isInterface()) throw new IllegalArgumentException("No binding for " + type.getName());
        try {
            Constructor<?>[] constructors = type.getDeclaredConstructors();
            Constructor<?> constructor = constructors[0];
            for (Constructor<?> candidate : constructors) {
                if (candidate.getParameterCount() > constructor.getParameterCount()) constructor = candidate;
            }
            constructor.setAccessible(true);
            Parameter[] parameters = constructor.getParameters();
            Object[] arguments = new Object[parameters.length];
            for (int i = 0; i < parameters.length; i++) arguments[i] = resolve(parameters[i]);
            T created = type.cast(constructor.newInstance(arguments));
            singletons.put(type, created);
            return created;
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Could not construct " + type.getName(), e);
        }
    }

    private Object resolve(Parameter parameter) {
        Value value = parameter.getAnnotation(Value.class);
        if (value != null) return config.convert(config.expression(value.value()), parameter.getType());
        if (parameter.getType() == List.class && parameter.getParameterizedType() instanceof ParameterizedType pt
                && pt.getActualTypeArguments()[0] == ProviderAuthService.class) {
            List<ProviderAuthService> providers = new ArrayList<>();
            providers.add(get(FtOAuthServiceImpl.class));
            providers.add(get(GoogleOAuthServiceImpl.class));
            providers.add(get(FacebookOAuthServiceImpl.class));
            return providers;
        }
        return get(parameter.getType());
    }

    private MailConfig mailConfig() {
        MailConfig mail = new MailConfig();
        mail.setHost(config.get("glancesync.mail.host"));
        mail.setPort(config.getInt("glancesync.mail.port"));
        mail.setUsername(config.get("glancesync.mail.username"));
        mail.setPassword(config.get("glancesync.mail.password"));
        mail.setStarttls(config.getBoolean("glancesync.mail.starttls"));
        mail.setFrom(config.get("glancesync.mail.from"));
        return mail;
    }

    @Override public void close() {
        ioExecutor.shutdown();
        dataSource.close();
    }
}

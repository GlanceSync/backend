package com.glancesync;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.glancesync.common.config.AppConfig;
import com.glancesync.common.config.AppContainer;
import com.glancesync.common.db.SchemaInitializer;
import com.glancesync.modules.auth.controller.AuthController;
import com.glancesync.modules.call.controller.CallsController;
import com.glancesync.modules.chat.controller.ChatController;
import com.glancesync.modules.discovery.controller.DiscoveryController;
import com.glancesync.modules.event.controller.EventsController;
import com.glancesync.modules.interest.controller.InterestsController;
import com.glancesync.modules.interest.controller.UserInterestsController;
import com.glancesync.modules.like.controller.LikesController;
import com.glancesync.modules.notification.controller.NotificationsController;
import com.glancesync.modules.oauth.controller.OAuthController;
import com.glancesync.modules.realtime.socket.RealtimeSocket;
import com.glancesync.modules.report.controller.ReportsController;
import com.glancesync.modules.admin.controller.AdminController;
import com.glancesync.modules.security.filter.JwtAuthenticationFilter;
import com.glancesync.modules.user.controller.AccountController;
import com.glancesync.modules.user.controller.MapController;
import com.glancesync.modules.user.controller.PicturesController;
import com.glancesync.modules.user.controller.ProfileController;
import com.glancesync.modules.user.controller.UsersController;
import com.glancesync.modules.visit.controller.VisitsController;
import com.glancesync.web.HttpRequest;
import com.glancesync.web.HttpResponse;
import com.glancesync.web.MediaType;
import com.glancesync.web.SparkRouter;

import static spark.Spark.*;

public final class Application {
    private Application() {}

    public static void main(String[] args) throws Exception {
        AppConfig config = new AppConfig();
        AppContainer container = new AppContainer(config);
        ObjectMapper json = container.objectMapper();
        SchemaInitializer schema = container.get(SchemaInitializer.class);
        schema.onStartup();

        port(config.getInt("server.port"));
        webSocket("/ws", container.get(RealtimeSocket.class));
        configureCors(config);
        configureAuthentication(container, json);

        SparkRouter router = new SparkRouter(json);
        router.add(container.get(AuthController.class));
        router.add(container.get(OAuthController.class));
        router.add(container.get(AccountController.class));
        router.add(container.get(ProfileController.class));
        router.add(container.get(PicturesController.class));
        router.add(container.get(MapController.class));
        router.add(container.get(UsersController.class));
        router.add(container.get(DiscoveryController.class));
        router.add(container.get(InterestsController.class));
        router.add(container.get(UserInterestsController.class));
        router.add(container.get(LikesController.class));
        router.add(container.get(VisitsController.class));
        router.add(container.get(ChatController.class));
        router.add(container.get(NotificationsController.class));
        router.add(container.get(ReportsController.class));
        router.add(container.get(EventsController.class));
        router.add(container.get(CallsController.class));
        router.add(container.get(AdminController.class));
        router.register();
        router.registerSwagger();

        notFound((request, response) -> {
            response.type(MediaType.APPLICATION_JSON);
            return "{\"message\":\"Not found.\"}";
        });
        awaitInitialization();

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try { schema.onShutdown(); } finally {
                stop();
                container.close();
            }
        }, "glancesync-shutdown"));
    }

    private static void configureAuthentication(AppContainer container, ObjectMapper json) {
        JwtAuthenticationFilter authentication = container.get(JwtAuthenticationFilter.class);
        before("/api/*", (request, response) -> {
            HttpResponse<?> rejection = authentication.authenticate(new HttpRequest<>(request));
            if (rejection == null) return;
            rejection.headers().forEach(response::header);
            response.type(MediaType.APPLICATION_JSON);
            halt(rejection.status(), json.writeValueAsString(rejection.body()));
        });
    }

    private static void configureCors(AppConfig config) {
        String allowed = config.get("server.cors.allowed-origins");
        before((request, response) -> {
            String origin = request.headers("Origin");
            if (origin != null && java.util.Arrays.asList(allowed.split(",")).contains(origin)) {
                response.header("Access-Control-Allow-Origin", origin);
                response.header("Vary", "Origin");
                response.header("Access-Control-Allow-Credentials", "true");
                response.header("Access-Control-Allow-Headers", "Authorization, Content-Type");
                response.header("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
            }
            if ("OPTIONS".equals(request.requestMethod())) halt(204, "");
        });
    }
}

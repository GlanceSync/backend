package com.glancesync.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.glancesync.common.dto.ErrorResponse;
import com.glancesync.common.exception.ApiException;
import com.glancesync.web.annotation.*;
import com.glancesync.web.cookie.Cookie;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.Request;
import spark.Response;
import spark.Route;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.RecordComponent;
import java.lang.reflect.Type;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static spark.Spark.*;

/** Adapts the project's small controller annotations to Spark routes. */
public final class SparkRouter {
    private static final Logger LOG = LoggerFactory.getLogger(SparkRouter.class);
    private final ObjectMapper json;
    private final List<Definition> definitions = new ArrayList<>();

    public SparkRouter(ObjectMapper json) { this.json = json; }

    public void add(Object controller) {
        Controller base = controller.getClass().getAnnotation(Controller.class);
        if (base == null) throw new IllegalArgumentException(controller.getClass() + " is not a controller");
        for (Method method : controller.getClass().getDeclaredMethods()) {
            String verb = null;
            String suffix = null;
            if (method.isAnnotationPresent(Get.class)) { verb = "GET"; suffix = method.getAnnotation(Get.class).value(); }
            else if (method.isAnnotationPresent(Post.class)) { verb = "POST"; suffix = method.getAnnotation(Post.class).value(); }
            else if (method.isAnnotationPresent(Put.class)) { verb = "PUT"; suffix = method.getAnnotation(Put.class).value(); }
            else if (method.isAnnotationPresent(Delete.class)) { verb = "DELETE"; suffix = method.getAnnotation(Delete.class).value(); }
            if (verb != null) definitions.add(new Definition(verb, sparkPath(base.value() + suffix), controller, method));
        }
    }

    public void register() {
        definitions.sort(Comparator
                .comparingInt((Definition d) -> count(d.path, ':'))
                .thenComparing((Definition d) -> -d.path.length()));
        for (Definition definition : definitions) {
            Route route = (request, response) -> invoke(definition, request, response);
            switch (definition.verb) {
                case "GET" -> get(definition.path, route);
                case "POST" -> post(definition.path, route);
                case "PUT" -> put(definition.path, route);
                case "DELETE" -> delete(definition.path, route);
                default -> throw new IllegalStateException("Unknown verb " + definition.verb);
            }
        }
    }

    /** Restores the OpenAPI document and Swagger UI that Micronaut used to generate. */
    public void registerSwagger() {
        Map<String, Object> document = openApiDocument();
        get("/swagger/glancesync-0.1.json", (request, response) -> {
            response.type("application/json");
            return json.writeValueAsString(document);
        });
        // JSON is valid YAML 1.2, so the old generated-spec URL remains usable.
        get("/swagger/glancesync-0.1.yml", (request, response) -> {
            response.type("application/yaml");
            return json.writeValueAsString(document);
        });
        get("/swagger-ui.html", (request, response) -> {
            response.type("text/html; charset=utf-8");
            return swaggerHtml();
        });
        get("/swagger-ui", (request, response) -> {
            response.redirect("/swagger-ui.html");
            return "";
        });
        get("/swagger-ui/", (request, response) -> {
            response.type("text/html; charset=utf-8");
            return swaggerHtml();
        });
        get("/swagger-ui/swagger-ui.css", (request, response) -> webJarAsset("swagger-ui.css", response));
        get("/swagger-ui/swagger-ui-bundle.js", (request, response) -> webJarAsset("swagger-ui-bundle.js", response));
    }

    private Object webJarAsset(String name, Response response) throws Exception {
        String resource = "/META-INF/resources/webjars/swagger-ui/5.17.14/" + name;
        try (var input = SparkRouter.class.getResourceAsStream(resource)) {
            if (input == null) {
                response.status(404);
                return "Not found";
            }
            response.type(name.endsWith(".css") ? "text/css; charset=utf-8" : "application/javascript; charset=utf-8");
            return input.readAllBytes();
        }
    }

    private String swaggerHtml() {
        return """
                <!doctype html>
                <html lang="en">
                <head>
                  <meta charset="utf-8">
                  <meta name="viewport" content="width=device-width, initial-scale=1">
                  <title>GlanceSync API</title>
                  <link rel="stylesheet" href="/swagger-ui/swagger-ui.css">
                </head>
                <body>
                  <div id="swagger-ui"></div>
                  <script src="/swagger-ui/swagger-ui-bundle.js"></script>
                  <script>
                    SwaggerUIBundle({
                      url: '/swagger/glancesync-0.1.json',
                      dom_id: '#swagger-ui',
                      deepLinking: true,
                      presets: [SwaggerUIBundle.presets.apis]
                    });
                  </script>
                </body>
                </html>
                """;
    }

    private Map<String, Object> openApiDocument() {
        Map<String, Object> paths = new LinkedHashMap<>();
        Map<String, Object> schemas = new LinkedHashMap<>();
        for (Definition definition : definitions) {
            Method method = definition.method;
            Map<String, Object> operation = new LinkedHashMap<>();
            io.swagger.v3.oas.annotations.tags.Tag tag =
                    definition.controller.getClass().getAnnotation(io.swagger.v3.oas.annotations.tags.Tag.class);
            if (tag != null) operation.put("tags", List.of(tag.name()));
            operation.put("operationId", method.getDeclaringClass().getSimpleName() + "_" + method.getName());

            List<Map<String, Object>> parameters = new ArrayList<>();
            Map<String, Object> requestBody = null;
            for (Parameter parameter : method.getParameters()) {
                if (parameter.isAnnotationPresent(PathVariable.class)) {
                    PathVariable annotation = parameter.getAnnotation(PathVariable.class);
                    String name = annotation.value().isBlank() ? parameter.getName() : annotation.value();
                    parameters.add(parameterSpec(name, "path", true, parameter.getParameterizedType(), schemas));
                } else if (parameter.isAnnotationPresent(QueryValue.class)) {
                    QueryValue annotation = parameter.getAnnotation(QueryValue.class);
                    String name = annotation.value().isBlank() ? parameter.getName() : annotation.value();
                    parameters.add(parameterSpec(name, "query", false, parameter.getParameterizedType(), schemas));
                } else if (parameter.isAnnotationPresent(Body.class)) {
                    String mediaType = consumes(method);
                    requestBody = Map.of("required", true, "content",
                            Map.of(mediaType, Map.of("schema", schemaFor(parameter.getParameterizedType(), schemas, new java.util.HashSet<>()))));
                }
            }
            if (!parameters.isEmpty()) operation.put("parameters", parameters);
            if (requestBody != null) operation.put("requestBody", requestBody);

            Type responseType = unwrapResponse(method.getGenericReturnType());
            Map<String, Object> response = new LinkedHashMap<>();
            response.put("description", "Successful response");
            if (responseType != void.class && responseType != Void.class) {
                response.put("content", Map.of("application/json",
                        Map.of("schema", schemaFor(responseType, schemas, new java.util.HashSet<>()))));
            }
            operation.put("responses", Map.of("200", response, "400", Map.of("description", "Invalid request"),
                    "401", Map.of("description", "Authentication required"), "404", Map.of("description", "Not found")));

            String openApiPath = definition.path.replaceAll(":([A-Za-z0-9_]+)", "{$1}");
            @SuppressWarnings("unchecked")
            Map<String, Object> pathItem = (Map<String, Object>) paths.computeIfAbsent(openApiPath, ignored -> new LinkedHashMap<>());
            pathItem.put(definition.verb.toLowerCase(), operation);
        }

        Map<String, Object> components = new LinkedHashMap<>();
        components.put("securitySchemes", Map.of("bearer", Map.of("type", "http", "scheme", "bearer", "bearerFormat", "JWT")));
        components.put("schemas", schemas);
        Map<String, Object> document = new LinkedHashMap<>();
        document.put("openapi", "3.0.3");
        document.put("info", Map.of("title", "GlanceSync", "version", "0.1", "description", "GlanceSync backend API"));
        document.put("security", List.of(Map.of("bearer", List.of())));
        document.put("paths", paths);
        document.put("components", components);
        return document;
    }

    private Map<String, Object> parameterSpec(String name, String location, boolean required, Type type,
                                               Map<String, Object> schemas) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("name", name);
        result.put("in", location);
        result.put("required", required);
        result.put("schema", schemaFor(type, schemas, new java.util.HashSet<>()));
        return result;
    }

    private Map<String, Object> schemaFor(Type type, Map<String, Object> schemas, Set<Class<?>> visiting) {
        if (type instanceof ParameterizedType parameterized) {
            Type raw = parameterized.getRawType();
            if (raw == HttpResponse.class) return schemaFor(parameterized.getActualTypeArguments()[0], schemas, visiting);
            if (raw instanceof Class<?> rawClass && Collection.class.isAssignableFrom(rawClass)) {
                return Map.of("type", "array", "items", schemaFor(parameterized.getActualTypeArguments()[0], schemas, visiting));
            }
            if (raw instanceof Class<?> rawClass && Map.class.isAssignableFrom(rawClass)) {
                return Map.of("type", "object", "additionalProperties", schemaFor(parameterized.getActualTypeArguments()[1], schemas, visiting));
            }
            return schemaFor(raw, schemas, visiting);
        }
        if (!(type instanceof Class<?> cls)) return Map.of("type", "object");
        if (cls == String.class || cls == Character.class || cls == char.class) return Map.of("type", "string");
        if (cls == UUID.class) return Map.of("type", "string", "format", "uuid");
        if (cls == Instant.class || cls == LocalDateTime.class || cls == OffsetDateTime.class || cls == ZonedDateTime.class)
            return Map.of("type", "string", "format", "date-time");
        if (cls == LocalDate.class) return Map.of("type", "string", "format", "date");
        if (cls == boolean.class || cls == Boolean.class) return Map.of("type", "boolean");
        if (cls == byte[].class) return Map.of("type", "string", "format", "binary");
        if (cls == int.class || cls == Integer.class || cls == long.class || cls == Long.class)
            return Map.of("type", "integer", "format", cls == long.class || cls == Long.class ? "int64" : "int32");
        if (Number.class.isAssignableFrom(cls) || cls == double.class || cls == float.class)
            return Map.of("type", "number", "format", "double");
        if (cls.isEnum()) {
            List<String> values = java.util.Arrays.stream(cls.getEnumConstants()).map(Object::toString).toList();
            return Map.of("type", "string", "enum", values);
        }
        if (cls.isArray()) return Map.of("type", "array", "items", schemaFor(cls.getComponentType(), schemas, visiting));
        if (cls == Object.class || cls == void.class || cls == Void.class) return Map.of("type", "object");

        String name = cls.getSimpleName();
        if (!schemas.containsKey(name) && visiting.add(cls)) {
            Map<String, Object> properties = new LinkedHashMap<>();
            if (cls.isRecord()) {
                for (RecordComponent component : cls.getRecordComponents()) {
                    properties.put(component.getName(), schemaFor(component.getGenericType(), schemas, visiting));
                }
            }
            schemas.put(name, Map.of("type", "object", "properties", properties));
            visiting.remove(cls);
        }
        return Map.of("$ref", "#/components/schemas/" + name);
    }

    private Type unwrapResponse(Type type) {
        if (type instanceof ParameterizedType parameterized && parameterized.getRawType() == HttpResponse.class)
            return parameterized.getActualTypeArguments()[0];
        return type;
    }

    private String consumes(Method method) {
        if (method.isAnnotationPresent(Post.class) && method.getAnnotation(Post.class).consumes().length > 0)
            return method.getAnnotation(Post.class).consumes()[0];
        if (method.isAnnotationPresent(Put.class) && method.getAnnotation(Put.class).consumes().length > 0)
            return method.getAnnotation(Put.class).consumes()[0];
        return MediaType.APPLICATION_JSON;
    }

    private Object invoke(Definition definition, Request request, Response response) {
        try {
            Object[] args = arguments(definition.method, request);
            Object result = definition.method.invoke(definition.controller, args);
            return render(result, response);
        } catch (InvocationTargetException e) {
            return renderError(e.getCause(), request, response);
        } catch (Throwable e) {
            return renderError(e, request, response);
        }
    }

    private Object[] arguments(Method method, Request request) throws Exception {
        Parameter[] parameters = method.getParameters();
        Object[] args = new Object[parameters.length];
        for (int i = 0; i < parameters.length; i++) {
            Parameter parameter = parameters[i];
            if (parameter.getType() == HttpRequest.class) args[i] = new HttpRequest<>(request);
            else if (parameter.getType() == Session.class) args[i] = new Session(request.session(true));
            else if (parameter.isAnnotationPresent(Body.class)) {
                if (parameter.getType() == byte[].class) args[i] = request.bodyAsBytes();
                else if (request.body() == null || request.body().isBlank()) args[i] = null;
                else args[i] = json.readValue(request.body(), json.getTypeFactory().constructType(parameter.getParameterizedType()));
            } else if (parameter.isAnnotationPresent(PathVariable.class)) {
                PathVariable annotation = parameter.getAnnotation(PathVariable.class);
                String name = annotation.value().isBlank() ? parameter.getName() : annotation.value();
                String rawValue = request.params(":" + name);
                if (rawValue == null) rawValue = request.params(name);
                args[i] = convert(rawValue, parameter.getType());
            } else if (parameter.isAnnotationPresent(QueryValue.class)) {
                QueryValue annotation = parameter.getAnnotation(QueryValue.class);
                String name = annotation.value().isBlank() ? parameter.getName() : annotation.value();
                args[i] = convert(request.queryParams(name), parameter.getType());
            } else {
                throw new IllegalArgumentException("Unsupported endpoint parameter " + parameter);
            }
        }
        return args;
    }

    private Object convert(String value, Class<?> type) {
        if (value == null) return null;
        if (type == String.class) return value;
        if (type == UUID.class) return UUID.fromString(value);
        if (type == Integer.class || type == int.class) return Integer.valueOf(value);
        if (type == Long.class || type == long.class) return Long.valueOf(value);
        if (type == Double.class || type == double.class) return Double.valueOf(value);
        if (type == Boolean.class || type == boolean.class) return Boolean.valueOf(value);
        throw new IllegalArgumentException("Unsupported request value type " + type.getName());
    }

    private Object render(Object value, Response response) throws Exception {
        Object body = value;
        if (value instanceof HttpResponse<?> http) {
            response.status(http.status());
            http.headers().forEach(response::header);
            if (http.cookie() != null) response.header("Set-Cookie", cookieHeader(http.cookie()));
            body = http.body();
        }
        if (body == null) return "";
        if (body instanceof byte[] bytes) return bytes;
        if (body instanceof String text && response.raw().getContentType() != null) return text;
        response.type(MediaType.APPLICATION_JSON);
        return json.writeValueAsString(body);
    }

    private Object renderError(Throwable error, Request request, Response response) {
        try {
            if (error instanceof ApiException api) {
                response.status(api.status().getCode());
                response.type(MediaType.APPLICATION_JSON);
                return json.writeValueAsString(new ErrorResponse(api.getMessage(), api.fields()));
            }
            if (error instanceof IllegalArgumentException) {
                response.status(400);
                response.type(MediaType.APPLICATION_JSON);
                return json.writeValueAsString(ErrorResponse.of("The request contains an invalid value."));
            }
            LOG.error("Unhandled error on {} {}", request.requestMethod(), request.pathInfo(), error);
            response.status(500);
            response.type(MediaType.APPLICATION_JSON);
            return json.writeValueAsString(ErrorResponse.of("Something went wrong. Please try again."));
        } catch (Exception serializationError) {
            LOG.error("Could not serialize error response", serializationError);
            response.status(500);
            return "{\"message\":\"Something went wrong. Please try again.\"}";
        }
    }

    private static String cookieHeader(Cookie cookie) {
        StringBuilder value = new StringBuilder(cookie.name()).append('=')
                .append(URLEncoder.encode(cookie.value(), StandardCharsets.UTF_8))
                .append("; Path=").append(cookie.path())
                .append("; SameSite=").append(cookie.sameSite());
        if (cookie.maxAge() != null) value.append("; Max-Age=").append(cookie.maxAge().toSeconds());
        if (cookie.httpOnly()) value.append("; HttpOnly");
        if (cookie.secure()) value.append("; Secure");
        return value.toString();
    }

    private static String sparkPath(String path) { return path.replaceAll("\\{([^}]+)}", ":$1"); }
    private static int count(String text, char needle) {
        int total = 0;
        for (int i = 0; i < text.length(); i++) if (text.charAt(i) == needle) total++;
        return total;
    }
    private record Definition(String verb, String path, Object controller, Method method) {}
}

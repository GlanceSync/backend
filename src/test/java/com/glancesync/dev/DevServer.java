package com.glancesync.dev;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Development runner started by {@code ./mvnw -Pdev}: runs the application, watches Java sources and
 * configuration files, and on every saved change stops the app, recompiles with Maven and starts it again.
 * Lives in test sources so it never ships in the application jar.
 */
public final class DevServer {

    private static final String MAIN_CLASS = "com.glancesync.Application";
    private static final List<String> WATCHED = List.of("src/main/java", "src/main/resources", "pom.xml", ".env");
    private static final long POLL_MILLIS = 500;
    private static final long STOP_TIMEOUT_SECONDS = 10;

    private final Path root;
    private final String classpath;
    private Process app;

    private DevServer(Path root) {
        this.root = root;
        // The dev JVM runs with the test classpath; the app only needs main classes and dependencies.
        this.classpath = Stream.of(System.getProperty("java.class.path").split(File.pathSeparator))
                .filter(entry -> !entry.endsWith("test-classes"))
                .collect(Collectors.joining(File.pathSeparator));
    }

    public static void main(String[] args) throws Exception {
        Path root = Path.of(args.length > 0 ? args[0] : ".").toAbsolutePath().normalize();
        DevServer server = new DevServer(root);
        Runtime.getRuntime().addShutdownHook(new Thread(server::stopApp, "dev-server-shutdown"));
        server.run();
    }

    private void run() throws Exception {
        Path pom = root.resolve("pom.xml");
        Map<Path, Long> snapshot = snapshot();
        startApp();
        log("Watching " + String.join(", ", WATCHED) + " (Ctrl-C to quit)");
        while (true) {
            Thread.sleep(POLL_MILLIS);
            Map<Path, Long> current = snapshot();
            if (current.equals(snapshot)) continue;
            Map<Path, Long> previous = snapshot;
            // Wait for the files to settle so a burst of saves triggers a single restart.
            do {
                snapshot = current;
                Thread.sleep(POLL_MILLIS);
                current = snapshot();
            } while (!current.equals(snapshot));
            log("Change detected");
            if (!Objects.equals(previous.get(pom), current.get(pom))) {
                log("pom.xml changed: restart ./mvnw -Pdev to pick up dependency changes");
            }
            stopApp();
            if (compile()) {
                startApp();
            } else {
                log("Compilation failed; waiting for the next change");
            }
        }
    }

    private Map<Path, Long> snapshot() throws IOException {
        Map<Path, Long> files = new HashMap<>();
        for (String watched : WATCHED) {
            Path path = root.resolve(watched);
            if (!Files.exists(path)) continue;
            try (Stream<Path> walk = Files.walk(path)) {
                for (Path file : (Iterable<Path>) walk.filter(Files::isRegularFile)::iterator) {
                    try {
                        files.put(file, Files.getLastModifiedTime(file).toMillis());
                    } catch (IOException ignored) {
                        // Deleted between listing and stat: the next poll will see it gone.
                    }
                }
            }
        }
        return files;
    }

    private boolean compile() throws IOException, InterruptedException {
        log("Compiling...");
        boolean windows = System.getProperty("os.name").toLowerCase().contains("win");
        List<String> command = new ArrayList<>();
        command.add(root.resolve(windows ? "mvnw.cmd" : "mvnw").toString());
        command.add("-q");
        command.add("compile");
        return new ProcessBuilder(command).directory(root.toFile()).inheritIO().start().waitFor() == 0;
    }

    private synchronized void startApp() throws IOException {
        log("Starting " + MAIN_CLASS);
        String java = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        app = new ProcessBuilder(java, "-cp", classpath, MAIN_CLASS)
                .directory(root.toFile())
                .inheritIO()
                .start();
    }

    private synchronized void stopApp() {
        if (app == null) return;
        if (app.isAlive()) {
            log("Stopping app (pid " + app.pid() + ")");
            app.destroy();
            try {
                if (!app.waitFor(STOP_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                    log("App did not stop within " + STOP_TIMEOUT_SECONDS + "s; killing it");
                    app.destroyForcibly().waitFor();
                }
            } catch (InterruptedException e) {
                app.destroyForcibly();
                Thread.currentThread().interrupt();
            }
        }
        app = null;
    }

    private static void log(String message) {
        System.out.println("\u001B[1;36m[dev]\u001B[0m " + message);
    }
}

package org.example.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class AppPaths {
    private static final Logger log = LoggerFactory.getLogger(AppPaths.class);
    private static final String ENV_HOME = "PRESTATAIRES_HOME";
    private static volatile Path cachedRoot;

    private AppPaths() {
    }

    public static Path dataRoot() {
        Path root = cachedRoot;
        if (root != null) {
            return root;
        }
        synchronized (AppPaths.class) {
            if (cachedRoot == null) {
                cachedRoot = computeRoot();
            }
            return cachedRoot;
        }
    }

    public static Path authDb() {
        return dataRoot().resolve("auth.db");
    }

    public static Path userDb(String username) {
        return dataRoot().resolve(username + ".db");
    }

    public static Path outboxDir() {
        Path dir = dataRoot().resolve("outbox");
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            throw new IllegalStateException("Impossible de créer le dossier outbox: " + dir, e);
        }
        return dir;
    }

    private static Path computeRoot() {
        String env = System.getenv(ENV_HOME);
        Path base = (env != null && !env.isBlank())
                ? Path.of(env)
                : Path.of(System.getProperty("user.home"), ".prestataires");
        try {
            Files.createDirectories(base);
        } catch (IOException e) {
            throw new IllegalStateException("Impossible de créer le dossier des données: " + base, e);
        }
        log.debug("[AppPaths] dataRoot={}", base.toAbsolutePath());
        return base;
    }
}

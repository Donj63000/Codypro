package org.example.util;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class AppPathsTest {

    @TempDir
    Path tempDir;

    private Path previousRoot;
    private String previousUserHome;

    @BeforeEach
    void setUp() throws Exception {
        previousRoot = readCachedRoot();
        previousUserHome = System.getProperty("user.home");
        Path root = tempDir.resolve("data");
        Files.createDirectories(root);
        writeCachedRoot(root);
    }

    @AfterEach
    void tearDown() throws Exception {
        writeCachedRoot(previousRoot);
        if (previousUserHome != null) {
            System.setProperty("user.home", previousUserHome);
        }
    }

    @Test
    void dataRootReturnsCachedValue() {
        Path cached = AppPaths.dataRoot();

        assertEquals(tempDir.resolve("data"), cached);
    }

    @Test
    void authDbResolvesInsideDataRoot() {
        Path auth = AppPaths.authDb();

        assertEquals(tempDir.resolve("data").resolve("auth.db"), auth);
    }

    @Test
    void userDbAppendsDbSuffix() {
        Path userDb = AppPaths.userDb("alice");

        assertEquals(tempDir.resolve("data").resolve("alice.db"), userDb);
    }

    @Test
    void userDbKeepsUsernameCase() {
        Path userDb = AppPaths.userDb("AliceAdmin");

        assertTrue(userDb.endsWith("AliceAdmin.db"));
    }

    @Test
    void outboxDirCreatesDirectoryIfMissing() {
        Path outbox = AppPaths.outboxDir();

        assertTrue(Files.exists(outbox));
        assertTrue(Files.isDirectory(outbox));
    }

    @Test
    void outboxDirIsStableAcrossCalls() {
        Path first = AppPaths.outboxDir();
        Path second = AppPaths.outboxDir();

        assertEquals(first, second);
    }

    @Test
    void outboxDirLivesUnderDataRoot() {
        Path outbox = AppPaths.outboxDir();

        assertEquals(AppPaths.dataRoot().resolve("outbox"), outbox);
    }

    @Test
    void authDbFileNameIsAlwaysAuthDb() {
        assertEquals("auth.db", AppPaths.authDb().getFileName().toString());
    }

    @Test
    void clearingCacheRecomputesFromUserHome() throws Exception {
        writeCachedRoot(null);
        System.setProperty("user.home", tempDir.toString());

        Path computed = AppPaths.dataRoot();

        assertEquals(tempDir.resolve(".prestataires"), computed);
        assertTrue(Files.exists(computed));
    }

    @Test
    void replacingCacheSwitchesResolvedPaths() throws Exception {
        Path firstRoot = tempDir.resolve("first");
        Path secondRoot = tempDir.resolve("second");
        Files.createDirectories(firstRoot);
        Files.createDirectories(secondRoot);

        writeCachedRoot(firstRoot);
        Path firstUserDb = AppPaths.userDb("alice");

        writeCachedRoot(secondRoot);
        Path secondUserDb = AppPaths.userDb("alice");

        assertEquals(firstRoot.resolve("alice.db"), firstUserDb);
        assertEquals(secondRoot.resolve("alice.db"), secondUserDb);
    }

    private static Path readCachedRoot() throws Exception {
        Field field = AppPaths.class.getDeclaredField("cachedRoot");
        field.setAccessible(true);
        return (Path) field.get(null);
    }

    private static void writeCachedRoot(Path value) throws Exception {
        Field field = AppPaths.class.getDeclaredField("cachedRoot");
        field.setAccessible(true);
        field.set(null, value);
    }
}

package org.example.security;

import org.example.dao.AuthDB;
import org.example.util.AppPaths;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AuthServiceWorkflowTest {

    @TempDir
    Path tempDir;

    private Path previousRoot;
    private AuthDB authDb;
    private AuthService service;

    @BeforeEach
    void setUp() throws Exception {
        previousRoot = cachedRoot();
        Path dataRoot = tempDir.resolve("appdata");
        Files.createDirectories(dataRoot);
        setCachedRoot(dataRoot);

        authDb = new AuthDB(tempDir.resolve("auth.sqlite").toString());
        service = new AuthService(authDb);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (authDb != null) {
            authDb.close();
        }
        setCachedRoot(previousRoot);
    }

    @Test
    void registerPersistsUserInList() throws Exception {
        service.register("alice", chars("LongPass123"));

        List<AuthService.UserSummary> users = service.listUsers();

        assertEquals(1, users.size());
        assertEquals("alice", users.get(0).username());
    }

    @Test
    void registerTrimsUsernameBeforePersisting() throws Exception {
        service.register("  alice  ", chars("LongPass123"));

        AuthService.Session session = service.login("alice", chars("LongPass123"));

        assertNotNull(session);
        assertEquals("alice", session.username());
    }

    @Test
    void registerRejectsBlankUsername() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.register("   ", chars("LongPass123")));

        assertTrue(ex.getMessage().contains("vide"));
    }

    @Test
    void registerRejectsShortPassword() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.register("alice", chars("short")));

        assertTrue(ex.getMessage().contains("au moins"));
    }

    @Test
    void registerRejectsWhitespaceOnlyPassword() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.register("alice", chars("        ")));

        assertTrue(ex.getMessage().contains("espaces"));
    }

    @Test
    void registerRejectsDuplicateUsernameCaseInsensitive() throws Exception {
        service.register("Alice", chars("LongPass123"));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.register("alice", chars("AnotherPass123")));

        assertTrue(ex.getMessage().contains("existe déjà"));
    }

    @Test
    void loginReturnsNullForUnknownUser() throws Exception {
        AuthService.Session session = service.login("ghost", chars("LongPass123"));

        assertNull(session);
    }

    @Test
    void loginReturnsNullForWrongPassword() throws Exception {
        service.register("alice", chars("LongPass123"));

        AuthService.Session session = service.login("alice", chars("WrongPass123"));

        assertNull(session);
    }

    @Test
    void loginReturnsSessionForValidCredentials() throws Exception {
        service.register("alice", chars("LongPass123"));

        AuthService.Session session = service.login("alice", chars("LongPass123"));

        assertNotNull(session);
        assertTrue(session.userId() > 0);
        assertEquals("alice", session.username());
        assertNotNull(session.key());
    }

    @Test
    void updateUsernameRenamesAccountAndAllowsLoginWithNewName() throws Exception {
        service.register("alice", chars("LongPass123"));
        int userId = service.login("alice", chars("LongPass123")).userId();

        service.updateUsername(userId, "neo");

        assertNull(service.login("alice", chars("LongPass123")));
        assertNotNull(service.login("neo", chars("LongPass123")));
    }

    @Test
    void updateUsernameRejectsDuplicateTarget() throws Exception {
        service.register("alice", chars("LongPass123"));
        service.register("bob", chars("LongPass123"));
        int bobId = service.login("bob", chars("LongPass123")).userId();

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.updateUsername(bobId, "Alice"));

        assertTrue(ex.getMessage().contains("déjà utilisé"));
    }

    @Test
    void changePasswordRejectsWrongCurrentPassword() throws Exception {
        service.register("alice", chars("LongPass123"));
        int userId = service.login("alice", chars("LongPass123")).userId();

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.changePassword(userId, chars("BadPass123"), chars("NewPass123")));

        assertTrue(ex.getMessage().contains("incorrect"));
    }

    @Test
    void changePasswordAllowsLoginWithNewPassword() throws Exception {
        service.register("alice", chars("LongPass123"));
        int userId = service.login("alice", chars("LongPass123")).userId();

        service.changePassword(userId, chars("LongPass123"), chars("NewPass123"));

        assertNull(service.login("alice", chars("LongPass123")));
        assertNotNull(service.login("alice", chars("NewPass123")));
    }

    @Test
    void deleteUserForbidLastThrows() throws Exception {
        service.register("alice", chars("LongPass123"));
        int userId = service.login("alice", chars("LongPass123")).userId();

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> service.deleteUser(userId, true));

        assertTrue(ex.getMessage().contains("dernier compte"));
        assertNotNull(service.login("alice", chars("LongPass123")));
    }

    @Test
    void deleteUserRemovesAccountWhenMultipleExist() throws Exception {
        service.register("alice", chars("LongPass123"));
        service.register("bob", chars("LongPass123"));
        int bobId = service.login("bob", chars("LongPass123")).userId();

        service.deleteUser(bobId, true);

        assertNull(service.login("bob", chars("LongPass123")));
        assertNotNull(service.login("alice", chars("LongPass123")));
    }

    private static char[] chars(String raw) {
        return raw.toCharArray();
    }

    private static Path cachedRoot() throws Exception {
        Field field = AppPaths.class.getDeclaredField("cachedRoot");
        field.setAccessible(true);
        return (Path) field.get(null);
    }

    private static void setCachedRoot(Path root) throws Exception {
        Field field = AppPaths.class.getDeclaredField("cachedRoot");
        field.setAccessible(true);
        field.set(null, root);
    }
}

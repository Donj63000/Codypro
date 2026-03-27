package org.example.gui;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ThemeStylesheetTest {

    @Test
    void darkStylesheetCoversCriticalDarkThemeControls() throws IOException {
        String css = readCss("/css/dark.css");

        assertContains(css, ".dialog-pane");
        assertContains(css, ".combo-box-base");
        assertContains(css, ".menu-bar");
        assertContains(css, ".context-menu");
        assertContains(css, ".spinner");
        assertContains(css, ".date-picker-popup");
        assertContains(css, ".check-box");
        assertContains(css, ".text-area .content");
    }

    @Test
    void lightStylesheetCoversCriticalThemeControls() throws IOException {
        String css = readCss("/css/light.css");

        assertContains(css, ".dialog-pane");
        assertContains(css, ".combo-box-base");
        assertContains(css, ".menu-bar");
        assertContains(css, ".context-menu");
        assertContains(css, ".spinner");
        assertContains(css, ".date-picker-popup");
        assertContains(css, ".check-box");
        assertContains(css, ".text-area .content");
    }

    @Test
    void themeStylesheetsDoNotContainSlashSlashComments() throws IOException {
        assertNoInvalidComment("/css/dark.css");
        assertNoInvalidComment("/css/light.css");
    }

    private static void assertNoInvalidComment(String resourcePath) throws IOException {
        String css = readCss(resourcePath);
        boolean hasInvalidComment = css.lines().map(String::trim).anyMatch(line -> line.startsWith("//"));
        assertFalse(hasInvalidComment, "Le fichier " + resourcePath + " contient un commentaire // invalide pour JavaFX CSS.");
    }

    private static void assertContains(String css, String selector) {
        assertTrue(css.contains(selector), "Le sélecteur " + selector + " est manquant dans la feuille de style.");
    }

    private static String readCss(String resourcePath) throws IOException {
        try (InputStream in = ThemeStylesheetTest.class.getResourceAsStream(resourcePath)) {
            assertNotNull(in, "La ressource " + resourcePath + " doit exister.");
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}

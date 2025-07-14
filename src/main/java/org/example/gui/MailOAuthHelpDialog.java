package org.example.gui;

import javafx.scene.control.*;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/** Dialogue affichant le tutoriel détaillé pour la connexion Gmail OAuth2. */
public class MailOAuthHelpDialog extends Dialog<Void> {

    private static final String HELP_TEXT = """
        ### Activer l’envoi via Gmail – pas à pas

        1. **Créer ou ouvrir un projet Google Cloud**
           - Rendez-vous sur https://console.cloud.google.com/ puis « Créer un projet ».
           - Nommez-le (ex. *Prestataires‑Manager*).

        2. **Activer l’API Gmail**
           - Menu : **API & Services › Bibliothèque**.
           - Cherchez « Gmail API », ouvrez la fiche puis cliquez sur **Activer**.

        3. **Configurer l’écran de consentement OAuth**
           - Menu : **API & Services › Écran de consentement OAuth**.
           - Sélectionnez **Externe** comme type d’utilisateur.
           - Indiquez un nom d’application et un e‑mail de contact.
           - Laissez l’application en mode *Test* et ajoutez votre adresse dans « Testeurs ».

        4. **Créer les identifiants OAuth – Application de bureau**
           - Menu : **API & Services › Identifiants** → **+ Créer des identifiants** → **ID client OAuth**.
           - Choisissez **Application de bureau** et notez l’**ID client** ainsi que le **Secret client**.

        5. **Renseigner Prestataires‑Manager**
           - Dans le champ **Client OAuth**, saisissez : `ID_CLIENT:SECRET_CLIENT` (un seul « : », aucun espace).
           - Cliquez ensuite sur **Se connecter à Google** et accordez les permissions.

        6. **Tester l’envoi**
           - Utilisez **Tester l’envoi** pour valider la configuration.

        ---
        #### Questions fréquentes

        • **Consentement requis** : ajoutez votre adresse dans les testeurs.
        • **invalid_grant** lors du rafraîchissement : relancez « Se connecter à Google ».
        • **Mot de passe d’application** : utilisez l’onglet **SMTP classique** (port 465 SSL).
        """;

    public MailOAuthHelpDialog() {
        setTitle("Aide – connexion Gmail");
        getDialogPane().getButtonTypes().add(ButtonType.CLOSE);

        TextArea ta = new TextArea(HELP_TEXT);
        ta.setEditable(false);
        ta.setWrapText(true);
        ta.setPrefColumnCount(80);
        ta.setPrefRowCount(35);

        // s’adapte à l’espace disponible
        ta.setMaxWidth(Double.MAX_VALUE);
        ta.setMaxHeight(Double.MAX_VALUE);
        VBox.setVgrow(ta, Priority.ALWAYS);

        Region spacer = new Region(); // pour garder un padding correct avec le CSS foncé
        VBox root = new VBox(ta, spacer);
        getDialogPane().setContent(root);

        ThemeManager.apply(this);   // applique le thème sombre
    }
}

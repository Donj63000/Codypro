package org.example.gui;

import javafx.scene.control.*;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/** Dialogue affichant le tutoriel détaillé pour la connexion Gmail OAuth2. */
public class MailOAuthHelpDialog extends Dialog<Void> {

    private static final String HELP_TEXT = """
        ### Activer l’envoi via Gmail – pas à pas

        1. **Créer / ouvrir un projet Google Cloud**
           - Accédez à https://console.cloud.google.com/ et sélectionnez « Créer un projet ».
           - Donnez‑lui un nom (ex. *Prestataires‑Manager*).

        2. **Activer l’API Gmail**
           - Menu : **API & Services › Bibliothèque**.
           - Recherchez « Gmail API », ouvrez la fiche puis **Activer**.

        3. **Configurer l’écran de consentement OAuth**
           - Menu : **API & Services › Écran de consentement OAuth**.
           - Type d’utilisateur : **Externe**.
           - Renseignez : nom de l’application, e‑mail de contact.
           - Laissez l’application en mode *Test* ; ajoutez votre propre adresse dans « Testeurs ».

        4. **Créer les identifiants OAuth « Application de bureau »**
           - Menu : **API & Services › Identifiants** → **+ Créer des identifiants** → **ID client OAuth**.
           - Type d’application : **Application de bureau**.
           - Notez **ID client** et **Secret client**.

        5. **Renseigner Prestataires‑Manager**
           - Dans le champ **Client OAuth**, saisissez :  
             `ID_CLIENT:SECRET_CLIENT`  (un seul « : », aucun espace).
           - Cliquez **Se connecter à Google**, accordez les permissions.

        6. **Tester l’envoi**
           - Utilisez **Tester l’envoi** pour confirmer la configuration.

        ---
        **Questions fréquentes**

        | Problème | Solution |
        |----------|----------|
        | *Consentement requis* | Ajoutez votre adresse dans les testeurs. |
        | *invalid_grant* au refresh | Relancez « Se connecter à Google ». |
        | Mot de passe d’application | Utilisez l’onglet **SMTP classique** (port 465 SSL). |
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

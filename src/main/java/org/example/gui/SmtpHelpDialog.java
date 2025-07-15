package org.example.gui;

import javafx.scene.control.*;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/**
 * Dialogue affichant une aide pas‑à‑pas pour la configuration classique
 * SMTP (hôte, port, SSL, authentification…).
 */
public class SmtpHelpDialog extends Dialog<Void> {

    private static final String HELP_TEXT = """
        ### Paramétrage SMTP classique – pas à pas
        
        1. **Trouver les informations de votre fournisseur**
           | Fournisseur        | Serveur (host)            | Port SSL (465) | Port STARTTLS (587) |
           |--------------------|---------------------------|---------------|---------------------|
           | Gmail (mot de passe d’appli) | `smtp.gmail.com`    | 465           | 587                 |
           | Outlook / Office365          | `smtp.office365.com`| 465           | 587                 |
           | Free                         | `smtp.free.fr`      | 465           | —                   |
           | Orange                       | `smtp.orange.fr`    | 465           | 587                 |
           | OVH (e‑mail pro)             | `ssl0.ovh.net`      | 465           | 587                 |
           
           Si votre fournisseur n’est pas listé : cherchez « SMTP <nom_du_FAI> ».

        2. **SSL vs. STARTTLS**
           * **SSL (ou SMTPS)** : connexion chiffrée dès l’ouverture (port 465).  
           * **STARTTLS** : la connexion démarre en clair puis passe en TLS (port 587).

           Choisissez le port correspondant et cochez / décochez la case **SSL**.

        3. **Utilisateur / mot de passe**
           * Généralement l’adresse e‑mail complète est utilisée comme *utilisateur*.  
           * Certains services (Gmail, iCloud…) requièrent un **mot de passe d’application** :
             créez‑le dans le tableau de bord sécurité du fournisseur et copiez‑le ici.

        4. **Adresse expéditeur**
           Saisissez l’adresse qui doit apparaître dans le champ « From ».  
           Pour éviter que les messages arrivent dans le spam :  
           – utilisez la même adresse que celle du compte SMTP ;  
           – vérifiez que votre domaine publie un enregistrement **SPF** correct.

        5. **Tester l’envoi**
           Cliquez sur **Tester l’envoi** : vous recevrez un message témoin.  
           En cas d’erreur :
           * `Authentication failed` → vérifiez identifiant / mot de passe.  
           * `Could not connect` → port ou hôte incorrect, pare‑feu bloquant.  
           * `535 5.7.0 Authentication Required` → activez l’authentification SMTP dans
             le webmail ou créez un mot de passe d’application.
        
        ---
        #### Glossaire rapide
        • **SMTP** : protocole standard d’envoi d’e‑mails.  
        • **SSL / TLS** : chiffrement de la connexion.  
        • **STARTTLS** : déclenchement du chiffrement après la connexion.  
        • **SPF / DKIM** : enregistrements DNS prouvant l’authenticité des mails.        
        """;

    public SmtpHelpDialog() {
        setTitle("Aide – configuration SMTP classique");
        getDialogPane().getButtonTypes().add(ButtonType.CLOSE);

        TextArea ta = new TextArea(HELP_TEXT);
        ta.setEditable(false);
        ta.setWrapText(true);
        ta.setPrefColumnCount(80);
        ta.setPrefRowCount(40);

        // occuper l’espace disponible
        ta.setMaxWidth(Double.MAX_VALUE);
        ta.setMaxHeight(Double.MAX_VALUE);
        VBox.setVgrow(ta, Priority.ALWAYS);

        Region spacer = new Region(); // pour un padding correct avec le thème sombre
        VBox root = new VBox(ta, spacer);
        getDialogPane().setContent(root);

        ThemeManager.apply(this);
    }
}

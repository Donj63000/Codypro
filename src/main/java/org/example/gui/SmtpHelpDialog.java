package org.example.gui;

import javafx.scene.control.*;
import javafx.scene.layout.*;

public final class SmtpHelpDialog extends Dialog<Void> {
    private static final String TITLE = "Aide – configuration SMTP classique";

    private static final String HELP_TEXT = """
        ### Paramétrage SMTP classique – pas à pas

        1. **Trouver les informations de votre fournisseur**

           | Fournisseur                | Serveur SMTP              | Port SSL 465 | Port STARTTLS 587 |
           |----------------------------|---------------------------|--------------|-------------------|
           | Gmail (mot de passe appli) | `smtp.gmail.com`          | 465          | 587               |
           | Outlook / Office 365       | `smtp.office365.com`      | 465          | 587               |
           | Free                       | `smtp.free.fr`            | 465          | —                 |
           | Orange                     | `smtp.orange.fr`          | 465          | 587               |
           | OVH e‑mail pro             | `ssl0.ovh.net`            | 465          | 587               |

           Pour tout autre service, cherchez : **SMTP _nom‑du‑fournisseur_**.

        2. **SSL vs STARTTLS**

           * **SSL / SMTPS** : chiffrement immédiat (465).  
           * **STARTTLS** : chiffrement négocié après connexion (587).

           Sélectionnez le port puis activez ou non l’option **SSL**.

        3. **Authentification**

           * L’utilisateur est généralement l’adresse complète.  
           * Gmail, iCloud … exigent un **mot de passe d’application**.

        4. **Adresse expéditeur**

           Renseignez l’adresse qui apparaîtra dans « From ».  
           Pour éviter le spam : utilisez la même adresse que celle du compte SMTP et
           assurez‑vous que le domaine publie un enregistrement **SPF** valide.

        5. **Tester l’envoi**

           Utilisez **Tester l’envoi** ; vous devez recevoir un message test.  
           * `Authentication failed` : identifiant ou mot de passe incorrect.  
           * `Could not connect` : hôte ou port erroné, pare‑feu bloquant.  
           * `535 5.7.0 Authentication Required` : activez SMTP authentifié ou créez
             un mot de passe d’application.

        ---

        #### Glossaire

        • **SMTP** : protocole d’envoi des e‑mails  
        • **SSL / TLS** : chiffrement de la connexion  
        • **STARTTLS** : bascule vers TLS après connexion  
        • **SPF / DKIM** : enregistrements DNS d’authentification
        """;

    public SmtpHelpDialog() {
        setTitle(TITLE);
        getDialogPane().getButtonTypes().add(ButtonType.CLOSE);

        TextArea area = new TextArea(HELP_TEXT);
        area.setEditable(false);
        area.setWrapText(true);
        area.setPrefColumnCount(80);
        area.setPrefRowCount(40);
        area.setMaxWidth(Double.MAX_VALUE);
        area.setMaxHeight(Double.MAX_VALUE);
        VBox.setVgrow(area, Priority.ALWAYS);

        VBox box = new VBox(area, new Region());
        box.setPrefWidth(760);
        getDialogPane().setContent(box);

        ThemeManager.apply(this);
    }
}

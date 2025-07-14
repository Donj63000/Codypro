# Prestataires Manager

Ce projet est une petite application JavaFX destinée à gérer des prestataires de services.
Elle stocke les informations dans une base SQLite locale et propose une interface graphique pour :

- lister, créer et modifier des prestataires ;
- enregistrer les prestations réalisées pour chaque prestataire ;
- rechercher dans les enregistrements ;
- exporter les fiches de prestataires et l'historique global au format PDF.

## Prérequis

- Java 17 ou supérieur
- Maven
- Un identifiant *client OAuth 2.0* Google (facultatif mais
  nécessaire pour l'envoi via Gmail)

## Version de sqlite-jdbc requise

La méthode `setFullSync(boolean)` nécessite la bibliothèque
`sqlite-jdbc` en version **3.39.4.0** ou supérieure. Assurez-vous qu'aucun
autre module n'impose une version plus ancienne. Pour vérifier la
version effectivement utilisée et détecter d'éventuels conflits, lancez :

```bash
mvn -q dependency:tree -Dincludes=org.xerial:sqlite-jdbc
```

## Exécution de l'application

Le composant `Mailer` s'appuie désormais sur la configuration
fournie par la classe [`MailPrefs`](src/main/java/org/example/mail/MailPrefs.java).
Par défaut, `MailPrefs.defaultValues()` est utilisée mais vous
pouvez adapter ces paramètres (serveur SMTP, identifiants, modèles de
messages) selon votre environnement avant de lancer l'application :

```java
MailPrefs prefs = MailPrefs.defaultValues();
// personnaliser si nécessaire :
// MailPrefs custom = new MailPrefs(...);
```

L'exécution se fait ensuite via Maven :

```bash
mvn javafx:run
```

Maven télécharge les dépendances JavaFX nécessaires et lance
`org.example.MainApp`.

Vous pouvez également construire le projet en JAR :

```bash
mvn package
```

L'archive générée se trouve dans `target/`.

Un bouton **Paramètres e-mail…** dans la fenêtre principale ouvre la boîte de
configuration rapide `MailQuickSetupDialog`. Celle‑ci propose une liste de
fournisseurs (Gmail, Outlook / Office365 (OAuth), Personnalisé), un lien **Connexion Gmail…** ou
**Connexion Microsoft…** pour l'authentification OAuth et masque par défaut la
section avancée (hôte, port, SSL, identifiants, etc.). Vous pouvez toujours
déployer cette section pour saisir manuellement un autre serveur SMTP.

## Installation dans IntelliJ

1. Ouvrez IntelliJ IDEA.
2. Choisissez **File > Open…** puis sélectionnez le fichier `pom.xml` du projet.
3. Confirmez l'importation en tant que projet Maven et attendez le téléchargement des dépendances.
4. Lancez la classe `org.example.MainApp` ou utilisez la commande *Run* proposée par Maven.

## Sécurité et environnement

L'application sait désormais s'authentifier auprès de Gmail via OAuth 2. Pour
en bénéficier vous devez créer un *client OAuth* sur
[console.cloud.google.com](https://console.cloud.google.com) :

1. activez l'API Gmail ;
2. créez des identifiants **Client OAuth 2.0** de type *Application de bureau*
   ou *Web* et autorisez l'URL de redirection `http://localhost` ;
3. renseignez uniquement l'`client_id` dans la base (champ `oauth_client`).

La boîte `MailQuickSetupDialog` contient le lien « Connexion Gmail… » ou
« Connexion Microsoft… » qui lance l'autorisation dans votre navigateur. Une fois
validée, le jeton est mémorisé et utilisé par `Mailer.send()` sans saisie de mot
de passe. Si vous préférez un autre serveur, ouvrez la section avancée et saisissez
simplement ses paramètres SMTP.

Un mode avancé d'authentification Gmail est maintenant disponible. Il repose
sur la classe `GoogleAuthService` et permet de
générer un `refresh_token` permanent puis un `access_token` à la volée pour les
envois. Sélectionnez **Gmail OAuth2** dans la boîte de configuration rapide et
cliquez sur *Se connecter à Google* pour autoriser l'application.

Les champs `oauth_client` et `oauth_refresh` sont chiffrés avant
l'enregistrement en base. La clé symétrique doit être définie via la
variable d'environnement `TOKEN_KEY` (seuls les 16 octets utilisés).
L'application refuse de démarrer si cette variable est absente.

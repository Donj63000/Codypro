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

Un bouton **Mail…** dans la fenêtre principale ouvre désormais la boîte de
dialogue `MailSettingsDialog`. Celle‑ci propose un lien **Connexion Gmail…**
pour autoriser l'envoi depuis votre compte et permet d'éditer les modèles
d'e‑mail. Les options SMTP avancées (hôte, port, SSL, identifiants, etc.)
y sont également accessibles pour utiliser un autre fournisseur.

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
3. renseignez l'`client_id` et le `client_secret` dans la base (champ
   `oauth_client` sous la forme `id:secret`).

La boîte `MailSettingsDialog` contient le lien « Connexion Gmail… » qui lance
l'autorisation dans votre navigateur. Une fois validée, le jeton est mémorisé et
utilisé par `Mailer.send()` sans saisie de mot de passe. Si vous préférez un
autre serveur, indiquez simplement ses paramètres SMTP dans la même boîte de
dialogue.

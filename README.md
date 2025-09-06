# Prestataires Manager

Application JavaFX pour gérer des prestataires de services.
Elle stocke les informations dans une base SQLite locale et propose une interface graphique pour :

- lister, créer et modifier des prestataires ;
- enregistrer les prestations réalisées pour chaque prestataire ;
- rechercher dans les enregistrements ;
- exporter les fiches de prestataires et l'historique global au format PDF.

## Prérequis

- Java 17 ou supérieur
- Maven
- Un identifiant client OAuth 2.0 Google (facultatif mais nécessaire pour l'envoi via Gmail)

## sqlite-jdbc avec chiffrement

Le projet utilise SQLite chiffré pour les bases utilisateur (SQLCipher via l’artefact Willena). Le `pom.xml` référence :

```xml
<dependency>
  <groupId>io.github.willena</groupId>
  <artifactId>sqlite-jdbc</artifactId>
  <version>3.50.1.0</version>
</dependency>
```

Cette dépendance expose `org.sqlite.*` et permet d’utiliser `PRAGMA key/rekey`.
Pour vérifier les conflits de dépendances :

```bash
mvn -q dependency:tree -Dincludes=io.github.willena:sqlite-jdbc
```

## Exécution

Le composant `Mailer` s'appuie sur la configuration fournie par la classe [`MailPrefs`](src/main/java/org/example/mail/MailPrefs.java).
Par défaut, `MailPrefs.defaultValues()` est utilisée mais vous pouvez adapter ces paramètres (serveur SMTP, identifiants, modèles de messages) avant lancement :

```java
MailPrefs prefs = MailPrefs.defaultValues();
// personnaliser si nécessaire
```

L'exécution via Maven :

```bash
mvn javafx:run
```

Maven télécharge les dépendances JavaFX nécessaires et lance `org.example.MainApp`.

Construction du JAR :

```bash
mvn package
```

L'archive générée se trouve dans `target/`.

## Paramètres e‑mail et OAuth

Un bouton « Paramètres e‑mail » dans la fenêtre principale ouvre la configuration rapide `MailQuickSetupDialog` (fournisseurs : Gmail, Outlook/Office365 (OAuth), Personnalisé). Un lien « Connexion Gmail » ou « Connexion Microsoft » lance l'authentification dans votre navigateur. Une fois validée, le jeton est mémorisé et utilisé par `Mailer.send()`.

Pour Gmail, créez un client OAuth (application de bureau) dans la console Google Cloud, puis renseignez l’ID client dans `oauth_client` (le secret n’est pas requis, PKCE est utilisé).

## Installation dans IntelliJ

1. Ouvrez IntelliJ IDEA.
2. File > Open puis sélectionnez `pom.xml`.
3. Confirmez l'import en tant que projet Maven.
4. Lancez `org.example.MainApp` ou la cible Maven Run.


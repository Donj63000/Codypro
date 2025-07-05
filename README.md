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

## Installation dans IntelliJ

1. Ouvrez IntelliJ IDEA.
2. Choisissez **File > Open…** puis sélectionnez le fichier `pom.xml` du projet.
3. Confirmez l'importation en tant que projet Maven et attendez le téléchargement des dépendances.
4. Lancez la classe `org.example.MainApp` ou utilisez la commande *Run* proposée par Maven.

## Sécurité et environnement

Pour envoyer des e‑mails via Gmail vous devez activer les « mots de passe d'application » sur votre compte (ou
utiliser le serveur SMTP fourni par votre hébergeur). Configurez ensuite les paramètres SMTP
correspondants dans `MailPrefs` (hôte, port, utilisateur, mot de passe, etc.) pour que `Mailer.send()`
puisse établir la connexion chiffrée sur le port adéquat.

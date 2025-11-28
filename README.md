# Prestataires Manager

Application JavaFX pour gérer des prestataires de services.
Elle stocke les informations dans une base SQLite locale et propose une interface graphique pour :

- lister, créer et modifier des prestataires ;
- enregistrer les prestations réalisées pour chaque prestataire ;
- rechercher dans les enregistrements ;
- exporter les fiches de prestataires et l'historique global au format PDF ;
- administrer les comptes utilisateurs (création, renommage, suppression, changement de mot de passe).

## Prérequis

- Java 17 ou supérieur
- Maven

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

## Notifications

L'ancien module d'e-mails a été retiré. Les rappels s'affichent désormais dans le tableau de bord, et un système de notifications push est en préparation pour les prochaines versions.

## Installation dans IntelliJ

1. Ouvrez IntelliJ IDEA.
2. File > Open puis sélectionnez `pom.xml`.
3. Confirmez l'import en tant que projet Maven.
4. Lancez `org.example.MainApp` ou la cible Maven Run.

## Devlog (journaldebord.txt)

Creez et maintenez un fichier `journaldebord.txt` a la racine du projet comme journal de bord (devlog). A chaque modification ou action sur le plugin, ajoutez une ligne avec la date et une courte description en francais pour garder l'historique (exemple : `2025-10-12 - ajout de la recherche avancee`).
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

Le composant `Mailer` utilise deux variables d'environnement :

- `MAIL_USER` – adresse utilisée pour envoyer les mails
- `MAIL_PWD` – mot de passe du compte

Exportez-les avant de démarrer le programme :

```bash
export MAIL_USER="user@example.com"
export MAIL_PWD="secret"
mvn javafx:run
```

Maven télécharge les dépendances JavaFX nécessaires et lance `org.example.MainApp`.

Vous pouvez également construire le projet en JAR :

```bash
mvn package
```

L'archive générée se trouve dans `target/`.

## Installation dans IntelliJ

1. Ouvrez IntelliJ IDEA.
2. Choisissez **File > Open…** puis sélectionnez le fichier `pom.xml` du projet.
3. Confirmez l'importation en tant que projet Maven et attendez le téléchargement des dépendances.
4. Configurez les variables d'environnement `MAIL_USER` et `MAIL_PWD` dans la configuration d'exécution.
5. Lancez la classe `org.example.MainApp` ou utilisez la commande *Run* proposée par Maven.

## Sécurité et environnement

Pour envoyer des e‑mails via Gmail vous devez activer les « mots de passe d'application » sur votre compte (ou
utiliser le serveur SMTP fourni par votre hébergeur). Une fois activés, exportez les variables d'environnement suivantes avant de lancer le programme afin que `Mailer.send()` utilise TLS sur le port 465 :

```bash
export MAIL_USER="your.address@gmail.com"
export MAIL_PWD="the_application_password"
```

Avec ces variables en place, l'envoi de mails doit fonctionner immédiatement.

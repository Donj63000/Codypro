# Agent – Guide de Collaboration (CodyPrestataires)

Ce document précise comment travailler efficacement avec l’agent IA sur ce dépôt. Il cadre le contexte, les commandes utiles, les conventions à respecter et les garde‑fous sécurité spécifiques au projet.

## Contexte du projet
- Fonction: Application JavaFX pour gérer des prestataires (création, suivi, factures, rappels) avec export PDF et alertes d’échéance (notifications push à venir).
- Stockage: SQLite local par utilisateur, chiffré (SQLCipher via `io.github.willena/sqlite-jdbc`). Authentification locale (Argon2 pour hash, PBKDF2 pour dériver la clé AES des données).
- UI/Thème: JavaFX, thèmes clair/sombre (`src/main/resources/css`), police Inter.

## Arborescence (repères rapides)
- `org.example.MainApp`: bootstrap JavaFX, login/register, ouverture DB utilisateur chiffrée, initialisation du tableau de bord et des alertes.
- `org.example.dao.*`: accès DB, schéma, migrations légères, calculs d’alertes factures.
- `org.example.security.*`: Argon2 (hash), PBKDF2 (dérivation), AES‑GCM (données sensibles/champs).
- `org.example.gui.*`: vues et dialogues (login, register, thèmes, formulaires prestataire/factures, alertes).
- `org.example.model.*`: modèles (Prestataire, Facture, ServiceRow, Rappel).
- `org.example.pdf.PDF`: export PDF (OpenPDF).

## Prérequis & commandes
- Pré-requis: Java 17+, Maven.
- Lancer l’app: `mvn javafx:run`
- Packager: `mvn package` (JAR + libs runtime dans `target/lib`)
- Dépendances SQLite (SQLCipher): `mvn -q dependency:tree -Dincludes=io.github.willena:sqlite-jdbc`

## Invariants et sécurité (à respecter strictement)
- Chiffrement des données utilisateur: ne jamais commuter la DB en clair. Les champs sensibles (mots de passe, futures clés de notification) passent par `TokenCrypto`/`CryptoUtils` (AES‑GCM).
- Auth locale: ne pas altérer Argon2/PBKDF2 ni diminuer les itérations par défaut.
- OAuth legacy: module retiré. Toute future intégration push devra stocker ses secrets chiffrés et respecter la même discipline que l’auth locale.
- Logs: éviter toute donnée sensible dans les traces; conserver SLF4J simple (niveau via `org.slf4j.simpleLogger.defaultLogLevel`).

## Conventions de code
- Java 17: utilisation de `record` quand pertinent, `var` limité, API JavaFX moderne.
- Base de données:
  - Toujours `PreparedStatement`, aucun SQL concaténé avec données utilisateur.
  - Schéma géré par `DB.initSchema(...)` + helpers d’upgrade (ajout colonnes/index). Ne pas introduire de migrations destructives.
- Paquets: rester sous `org.example.*`, ne pas renommer sans nécessité.
- UI: labels FR par défaut, cohérents; styles via `ThemeManager` et CSS existant.
- Notifications: factoriser toute future logique push dans un service dédié appelé depuis `MainApp` ou un scheduler clairement identifié.

## Flux et comportements clés
- Démarrage: `MainApp.start()`
  1) Auth locale (création utilisateur si base `auth.db` vide).
  2) DB utilisateur: `~/.prestataires/<username>.db` ouverte via `UserDB.openOrRepair(key)` (gère NOTADB, migration, WAL).
  3) DAO sécurisé: `SecureDB` chiffre/déchiffre certaines colonnes métier.
  4) UI: `MainView` affiche la table, les métriques et charge les alertes d’échéance via `updateAlerts()`.
- Alertes factures: `DB.facturesImpayeesAvant(...)` + `MainView.renderAlerts(...)` synthétisent retards et échéances sous 72 h.

## Ce que l’agent peut faire
- Corriger/ajouter des fonctionnalités UI JavaFX (dialogs, tables, thèmes).
- Étendre le modèle/DAO (nouvelles colonnes) via migrations non destructives et tests manuels.
- Préparer l’arrivée des notifications push (stockage de tokens, déclencheurs, simulations).
- Améliorer PDF (mises en page/sections/export sélectif).
- Outillage dev: scripts de build, vérifications de dépendances, diagnostics.

## À éviter (Do/Don’t)
- Ne pas committer de secrets (OAuth, SMTP, tokens) ni de bases utilisateur.
- Ne pas changer les primitives crypto (AES‑GCM/Argon2/PBKDF2) ni réduire paramètres KDF.
- Ne pas désactiver WAL/PRAGMA sans restauration adéquate (`SqlcipherUtil`).
- Ne pas injecter de nouvelles lib lourdes sans besoin (préférer standard lib/dep existantes).

## Dépannage rapide
- `SQLITE_NOTADB`: la séquence `openOrRepair` de `UserDB` migre (clair->chiffré) ou isole fichier corrompu (`.corrupt.<ts>`), puis rouvre correctement avec clé.
- Alertes absentes: vérifier qu’il existe des factures impayées avec échéance <72 h ou en retard; sinon la section reste masquée.
- Factures non visibles dans les métriques: confirmer que `DB.facturesImpayeesAvant` renvoie des résultats et que `MainView.reload` est invoqué.

## Conseils de requêtes à l’agent
- Décrire: objectif, contraintes (sécurité/UX/performances), acceptance criteria, impact UI/DB.
- Donner: extraits de fichiers clés, stacktrace, scénario de repro, contexte OS/JDK.
- Demander: patch minimal, tests manuels suggérés, commandes Maven à exécuter.

## Exemples de tâches formulées
- « Prépare la structure pour des notifications push (entités, DAO, stub service) ».
- « Améliore le panneau d’alertes avec un filtre par statut ».
- « Explique pourquoi une facture réglée apparaît encore dans les alertes ».
- « Ajoute un export PDF des factures impayées par période ».

## `instructions.md` (prioritaire)
- Ce dépôt contient un fichier `instructions.md` où le propriétaire place ses prompts/consignes.
- Lorsque l'utilisateur le demande, l'agent DOIT suivre et appliquer à la lettre ce qui est écrit dans `instructions.md` pour ce projet, sans s'écarter ni extrapoler.
- En cas de conflit avec des pratiques générales, considérer `instructions.md` comme source d'autorité pour ce dépôt, sauf instruction explicite contraire donnée par l'utilisateur dans l'échange en cours.
- Avant d'implémenter une demande mentionnant `instructions.md`, s'y référer systématiquement et exécuter précisément les étapes indiquées.

## Commandes utiles (rappel)
- Lancer: `mvn javafx:run`
- Packager: `mvn package`
- Dép graph deps SQLite chiffrée: `mvn -q dependency:tree -Dincludes=io.github.willena:sqlite-jdbc`

---

Si vous souhaitez, je peux aussi: (a) ajouter un gabarit de tests unitaires ciblant `CryptoUtils`/`TokenCrypto`/`DB`, (b) documenter l’UX du panneau d’alertes (captures), (c) ajouter une checklist CI légère (format/lint/compile).

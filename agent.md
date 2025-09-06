# Agent – Guide de Collaboration (CodyPrestataires)

Ce document précise comment travailler efficacement avec l’agent IA sur ce dépôt. Il cadre le contexte, les commandes utiles, les conventions à respecter et les garde‑fous sécurité spécifiques au projet.

## Contexte du projet
- Fonction: Application JavaFX pour gérer des prestataires (création, suivi, factures, rappels) avec export PDF et envoi d’e‑mails (SMTP classique ou OAuth pour Gmail/Outlook).
- Stockage: SQLite local par utilisateur, chiffré (SQLCipher via `io.github.willena/sqlite-jdbc`). Authentification locale (Argon2 pour hash, PBKDF2 pour dériver la clé AES des données).
- UI/Thème: JavaFX, thèmes clair/sombre (`src/main/resources/css`), police Inter.
- Mail: Templates FR/EN, auto‑découverte SMTP (SRV/Mozilla), relais SMTP local optionnel pour tests (localhost:2525).

## Arborescence (repères rapides)
- `org.example.MainApp`: bootstrap JavaFX, login/register, ouverture DB utilisateur chiffrée, planif. des rappels, lancement relais SMTP local.
- `org.example.dao.*`: accès DB, schéma, migrations légères, DAO des préférences mail chiffrées.
- `org.example.security.*`: Argon2 (hash), PBKDF2 (dérivation), AES‑GCM (données sensibles/champs).
- `org.example.mail.*`: configuration SMTP/OAuth, Google/Microsoft OAuth (flux locaux), envoi via Jakarta Mail, relais SMTP local.
- `org.example.gui.*`: vues et dialogues (login, register, paramètres mail, aides OAuth/SMTP, thème).
- `org.example.model.*`: modèles (Prestataire, Facture, ServiceRow, Rappel).
- `org.example.pdf.PDF`: export PDF (OpenPDF).

## Prérequis & commandes
- Pré-requis: Java 17+, Maven.
- Lancer l’app: `mvn javafx:run`
- Packager: `mvn package` (JAR + libs runtime dans `target/lib`)
- Dépendances SQLite (SQLCipher): `mvn -q dependency:tree -Dincludes=io.github.willena:sqlite-jdbc`

## Invariants et sécurité (à respecter strictement)
- Chiffrement des données utilisateur: ne jamais commuter la DB en clair. Les champs sensibles (mots de passe SMTP, tokens OAuth) passent par `TokenCrypto`/`CryptoUtils` (AES‑GCM).
- Auth locale: ne pas altérer Argon2/PBKDF2 ni diminuer les itérations par défaut.
- OAuth:
  - Gmail: client « application installée » (PKCE) — seul `client_id` requis; secret non nécessaire.
  - Microsoft: `client_id:client_secret` requis.
  - Ne jamais consigner ni committer des tokens/ID/secret. Utiliser `MailPrefsDAO` qui chiffre en DB.
- Relais SMTP local: éviter boucles de relais (si `host` = localhost, messages archivés en `~/.prestataires/outbox`).
- Logs: éviter toute donnée sensible dans les traces; conserver SLF4J simple (niveau via `org.slf4j.simpleLogger.defaultLogLevel`).

## Conventions de code
- Java 17: utilisation de `record` quand pertinent, `var` limité, API JavaFX moderne.
- Base de données:
  - Toujours `PreparedStatement`, aucun SQL concaténé avec données utilisateur.
  - Schéma géré par `DB.initSchema(...)` + helpers d’upgrade (ajout colonnes/index). Ne pas introduire de migrations destructives.
- Paquets: rester sous `org.example.*`, ne pas renommer sans nécessité.
- UI: labels FR par défaut, cohérents; styles via `ThemeManager` et CSS existant.
- Mail: passer par `Mailer.send(...)` ou `Mailer.send(..., MimeMessage)` pour garantir l’initialisation de Session (OAuth/SSL/TLS).

## Flux et comportements clés
- Démarrage: `MainApp.start()`
  1) Auth locale (création utilisateur si base `auth.db` vide).
  2) DB utilisateur: `~/.prestataires/<username>.db` ouverte via `UserDB.openOrRepair(key)` (gère NOTADB, migration, WAL).
  3) DAO sécurisé: `SecureDB` chiffre/déchiffre certaines colonnes métier.
  4) UI + scheduler rappels (toutes les 60 min).
  5) Relais SMTP local (2525) pour tests.
- Envoi e‑mail: `MailPrefsDAO.load()` -> `MailPrefs` -> `OAuthServiceFactory` si provider `gmail|outlook` -> `Mailer` (Jakarta Mail + XOAUTH2 si besoin).
- OAuth (desktop): serveur HTTP local éphémère + navigateur; persistance du refresh token chiffré; rafraîchissement auto.

## Ce que l’agent peut faire
- Corriger/ajouter des fonctionnalités UI JavaFX (dialogs, tables, thèmes).
- Étendre le modèle/DAO (nouvelles colonnes) via migrations non destructives et tests manuels.
- Ajouter un provider mail (nouveau `OAuthService` + `OAuthServiceFactory`).
- Améliorer la robustesse mail (retries limités, erreurs expliquées UI, envoi test).
- Améliorer PDF (mises en page/sections/export sélectif).
- Outillage dev: scripts de build, vérifications de dépendances, diagnostics.

## À éviter (Do/Don’t)
- Ne pas committer de secrets (OAuth, SMTP, tokens) ni de bases utilisateur.
- Ne pas changer les primitives crypto (AES‑GCM/Argon2/PBKDF2) ni réduire paramètres KDF.
- Ne pas désactiver WAL/PRAGMA sans restauration adéquate (`SqlcipherUtil`).
- Ne pas injecter de nouvelles lib lourdes sans besoin (préférer standard lib/dep existantes).

## Dépannage rapide
- `SQLITE_NOTADB`: la séquence `openOrRepair` de `UserDB` migre (clair->chiffré) ou isole fichier corrompu (`.corrupt.<ts>`), puis rouvre correctement avec clé.
- OAuth bloqué: vérifier `oauth.port` (JVM) ou `OAUTH_PORT` (env); pas de proxy bloquant; s’assurer que `oauthClient` est renseigné.
- Envoi mail échoue:
  - SMTP classique: host/port/SSL/STARTTLS/identifiants.
  - OAuth Gmail/Outlook: refresh token expiré -> `MailPrefsDAO.invalidateOAuth()` ou relancer l’assistant.
  - Tester via « Paramètres e‑mail » -> « Tester l’envoi » ou via relais local (soumettre un `.eml`).

## Conseils de requêtes à l’agent
- Décrire: objectif, contraintes (sécurité/UX/performances), acceptance criteria, impact UI/DB.
- Donner: extraits de fichiers clés, stacktrace, scénario de repro, contexte OS/JDK.
- Demander: patch minimal, tests manuels suggérés, commandes Maven à exécuter.

## Exemples de tâches formulées
- « Ajoute un preset SMTP {fournisseur} et son auto‑découverte ».
- « Ajoute un style de templates mail ‘es’ et le sélecteur dans le dialog ».
- « Explique puis corrige l’erreur d’auth SMTP affichée lors des rappels ».
- « Ajoute un export PDF des factures impayées par période ».

## Commandes utiles (rappel)
- Lancer: `mvn javafx:run`
- Packager: `mvn package`
- Dép graph deps SQLite chiffrée: `mvn -q dependency:tree -Dincludes=io.github.willena:sqlite-jdbc`

---

Si vous souhaitez, je peux aussi: (a) ajouter un gabarit de tests unitaires ciblant `CryptoUtils`/`TokenCrypto`/`DB`, (b) documenter l’UX des dialogues mail (captures), (c) ajouter une checklist CI légère (format/lint/compile).


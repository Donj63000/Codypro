# Exigences – Prestataires Manager

Ce document définit les exigences fonctionnelles et non‑fonctionnelles de l’application, afin d’aligner développement, tests et validation utilisateur.

## 1. Vision et périmètre
- But: gérer les prestataires de services, leurs contrats, factures et rappels, avec envoi d’e‑mails et exports PDF.
- Cible: usage bureau mono‑utilisateur, données locales chiffrées, authentification locale.

## 2. Rôles et terminologie
- Utilisateur: personne utilisant l’app (auth locale).
- Prestataire: entité gérée (nom, société, contact, note, facturation, date contrat).
- Facture: montant, TVA, TTC, échéance, état payé/impayé.
- Rappel/Préavis: e‑mail automatique vers prestataire et/ou utilisateur selon règles.
- MailPrefs: préférences d’envoi (SMTP classique ou OAuth Gmail/Outlook).

## 3. Exigences fonctionnelles (EF)
- EF‑01 Authentification locale
  - Inscription si `auth.db` vide, puis connexion utilisateur.
  - Critères: après inscription, une session est ouverte automatiquement; après connexion valide, session créée.
- EF‑02 Chiffrement des données utilisateur
  - La base `~/.prestataires/<username>.db` doit être chiffrée (SQLCipher). Si base claire détectée, migration automatique vers chiffrée; si fichier corrompu, isolement et recréation.
  - Critères: ouverture via `openOrRepair` réussit pour 3 cas (chiffrée valide, claire à migrer, corrompue à isoler).
- EF‑03 Gestion des prestataires
  - Lister, créer, modifier, supprimer; stocker date de contrat (format FR) et compter impayés.
  - Critères: opérations CRUD persistantes; index et FK respectés.
- EF‑04 Services rendus
  - Ajouter et lister les lignes de services par prestataire avec date; chiffrer la description au repos.
  - Critères: description non lisible dans la DB; lecture restituée en clair dans l’UI.
- EF‑05 Factures
  - Créer, lister (filtre payé/impayé), marquer payée/non payée; champs montants (HT, TVA, TTC, devise) gérés et normalisés.
  - Critères: calculs par défaut cohérents (TVA/ttc), dates stockées en `yyyy-MM-dd` + timestamp.
- EF‑06 Rappels planifiés (scheduler)
  - Tâche périodique (60 min) qui:
    - Envoie préavis internes à l’utilisateur 48/24/12 h avant l’échéance (une seule fois par tranche).
    - Envoie e‑mails de rappel aux prestataires au‑delà de `delayHours` après l’échéance et copie à soi si configuré.
  - Critères: déduplication par clé `factureId:slot`; marquage `preavis_envoye`.
- EF‑07 E‑mails sortants
  - Envoi via SMTP classique (SSL ou STARTTLS) ou OAuth XOAUTH2 (Gmail/Outlook).
  - Critères: From renseigné; en cas d’OAuth, jeton d’accès valide; en cas d’échec OAuth, invalidation du refresh token et feedback UI.
- EF‑08 Templates e‑mail
  - Deux jeux min. (FR/EN) pour prestataire et copie à soi, avec variables `%NOM%`, `%EMAIL%`, `%MONTANT%`, `%ECHEANCE%`, `%ID%`.
  - Critères: rendu correct, placeholders remplacés; fallback sur défauts si champs vides.
- EF‑09 Assistant de configuration mail
  - Dialog « Paramètres e‑mail »: presets (local, custom, gmail, outlook), test d’envoi, flux OAuth Gmail (PKCE) et aide dédiée.
  - Critères: test d’envoi réussi avec conf valide; OAuth stocke refresh token chiffré et déduit l’adresse utilisateur si manquante.
- EF‑10 Relais SMTP local
  - Serveur SMTP local (par défaut 2525) acceptant un message et le relayant via `MailPrefs`, ou l’archivant si `host=localhost`.
  - Critères: messages `.eml` déposés en `~/.prestataires/outbox` si boucle potentielle.
- EF‑11 Export PDF
  - Export « fiche prestataire » et « historique global » (OpenPDF).
  - Critères: fichiers générés avec contenu minimal lisible et encodage correct.
- EF‑12 Thèmes et UI
  - Thèmes clair/sombre via CSS; bascule safe UI (`-Dapp.safeUi=true`) pour désactiver les styles si besoin.
  - Critères: l’UI démarre avec styles par défaut et reste fonctionnelle en mode safe.
- EF‑13 Internationalisation minimale
  - Textes en français par défaut; templates e‑mail FR/EN sélectionnables.
  - Critères: sélection de style mail persistée.
- EF‑14 Journalisation
  - Logs via SLF4J Simple; niveau configurable par propriété système.
  - Critères: aucune donnée sensible (mots de passe, tokens) dans les logs.

## 4. Exigences non‑fonctionnelles (ENF)
- ENF‑01 Sécurité: hashage mots de passe avec Argon2id; dérivation clé AES via PBKDF2; chiffrement AES‑GCM.
- ENF‑02 Robustesse DB: WAL activé, FK ON, busy_timeout, détection auto des colonnes manquantes et migration additive.
- ENF‑03 Performance: ouverture DB et première requête < 1s sur poste standard; scheduler non bloquant (thread daemon).
- ENF‑04 Portabilité: Java 17+, Maven; Windows/macOS/Linux (JavaFX profils de plateforme).
- ENF‑05 Confidentialité: préférences mail sensibles chiffrées au repos (pwd/oauth).
- ENF‑06 Expérience: erreurs présentées dans l’UI avec messages compréhensibles et actions de remédiation (ex: reconfigurer OAuth).

## 5. Contraintes & dépendances
- Java 17+, Maven.
- Dépendances principales: JavaFX, sqlite-jdbc (SQLCipher), HikariCP, Argon2, Jakarta Mail, OpenPDF, Jackson, SLF4J, SubEthaSMTP.

## 6. Données & schéma (résumé)
- Tables: `prestataires`, `services`, `factures`, `rappels`, `mail_prefs` (+ `users` dans `auth.db`).
- Horodatages en colonnes `*_ts` (epoch seconds UTC) et dates lisibles `yyyy-MM-dd`.
- Clés étrangères avec cascade conformément au schéma.

## 7. Critères d’acceptation (exemples ciblés)
- CA‑01 À la première exécution, si aucun utilisateur, l’écran d’inscription s’affiche et la création aboutit à une session ouverte.
- CA‑02 Si `~/.prestataires/<username>.db` est une DB claire, elle est migrée automatiquement en chiffrée; sinon si corrompue, elle est mise de côté et une DB chiffrée neuve est créée.
- CA‑03 Un rappel 24h avant l’échéance est envoyé une seule fois et visible dans les logs; `preavis_envoye` vaut 1 après envoi.
- CA‑04 En OAuth Gmail, un test d’envoi aboutit sans saisir de mot de passe SMTP; en cas d’expiration, l’app force la reconnexion.
- CA‑05 L’export « Fiche prestataire » crée un PDF non vide avec les champs principaux.

## 8. Hors périmètre (v1)
- Multi‑utilisateur simultané (concurrence sur la même DB).
- Synchronisation cloud ou serveur distant.
- Gestion avancée des relances (templates HTML riches, pièces jointes multiples, KPI).

## 9. Build & exécution
- Lancer: `mvn javafx:run`
- Packager: `mvn package` (JAR + libs en `target/lib`).
- Dépendance SQLCipher: `mvn -q dependency:tree -Dincludes=io.github.willena:sqlite-jdbc`

## 10. Qualité & tests (lignes directrices)
- Tests unitaires ciblant: `CryptoUtils` (chiffrement), `TokenCrypto` (rond‑trip), migrations `DB` (colonnes manquantes), rendu templates `Mailer`.
- Tests manuels: parcours OAuth (Gmail/Outlook), envoi test, relais local, exports PDF, scénarios NOTADB.

---
Document vivant: ajuster selon l’évolution des besoins et du code.


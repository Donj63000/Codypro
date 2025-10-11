# Exigences – Prestataires Manager

Ce document définit les exigences fonctionnelles et non‑fonctionnelles de l’application, afin d’aligner développement, tests et validation utilisateur.

## 1. Vision et périmètre
- But: gérer les prestataires de services, leurs contrats, factures et rappels, avec alertes d’échéance (tableau de bord aujourd’hui, notifications push en préparation) et exports PDF.
- Cible: usage bureau mono‑utilisateur, données locales chiffrées, authentification locale.

## 2. Rôles et terminologie
- Utilisateur: personne utilisant l’app (auth locale).
- Prestataire: entité gérée (nom, société, contact, note, facturation, date contrat).
- Facture: montant, TVA, TTC, échéance, état payé/impayé.
- Rappel/Préavis: notification planifiée associée à une facture (affichée dans l’UI, future distribution push).

## 3. Exigences fonctionnelles (EF)
- EF‑01 Authentification locale
  - Inscription si `auth.db` vide, puis connexion utilisateur.
  - Gestion des comptes depuis l'interface (« Configuration du compte ») avec création, renommage, suppression (hors session active) et changement de mot de passe.
  - Critères: après inscription, une session est ouverte automatiquement; après connexion valide, session créée; les opérations CRUD sur les comptes sont reflétées immédiatement et empêchent la suppression du dernier compte ou du compte actif.
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
- EF‑06 Alertes d’échéance
  - Tableau de bord mettant en avant les factures à échéance sous 72 h et celles en retard, avec résumé et liste détaillée.
  - Critères: l’alerte s’affiche/masque dynamiquement en fonction des données et indique un résumé cohérent (retards vs à surveiller).
- EF‑07 Export PDF
  - Export « fiche prestataire » et « historique global » (OpenPDF).
  - Critères: fichiers générés avec contenu minimal lisible et encodage correct.
- EF‑08 Thèmes et UI
  - Thèmes clair/sombre via CSS; bascule safe UI (`-Dapp.safeUi=true`) pour désactiver les styles si besoin.
  - Critères: l’UI démarre avec styles par défaut et reste fonctionnelle en mode safe.
- EF‑09 Internationalisation minimale
  - Textes en français par défaut; contenus clé exposés dans les deux thèmes.
  - Critères: terminologie homogène entre les vues principales.
- EF‑10 Journalisation
  - Logs via SLF4J Simple; niveau configurable par propriété système.
  - Critères: aucune donnée sensible (mots de passe, tokens) dans les logs.

## 4. Exigences non‑fonctionnelles (ENF)
- ENF‑01 Sécurité: hashage mots de passe avec Argon2id; dérivation clé AES via PBKDF2; chiffrement AES‑GCM.
- ENF‑02 Robustesse DB: WAL activé, FK ON, busy_timeout, détection auto des colonnes manquantes et migration additive.
- ENF‑03 Performance: ouverture DB et première requête < 1s sur poste standard; scheduler non bloquant (thread daemon).
- ENF‑04 Portabilité: Java 17+, Maven; Windows/macOS/Linux (JavaFX profils de plateforme).
- ENF‑05 Confidentialité: secrets applicatifs (mots de passe, clés futures pour notifications) doivent rester chiffrés ou dérivés.
- ENF‑06 Expérience: erreurs présentées dans l’UI avec messages compréhensibles et actions de remédiation (ex: reconfigurer OAuth).

## 5. Contraintes & dépendances
- Java 17+, Maven.
- Dépendances principales: JavaFX, sqlite-jdbc (SQLCipher), HikariCP, Argon2, OpenPDF, Jackson, Ikonli, SLF4J.

## 6. Données & schéma (résumé)
- Tables: `prestataires`, `services`, `factures`, `rappels` (+ `users` dans `auth.db`).
- Horodatages en colonnes `*_ts` (epoch seconds UTC) et dates lisibles `yyyy-MM-dd`.
- Clés étrangères avec cascade conformément au schéma.

## 7. Critères d’acceptation (exemples ciblés)
- CA‑01 À la première exécution, si aucun utilisateur, l’écran d’inscription s’affiche et la création aboutit à une session ouverte.
- CA‑02 Si `~/.prestataires/<username>.db` est une DB claire, elle est migrée automatiquement en chiffrée; sinon si corrompue, elle est mise de côté et une DB chiffrée neuve est créée.
- CA‑03 Une facture dont l’échéance est dans moins de 72 h apparaît dans la section alertes avec la bonne couleur (warning/danger) et le résumé se met à jour.
- CA‑04 Si toutes les factures sont réglées ou au-delà de 72 h, la section alertes se masque automatiquement.
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
- Tests unitaires ciblant: `CryptoUtils` (chiffrement), `TokenCrypto` (rond‑trip), migrations `DB` (colonnes manquantes), calculs de métriques/alertes factures.
- Tests manuels: vérification des alertes (factures proches/retard), exports PDF, scénarios NOTADB.

---
Document vivant: ajuster selon l’évolution des besoins et du code.

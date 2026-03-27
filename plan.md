# Plan d’implémentation – relances e-mail automatiques pour factures impayées

## 1. But du chantier

Implémenter dans cette application JavaFX locale un sous-système complet de relance e-mail couvrant **deux flux distincts** :

1. **Flux interne** : envoyer un e-mail de synthèse à l’utilisateur de l’application pour lister les prestataires / factures impayés.
2. **Flux externe** : envoyer automatiquement un e-mail au prestataire lorsque sa facture approche de l’échéance, le jour d’échéance si nécessaire, puis en relance périodique tant qu’elle n’est pas réglée.

Le système doit être **fiable, traçable, sans doublons, testable**, et compatible avec l’architecture actuelle : application bureau JavaFX, base locale SQLite chiffrée, mono-utilisateur.

---

## 2. Décision de périmètre pour la v1

### 2.1 Décision obligatoire

La **v1** doit fonctionner selon ce mode d’exploitation :

- l’application doit être **ouverte** ou **minimisée dans le tray** pour que les relances partent ;
- il n’y a **pas de backend distant**, **pas de service Windows/macOS/Linux séparé**, **pas d’exécution cloud** ;
- les envois sont faits **depuis le poste local** de l’utilisateur connecté.

### 2.2 Pourquoi cette décision

Le dépôt actuel est construit autour d’une base utilisateur locale chiffrée, ouverte après authentification. Tant qu’aucune session n’est ouverte, le programme ne dispose pas naturellement de la clé nécessaire pour exploiter la base et envoyer des rappels en tâche de fond hors application. Vouloir un système qui fonctionne application fermée reviendrait à changer d’architecture.

### 2.3 Hors périmètre explicite de la v1

Ne pas implémenter dans ce chantier :

- service autonome quand l’application est fermée ;
- synchronisation cloud ;
- serveur distant ;
- campagnes e-mail HTML riches ;
- pièces jointes ;
- OAuth Gmail / Microsoft ;
- multi-utilisateur simultané ;
- KPI avancés / reporting métier.

---

## 3. Constat sur le code actuel

Ce dépôt contient déjà une base utile, mais incomplète pour le besoin demandé.

### 3.1 Éléments déjà présents

- `src/main/java/org/example/notifications/NotificationService.java`
  - scheduler local toutes les 60 secondes ;
  - notifications desktop uniquement.
- `src/main/java/org/example/model/NotificationSettings.java`
  - préavis, heure, minute, répétition, template sujet/corps.
- `src/main/java/org/example/util/NotificationTemplateEngine.java`
  - moteur simple de placeholders pour les messages.
- `src/main/java/org/example/model/Prestataire.java`
  - champ e-mail déjà disponible.
- `src/main/java/org/example/dao/DB.java`
  - tables `factures`, `rappels`, `notification_settings` ;
  - champs `paye`, `echeance`, `preavis_envoye` ;
  - méthodes `addRappel`, `rappelsAEnvoyer`, `markRappelEnvoye`, etc.
- `src/main/java/org/example/gui/NotificationSettingsDialog.java`
  - UI de configuration des notifications desktop.
- `src/main/java/org/example/gui/PrestataireFormDialog.java`
  - validation de base de l’adresse e-mail.

### 3.2 Limitations actuelles à corriger

- Le service existant ne gère **pas** l’envoi e-mail.
- `NotificationService.tick()` s’arrête actuellement si `desktopPopup=false` : ce comportement ne doit **plus** bloquer les e-mails.
- `DB.facturesImpayeesAvant(...)` filtre sur `preavis_envoye=0`, ce qui mélange :
  - le **suivi métier** des impayés,
  - et l’**état technique** d’un rappel déjà envoyé.
- `reminderHistory` dans `NotificationService` est en mémoire uniquement, donc non fiable après redémarrage.
- La table `rappels` est trop limitée pour un vrai outbox e-mail robuste.
- Les secrets SMTP n’ont pas encore de stockage dédié sécurisé.
- Le README indique que l’ancien module e-mail a été retiré : il faut donc réintroduire une solution propre, documentée et maintenable.

---

## 4. Résultat attendu en fin de chantier

À la fin, on doit pouvoir démontrer ceci :

1. L’utilisateur configure un transport SMTP dans l’application.
2. L’utilisateur active séparément :
   - le digest interne ;
   - les relances externes.
3. Une facture impayée à échéance proche déclenche automatiquement un e-mail externe à l’heure prévue.
4. Une facture arrivée à échéance et toujours impayée peut déclencher un e-mail le jour J puis des relances périodiques.
5. Un e-mail interne de synthèse est envoyé à l’utilisateur avec la liste des impayés.
6. Aucun doublon n’est envoyé lors des redémarrages ou des ticks répétés.
7. Si une facture est marquée payée avant l’envoi, l’e-mail externe ne part pas.
8. Si une adresse prestataire manque, l’e-mail externe ne part pas mais l’impayé reste visible côté interne.
9. Les erreurs d’envoi sont historisées.
10. Le mot de passe SMTP n’est jamais stocké en clair dans la base.
11. Les notifications desktop existantes continuent de fonctionner.
12. Les tests automatiques couvrent le comportement critique.

---

## 5. Préparation spécifique pour Codex

### 5.1 Important

Le dépôt contient `agent.md`, mais pas `AGENTS.md` à la racine.

### 5.2 Action demandée à Codex en premier

**Phase 0 obligatoire** : créer un fichier `AGENTS.md` à la racine en recopiant fidèlement le contenu utile de `agent.md`, puis ajouter à la fin une courte note indiquant que, pour ce chantier, `plan.md` doit être lu avant toute implémentation.

### 5.3 Règles de travail à respecter par Codex

- lire `AGENTS.md`, `agent.md`, `requirements.md`, puis ce `plan.md` ;
- travailler par phases incrémentales ;
- ne jamais faire de migration destructive ;
- ne jamais stocker un secret en clair ;
- ne jamais casser l’existant desktop ;
- commenter brièvement en français les portions non triviales ;
- ajouter / adapter les tests à chaque étape ;
- exécuter `mvn test` régulièrement ;
- éviter d’ajouter des dépendances lourdes inutiles.

---

## 6. Architecture cible

### 6.1 Principe général

Le moteur de relance doit être séparé en **quatre responsabilités** :

1. **Chargement sécurisé de la configuration e-mail**
2. **Planification des messages à envoyer**
3. **File d’attente persistée (outbox)**
4. **Envoi réel via SMTP + historique d’état**

### 6.2 Composants recommandés

Créer les composants suivants (noms proposés, à garder sauf contrainte forte) :

- `org.example.model.EmailSettings`
- `org.example.model.NotificationJob`
- `org.example.model.NotificationJobType`
- `org.example.model.NotificationJobStatus`
- `org.example.model.SmtpSecurityMode`
- `org.example.dao.EmailSettingsStore`
- `org.example.notifications.EmailSender`
- `org.example.notifications.SmtpEmailSender`
- `org.example.notifications.NotificationPlanner`
- `org.example.notifications.NotificationDispatcher`
- `org.example.notifications.InternalDigestComposer`

### 6.3 Rôle de chaque composant

#### `EmailSettings`
Contient la configuration e-mail métier et technique.

#### `EmailSettingsStore`
Charge/sauvegarde la configuration e-mail dans la base, en chiffrant le mot de passe SMTP via `TokenCrypto`.

#### `NotificationJob`
Représente un message prêt à être envoyé ou déjà envoyé/échoué.

#### `NotificationPlanner`
Décide quels jobs doivent exister **maintenant**. Il ne doit pas envoyer directement.

#### `NotificationDispatcher`
Lit les jobs en attente, envoie les e-mails, marque le résultat.

#### `EmailSender`
Abstraction de transport pour faciliter les tests.

#### `SmtpEmailSender`
Implémentation concrète via SMTP.

#### `InternalDigestComposer`
Construit le contenu du mail interne de synthèse.

#### `NotificationService`
Reste l’orchestrateur de fond appelé par `MainApp`, mais délègue la logique aux composants ci-dessus.

---

## 7. Modèle de données cible

### 7.1 Table `email_settings`

Créer une nouvelle table dédiée à la configuration e-mail. Ne pas surcharger `notification_settings`, qui reste dédiée au comportement desktop.

```sql
CREATE TABLE IF NOT EXISTS email_settings(
    id INTEGER PRIMARY KEY CHECK(id=1),
    email_enabled INTEGER NOT NULL DEFAULT 0,

    smtp_host TEXT NOT NULL DEFAULT '',
    smtp_port INTEGER NOT NULL DEFAULT 587,
    smtp_security_mode TEXT NOT NULL DEFAULT 'STARTTLS',
    smtp_username TEXT NOT NULL DEFAULT '',
    smtp_password_enc TEXT NOT NULL DEFAULT '',

    from_email TEXT NOT NULL DEFAULT '',
    from_name TEXT NOT NULL DEFAULT '',
    reply_to TEXT NOT NULL DEFAULT '',

    internal_digest_enabled INTEGER NOT NULL DEFAULT 0,
    internal_digest_to TEXT NOT NULL DEFAULT '',
    internal_digest_hour INTEGER NOT NULL DEFAULT 8,
    internal_digest_minute INTEGER NOT NULL DEFAULT 0,

    external_reminders_enabled INTEGER NOT NULL DEFAULT 0,
    external_lead_days INTEGER NOT NULL DEFAULT 3,
    external_send_hour INTEGER NOT NULL DEFAULT 9,
    external_send_minute INTEGER NOT NULL DEFAULT 0,
    send_on_due_date INTEGER NOT NULL DEFAULT 1,
    overdue_repeat_days INTEGER NOT NULL DEFAULT 3,

    external_subject_template TEXT NOT NULL DEFAULT 'Facture {{prestataire}} : échéance le {{echeance}}',
    external_body_template TEXT NOT NULL DEFAULT 'Bonjour,\n\nLa facture {{facture}} d''un montant de {{montant}} arrive {{delai}}.\nStatut : {{statut}}.\n\nSi votre règlement a déjà été effectué, merci d''ignorer ce message.'
);
```

#### Remarques

- `smtp_password_enc` contient une valeur **chiffrée**.
- `email_enabled` permet de désactiver complètement le moteur e-mail sans toucher aux autres options.
- le digest interne et les relances externes sont activables séparément.

### 7.2 Table `notification_jobs`

Créer une vraie file de jobs persistée.

```sql
CREATE TABLE IF NOT EXISTS notification_jobs(
    id INTEGER PRIMARY KEY,
    channel TEXT NOT NULL,
    job_type TEXT NOT NULL,
    status TEXT NOT NULL DEFAULT 'PENDING',

    facture_id INTEGER,
    prestataire_id INTEGER,

    recipient TEXT NOT NULL,
    subject TEXT NOT NULL,
    body TEXT NOT NULL,

    dedupe_key TEXT NOT NULL UNIQUE,

    scheduled_at_ts INTEGER NOT NULL,
    next_attempt_at_ts INTEGER NOT NULL,
    sent_at_ts INTEGER,
    last_attempt_at_ts INTEGER,

    attempt_count INTEGER NOT NULL DEFAULT 0,
    max_attempts INTEGER NOT NULL DEFAULT 3,
    last_error TEXT NOT NULL DEFAULT '',

    created_at_ts INTEGER NOT NULL,
    updated_at_ts INTEGER NOT NULL,

    FOREIGN KEY(facture_id) REFERENCES factures(id) ON DELETE CASCADE,
    FOREIGN KEY(prestataire_id) REFERENCES prestataires(id) ON DELETE CASCADE
);
```

### 7.3 Index à ajouter

Ajouter au minimum :

```sql
CREATE INDEX IF NOT EXISTS idx_jobs_pending ON notification_jobs(status, next_attempt_at_ts);
CREATE INDEX IF NOT EXISTS idx_jobs_facture ON notification_jobs(facture_id);
CREATE INDEX IF NOT EXISTS idx_jobs_prestataire ON notification_jobs(prestataire_id);
CREATE INDEX IF NOT EXISTS idx_jobs_scheduled ON notification_jobs(scheduled_at_ts);
```

### 7.4 Compatibilité ascendante

- **Ne pas supprimer** `rappels`.
- **Ne pas supprimer** `preavis_envoye`.
- `preavis_envoye` peut être conservé comme **marqueur legacy** après premier rappel réussi, mais il ne doit plus servir de source de vérité pour le tableau de bord, le digest interne ou la logique globale des impayés.

---

## 8. Configuration métier à implémenter

### 8.1 Paramètres SMTP

L’UI et le modèle doivent permettre de configurer :

- hôte SMTP ;
- port ;
- mode de sécurité (`NONE`, `STARTTLS`, `SSL_TLS`) ;
- identifiant SMTP ;
- mot de passe SMTP ;
- adresse d’expédition ;
- nom d’expéditeur ;
- adresse de réponse facultative.

### 8.2 Paramètres digest interne

- activation / désactivation ;
- destinataire interne ;
- heure ;
- minute.

### 8.3 Paramètres relances externes

- activation / désactivation ;
- nombre de jours avant échéance ;
- heure d’envoi ;
- minute d’envoi ;
- envoi le jour d’échéance oui/non ;
- intervalle de relance en retard (en jours).

### 8.4 Paramètres non configurables en v1

Conserver en dur pour simplifier la v1 :

- `max_attempts = 3` ;
- backoff d’échec = 15 minutes ;
- format = texte brut UTF-8 ;
- pas de pièce jointe.

---

## 9. Règles métier détaillées

### 9.1 Rappel externe avant échéance

Une facture est éligible au premier rappel externe si :

- elle est impayée (`paye=0`) ;
- le prestataire possède une adresse e-mail valide ;
- `external_reminders_enabled=true` ;
- `email_enabled=true` ;
- l’instant actuel est supérieur ou égal à :
  - `date_echeance à external_send_hour:external_send_minute` moins `external_lead_days` ;
- aucun job `SENT` n’existe avec la clé de déduplication du premier rappel.

Clé recommandée :

```text
external:first:<factureId>:<dueDate>
```

### 9.2 Rappel externe le jour d’échéance

Une facture est éligible au rappel du jour J si :

- elle est impayée ;
- l’option `send_on_due_date=true` ;
- l’instant actuel est supérieur ou égal à la date d’échéance à l’heure configurée ;
- aucun job `SENT` n’existe pour ce type.

Clé recommandée :

```text
external:due:<factureId>:<dueDate>
```

### 9.3 Relances en retard

Une facture est éligible à une relance retard si :

- elle est impayée ;
- elle est passée d’échéance ;
- `overdue_repeat_days > 0` ;
- on a atteint un palier entier de retard selon l’intervalle configuré.

Règle proposée : pour `overdue_repeat_days = N`, envoyer aux dates :

- `dueDate + N jours`
- `dueDate + 2N jours`
- `dueDate + 3N jours`
- etc.

Clé recommandée :

```text
external:overdue:<factureId>:<dueDate>:<bucket>
```

où `bucket = nbJoursRetard / overdue_repeat_days` (division entière strictement positive).

### 9.4 Digest interne

Créer **au plus un digest par jour local** si :

- `internal_digest_enabled=true` ;
- `email_enabled=true` ;
- la date/heure du digest est atteinte ;
- il existe au moins une facture impayée ;
- aucun job `SENT` n’existe pour ce jour.

Clé recommandée :

```text
internal:digest:<yyyy-MM-dd>
```

### 9.5 Arrêt des relances

Aucun e-mail externe ne doit partir si, au moment du dispatch :

- la facture a été réglée ;
- la facture a été supprimée ;
- l’adresse prestataire est absente/invalide ;
- la configuration e-mail est inactive ou invalide.

### 9.6 Important

Même si `preavis_envoye=1`, une facture impayée doit **continuer** à apparaître :

- dans le tableau de bord métier ;
- dans le digest interne ;
- dans les requêtes de suivi des impayés.

---

## 10. Stratégie technique de planification

### 10.1 Règle de conception

Le planner ne doit **pas** créer des jobs des semaines à l’avance. Il doit créer des jobs **au moment où ils deviennent dus**.

### 10.2 Pourquoi

Cela simplifie fortement :

- la gestion des changements de date d’échéance ;
- l’annulation implicite si une facture devient payée avant le moment d’envoi ;
- l’idempotence après redémarrage ;
- la reprise après fermeture/réouverture de l’application.

### 10.3 Conséquence

Le tick périodique doit faire :

1. charger les paramètres ;
2. calculer les jobs dus maintenant ;
3. insérer les jobs manquants via `dedupe_key` ;
4. dispatcher les jobs `PENDING` dus ;
5. historiser l’état.

---

## 11. Plan de réalisation détaillé par phases

## Phase 0 – Préparer le dépôt pour Codex

### Objectif
Donner à Codex un contexte projet réellement exploitable.

### Tâches

- créer `AGENTS.md` à la racine à partir de `agent.md` ;
- ajouter une note courte demandant de lire `plan.md` pour ce chantier ;
- vérifier qu’il n’existe pas d’`instructions.md` à respecter ;
- lancer un test de base : `mvn test` ;
- consigner les écarts éventuels avant modifications.

### Critère de fin

- `AGENTS.md` existe ;
- `mvn test` passe ou les écarts initiaux sont clairement identifiés.

---

## Phase 1 – Poser les modèles et le schéma de base

### Fichiers à créer / modifier

Créer :

- `src/main/java/org/example/model/EmailSettings.java`
- `src/main/java/org/example/model/NotificationJob.java`
- `src/main/java/org/example/model/NotificationJobType.java`
- `src/main/java/org/example/model/NotificationJobStatus.java`
- `src/main/java/org/example/model/SmtpSecurityMode.java`

Modifier :

- `src/main/java/org/example/dao/DB.java`
- `src/main/java/org/example/dao/DbBootstrap.java`

### Tâches

- ajouter les nouveaux modèles ;
- définir valeurs par défaut + normalisation ;
- créer la table `email_settings` ;
- créer la table `notification_jobs` ;
- ajouter les index ;
- mettre à jour **les deux** chemins de schéma :
  - `DB.initSchema(...)` / migrations internes ;
  - `DbBootstrap.ensureSchema(...)`.

### Contraintes

- migrations additives uniquement ;
- ne rien casser pour les bases existantes ;
- les timestamps doivent rester en **epoch seconds UTC** comme le reste du schéma.

### Critère de fin

- ouverture d’une base neuve OK ;
- ouverture d’une base existante OK ;
- nouvelles tables présentes ;
- tests unitaires de schéma ajoutés.

---

## Phase 2 – Ajouter le stockage sécurisé de la configuration SMTP

### Fichiers à créer / modifier

Créer :

- `src/main/java/org/example/dao/EmailSettingsStore.java`

Modifier si nécessaire :

- `src/main/java/org/example/util/TokenCrypto.java` (uniquement si nécessaire, sans casser l’existant)
- `src/main/java/org/example/MainApp.java`

### Tâches

- implémenter `EmailSettingsStore(ConnectionProvider provider, SecretKey key)` ;
- charger/sauvegarder `EmailSettings` ;
- chiffrer `smtp_password` avec `TokenCrypto.encrypt(...)` ;
- déchiffrer avec `TokenCrypto.decrypt(...)` ;
- ne jamais logguer le mot de passe ;
- prévoir un comportement sûr si le secret n’est pas déchiffrable.

### Remarque d’implémentation

La configuration e-mail est un bon candidat pour un store dédié plutôt qu’un gonflement direct de `DB`, car elle manipule un secret et doit être injectable à la fois dans l’UI et dans le service de notifications.

### Critère de fin

- round-trip de sauvegarde/chargement validé ;
- mot de passe SMTP stocké chiffré dans la base ;
- aucune fuite dans les logs.

---

## Phase 3 – Séparer le suivi métier des impayés de l’état des relances

### Problème à résoudre

Aujourd’hui, `facturesImpayeesAvant(...)` filtre `preavis_envoye=0`. Cette méthode ne doit plus servir au suivi métier global.

### Fichiers à modifier

- `src/main/java/org/example/dao/DB.java`
- `src/main/java/org/example/gui/MainView.java`
- tests de DAO existants

### Tâches

Ajouter des méthodes explicites, par exemple :

- `facturesImpayeesPourSuiviAvant(LocalDateTime limit)`
- `facturesImpayeesPourDigest()`
- `facturesImpayeesAvecPrestataire()` ou équivalent pratique

Mettre à jour `MainView` pour utiliser la requête de suivi métier qui **ignore** `preavis_envoye`.

### Règle

- `preavis_envoye` peut rester mis à jour pour compatibilité legacy ;
- mais il ne doit plus faire disparaître un impayé des vues internes de suivi.

### Tests à adapter / ajouter

- une facture impayée déjà prévenue doit **encore** apparaître dans le suivi interne ;
- une facture payée ne doit pas apparaître ;
- une facture future hors fenêtre ne doit pas apparaître.

### Critère de fin

- le tableau de bord et le digest futur se basent sur une vraie vue métier des impayés ;
- la logique n’est plus couplée à `preavis_envoye`.

---

## Phase 4 – Introduire l’outbox persisté et le dispatcher

### Fichiers à créer / modifier

Créer :

- `src/main/java/org/example/notifications/EmailSender.java`
- `src/main/java/org/example/notifications/SmtpEmailSender.java`
- `src/main/java/org/example/notifications/NotificationDispatcher.java`

Modifier :

- `pom.xml`
- `src/main/java/org/example/dao/DB.java`

### Tâches

#### Dépendance mail
Ajouter une dépendance mail légère et compatible Java 17 / Jakarta Mail. Préférer une solution standard basée sur Jakarta Mail / Angus Mail, sans framework lourd.

#### DAO jobs
Ajouter dans `DB` des méthodes de ce type :

- `insertNotificationJobIfAbsent(NotificationJob job)`
- `notificationJobExistsByDedupeKey(String key)`
- `pendingNotificationJobsDue(Instant now)`
- `markNotificationJobSent(...)`
- `markNotificationJobAttemptFailed(...)`
- `listNotificationJobs(int limit)`
- éventuellement `findNotificationJobByDedupeKey(...)`

#### Dispatcher
Le dispatcher doit :

- lire les jobs `PENDING` dont `next_attempt_at_ts <= now` ;
- envoyer via `EmailSender` ;
- marquer `SENT` si succès ;
- incrémenter `attempt_count`, `last_attempt_at_ts`, `last_error` si échec ;
- reprogrammer dans 15 minutes tant que `attempt_count < max_attempts` ;
- marquer `FAILED` si le nombre max est atteint.

#### Règle critique
Avant l’envoi d’un **job externe lié à une facture**, le dispatcher doit recharger la facture / le prestataire et vérifier que :

- la facture existe toujours ;
- la facture est toujours impayée ;
- le prestataire a toujours une adresse valide.

Sinon, le job doit être marqué comme annulé ou échoué fonctionnellement, mais **ne doit pas être envoyé**.

### Critère de fin

- l’application possède un vrai mécanisme de file d’attente persistée ;
- l’envoi n’est pas fait directement dans le planner ;
- les échecs sont rejouables et historisés.

---

## Phase 5 – Implémenter le planner des relances externes

### Fichiers à créer / modifier

Créer :

- `src/main/java/org/example/notifications/NotificationPlanner.java`

Modifier :

- `src/main/java/org/example/notifications/NotificationService.java`
- `src/main/java/org/example/util/NotificationTemplateEngine.java` (uniquement si besoin de placeholders supplémentaires)
- `src/main/java/org/example/dao/DB.java`

### Tâches

- calculer les factures impayées pertinentes ;
- vérifier la présence d’un e-mail prestataire ;
- déterminer si un premier rappel est dû ;
- déterminer si un rappel du jour d’échéance est dû ;
- déterminer si un rappel retard est dû ;
- rendre sujet/corps à partir du template externe ;
- insérer un `NotificationJob` avec `dedupe_key` stable ;
- ne jamais créer de doublon.

### Important

Le planner ne doit **jamais** envoyer directement.

### Compatibilité legacy

Lors d’un premier rappel externe **envoyé avec succès**, il est acceptable de mettre `preavis_envoye=1` pour compatibilité historique, mais cette colonne ne doit pas piloter le suivi métier.

### Critère de fin

- la logique de planification externe est pure, testable, idempotente ;
- les dedupe keys évitent les doublons ;
- les cas “sans e-mail” sont gérés proprement.

---

## Phase 6 – Implémenter le digest interne

### Fichiers à créer / modifier

Créer :

- `src/main/java/org/example/notifications/InternalDigestComposer.java`

Modifier :

- `src/main/java/org/example/notifications/NotificationPlanner.java`
- `src/main/java/org/example/dao/DB.java`

### Tâches

- récupérer les impayés via la vue métier correcte ;
- regrouper par prestataire / société ;
- calculer nombre de factures et montant total ;
- construire un corps texte clair ;
- créer un job `internal:digest:<date>` quand l’heure du digest est atteinte ;
- ne pas créer de digest si aucun impayé.

### Format recommandé du mail interne

Objet recommandé :

```text
Synthèse des factures impayées – 27/03/2026
```

Corps recommandé :

- résumé global (nb prestataires, nb factures, total TTC) ;
- blocs par prestataire ;
- pour chaque facture : libellé, échéance, statut, montant ;
- éventuellement section “prestataires sans e-mail” si pertinent.

### Règle métier

Le digest doit lister les impayés **même s’ils ont déjà reçu un rappel externe**.

### Critère de fin

- un digest quotidien peut être envoyé sans doublon ;
- le contenu reflète la réalité métier courante.

---

## Phase 7 – Refonte de l’UI de configuration e-mail et d’historique

### Choix d’UX

Conserver `NotificationSettingsDialog` pour les notifications locales desktop. Ajouter un **dialogue séparé** pour l’e-mail afin d’éviter de mélanger deux systèmes différents.

### Fichiers à créer / modifier

Créer :

- `src/main/java/org/example/gui/EmailSettingsDialog.java`
- `src/main/java/org/example/gui/NotificationHistoryDialog.java` (ou équivalent)

Modifier :

- `src/main/java/org/example/gui/MainView.java`
- `src/main/java/org/example/gui/PrestataireFormDialog.java`
- `src/main/java/org/example/gui/FacturesManagerDialog.java`
- éventuellement `src/main/resources/css/*.css`

### Tâches UI

#### Nouveau dialogue `EmailSettingsDialog`
Ajouter les sections :

1. **Activation générale**
   - activer l’envoi e-mail
2. **Transport SMTP**
   - hôte, port, sécurité, login, mot de passe
   - expéditeur, nom, reply-to
3. **Digest interne**
   - activation, destinataire, heure, minute
4. **Relances externes**
   - activation, préavis, heure, minute, relance jour J, relance retard
5. **Templates externes**
   - sujet
   - corps
6. **Test**
   - bouton “Envoyer un e-mail de test”

#### Validation UI

- `PasswordField` pour le mot de passe SMTP ;
- validation des adresses ;
- validation des plages horaires ;
- messages d’erreur compréhensibles.

#### Historique

Ajouter une vue simple de l’historique des jobs :

- date programmée ;
- destinataire ;
- type ;
- statut ;
- nombre de tentatives ;
- dernière erreur.

#### Prestataire

Dans `PrestataireFormDialog`, conserver / renforcer la validation e-mail et préciser qu’une adresse absente désactivera les relances automatiques pour ce prestataire.

#### Factures

Après ajout / édition / suppression / bascule payé-impayé dans `FacturesManagerDialog`, déclencher un `requestImmediateScan()` du service de notifications pour éviter d’attendre 60 secondes.

### Critère de fin

- l’utilisateur peut configurer tout le système sans toucher la base ;
- il peut tester le transport ;
- il peut consulter l’historique minimal.

---

## Phase 8 – Orchestration dans `MainApp` et `NotificationService`

### Fichiers à modifier

- `src/main/java/org/example/MainApp.java`
- `src/main/java/org/example/AppServices.java`
- `src/main/java/org/example/notifications/NotificationService.java`

### Tâches

- construire un `EmailSettingsStore` avec la clé de session ;
- charger les paramètres desktop et e-mail de façon sûre ;
- initialiser `SmtpEmailSender` ;
- injecter `NotificationPlanner` + `NotificationDispatcher` dans `NotificationService` ;
- ajouter une méthode `requestImmediateScan()` ;
- arrêter proprement le scheduler à la fermeture.

### Règle critique à corriger

Le moteur e-mail doit continuer à fonctionner **même si** `desktopPopup=false`.

Donc :

- ne plus faire de `return` global du `tick()` sur ce booléen ;
- séparer le canal desktop du canal e-mail.

### Tick recommandé

À chaque tick :

1. charger les paramètres courants ;
2. planifier les jobs externes dus ;
3. planifier le digest interne du jour si dû ;
4. dispatcher les jobs `PENDING` ;
5. gérer éventuellement l’aperçu desktop existant.

### Critère de fin

- `MainApp` démarre correctement le nouveau système ;
- l’arrêt et le redémarrage ne provoquent pas de doublons ;
- le desktop et l’e-mail sont découplés.

---

## Phase 9 – Tests, documentation, finitions

### Fichiers à créer / modifier

Créer / modifier les tests sous :

- `Tests_unitaires/src/test/java/...`

Modifier aussi :

- `README.md`
- `requirements.md`
- éventuellement `agent.md` / `AGENTS.md` si un rappel doc est utile

### Tests unitaires minimaux à ajouter

#### DAO / schéma

- création des tables `email_settings` et `notification_jobs` ;
- insert/select jobs ;
- déduplication par `dedupe_key` ;
- retry sur échec ;
- lecture/sauvegarde des paramètres e-mail.

#### Sécurité

- round-trip `EmailSettingsStore` ;
- mot de passe SMTP non stocké en clair.

#### Planner

- crée un premier rappel quand la fenêtre est atteinte ;
- ne crée pas de doublon au tick suivant ;
- crée le rappel du jour J si activé ;
- crée un bucket de retard correct ;
- ignore les prestataires sans e-mail.

#### Dispatcher

- envoi OK -> job `SENT` ;
- échec -> retry puis `FAILED` après max tentatives ;
- facture devenue payée avant envoi -> pas d’envoi.

#### Digest

- un seul digest par jour ;
- digest ignoré si aucun impayé ;
- digest inclut une facture déjà prévenue.

#### Régression métier

Mettre à jour `DBFactureAlertsTest` pour refléter la nouvelle séparation entre :

- suivi métier des impayés ;
- état technique de rappel.

### Documentation à mettre à jour

#### `README.md`
Ajouter :

- fonctionnement de la v1 e-mail ;
- contrainte “application ouverte ou minimisée” ;
- configuration SMTP ;
- historique / diagnostic.

#### `requirements.md`
Ajouter :

- EF : digest interne ;
- EF : rappels externes SMTP ;
- ENF : stockage chiffré du secret SMTP ;
- critères d’acceptation associés.

### Critère de fin

- `mvn test` vert ;
- documentation alignée ;
- feature utilisable de bout en bout.

---

## 12. Détail des fichiers existants à toucher

### `pom.xml`

- ajouter la dépendance mail ;
- ne pas ajouter de framework lourd ;
- conserver Java 17.

### `DB.java`

- schéma `email_settings` ;
- schéma `notification_jobs` ;
- index ;
- méthodes DAO pour jobs ;
- nouvelles requêtes métier d’impayés.

### `DbBootstrap.java`

- même schéma minimal côté bootstrap.

### `NotificationService.java`

- passer d’un service centré desktop à un orchestrateur multi-canaux.

### `MainView.java`

- utiliser les bonnes requêtes métier ;
- ajouter l’entrée de configuration e-mail ;
- ajouter l’entrée d’historique si nécessaire.

### `NotificationSettingsDialog.java`

- clarifier qu’il s’agit des notifications locales / desktop ;
- ne pas y mélanger les secrets SMTP si un dialogue dédié est créé.

### `PrestataireFormDialog.java`

- conserver validation ;
- message UX sur l’absence d’e-mail.

### `FacturesManagerDialog.java`

- appeler `requestImmediateScan()` après mutation d’une facture.

### `MainApp.java`

- injecter `EmailSettingsStore`, `NotificationPlanner`, `NotificationDispatcher`, `EmailSender`.

---

## 13. Contraintes techniques impératives

- Tous les accès SQL via `PreparedStatement`.
- Aucune migration destructive.
- Timestamps persistés en epoch seconds UTC.
- Horaire de décision calculé en heure locale machine via `ZoneId.systemDefault()`.
- Aucune opération réseau sur le thread JavaFX.
- Aucun secret SMTP dans les logs.
- Aucun envoi e-mail direct depuis l’UI.
- Le moteur doit être idempotent.
- Le moteur doit survivre à un redémarrage sans doublonner.

---

## 14. Stratégie de déduplication

### Principe

La déduplication repose sur `dedupe_key` unique en base.

### Avantages

- pas de doublon si le scheduler tourne plusieurs fois ;
- pas de doublon après redémarrage ;
- pas de dépendance à un historique en mémoire uniquement.

### Exemple de clés

- `external:first:42:2026-03-30`
- `external:due:42:2026-03-30`
- `external:overdue:42:2026-03-30:1`
- `internal:digest:2026-03-27`

---

## 15. Plan de tests manuels

### Scénario 1 – Test transport SMTP

- configurer SMTP valide ;
- cliquer “Envoyer un e-mail de test” ;
- vérifier la réception ;
- vérifier l’historique.

### Scénario 2 – Premier rappel externe

- créer un prestataire avec e-mail ;
- créer une facture impayée avec échéance proche ;
- régler le préavis pour la rendre immédiatement due ;
- lancer `requestImmediateScan()` ou attendre le tick ;
- vérifier un seul e-mail envoyé.

### Scénario 3 – Pas de doublon

- relancer le scan plusieurs fois ;
- vérifier qu’aucun second e-mail identique ne part.

### Scénario 4 – Facture payée avant dispatch

- créer un job externe dû ;
- marquer la facture payée avant l’envoi effectif ;
- vérifier qu’aucun e-mail n’est envoyé.

### Scénario 5 – Prestataire sans e-mail

- créer une facture impayée sans e-mail prestataire ;
- vérifier :
  - pas d’e-mail externe ;
  - présence dans le digest interne.

### Scénario 6 – Digest interne

- activer le digest ;
- créer plusieurs impayés sur plusieurs prestataires ;
- atteindre l’heure du digest ;
- vérifier :
  - un seul e-mail ;
  - regroupement correct ;
  - montants corrects.

### Scénario 7 – Retry sur erreur SMTP

- casser volontairement le port SMTP ou le mot de passe ;
- vérifier les tentatives ;
- vérifier le passage à `FAILED` après le nombre max.

### Scénario 8 – Redémarrage de l’application

- envoyer un premier rappel ;
- fermer puis rouvrir l’application ;
- relancer le scan ;
- vérifier absence de doublon.

---

## 16. Définition de terminé

Le chantier est considéré terminé uniquement si :

- le schéma a été mis à jour sans migration destructive ;
- les secrets SMTP sont chiffrés ;
- les deux flux e-mail fonctionnent ;
- l’historique des jobs existe ;
- le tableau de bord interne ne masque plus un impayé à cause de `preavis_envoye` ;
- `desktopPopup=false` n’empêche plus les e-mails ;
- les tests automatisés passent ;
- la documentation est à jour.

---

## 17. Prompt recommandé à donner à Codex

```text
Travaille dans ce dépôt JavaFX.
Lis d’abord AGENTS.md, agent.md, requirements.md et plan.md.
Implémente ce plan par phases, sans sortir du périmètre v1.

Contraintes impératives :
- aucune migration destructive ;
- aucun secret en clair ;
- ne casse pas les notifications desktop existantes ;
- garde la base compatible avec les données existantes ;
- ajoute les tests nécessaires ;
- exécute mvn test après chaque phase importante.

Commence par la phase 0, puis enchaîne jusqu’à la phase 9.
À la fin de chaque phase :
- résume ce qui a été fait ;
- liste les fichiers modifiés ;
- signale les risques éventuels ;
- indique l’état des tests.

Quand tu modifies la logique des impayés, sépare strictement :
- le suivi métier des factures impayées,
- l’état technique des relances déjà envoyées.

Ne réintroduis pas d’ancien module e-mail opaque. Construis un système propre :
EmailSettingsStore + NotificationPlanner + NotificationDispatcher + EmailSender + outbox persisté.
```

---

## 18. Bonus recommandé si le temps le permet

À faire seulement si tout le reste est vert :

- exposer un filtre d’historique par statut ;
- ajouter une mention dans le digest interne sur les prestataires sans e-mail ;
- améliorer les messages d’erreur SMTP les plus fréquents ;
- ajouter un aperçu texte du digest avant envoi.


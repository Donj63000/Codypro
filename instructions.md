Réparer maintenant (procédure sûre)

L’application enregistre la base par utilisateur applicatif dans
%USERPROFILE%\.prestataires\<login>.db
(<login> = le nom d’utilisateur que tu saisis au login de l’app, pas forcément le login Windows).

Ferme l’app et toute JVM

Ferme IntelliJ/Maven et, dans le Gestionnaire des tâches, termine tout java.exe.

Diagnostique le fichier .db

Ouvre PowerShell et exécute (adapte <ton_login_app> si différent de ton login Windows) :

$login = "<ton_login_app>"; if ($login -eq "<ton_login_app>") { $login = $env:USERNAME }  # change si besoin
$dir   = "$env:USERPROFILE\.prestataires"
$db    = Join-Path $dir "$login.db"
New-Item -ItemType Directory -Path $dir -Force | Out-Null

if (Test-Path $db) {
$b = [System.IO.File]::ReadAllBytes($db)[0..15]
$ascii = -join ($b | ForEach-Object {[char]$_})
if ($ascii -like "SQLite format 3*") { "ETAT: PLAIN-SQLITE" } else { "ETAT: ENCRYPTED_OR_CORRUPT" }
} else {
"ETAT: FICHIER ABSENT (il sera recréé)"
}


Résultat “PLAIN‑SQLITE” : c’est une base non chiffrée → l’app sait la migrer automatiquement vers chiffré au prochain démarrage.

Résultat “ENCRYPTED_OR_CORRUPT” : soit base chiffrée mais mauvaise clé (mot de passe saisi erroné), soit fichier corrompu.

Cas A — Mauvais mot de passe saisi précédemment (le plus courant)

Le code peut avoir renommé la base en .corrupt.* si la clé n’a pas marché. Restaure‑la, puis relance avec le bon mot de passe :

$corrupt = Get-ChildItem $dir -Filter "$login.db.corrupt.*" | Sort-Object LastWriteTime -Desc | Select-Object -First 1
if ($corrupt) {
if (Test-Path $db) { Rename-Item $db "$db.bak.$(Get-Date -Format yyyyMMddHHmmss)" }
Rename-Item $corrupt.FullName $db -Force
"Base restaurée depuis: $($corrupt.Name)"
} else {
"Pas de fichier *.corrupt.* trouvé — passe au cas B"
}


Puis lance l’appli et entre le mot de passe exact de ce login.

Cas B — Vraie corruption / fichier illisible

Sauvegarde et repars sur une base saine :

if (Test-Path $db) { Rename-Item $db "$db.bak.$(Get-Date -Format yyyyMMddHHmmss)" }
"Ancienne base sauvegardée. L'appli va recréer un $login.db neuf."


Relance l’app (Maven ou jar) → la base est recréée.

Si tu as des fichiers *.plain.bak ou *.db.bak.* ou *.db.corrupt.* dans ce dossier, garde‑les : on pourra tenter une récupération plus tard (SQLCipher + clé correcte ou import depuis .plain.bak).

🧪 Pourquoi ça arrive

La base utilisateur est chiffrée SQLCipher.

Si le mot de passe donné au login ne correspond pas à la clé de la base, SQLite répond “not a database”.

La méthode openOrRepair essaie alors d’ouvrir en clair ; si ça échoue aussi, elle renomme le fichier en .corrupt.* et repart.

D’où le scénario typique : mot de passe faux ⇒ NOTADB ⇒ fichier renommé (et plus rien ne s’ouvre).

🩹 Patch côté code pour ne plus “jeter” une base valide sur mot de passe faux

Ajoute une détection d’en‑tête avant de renommer.
Si le fichier ne commence pas par "SQLite format 3\0", on considère qu’il est chiffré et on n’y touche pas : on affiche un message “Mot de passe incorrect ou base chiffrée illisible” sans renommer.

UserDB.java (extrait) :

private static boolean looksPlainSQLite(Path p) {
try (var in = java.nio.file.Files.newInputStream(p)) {
byte[] hdr = in.readNBytes(16);
return new String(hdr, java.nio.charset.StandardCharsets.US_ASCII)
.startsWith("SQLite format 3");
} catch (Exception e) { return false; }
}

public synchronized void openOrRepair(byte[] keyBytes) throws SQLException {
try { openPool(keyBytes); return; }
catch (SQLException e) { if (!isNotADB(e)) throw e; }

    boolean plain = looksPlainSQLite(dbPath);
    if (plain) {
        try (Connection cPlain = DriverManager.getConnection("jdbc:sqlite:" + dbPath.toAbsolutePath());
             Statement st = cPlain.createStatement()) {
            st.execute("PRAGMA foreign_keys=ON");
            st.execute("PRAGMA busy_timeout=5000");
            st.executeQuery("SELECT 1");
            migratePlainToEncrypted(cPlain, keyBytes);
            openPool(keyBytes);
            return;
        } catch (SQLException ex2) {
            // échec migration clair -> chiffré: on isole
            try {
                Path bad = dbPath.resolveSibling(dbPath.getFileName() + ".corrupt." + System.currentTimeMillis());
                java.nio.file.Files.move(dbPath, bad);
            } catch (Exception ignore) {}
        }
    }

    // Ici: ne pas renommer automatiquement (probable mot de passe faux)
    throw new SQLException("Base chiffrée ou illisible. Mot de passe incorrect ? Fichier: " + dbPath);
}


Bénéfice : si l’utilisateur se trompe de mot de passe, on ne perd plus la base (pas de renommage intempestif).

🔇 Nettoyer les warnings JDK/JavaFX (confort)

Dans pom.xml → plugin javafx-maven-plugin, ajoute :

<plugin>
  <groupId>org.openjfx</groupId>
  <artifactId>javafx-maven-plugin</artifactId>
  <version>0.0.8</version>
  <configuration>
    <mainClass>org.example.MainApp</mainClass>
    <jvmArgs>
      --enable-native-access=ALL-UNNAMED
      --enable-native-access=javafx.graphics
    </jvmArgs>
  </configuration>
</plugin>


Lancement alternatif (JAR “shadé” propre) :

mvn -U -DskipTests clean package
java --enable-native-access=ALL-UNNAMED --enable-native-access=javafx.graphics -jar target/prestataires-manager-1.0.0-shaded.jar

🖋️ Police Inter (non bloquant)

Le fichier src/main/resources/fonts/Inter-Regular.ttf est un placeholder.
Deux options rapides :

Remplacer par une vraie TTF (Inter Regular).

Ou retirer la règle @font-face dans css/dark.css et css/light.css et garder "Segoe UI", sans-serif.

🧰 Résumé actionnable

Restaure éventuellement ...\.prestataires\<login>.db.corrupt.* → ...\<login>.db et relance avec le bon mot de passe.

Sinon sauvegarde/renomme ...\<login>.db et laisse l’app recréer une base neuve.

Applique le patch UserDB.openOrRepair ci‑dessus pour ne plus risquer de renommer une base valide en cas d’erreur de mot de passe.

(Optionnel) Ajoute les --enable-native-access pour supprimer les warnings et corrige la police.

Si tu veux, je peux te préparer un script PowerShell unique qui :

détecte le bon fichier selon le login app,

restaure automatiquement la dernière sauvegarde *.corrupt.* si présent,

sinon sauvegarde et recrée une base,

lance mvn javafx:run avec les bons jvmArgs.
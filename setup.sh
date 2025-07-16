# 1. Installer Java 17 et Maven
apt-get update -qq
apt-get install -yqq openjdk-17-jdk maven

# 2. Vérifier les installations
java -version
javac -version
mvn -v

# 3. Configurer Maven pour utiliser le proxy Codex
mkdir -p ~/.m2
cat > ~/.m2/settings.xml <<'EOF2'
<settings>
  <proxies>
    <proxy>
      <id>codexProxy</id>
      <active>true</active>
      <protocol>http</protocol>
      <host>proxy</host>
      <port>8080</port>
    </proxy>
  </proxies>
</settings>
EOF2

# 4. Charger les dépendances hors ligne
mvn dependency:go-offline -B

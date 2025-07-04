# Prestataires Manager

This project is a small JavaFX application used to manage service providers ("prestataires").
It stores information in a local SQLite database and offers a GUI to:

- List, create and edit providers
- Record services performed for each provider
- Search through records
- Export provider sheets and a global history as PDF files

## Requirements

- Java 17 or later
- Maven

## Running the application

The `Mailer` component uses two environment variables:

- `MAIL_USER` – address used to send mails
- `MAIL_PWD` – password for this account

Export them before starting the program:

```bash
export MAIL_USER="user@example.com"
export MAIL_PWD="secret"
mvn javafx:run
```

Maven downloads the required JavaFX dependencies and launches `org.example.MainApp`.

You can also build the project into a JAR with:

```bash
mvn package
```

The resulting archive will be located under `target/`.

## Security & environment

To send e‑mails via Gmail you must enable “App passwords” on your account (or
use the SMTP server provided by your host). Once enabled, export the following
environment variables before running the program so that `Mailer.send()` can use
TLS on port 465:

```bash
export MAIL_USER="your.address@gmail.com"
export MAIL_PWD="the_application_password"
```

With these variables in place the mailer should work out of the box.

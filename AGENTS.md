# Repository Guidelines

## Project Structure & Module Organization
The JavaFX application code lives in `src/main/java/org/example`, grouped by feature packages such as `gui`, `dao`, `security`, and shared utilities. FXML views, icons, and styles reside under `src/main/resources`; keep both `css/dark.css` and `css/light.css` in sync when adding UI elements. Tests are located in `Tests_unitaires/src/test/java` and are wired into Maven through the build-helper plugin. Build artifacts are written to `target`, with third-party jars collected in `target/lib` during packaging.

## Build, Test, and Development Commands
Use `mvn clean compile` to produce class files against Java 17. Run `mvn test` to execute the JUnit 5 suite sourced from `Tests_unitaires`. Launch the app locally with `mvn -Pwindows javafx:run`, swapping the profile for `-Plinux` or `-Pmac` when needed; this boots `org.example.MainApp`. Package distribution artifacts through `mvn -Pwindows package`, which assembles the runnable jar and dependency bundle.

## Coding Style & Naming Conventions
Follow four-space indentation and target lines under roughly 120 characters. Apply the IDE’s Java 17 formatter before committing. Name classes with PascalCase, methods and fields with camelCase, and constants with UPPER_SNAKE_CASE. Prefer expressive method names over inline comments; reserve short notes for non-obvious logic such as encryption fallbacks. When updating styling, mirror changes across both theme stylesheets.

## Testing Guidelines
Stick to JUnit Jupiter tests named `*Test.java` (for example, `ServiceStatusTest`). Arrange each test with Arrange/Act/Assert, and use `@Nested` classes when they clarify scenarios. Always run `mvn test` prior to submitting changes; for targeted checks invoke `mvn -Dtest=ClassNameTest test`. Aim for deterministic coverage on DAO branches and presenter logic so failures highlight real regressions.

## Commit & Pull Request Guidelines
Write commit subjects in the imperative sentence style already in history (e.g., `Fix shading collisions and remove placeholder font`). Keep commits scoped and call out behavioral risks or migrations in the body when relevant. PR descriptions should summarize user-facing impact, list manual verification commands, and include screenshots for UI adjustments. Link related issues and explicitly flag security-sensitive or database-affecting work so reviewers can prioritize scrutiny.

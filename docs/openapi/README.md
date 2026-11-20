# OpenAPI-Verträge

Die vier Dateien sind eigenständige OpenAPI-3.1-Verträge für die über Traefik sichtbaren REST-Services:

- `user-service.openapi.json`
- `listing-service.openapi.json`
- `image-service.openapi.json`
- `messaging-service.openapi.json`

Sie dokumentieren die tatsächlichen Gateway-Pfade, Request-/Response-Schemas, Statuscodes, Bearer-Authentifizierung, Pagination, den `Idempotency-Key` und die gemeinsame Fehlerhülle. Eventverträge bleiben bewusst in den jeweiligen Service-Adaptern und in der Architekturdokumentation; OpenAPI beschreibt HTTP.

## Generiert, nicht handgepflegt

Die Dateien werden von `quarkus-smallrye-openapi` beim Build aus MicroProfile-OpenAPI-Annotationen im Code erzeugt (`@Operation`, `@APIResponse(s)`, `@Schema`, `@SecurityRequirement` in den jeweiligen `resources/*.kt`-Dateien) und unter `docs/openapi/` eingecheckt. Damit bleiben sie weiterhin ohne laufenden Stack prüfbar und erfordern keine Swagger-UI im Produkt — die Quelle der Wahrheit ist jetzt aber der Code, nicht die JSON-Datei selbst.

Bei einer API-Änderung:

1. Resource-Methode, DTO und/oder OpenAPI-Annotationen gemeinsam mit Use Case und Tests ändern.
2. Contract des betroffenen Service neu generieren: `.\gradlew.bat :<service>:quarkusBuild`.
3. Den Diff unter `docs/openapi/` reviewen und zusammen mit dem Code committen.

**Wichtig:** Läuft `quarkusBuild` für mehrere Services in derselben Gradle-Invocation (z. B. der bare Befehl `quarkusBuild` ohne Modul-Präfix, der alle vier Services trifft), können OpenAPI-Metadaten zwischen den Services vertauscht werden — ein beobachteter, nicht-deterministischer Bug in der Zusammenspiel von Quarkus/SmallRye-OpenAPI mit mehreren Augmentierungen in derselben JVM. Immer nur **einen** Service pro `./gradlew`-Aufruf regenerieren:

```powershell
.\gradlew.bat :user-service:quarkusBuild
.\gradlew.bat :listing-service:quarkusBuild
.\gradlew.bat :image-service:quarkusBuild
.\gradlew.bat :messaging-service:quarkusBuild
```

Wiederverwendbare Antwort-Sets (z. B. die gemeinsame 400/401/403/404/500-Kombination besitzergeschützter Endpunkte) liegen als eigene Meta-Annotationen in `resources/OpenApiResponses.kt` je Service; globale Metadaten (Info, Server, SecurityScheme) in `resources/OpenApiConfig.kt`. Beides ist pro Service dupliziert, da die vier Services keine gemeinsame Bibliothek teilen.

## Absicherung

Der Root-Task `validateOpenApi` vergleicht die committeten Verträge per `git diff --exit-code` gegen den aktuellen Stand auf der Platte. Er hängt bewusst **nicht** von `quarkusBuild` ab (siehe oben) — die vier `quarkusBuild`-Läufe müssen vorher als eigenständige Invocations passiert sein. Weicht der Stand ab — etwa weil jemand eine Annotation geändert, aber nicht neu generiert/committet hat — schlägt der Task mit dem Diff fehl. Zusätzlich prüft er JSON-Grundstruktur (OpenAPI-Version 3.1.0, `paths`/`components` vorhanden) und dass alle `$ref`-Referenzen dokumentlokal und auflösbar sind.

```powershell
.\gradlew.bat :user-service:quarkusBuild
.\gradlew.bat :listing-service:quarkusBuild
.\gradlew.bat :image-service:quarkusBuild
.\gradlew.bat :messaging-service:quarkusBuild
.\gradlew.bat validateOpenApi
```

# Teststrategie und Qualitätssicherung

Die Tests konzentrieren sich auf fachliche Regeln, Servicegrenzen und die für ein verteiltes System besonders riskanten Fehlerpfade.

## Testschwerpunkte

- Authentifizierung, Besitz und Konversationsteilnahme
- öffentliche und geschützte Sichten auf Listings und Bilder
- Statusübergänge, Validierung, Pagination und Idempotenz
- Flyway-Migrationen und Panache-Repositories gegen PostgreSQL
- HTTP-, Event-, Outbox-, MinIO- und Inter-Service-Fehlerverträge
- ein vertikaler Ablauf durch den laufenden Compose-Stack

## Testebenen

| Ebene | Technik | Abgedeckter Bereich |
|---|---|---|
| Application/Use Case | frameworkfreies Kotlin, JUnit, In-Memory-Ports | Validierung, Ownership, Sichtbarkeit, Status, Metrik-/Evententscheidungen und Idempotenz |
| REST/Auth | `@QuarkusTest`, REST Assured | Pfade, DTOs, Header, Statuscodes, Fehlerformat und JWT-Grenzen |
| Repository/Migration | Quarkus Dev Services/Testcontainers, PostgreSQL | Flyway-History, Mappings, Constraints, Soft-Delete, Pagination und atomare Änderungen |
| Events/Outbox | SmallRye In-Memory, Serializer- und Repositorytests | Eventverträge, stabile Event-IDs, Relay-Retry und Consumer-Deduplizierung |
| Reactive Clients/Fault Tolerance | HTTP-Mockserver und Quarkus-Mocks | typisierte Payloads, `404`, `5xx`, ungültige Antworten, Timeouts, Retries und Circuit Breaker |
| Storage/Watermark | In-Memory-/Mock-Storage und echte Bilddekodierung | MIME/Inhalt, Skalierung, Wasserzeichen, Kompensation und Orphan-Erkennung |
| System | JUnit/REST Assured gegen Docker Compose | Gateway, vier Services, Datenbanken, MinIO, Redpanda, Auth, Upload/Eventflow und Messaging |
| Statische Regeln | `architectureCheck`, `validateOpenApi` | Abhängigkeitsrichtung und valide OpenAPI-Verträge |

Kafka wird in Service-Integrationstests durch den In-Memory-Connector ersetzt, damit Eventverträge deterministisch bleiben. PostgreSQL wird nicht ersetzt: Quarkus Dev Services startet Testcontainer und Flyway migriert das echte Schema.

## Testumfang und Coverage

Die Services enthalten 39 frameworkfreie Application-Tests und 33 Quarkus-/Adaptertests. Hinzu kommt ein portabler Systemtest, der Registrierung und Login, Listing-CRUD, Besitzgrenzen, Multipart-Bildupload, Eventprojektion, Bildarchivierung, Messaging, `Idempotency-Key` und den `listing-deleted`-Folgeflow prüft.

| Service | Application | Runtime/Adapter | Summe |
|---|---:|---:|---:|
| User | 7 | 6 | 13 |
| Listing | 11 | 5 | 16 |
| Image | 13 | 12 | 25 |
| Messaging | 8 | 10 | 18 |
| **Gesamt** | **39** | **33** | **72** |

Die letzte aggregierte JaCoCo-Referenzmessung ergab:

| Metrik | Wert |
|---|---:|
| Line Coverage | 59,7 % (1.820 / 3.051 Zeilen) |
| Branch Coverage | 53,6 % (496 / 926 Zweige) |
| Instruction Coverage | 58,7 % (12.105 / 20.639 Instruktionen) |

Es gibt bewusst kein willkürliches Prozent-Gate. Kritische Pfade werden durch konkrete Tests abgesichert; der Report macht ungetestete Bereiche sichtbar.

Generierte Reports liegen nach dem Lauf unter:

- `build/reports/jacoco/jacocoAggregatedReport/html/index.html`
- `build/reports/jacoco/jacocoAggregatedReport/jacocoAggregatedReport.xml`
- `<modul>/build/reports/tests/test/index.html`
- `<modul>/build/test-results/test/TEST-*.xml`

## Testbefehle

Vollständiger lokaler Prüflauf unter Windows:

```powershell
.\gradlew.bat test architectureCheck --no-daemon
.\gradlew.bat :user-service:quarkusBuild --no-daemon
.\gradlew.bat :listing-service:quarkusBuild --no-daemon
.\gradlew.bat :image-service:quarkusBuild --no-daemon
.\gradlew.bat :messaging-service:quarkusBuild --no-daemon
.\gradlew.bat validateOpenApi jacocoAggregatedReport --no-daemon
```

Unter Linux/macOS:

```bash
./gradlew test architectureCheck --no-daemon
./gradlew :user-service:quarkusBuild --no-daemon
./gradlew :listing-service:quarkusBuild --no-daemon
./gradlew :image-service:quarkusBuild --no-daemon
./gradlew :messaging-service:quarkusBuild --no-daemon
./gradlew validateOpenApi jacocoAggregatedReport --no-daemon
```

Die vier `quarkusBuild`-Zeilen laufen bewusst als eigenständige Gradle-Invocations: mehrere Services' `quarkusBuild`-Tasks in derselben JVM können OpenAPI-Metadaten zwischen den Services vertauschen (siehe [openapi/README.md](openapi/README.md)).

Einzelne Ebenen unter Windows:

```powershell
# Frameworkfreie Use Cases
.\gradlew.bat :user-service:application:test :listing-service:application:test `
  :image-service:application:test :messaging-service:application:test

# Vollständiger Listing Service
.\gradlew.bat :listing-service:application:test :listing-service:test

# Systemtest gegen den laufenden Stack
.\gradlew.bat :system-tests:test "-Plensmarket.systemTests=true" `
  "-Plensmarket.baseUrl=http://localhost:8081" --rerun-tasks
```

`@QuarkusTest` verwendet pro Testkontext einen zufälligen freien HTTP-Port. Der Systemtest ist davon getrennt und läuft gezielt gegen den gestarteten Gateway auf Port 8081.

## Lasttests

Ergänzend zu den Testebenen oben gibt es k6-basierte Lasttests gegen `listing-service` über das Gateway: ein Stabilitätstest bei moderater Last sowie ein Breakpoint-Test, der die Kapazitätsgrenze sucht, inklusive eines Vergleichs bei horizontaler Skalierung (`docker compose up -d --scale listing-service=3`). Details zu Skripten, Ausführung und Auswertung (`analyze.py`): [load-tests/README.md](../load-tests/README.md).

Bewusst kein Teil des Gradle-Builds oder von `system-tests`: die Lasttests laufen über die k6-CLI statt über die JVM und prüfen Durchsatz, Latenz und Fehlerrate statt fachliches Verhalten.

## Continuous Integration

Der GitHub-Actions-Workflow (`.github/workflows/ci.yml`) führt `test`, `architectureCheck`, die vier `quarkusBuild`-Läufe (je Service eine eigene Invocation), `validateOpenApi` und `jacocoAggregatedReport` aus. Fehlgeschlagene Tests, Architekturverletzungen oder gegenüber dem generierten Stand veraltete/ungültige OpenAPI-Verträge blockieren den Job. JUnit-Ergebnisse und der aggregierte JaCoCo-Report werden als Workflow-Artefakte hochgeladen.

Image-Build und Compose-Systemtest werden bewusst lokal ausgeführt.

## Bewusste Testgrenzen

Nicht automatisiert nachgewiesen werden Browser-Kompatibilität, Soak-Tests (Langzeitlast), Disaster Recovery, TLS/Netzwerksegmentierung und ein produktiver Identity-Provider. Last-Tests und der Effekt horizontaler Skalierung sind manuell über die [Lasttests](#lasttests) (k6, außerhalb des Gradle-Builds) nachvollziehbar, aber nicht Teil der automatisierten CI-Pipeline.

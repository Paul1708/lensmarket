# Entwickler-Guide

LensMarket ist ein Microservice-MVP aus vier eigenständig baubaren Kotlin-/Quarkus-Services. Jeder Service besitzt eine eigene PostgreSQL-Datenbank; Traefik bildet das gemeinsame API-Gateway.

## Services und Module

- `user-service`: Registrierung, Login und Profile
- `listing-service`: Anzeigen, Suche und Statusverwaltung
- `image-service`: Upload, Wasserzeichen und Bildmetadaten
- `messaging-service`: Konversationen und Nachrichten

Jeder Service besteht aus einem frameworkfreien inneren Modul und einem ausführbaren äußeren Modul:

```text
<service>/
  application/
    build.gradle.kts
    src/main/kotlin/lensmarket/<fachgebiet>/domain/
      model/
      port/
      usecase/
    src/test/kotlin/...                  # schnelle Use-Case-Tests
  build.gradle.kts                       # Quarkus-Runtime
  src/main/kotlin/lensmarket/<fachgebiet>/
    auth/                                # JWT-/Passwort-Adapter
    composition/                         # CDI Composition Root
    data/                                # DB-, HTTP-, Storage-, Watermark-Adapter
    events/                              # Kafka Input-/Output-Adapter
    metrics/                             # Micrometer-Adapter
    resources/                           # HTTP Input-Adapter und API-DTOs
    runtime/                             # Coroutine-/Runtime-Brücken
  src/main/resources/
    application.properties
    db/migration/
```

Das Root-Projekt aggregiert diese acht Service-Module sowie `system-tests`. Die Service-Verzeichnisse besitzen zusätzlich eigene Settings und Wrapper. Dadurch funktionieren sowohl der Gesamtbuild als auch ein isolierter Build eines Services.

## Erzwungene Abhängigkeitsrichtung

```text
HTTP Resource ─┐
Kafka Consumer ├──> Application Use Case ───> Output Port <── Adapter
               │          │                     ▲
               └──────────┴──> Domain Model ────┘

Quarkus-Runtime/Adapter ───> application
application                -X-> Quarkus-Runtime/Adapter
```

Das `application`-Modul hat keine Produktionsdependency außer Kotlin selbst. Es darf insbesondere Quarkus, Jakarta, Panache, Mutiny, Vert.x, Jackson, MinIO, Thumbnailator und MicroProfile nicht importieren. Asynchrone Verträge sind dort als Kotlin-`suspend` formuliert.

`domain/model` enthält fachliche Modelle und Commands. Sie sind weder JPA-Entities noch HTTP-DTOs.

`domain/usecase` bündelt den vollständigen fachlichen Ablauf: Validierung, Persistenzentscheidung, IDs und Zeit, fachliche Metriken, Evententscheidung und typisierte Fehler. REST- und Kafka-Input-Adapter rufen Use Cases auf; sie implementieren den Ablauf nicht selbst.

`domain/port` enthält ausschließlich nach außen gerichtete Verträge. Dazu gehören Repository-, Storage- und Service-Lookup-Ports sowie Hashing, Token-Ausgabe/-Prüfung, Wasserzeichen, Metriken, Clock und ID-Generatoren.

`composition/*ApplicationModule.kt` ist der CDI Composition Root. Nur dort werden Use Cases aus ihren Ports zusammengesetzt. Resources injizieren konkrete Use Cases und kennen weder Repositories noch Publisher, Metriken oder Auth-Adapter.

`data/repository` implementiert Repository-Ports mit Hibernate Reactive Panache und mappt Domain-Modelle auf JPA-Entities. Datenbankspezifische Constraint-Texte werden ausschließlich im Adapter ausgewertet und in typisierte Application-Fehler übersetzt.

`events` enthält service-lokale JSON-Verträge, Kafka-Consumer, Outbox Relays und Publisher. Consumer delegieren an einen Use Case und greifen nicht direkt auf Repository-Ports zu. Relays lesen ausschließlich committed Outbox-Einträge aus dem äußeren Datenbankadapter. Ein geteiltes Event-Artefakt zwischen Services ist bewusst nicht erforderlich.

## Request- und Event-Fluss

Ein Schreibrequest läuft typischerweise so:

```text
HTTP -> Resource -> Use Case -> Repository-/Clock-/ID-Port -> Adapter
                            -> Metrics-Port              -> Micrometer
     <- API-Mapping <- Domain-Ergebnis oder typisierter Fehler

Repository-Adapter -> Fachdaten + Outbox (atomar)
Committed Outbox   -> Outbox Relay -> Kafka
```

Ein Consumer verwendet denselben Anwendungseinstieg:

```text
Kafka -> Consumer -> Use Case -> Repository-/Storage-/Metrics-Port -> Adapter
```

Die Adapter überbrücken die Laufzeitdetails. Panache-`Uni`, Reactive Messaging, typisierte Quarkus REST Clients und Vert.x-Worker bleiben im Quarkus-Modul; die innere API bleibt bei `suspend`. Inter-Service-Aufrufe dürfen keine blockierenden JDK-`HttpClient.send`-Aufrufe oder rohe `Map`-Verträge verwenden.

Öffentliche und besitzergeschützte Lesewege werden bereits im Application-Modul durch getrennte Use Cases repräsentiert. Die konkreten Listing-, Bild-, Status-, Rollen- und Uploadregeln sind in [domain-rules.md](domain-rules.md) festgehalten.

## Persistenzregeln

Jeder Service besitzt seine eigene Datenbank. Es gibt keine Foreign Keys zwischen Services. Fremde Daten werden nur über IDs wie `ownerUserId`, `listingId` oder `photographerUserId` referenziert.

Flyway ist die Quelle des Schemas. Hibernate läuft mit `validate`. Bereits angewendete Migrationen werden nicht nachträglich geändert; Schemaänderungen erhalten eine neue fortlaufende Migration.

JPA-Annotationen, SQL-/Constraint-Details und Domain-/Entity-Mapping gehören in das äußere Modul. Entities werden nie direkt als API-Antwort zurückgegeben.

## Neue Funktionen einbauen

Für einen neuen Ablauf in einem vorhandenen Service:

1. Command, Modell und typisierte Fehler in `application/.../domain/model` ergänzen.
2. Den Use Case in `application/.../domain/usecase` anlegen oder erweitern.
3. Benötigte äußere Fähigkeiten als `suspend`-Port in `application/.../domain/port` definieren.
4. Den Port im Quarkus-Modul durch einen Adapter implementieren.
5. Den Use Case im `composition`-Modul per CDI Producer verdrahten.
6. Resource oder Consumer ausschließlich gegen den Use Case anbinden.
7. API-DTOs, Entity und neue Flyway-Migration nur dort ergänzen, wo ihre jeweilige Grenze betroffen ist.
8. Zuerst einen frameworkfreien Use-Case-Test, danach relevante REST-/Repository-/Eventtests ergänzen.
9. `architectureCheck` ausführen, bevor die Änderung abgeschlossen wird.

Ein neuer äußerer Adapter darf vom inneren Port abhängen. Für die umgekehrte Richtung wird keine Dependency ergänzt.

## Build und Tests

Gesamtprojekt unter Windows prüfen:

```powershell
.\gradlew.bat clean test architectureCheck --no-daemon
.\gradlew.bat :user-service:quarkusBuild --no-daemon
.\gradlew.bat :listing-service:quarkusBuild --no-daemon
.\gradlew.bat :image-service:quarkusBuild --no-daemon
.\gradlew.bat :messaging-service:quarkusBuild --no-daemon
.\gradlew.bat validateOpenApi jacocoAggregatedReport --no-daemon
```

Die vier `quarkusBuild`-Zeilen müssen als **eigenständige** Aufrufe laufen (nicht als ein bare `quarkusBuild` ohne Modul-Präfix) — mehrere Services' `quarkusBuild`-Tasks in derselben Gradle-Invocation können OpenAPI-Metadaten zwischen den Services vertauschen (siehe [openapi/README.md](openapi/README.md)).

Frameworkfreie Module gezielt prüfen:

```powershell
.\gradlew.bat :user-service:application:test :listing-service:application:test :image-service:application:test :messaging-service:application:test architectureCheck --no-daemon
```

Einen Service unabhängig vom Root bauen, beispielsweise den User Service:

```powershell
Set-Location user-service
.\gradlew.bat :application:test :test quarkusBuild --no-daemon
```

Die Dockerfiles führen weiterhin den jeweiligen Service-Wrapper aus. Das Quarkus-Modul zieht sein lokales `application`-Submodul dabei automatisch ein.

KDoc und aggregierte Coverage erzeugen:

```powershell
.\gradlew.bat :dokkaGenerate jacocoAggregatedReport --no-daemon
```

`architectureCheck` kompiliert alle inneren Module und verhindert:

- Framework- oder Adapterimporte im inneren Modul,
- Produktionsdependencies im inneren Buildskript,
- Zugriffe von Resources auf Ports/Adapter statt Use Cases,
- direkte Repository-Zugriffe aus Event-Consumern.

Der GitHub-Actions-Workflow (`.github/workflows/ci.yml`) führt in einem blockierenden Job alle normalen Gradle-Tests, `architectureCheck`, die vier `quarkusBuild`-Läufe (je Service ein eigener `./gradlew`-Aufruf), `validateOpenApi` und `jacocoAggregatedReport` aus. Die `quarkusBuild`-Läufe regenerieren die OpenAPI-Verträge aus den Annotationen; `validateOpenApi` vergleicht sie per Diff gegen den committeten Stand unter `docs/openapi/` (siehe [openapi/README.md](openapi/README.md)). JUnit-Testergebnisse und der aggregierte JaCoCo-Report werden als Workflow-Artefakte abgelegt. Die vollständige Stack-Prüfung ist als reproduzierbarer lokaler Ablauf dokumentiert.

## Lokaler Stack

Gesamtsystem inklusive Monitoring starten:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\dc.ps1 up -d --build --wait --wait-timeout 180
```

Health über das Gateway prüfen:

```powershell
curl.exe -s http://localhost:8081/users/health
curl.exe -s http://localhost:8081/listings/health
curl.exe -s http://localhost:8081/images/health
curl.exe -s http://localhost:8081/messages/health
```

Die Build- und Testbefehle stehen ergänzend in der [Teststrategie](testing.md).

## Konventionen

- Resources bleiben kurz, HTTP-orientiert und use-case-only.
- Fachliche Abläufe, Metrik- und Evententscheidungen liegen in Use Cases.
- Ports zeigen von innen nach außen; Adapter implementieren sie.
- Blockierende Arbeit wird im Adapter auf einen Worker gelegt.
- Öffentliche Use Cases dürfen Owner-Filter nie als Sichtbarkeitserweiterung interpretieren.
- Fehlerantworten enthalten `error`, `message`, `status` und `path`.
- Events tragen `eventId`, `eventType`, `version` und `occurredAt`.
- Eventerzeugende DB-Änderungen schreiben Fachdaten und stabile Event-ID in derselben lokalen Transaktion; nur das Outbox Relay publiziert zum Broker.
- Eventzustellung ist at-least-once. Kritische Consumer müssen deshalb `processed_events` oder eine gleichwertige atomare Idempotenzgrenze verwenden.
- Synchrone Service-Clients liefern Abhängigkeitsausfälle als `503` und Timeouts als `504`; fachlich unsichere Daten-Fallbacks sind nicht zulässig.
- Externe Gateway-Pfade bleiben `/users`, `/listings`, `/images` und `/messages`.

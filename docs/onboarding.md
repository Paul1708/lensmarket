# Einstieg in das Projekt

Dieses Dokument ist der erste Anlaufpunkt, bevor in Code oder Detaildokumentation eingestiegen wird. Es erklärt, was LensMarket ist, warum das System so gebaut ist wie es ist, und wo weitere Details zu finden sind.

## Was LensMarket ist

LensMarket ist ein Kleinanzeigen-Marktplatz-MVP für Hobbyfotograf:innen: Registrierung/Login, Anzeigen mit Suche, Bild-Upload mit serverseitigem Wasserzeichen und Konversationen zwischen Interessent:in und Anbieter:in. Fachlich ist das überschaubar. Der eigentliche Zweck des Projekts ist, diesen kleinen fachlichen Kern **vollständig** als cloud-natives Microservice-System zu bauen — inklusive eigener Datenhoheit pro Service, Event-Kommunikation, Observability und einem reproduzierbaren Betriebsablauf.

Das System setzt bewusst **nicht** um: Kauf, Bestellung, Bezahlung, E-Mail-Verifikation, Passwort-Reset. 

## Leitprinzipien

Das sind die generelle Entscheidungen, die an vielen Stellen im Code wiederkehren.

**Ein Service, eine Datenbank:** `user-service`, `listing-service`, `image-service` und `messaging-service` haben je eine eigene PostgreSQL-Datenbank. Es gibt keine Cross-Service-Foreign-Keys und keinen Service, der in der Datenbank eines anderen liest. Fremde IDs (`ownerUserId`, `listingId`) sind lose Referenzen. Das macht manche Abfragen umständlicher (z. B. Listing-Daten per REST statt per JOIN holen), hält die Services aber unabhängig baubar, testbar und deploybar.

**Framework-freier Kern, Adapter außen:** Jeder Service hat ein inneres `application`-Modul (Domain-Modelle, Use Cases, Ports) ohne jede Quarkus-/Jakarta-/Panache-Abhängigkeit, und ein äußeres Quarkus-Modul mit den Adaptern. Die Regel ist nicht nur Stil, sondern wird von `architectureCheck` automatisiert erzwungen — ein Import von Quarkus im inneren Modul lässt den Build fehlschlagen. Details: [developer-guide.md](developer-guide.md).

**Niemand vertraut dem Client.** `ownerUserId` ist in keinem Create-Request ein Feld, das der Client setzen kann; Besitz wird immer aus dem geprüften JWT abgeleitet. Das Profilfeld `role` ist reine Selbstauskunft und verleiht keine Rechte. Berechtigungen entstehen ausschließlich aus Identität, Besitz und Konversationsteilnahme. Details: [domain-rules.md](domain-rules.md).

**Keine verteilte Transaktion:** Ein Schreibrequest, der ein Event auslöst, schreibt Daten und Event atomar in derselben lokalen Postgres-Transaktion (Transactional Outbox). Ein separates Relay publiziert erst danach zum Broker, at-least-once. Also keine 2-Phase-Commits, aber auch keine Garantie, dass ein Event "sofort" ankommt — Konsistenz ist eventual, und Consumer müssen mit doppelt zugestellten Events umgehen können. Details: [reliability.md](reliability.md).

**Fail closed:** Wenn der Listing Service für eine Besitzprüfung nicht erreichbar ist, bekommt der Client `503`/`504` — nie eine erfundene oder aus dem Cache geratene Antwort. Begrenzte Retries und ein Circuit Breaker gelten nur für sichere `GET`-Aufrufe.

**Beobachtbarkeit:** Prometheus-Metriken, ein Grafana-Dashboard, Alert-Regeln und Jaeger-Tracing sind von Anfang an Teil des Stacks, nicht nachträglich angeflanscht. Das spiegelt, dass "es läuft" in einem verteilten System ohne Beobachtbarkeit wenig aussagt.

**Reproduzierbarkeit:** Ein Befehl (`dc.ps1 up`) startet den gesamten Stack inklusive Monitoring aus einer `.env`-Datei ohne brauchbare Defaults für Secrets. Alle Gradle-Builds laufen über den eingecheckten Wrapper, keine globale Toolchain wird vorausgesetzt.

## Technologie-Stack

| Baustein | Technologie | Warum diese Wahl |
|---|---|---|
| Services | Kotlin auf Quarkus 3.35 | JVM-Ökosystem mit kurzer Startzeit und kleinem Speicherbedarf — relevant, wenn vier Services plus Infrastruktur lokal gleichzeitig laufen; Kotlin hält Domänen-Code knapp und null-sicher |
| Persistenz | PostgreSQL 16 + Flyway | ausgereift, reaktiv ansprechbar (Hibernate Reactive Panache), Flyway macht Schema-Historie explizit statt `ddl-auto` |
| Async-Kommunikation | Redpanda (Kafka-API-kompatibel) | ein einzelner Container statt Zookeeper + Broker-Cluster, für lokale Entwicklung ausreichend, Verträge bleiben Kafka-kompatibel |
| Objektspeicher | MinIO | S3-kompatible API, lokal ohne Cloud-Account betreibbar |
| Gateway | Traefik 3.7 | liest Routing direkt aus Docker-Labels, kein separates Config-Repository für Routen nötig |
| Frontend | statisches HTML/CSS/Vanilla-JS auf Nginx | kein Build-Step, kein Framework-Overhead — bewusst einfach gehalten, weil der Fokus des Projekts auf dem Backend liegt |
| Observability | Prometheus, Grafana, Jaeger 2, cAdvisor | Standard-Stack für Metriken, Dashboards, Alerts und verteiltes Tracing |
| Lokale Orchestrierung | Docker Compose (Basis + optionale Overlays) | eine Umgebung für Entwicklung, Demo und Systemtest, ohne einen echten Cluster zu benötigen |
| Tests | JUnit 5, REST Assured, Quarkus Dev Services/Testcontainers, eigener `architectureCheck`-Gradle-Task, k6 (Lasttests, außerhalb des Gradle-Builds) | Postgres wird in Tests nicht gemockt, sondern per Testcontainer real migriert |
| CI | GitHub Actions | ein blockierender Job (`.github/workflows/ci.yml`) aus Test, `architectureCheck`, `quarkusBuild`, `validateOpenApi` (regeneriert OpenAPI aus Annotationen und diff't gegen den committeten Stand), JaCoCo |

## Architektur in drei Sätzen

Ein Browser spricht ausschließlich mit Traefik auf Port 8081; Traefik routet nach Pfadpräfix an Frontend oder an einen der vier Services. Jeder Service validiert JWTs lokal und besitzt seine eigene Datenbank; synchrone Service-zu-Service-Aufrufe gibt es nur als abgesicherte `GET`-Lesezugriffe auf den Listing Service (Besitz-/Existenzprüfung). Schreiboperationen, die andere Services interessieren, laufen über Domain Events auf Redpanda, nicht über direkte Aufrufe.

Für das vollständige Bild — Systemkontext-, Container-, Komponenten- und Sequenzdiagramme — siehe [architecture.md](architecture.md).


## Weitere Dokumente und Informationen

Die Dokumente bauen aufeinander auf. Für den ersten Überblick reicht diese Reihenfolge:

| Schritt | Dokument | Wofür |
|---|---|---|
| 1 | [README.md](../README.md) | System starten, URLs, Build-/Testbefehle |
| 2 | [architecture.md](architecture.md) | Diagramme, Datenhoheit, Modul-/Sequenzsicht |
| 3 | [developer-guide.md](developer-guide.md) | Modulstruktur, Abhängigkeitsregeln, wie ein neues Feature entsteht |
| 4 | [domain-rules.md](domain-rules.md) | Sichtbarkeit, Statusmaschine, Autorisierungsgrenzen im Detail |
| 5 | [reliability.md](reliability.md) | Outbox, Idempotenz, DLQ, Fehlerverträge |
| 6 | [testing.md](testing.md) | Testebenen, aktueller Umfang, Coverage |
| 7 | [operations.md](operations.md) | Konfiguration, Betrieb, Troubleshooting, Backup |
| 8 | [observability-security.md](observability-security.md) | Metriken, Tracing, Threat Model, Secret-Rotation |
| 9 | [openapi/](openapi/README.md) | maschinenlesbare HTTP-Verträge aller vier Services |
| 10 | [load-tests/](../load-tests/README.md) | k6-Lasttests, horizontale Skalierung, Auswertung |

## Erste Schritte

1. `README.md` bis zum Abschnitt "Getting Started" lesen, `.env` aus `.env.example` anlegen.
2. `dc.ps1 up -d --build --wait --wait-timeout 180` ausführen und `dc.ps1 ps` auf vier `healthy` Services prüfen.
3. `http://localhost:8081/` im Browser öffnen, registrieren, eine Anzeige mit Bild anlegen, eine Konversation starten.
4. `.\gradlew.bat clean test architectureCheck --no-daemon`, danach je Service einzeln `.\gradlew.bat :<service>:quarkusBuild --no-daemon` (siehe [openapi/README.md](openapi/README.md), warum das nicht gebündelt laufen darf), abschließend `.\gradlew.bat validateOpenApi jacocoAggregatedReport --no-daemon` — bevor du Code änderst, damit du einen bekannten grünen Zustand siehst.
5. [architecture.md](architecture.md) und [developer-guide.md](developer-guide.md) lesen, dann mit einer kleinen Änderung in einem Service starten.
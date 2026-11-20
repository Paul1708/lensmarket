# Betriebsdokumentation

Dieses Runbook beschreibt den lokalen Compose-Betrieb.

## Voraussetzungen und Erstkonfiguration

Erforderlich sind Docker Engine mit Compose v2, JDK 17 und PowerShell unter Windows. Eine globale Gradle-Installation ist nicht nötig. Einmalig wird die ignorierte lokale Konfiguration angelegt:

```powershell
Copy-Item .env.example .env
```

Alle `replace-*`-Werte müssen durch neue lokale Werte ersetzt werden. Insbesondere benötigt `LENSMARKET_JWT_SECRET` mindestens 32 zufällige Bytes. `TRAEFIK_DASHBOARD_USERS` ist ein bcrypt-`htpasswd`-Eintrag; ein `$` muss in `.env` nicht zusätzlich für Compose verdoppelt werden. `.env` darf nie committed, geloggt oder als CI-Artefakt veröffentlicht werden.

Die Beispiele verwenden den kurzen Aufruf `.\dc.ps1`. Falls die lokale Windows-Ausführungsrichtlinie Skripte blockiert, kann derselbe Befehl mit `powershell -NoProfile -ExecutionPolicy Bypass -File .\dc.ps1 <argumente>` ausgeführt werden.

## One-command-Start und Stopp

Nach vorbereiteter `.env` baut und startet ein Befehl Basisstack, Monitoring, Dashboard und Tracing und wartet auf alle definierten Healthchecks:

```powershell
.\dc.ps1 up -d --build --wait --wait-timeout 180
```

Status und Logs:

```powershell
.\dc.ps1 ps
.\dc.ps1 logs --tail 200
.\dc.ps1 logs -f image-service listing-service
```

Geordneter Stopp ohne Datenverlust:

```powershell
.\dc.ps1 down --remove-orphans
```

`down --volumes` löscht alle lokalen Projekt-, Broker-, Objekt- und Monitoringdaten und wird nur nach ausdrücklicher Entscheidung verwendet.

## Konfiguration und Secrets

| Variable | Standard / Pflicht | Verwendung |
|---|---|---|
| `COMPOSE_PROJECT_NAME` | `cc_lensmarket` | stabiler Compose-/Volume-Präfix und Dashboard-Filter |
| `POSTGRES_USER` | `lensmarket` | lokaler DB-User aller vier getrennten Datenbanken |
| `POSTGRES_PASSWORD` | Pflicht | Datenbanken und Listing-Exporter |
| `LENSMARKET_JWT_SECRET` | Pflicht, ≥ 32 zufällige Bytes | Ausgabe und Validierung von HS256-JWTs in allen Services |
| `LENSMARKET_JWT_ISSUER` | `lensmarket-auth` | erwarteter `iss`-Claim |
| `LENSMARKET_JWT_AUDIENCE` | `lensmarket-services` | erwarteter `aud`-Claim |
| `LENSMARKET_TOKEN_TTL_SECONDS` | `7200` | Token-Lebensdauer im User Service |
| `MINIO_ROOT_USER` | `lensmarket` | lokaler MinIO-/Image-Service-Zugang |
| `MINIO_ROOT_PASSWORD` | Pflicht | lokales MinIO-Secret |
| `TRAEFIK_DASHBOARD_USERS` | Pflicht | bcrypt-Basic-Auth für Port 8088 |
| `LENSMARKET_HTTP_CONNECT_TIMEOUT_MS` | `1000` | Image/Messaging → Listing Connect-Timeout |
| `LENSMARKET_HTTP_REQUEST_TIMEOUT_MS` | `2000` | Image/Messaging → Listing Read-Timeout |
| `LENSMARKET_OUTBOX_RELAY_INTERVAL` | `1s` | Relay-Intervall in Listing, Image und Messaging |
| `LENSMARKET_OUTBOX_BATCH_SIZE` | `50` | maximale Events pro Relay-Lauf |
| `LENSMARKET_ORPHAN_SCAN_INTERVAL` | `1m` | nicht-destruktiver MinIO-Orphan-Scan |

Compose verdrahtet Datenbank-URLs, Broker, MinIO, interne Listing-URL und OTLP-Endpunkt selbst. Die Tracing-Overlaydatei aktiviert OpenTelemetry und setzt Jaeger als Ziel. Secrets werden gemeinsam rotiert; eine JWT-Secret-Rotation macht bestehende Tokens sofort ungültig. Der detaillierte Rotationsablauf steht in [`observability-security.md`](observability-security.md).

## Health und Erreichbarkeit

| Zweck | URL / Check | Erwartung |
|---|---|---|
| Frontend/Gateway | `http://localhost:8081/` | HTML über Traefik |
| User | `http://localhost:8081/users/health` | `status: UP` |
| Listing | `http://localhost:8081/listings/health` | `status: UP` |
| Image | `http://localhost:8081/images/health` | `status: UP` |
| Messaging | `http://localhost:8081/messages/health` | `status: UP` |
| Container-Readiness | `.\dc.ps1 ps` | DBs, Broker, MinIO und vier Services `healthy` |

Die externen `/health`-Routen sind einfache Gateway-/Anwendungs-Liveness-Checks. Die Container prüfen intern Quarkus `/q/health/ready`; dort fließen Datenbank-/Runtime-Readiness-Checks ein. Ein fachlicher E2E-Test bleibt für Abhängigkeiten und Eventflow aussagekräftiger als ein einzelner Health-Endpunkt.

## Monitoring, Alerts und Tracing

| Oberfläche | URL | Zweck |
|---|---|---|
| Prometheus | `http://localhost:8081/prom/` | Targets, PromQL, Regeln und Alertstatus |
| Grafana | `http://localhost:8081/grafana/` | provisioniertes LensMarket-Dashboard |
| Jaeger 2 | `http://localhost:8081/tracing/` | serviceübergreifende Traces |
| Traefik-Dashboard | `http://localhost:8088/dashboard/` | Router/Services; Basic Auth erforderlich |

Schnellprüfung:

```powershell
$targets = (Invoke-RestMethod 'http://localhost:8081/prom/api/v1/targets').data.activeTargets
$targets | Select-Object @{n='service';e={$_.labels.service}}, health, scrapeUrl

$rules = (Invoke-RestMethod 'http://localhost:8081/prom/api/v1/rules').data.groups.rules
$rules | Select-Object name, state, health

Invoke-RestMethod 'http://localhost:8081/grafana/api/health'
Invoke-RestMethod 'http://localhost:8081/tracing/api/services'
```

Die Regeln decken nicht erreichbare und verschwundene Services, API-Fehler, Projektions-Misses, unbekannte Consumer-Ergebnisse, Outbox-Backlog/-Publishfehler, DLQ/Consumer-Fehler und verwaiste MinIO-Objekte ab. Prometheus besitzt im lokalen MVP keinen Alertmanager; Regeln und Zustände werden demonstriert, aber es werden keine externen Benachrichtigungen versendet.

Ein E2E-Durchlauf erzeugt Traces aller vier Services. Für den synchronen Nachweis wird in Jaeger ein Trace mit `messaging-service` und `listing-service` geprüft. Fehlende Traces beeinträchtigen den Fachablauf nicht; OpenTelemetry ist eine Observability-Abhängigkeit, keine funktionale Abhängigkeit.

## DLQ inspizieren und replayen

Nicht verarbeitbare Events landen in `image-watermarked-dlq` beziehungsweise `listing-deleted-dlq`. Zuerst wird ohne Veränderung inspiziert:

```powershell
.\dc.ps1 exec -T redpanda rpk topic consume image-watermarked-dlq --num 10
.\dc.ps1 exec -T redpanda rpk topic consume listing-deleted-dlq --num 10
```

Vor dem Replay müssen Ursache und Payload fachlich geprüft sein. Nur ein freigegebenes JSON-Ereignis wird auf das jeweilige Quelltopic geschrieben:

```powershell
Get-Content -Raw .\reviewed-event.json | .\dc.ps1 exec -T redpanda rpk topic produce image-watermarked
```

Die DLQ wird nicht blind geleert. Der ursprüngliche Eintrag bleibt Audit-Spur; stabile Event-ID und Consumer-Deduplizierung schützen Wiederholungen. Weitere Details stehen in [`reliability.md`](reliability.md).

## Backup- und Restore-Annahmen

Persistente führende Daten liegen in vier PostgreSQL-Volumes und im MinIO-Volume. Redpanda enthält transportierte Ereignisse, ist aber nicht die führende Fachquelle. Prometheus-/Grafana-Laufzeitdaten und Jaeger-Traces sind flüchtig; ihre Konfiguration, Regeln und Dashboards liegen versioniert im Repository.

Für ein konsistentes lokales Backup werden schreibende Clients angehalten. Je Datenbank wird ein logischer Custom-Format-Dump erzeugt, zum Beispiel:

```powershell
New-Item -ItemType Directory -Force .\backups | Out-Null
.\dc.ps1 exec -T user-db sh -c 'pg_dump -U "$POSTGRES_USER" -d users -Fc -f /tmp/users.dump'
.\dc.ps1 cp user-db:/tmp/users.dump .\backups\users.dump
```

Analog werden `listings` aus `listing-db`, `images` aus `image-db` und `messages` aus `messaging-db` gesichert. MinIO benötigt zusätzlich einen Bucket-/Volume-Snapshot der Wasserzeichenobjekte. Datenbank und MinIO-Snapshot müssen denselben Ruhezeitpunkt repräsentieren; andernfalls kann der Orphan-Scan Abweichungen nur melden, nicht automatisch korrekt reparieren.

Restore erfolgt in leere, versionskompatible Instanzen: Stack/Services stoppen, Datenbanken initialisieren, Dumps mit `pg_restore --clean --if-exists --no-owner` einspielen, MinIO-Snapshot wiederherstellen und erst dann Broker und Services starten. Danach sind Flyway-Status, vier Readiness-Checks, Orphan-Gauge, Outbox-Gauges und der E2E-Test zu prüfen. Dieser Ablauf ist eine dokumentierte Annahme und wurde nicht als automatischer Disaster-Recovery-Test verifiziert.

## Troubleshooting

| Symptom | Prüfung | Maßnahme |
|---|---|---|
| Compose lehnt Konfiguration ab | `.\dc.ps1 config --quiet` | fehlenden/ungültigen `.env`-Wert korrigieren; keine Platzhalter verwenden |
| Port 8081 oder 8088 belegt | `Get-NetTCPConnection -LocalPort 8081,8088` | konkurrierenden Prozess beenden oder den LensMarket-Stack geordnet stoppen |
| Service bleibt unhealthy | `.\dc.ps1 ps`; `.\dc.ps1 logs --tail 200 <service>` | zuerst abhängige DB, Broker oder MinIO prüfen; Flyway-/Credentialfehler im Log beheben |
| Geschützter Request liefert 401 | Token-Header, Uhrzeit, Issuer/Audience prüfen | neu anmelden; Secret-/Claim-Konfiguration aller Services muss identisch sein |
| Upload/Konversation liefert 503/504 | Listing-Health und Listing-Logs prüfen | Abhängigkeit beheben; begrenzte Retries/Circuit Breaker nicht durch Endlosschleifen umgehen |
| Listing zeigt Bild nach Upload noch nicht | Outbox-Gauges, Redpanda, `image-watermarked`-Consumer prüfen | Eventual Consistency abwarten; bei Fehler DLQ kontrolliert bearbeiten |
| Outbox-Alert | `lensmarket_outbox_pending`, `lensmarket_outbox_oldest_seconds`, Service-Logs | Brokerzustand und letzten Relay-Fehler beheben; Relay wiederholt automatisch |
| Keine Traces | Jaeger-Service, OTLP-Variablen, Service-Logs prüfen | Stack über `dc.ps1` inklusive Tracing-Overlay starten und Fachrequest erneut senden |
| Systemtests kollidieren mit Service-Tests | Port 8081 prüfen | normalen `test`-Lauf bei gestopptem Compose ausführen; Systemtest danach gegen laufenden Stack |

Ein Reset mit `down --volumes` ist keine Fehlerbehebung erster Wahl: Er löscht alle lokalen Daten. Vorher Backupbedarf und Projektpräfix prüfen.

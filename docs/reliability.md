# Fehlerbehandlung und verteilte Konsistenz

LensMarket verwendet klar begrenzte lokale Transaktionen mit beobachtbarer, wiederholbarer Weiterverarbeitung. Es gibt bewusst keinen globalen verteilten Commit.

## Synchrone Listing-Aufrufe

Image und Messaging fragen ausschließlich per `GET` beim Listing Service an. Für diese sicheren und idempotenten Aufrufe gilt dieselbe Policy:

| Aspekt | Festlegung |
|---|---|
| Connect-Timeout | 1.000 ms, konfigurierbar über `LENSMARKET_HTTP_CONNECT_TIMEOUT_MS` |
| Request-Timeout | 2.000 ms, konfigurierbar über `LENSMARKET_HTTP_REQUEST_TIMEOUT_MS` |
| Fault-Tolerance-Timeout | 2.500 ms als obere Grenze eines CDI-Aufrufs |
| Retry | höchstens 2 Wiederholungen, 100 ms Delay, 50 ms Jitter, insgesamt höchstens 5 s |
| Circuit Breaker | 4 Aufrufe im Fenster, öffnet ab 50 % Fehlern, erneuter Versuch nach 5 s |
| Fallback | fail closed; keine erfundene Ownership-, Listing- oder Teilnehmerinformation |

Nur technische Ausfälle, `5xx` und Timeouts werden wiederholt. Ein echtes `404` ist ein fachliches Ergebnis. Der externe Fehlervertrag lautet:

- nicht vorhandenes Listing: `404`
- Abhängigkeit nicht erreichbar, Circuit offen oder ungültige Antwort: `503 DEPENDENCY_UNAVAILABLE`
- Connect-/Request-/Fault-Tolerance-Timeout: `504 DEPENDENCY_TIMEOUT`

Interne Ausnahmen werden nicht an API-Clients durchgereicht.

## Transactional Outbox

Listing, Image und Messaging schreiben Fachdaten und ausgehende Events in derselben lokalen PostgreSQL-Transaktion. Ein Use Case publiziert nicht direkt zum Broker.

```text
Use Case -> Repository-Transaktion -> Fachdaten + outbox_events
                                      |
                                      v
                                Outbox Relay -> Redpanda
```

Jeder Outbox-Eintrag enthält eine stabile Event-ID, Aggregat-ID, Eventtyp, Topic, JSON-Payload, fachlichen Zeitpunkt, Versuchszähler, nächsten Versuch, Publikationszeit und den letzten Fehler. Die Event-ID wird vor dem Commit erzeugt und bleibt bei Retry oder Neustart unverändert.

Die Relays laufen standardmäßig jede Sekunde, lesen höchstens 50 fällige Events und verwenden exponentiellen Backoff von 1 bis 64 Sekunden. Ein erfolgreiches Senden wird erst nach dem Broker-Ack als publiziert markiert.

Die Zustellung ist damit **at least once**: Nach Broker-Ack und vor dem Datenbank-Update kann derselbe Eintrag erneut gesendet werden. Stabile Event-IDs und idempotente Consumer machen Wiederholungen beherrschbar.

Atomar gespeichert werden:

- Listing-Erstellung, Statuswechsel und Soft-Delete samt Event
- Bildmetadaten samt `image-uploaded` und `image-watermarked`
- Nachricht samt `message-sent`; derselbe `Idempotency-Key` erzeugt kein Duplikat

## Consumer-Idempotenz und Reihenfolge

- `image-watermarked` wird im Listing Service über `processed_events.event_id` dedupliziert. Bild-ID und Processed-Marker werden atomar geschrieben.
- `listing-deleted` wird im Image Service ebenfalls über die Event-ID dedupliziert. Erst nach dem Entfernen aller Objekte und Metadaten werden Processed-Marker und Listing-Tombstone atomar geschrieben.
- Ein verspätetes Bild-Event kann ein gelöschtes Listing nicht wieder sichtbar machen.
- Ein Upload für ein bereits gelöschtes Listing wird durch `deleted_listings` abgelehnt; das zuvor geschriebene MinIO-Objekt wird kompensierend entfernt.

Nicht verarbeitbare Events landen in `image-watermarked-dlq` beziehungsweise `listing-deleted-dlq`.

## DLQ inspizieren und replayen

Zuerst wird ohne Veränderung inspiziert:

```powershell
.\dc.ps1 exec -T redpanda rpk topic consume image-watermarked-dlq --num 10
.\dc.ps1 exec -T redpanda rpk topic consume listing-deleted-dlq --num 10
```

Vor einem Replay müssen Ursache und Payload fachlich geprüft sein. Nur ein freigegebenes Ereignis wird auf das Quelltopic geschrieben:

```powershell
Get-Content -Raw .\reviewed-event.json | .\dc.ps1 exec -T redpanda rpk topic produce image-watermarked
```

Die DLQ wird nicht blind geleert. Der ursprüngliche Eintrag bleibt als Audit-Spur erhalten; die Consumer-Deduplizierung schützt vor doppelter Verarbeitung.

## MinIO-/Datenbank-Konsistenz

Der Upload folgt dieser Reihenfolge:

1. Eingabe validieren und Wasserzeichen erzeugen.
2. Wasserzeichenobjekt nach MinIO schreiben.
3. Bildmetadaten und beide Outbox-Events atomar nach PostgreSQL schreiben.
4. Bei einem fehlgeschlagenen Datenbank-Commit das Objekt kompensierend löschen.

Der Listing-Löschconsumer entfernt je Bild zuerst das MinIO-Objekt und danach die Metadaten. Bei einem Storage-Fehler bleiben Zustand und Event wiederholbar. Zusätzlich vergleicht der Image Service regelmäßig die MinIO-Objekte mit den gespeicherten Keys. Er meldet verwaiste Objekte per Log und Gauge, löscht sie aber nicht automatisch.

## Beobachtbarkeit

Die relevanten Prometheus-Metriken sind:

- `lensmarket_outbox_pending{service}`
- `lensmarket_outbox_oldest_seconds{service}`
- `lensmarket_outbox_publish_failures_total{service,event_type}`
- `lensmarket_storage_orphaned_objects{service="image-service"}`
- `lensmarket_events_consumed_total{service,event_type,result}`

Alert-Regeln melden alte Outbox-Einträge, Publish-Fehler, Consumer-Fehler/DLQ-Einträge und verwaiste MinIO-Objekte.

## Automatisierte Fehlernachweise

| Fehlerfall | Erwarteter Zustand |
|---|---|
| Listing Service nicht erreichbar | begrenzte GET-Retries, danach `503` |
| Listing Service Timeout | `504` statt interner Ausnahme |
| Broker nicht erreichbar | Fachdaten und Outbox-Eintrag bleiben committed; späterer Retry verwendet denselben Eintrag |
| Datenbankfehler nach MinIO-Upload | Objekt wird kompensierend entfernt |
| MinIO-Fehler beim Listing-Löschen | Metadaten und Event bleiben für den Retry erhalten |
| doppeltes Event | genau eine fachliche Zustandsänderung |
| verspätetes Event nach Listing-Löschung | keine Wiederbelebung von Listing oder Bildmetadaten |
| Relay-/Service-Neustart | unpublizierte Datenbankeinträge bleiben fällig |

```powershell
.\gradlew.bat test architectureCheck --no-daemon
.\gradlew.bat :user-service:quarkusBuild --no-daemon
.\gradlew.bat :listing-service:quarkusBuild --no-daemon
.\gradlew.bat :image-service:quarkusBuild --no-daemon
.\gradlew.bat :messaging-service:quarkusBuild --no-daemon
.\gradlew.bat validateOpenApi --no-daemon
```

Der Systemtest ist in der [Teststrategie](testing.md) beschrieben.

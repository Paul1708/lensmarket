# Infrastruktur, Observability und Security-Basis

## Infrastruktur und Monitoring

Traefik besitzt zwei explizite EntryPoints: `web` auf Port 80 und `traefik` auf Port 8080. Dashboard/API und Prometheus-Metriken liegen bewusst auf `traefik`; der Host veröffentlicht ihn lokal als Port 8088. Der Dashboard-Router akzeptiert nur `/api` und `/dashboard` mit `Host(localhost)` und ist durch Basic Auth geschützt. Prometheus greift intern auf `/metrics` zu.

Prometheus übernimmt bei DNS-Service-Discovery den DNS-Namen als Label `service`. Damit unterscheiden die Regeln zwei Ausfälle:

- `LensMarketServiceDown`: ein entdecktes Ziel liefert `up == 0`;
- `LensMarketExpectedServiceMissing`: eines der vier Soll-Ziele ist vollständig aus DNS/Discovery verschwunden.

Die Event-Verträge sind explizit:

| Event | Reguläre Ergebnisse | Separat beobachtete Fehler |
| --- | --- | --- |
| `image-watermarked` | `attached`, `duplicate` | `listing_not_found`, `failed`, unbekannte Werte |
| `listing-deleted` | `images_deleted`, `no_images_found`, `duplicate` | `failed`, unbekannte Werte |

Das Grafana-Dashboard ermittelt `container_label_com_docker_compose_project` aus cAdvisor und verwendet die Variable `$compose_project`. Dadurch bleiben CPU- und RAM-Panels bei einem abweichenden `COMPOSE_PROJECT_NAME` funktionsfähig.

## Distributed Tracing

Alle Services enthalten die Quarkus-OpenTelemetry-Erweiterung. REST-Server, Reactive REST Clients und Quarkus Messaging Kafka propagieren Trace-Kontext; JDBC/Flyway ist ebenfalls instrumentiert. Im Basissetup kann der SDK-Export abgeschaltet bleiben. `monitoring/docker-compose-tracing.yml` aktiviert ihn und sendet OTLP/gRPC an das gepinnte Jaeger-2-Backend.

Jaeger ist über `http://localhost:8081/tracing/` geroutet. Der Router entfernt den externen Präfix vor der Weiterleitung. Über die Jaeger Query API lässt sich ein Trace prüfen, der sowohl `messaging-service` als auch `listing-service` enthält; der Messaging-Flow löst den typisierten Listing-REST-Client aus. Kafka-Spans werden von der Quarkus-Kafka-Integration automatisch erzeugt und propagiert.

## Threat Model und Trust Boundaries

| Grenze / Asset | Relevante Bedrohung | Kontrolle im MVP | Restrisiko / produktive Folge |
| --- | --- | --- | --- |
| Browser → Traefik | Spoofing, Brute Force, fremde Origins | zentrale Routen, CORS-Headerliste, Rate Limit für Login/Registrierung | TLS und enger Origin-Satz vor öffentlichem Betrieb |
| Traefik → REST-Services | Umgehung des Gateways, manipulierte Identität | Services vertrauen keiner User-ID aus Request-Bodies; jeder geschützte Pfad validiert JWT und Besitz/Teilnahme | interne Ports dürfen produktiv nicht öffentlich exponiert werden |
| User Service → übrige Services | Token-Fälschung, Algorithmuswechsel, Replay | JOSE-Validierung, HS256-Allowlist, Issuer/Audience, Zeit- und Pflichtclaims, kurze TTL | gemeinsames HMAC-Material; später OIDC/asymmetrische Schlüssel und Revocation |
| Service → Service | SSRF, falsche oder langsame Antwort | feste konfigurierte URLs, typisierte Reactive Clients, Timeouts, Retry nur für GET, Circuit Breaker | mTLS/Service-Identität für eine nicht-lokale Umgebung |
| Services ↔ Redpanda | gefälschte, doppelte oder verspätete Events | internes Netz, stabile Event-ID, Transactional Outbox, atomare Deduplizierung, DLQ | lokaler Broker ohne TLS/SASL; produktiv ACLs, TLS und Schema-Governance |
| Service → eigene PostgreSQL-DB | Credential-Leak, Cross-Service-Zugriff | getrennte Datenbanken/Volumes, keine Cross-Service-FKs, Credentials nur aus Umgebung | eigene DB-User je Service und Plattform-Secrets produktiv einführen |
| Image Service ↔ MinIO | Objektzugriff, inkonsistente DB/Objekte | nicht öffentliches Service-Netz, Watermark-only, Kompensation und Orphan-Erkennung | TLS, Bucket Policy und kurzlebige Credentials produktiv |
| Prometheus/Grafana/Jaeger | Offenlegung von Topologie, IDs und Betriebsdaten | lokale Gateway-Veröffentlichung; Traefik-Admin separat mit Basic Auth | Monitoring und Jaeger produktiv hinter SSO/VPN und TLS legen |
| Docker Socket → Traefik/cAdvisor | Hostübernahme bei Containerkompromittierung | read-only Mount wo unterstützt, keine REST-Exposition | Socket-Proxy oder orchestrator-native Discovery für Produktion |

Interne Netzteilnahme ist keine Benutzer-Authentifizierung. Service-zu-Service-Aufrufe dürfen nur die dafür vorgesehenen öffentlichen Projektionen lesen; eingehende Events und HTTP-Antworten werden weiterhin als untrusted input validiert.

## Secrets und Rotation

`docker-compose.yml` enthält keine nutzbaren Passwörter, JWT-Secrets oder Basic-Auth-Verifier mehr. Ein Start verlangt eine ignorierte `.env`; `.env.example` enthält ausschließlich Platzhalter. Produktive Quarkus-Profile haben keinen Secret-Fallback. Nur `%dev` und `%test` besitzen klar bezeichnete, nicht produktive Fixtures.

Rotation:

1. Alle Werte aus den früher im Repository sichtbaren Defaults als kompromittiert behandeln und nicht wiederverwenden.
2. Neue zufällige Werte für PostgreSQL, MinIO und JWT erzeugen; für JWT mindestens 32 zufällige Bytes verwenden.
3. Einen neuen bcrypt-`htpasswd`-Eintrag für Traefik erzeugen und in `TRAEFIK_DASHBOARD_USERS` setzen.
4. Stack mit neuen Volumes initialisieren oder die Passwörter in bestehenden Datenbanken/MinIO zuerst atomar ändern.
5. Alle Services gemeinsam mit dem neuen JWT-Secret neu starten. Vorher ausgegebene Tokens werden dadurch sofort ungültig.
6. Compose-Konfiguration, Service-Health, Dashboard-Zugriff und Monitoring-Endpunkte nach der Rotation erneut prüfen.

Secrets gehören lokal in `.env`, in CI in geschützte/maskierte Variablen und in einer produktiven Plattform in ein Secret-Management-System. Sie dürfen weder als CI-Artefakt noch in Logs, Traces oder Screenshots erscheinen.

## Überprüfung

```powershell
Copy-Item .env.example .env
# Alle replace-* Werte in .env ersetzen.
./dc.ps1 up -d --build
./gradlew.bat :system-tests:test "-Plensmarket.systemTests=true" --rerun-tasks
```

Anschließend werden Dashboard-Authentifizierung, Prometheus-Ziele und Regeln, Grafana-Provisionierung, cAdvisor-CPU/RAM-Daten, Auth-Fehlermetriken und ein serviceübergreifender Jaeger-Trace geprüft.

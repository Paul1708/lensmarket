# LensMarket-Architektur

Dieses Dokument ist die zentrale, codegleiche Architektursicht für Entwicklung und Betrieb. Die Diagramme zeigen die lokale Compose-Umgebung.
## Systemkontext

```mermaid
flowchart LR
    visitor["Besucher:in<br/>sucht Anzeigen und Profile"]
    member["Angemeldete:r Nutzer:in<br/>verwaltet Anzeigen, Bilder und Nachrichten"]
    operator["Betreiber:in<br/>prüft Health, Metriken, Alerts und Traces"]
    lensmarket["LensMarket<br/>Kleinanzeigen-MVP für Fotografie"]

    visitor -->|"HTTPS/REST im Produktivziel<br/>HTTP im lokalen Stack"| lensmarket
    member -->|"REST + Bearer-JWT"| lensmarket
    operator -->|"Dashboard/Query APIs"| lensmarket
```

LensMarket ermöglicht Registrierung und Login, öffentliche Profile, Anzeigen und Suche, Wasserzeichen-Bilduploads sowie Konversationen zwischen Interessent:innen und Anzeigenbesitzer:innen. Kauf, Bestellung, Bezahlung, E-Mail-Verifikation und Passwort-Reset gehören nicht zum MVP.

## Container, Services und Datenhoheit

```mermaid
flowchart TB
    browser["Browser / API-Client"]
    operator["Betreiber:in"]
    gateway["Traefik 3.7<br/>Gateway, CORS, Rate Limit"]
    frontend["Nginx<br/>statisches Frontend"]

    subgraph services["Kotlin / Quarkus 3.35"]
        users["User Service<br/>Konten, Profile, JWT-Ausgabe"]
        listings["Listing Service<br/>Anzeigen, Suche, Status"]
        images["Image Service<br/>Upload, Watermark, Auslieferung"]
        messages["Messaging Service<br/>Konversationen, Nachrichten"]
    end

    userdb[("users PostgreSQL<br/>users")]
    listingdb[("listings PostgreSQL<br/>listings, outbox_events,<br/>processed_events")]
    imagedb[("images PostgreSQL<br/>images, outbox_events,<br/>processed_events, deleted_listings")]
    messagedb[("messages PostgreSQL<br/>conversations, messages,<br/>outbox_events")]
    minio[("MinIO<br/>nur Wasserzeichenobjekte")]
    broker["Redpanda<br/>Kafka-kompatibler Event-Broker"]
    prom["Prometheus<br/>Metriken + Regeln"]
    grafana["Grafana<br/>Dashboard"]
    jaeger["Jaeger 2<br/>OTLP-Traces"]
    cadvisor["cAdvisor<br/>Container-Metriken"]

    browser -->|"localhost:8081"| gateway
    gateway --> frontend
    gateway -->|"/users"| users
    gateway -->|"/listings"| listings
    gateway -->|"/images"| images
    gateway -->|"/messages"| messages

    users --> userdb
    listings --> listingdb
    images --> imagedb
    images --> minio
    messages --> messagedb
    images -->|"GET Listing-Projektion"| listings
    messages -->|"GET Listing-Projektion"| listings

    listings <-->|"Domain Events"| broker
    images <-->|"Domain Events"| broker
    messages -->|"Domain Events"| broker

    users -. "Metriken" .-> prom
    listings -. "Metriken" .-> prom
    images -. "Metriken" .-> prom
    messages -. "Metriken" .-> prom
    cadvisor --> prom
    prom --> grafana
    users -.-> jaeger
    listings -.-> jaeger
    images -.-> jaeger
    messages -.-> jaeger
    operator -->|"/grafana, /prom, /tracing"| gateway
    operator -->|"Basic Auth :8088"| gateway
```

Jeder fachliche Service besitzt ausschließlich seine eigene Datenbank und seine eigenen Flyway-Migrationen. IDs anderer Services sind lose Referenzen; es gibt weder Cross-Service-Foreign-Keys noch direkte Zugriffe auf fremde Datenbanken. Der Image Service besitzt zusätzlich den MinIO-Bucket. Redpanda transportiert Integrationsereignisse, ist aber nicht die führende Datenquelle.

| Service | Führende Daten | Synchron konsumiert | Publiziert | Konsumiert |
|---|---|---|---|---|
| User | Konten, Passwort-Hashes, Profile | – | – | – |
| Listing | Anzeigen, Status, Bild-ID-Projektion | – | `listing-created`, `listing-status-changed`, `listing-deleted` | `image-watermarked` |
| Image | Bildmetadaten, Wasserzeichenobjekte, Listing-Tombstones | öffentliche Listing-Projektion | `image-uploaded`, `image-watermarked` | `listing-deleted` |
| Messaging | Konversationen, Nachrichten, Idempotency-Keys | öffentliche Listing-Projektion | `message-sent` | – |

## Modul- und Dependency-Graph

Alle vier Services verwenden dieselbe Schichtenrichtung. Kafka-Consumer und Outbox Relay kommen nur in den Services mit Domain Events hinzu:

```mermaid
flowchart LR
    http["HTTP Resource<br/>API-DTOs"]
    eventin["Kafka Consumer"]
    composition["CDI Composition Root"]
    usecase["Application Use Cases<br/>frameworkfreies Kotlin"]
    model["Domain Model / Commands"]
    ports["Output Ports<br/>suspend-Verträge"]
    persistence["Panache / PostgreSQL Adapter"]
    clients["Reactive REST Client"]
    relay["Outbox Relay / Kafka Publisher"]
    platform["Auth, Metrics, Storage,<br/>Watermark, Clock, IDs"]
    database[("PostgreSQL<br/>Fachdaten + Outbox")]
    broker["Redpanda"]

    http --> usecase
    broker --> eventin
    eventin --> usecase
    composition --> usecase
    composition --> persistence
    composition --> clients
    composition --> platform
    usecase --> model
    usecase --> ports
    persistence -. "implementiert" .-> ports
    clients -. "implementiert" .-> ports
    platform -. "implementiert" .-> ports
    persistence -->|"Fachdaten + Event atomar"| database
    relay -->|"liest committed Einträge"| database
    relay --> broker
```

Das äußere Quarkus-Modul hängt vom inneren `application`-Modul ab. Die Gegenrichtung ist verboten. Schreibende Use Cases rufen Repository-Ports auf, deren Adapter Fachdaten und Outbox-Einträge atomar speichern. Das äußere Outbox Relay publiziert ausschließlich bereits committed Einträge und ist kein Application-Port. `architectureCheck` kompiliert die inneren Module und verhindert Framework-Imports, äußere Produktionsdependencies, Resource-Zugriffe an Use Cases vorbei sowie direkte Repository-Zugriffe aus Consumern.

## Sequenz: Bild-Upload und Eventflow

```mermaid
sequenceDiagram
    autonumber
    actor Client
    participant Gateway as Traefik
    participant Image as Image Service
    participant Listing as Listing Service
    participant MinIO
    participant ImageDB as Image PostgreSQL
    participant Relay as Image Outbox Relay
    participant Broker as Redpanda
    participant Consumer as Listing Consumer
    participant ListingDB as Listing PostgreSQL

    Client->>Gateway: POST /images/upload<br/>Bearer JWT + multipart(listingId, file)
    Gateway->>Image: Request weiterleiten
    Image->>Image: JWT, Größe, MIME und dekodierbaren Inhalt prüfen
    Image->>Listing: GET /listings/{listingId}
    Listing-->>Image: öffentliche ACTIVE-Projektion mit ownerUserId
    Image->>Image: Besitz prüfen, skalieren, Wasserzeichen erzeugen
    Image->>MinIO: Wasserzeichenobjekt speichern
    Image->>ImageDB: Bild + image-uploaded + image-watermarked<br/>in lokaler Transaktion speichern
    alt DB-Transaktion schlägt fehl
        Image->>MinIO: Objekt kompensierend löschen
        Image-->>Client: einheitlicher Fehler
    else Commit erfolgreich
        Image-->>Client: 201 + ImageResponse + Location
        Relay->>ImageDB: fällige Outbox-Einträge lesen
        Relay->>Broker: Events at-least-once publizieren
        Broker->>Consumer: image-watermarked(eventId, listingId, imageId)
        Consumer->>ListingDB: imageId + processed event atomar schreiben
    end
```

Der HTTP-Erfolg bestätigt die dauerhaft gespeicherten Bilddaten und Outbox-Einträge, nicht die bereits aktualisierte Listing-Projektion. Brokerzustellung ist bewusst eventual consistent und at-least-once; stabile Event-IDs und `processed_events` machen Wiederholungen idempotent.

## Sequenz: Messaging

```mermaid
sequenceDiagram
    autonumber
    actor Customer as Interessent:in
    participant Gateway as Traefik
    participant Messaging as Messaging Service
    participant Listing as Listing Service
    participant MessageDB as Messaging PostgreSQL
    participant Relay as Messaging Outbox Relay
    participant Broker as Redpanda

    Customer->>Gateway: POST /messages/conversations<br/>Bearer JWT + listingId
    Gateway->>Messaging: Request weiterleiten
    Messaging->>Listing: GET /listings/{listingId}<br/>Retry/Timeout/Circuit Breaker
    Listing-->>Messaging: ACTIVE-Listing mit ownerUserId
    Messaging->>Messaging: kein Selbstkontakt, Teilnehmer ableiten
    Messaging->>MessageDB: Konversation anlegen oder vorhandene lesen
    Messaging-->>Customer: 201 + Location oder idempotentes 200

    Customer->>Gateway: POST /messages/conversations/{id}/messages<br/>Idempotency-Key + Body
    Gateway->>Messaging: Header und Request weiterleiten
    Messaging->>MessageDB: Teilnahme und vorhandenen Key prüfen
    alt neuer Key oder kein Key
        Messaging->>MessageDB: Nachricht + message-sent-Outbox atomar speichern
        Messaging-->>Customer: 201 + Location
        Relay->>MessageDB: fälligen Outbox-Eintrag lesen
        Relay->>Broker: message-sent at-least-once publizieren
    else derselbe Key für Sender und Konversation
        Messaging-->>Customer: 200 + ursprüngliche Nachricht
    end
```

## Qualitäts- und Betriebsgrenzen

- Authentifizierung ist zustandslos: Der User Service stellt HS256-JWTs aus; alle geschützten Services validieren Signatur, Algorithmus, Issuer, Audience, Zeit- und Pflichtclaims lokal.
- Autorisierung basiert auf Identität, Besitz und Konversationsteilnahme. Das selbst pflegbare Profilfeld `role` verleiht keine Berechtigungen.
- Synchrone Abhängigkeiten existieren nur als sichere Listing-`GET`-Aufrufe. Technische Ausfälle werden nach begrenzten Retries als `503`, Timeouts als `504` sichtbar.
- Schreiboperationen mit Events verwenden lokale Transaktionen und Transactional Outbox; es gibt keine verteilte ACID-Transaktion.
- Prometheus/Grafana liefern Metriken und Alerts, Jaeger 2 Distributed Tracing. Logs enthalten Trace- und Span-IDs, sind derzeit aber bewusst kein JSON.
- Compose bildet eine reproduzierbare lokale Referenzumgebung ab. TLS, Broker-ACLs, asymmetrische/OIDC-Identität, Hochverfügbarkeit und orchestrator-native Secrets wären für einen produktiven Betrieb zusätzlich erforderlich.

Die konkreten API-Verträge stehen unter [`docs/openapi/`](openapi/), die Ausfallsemantik in [`reliability.md`](reliability.md) und der Betriebsablauf in [`operations.md`](operations.md).

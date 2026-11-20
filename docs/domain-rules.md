# Fachliche Regeln und API-Grenzen

## Listing-Sichtbarkeit

Öffentliche Zugriffe liefern ausschließlich nicht gelöschte Listings im Status `ACTIVE`:

- `GET /listings`
- `GET /listings/{id}`

Der Query-Parameter `ownerUserId` schränkt die öffentliche Suche auf einen Anbieter ein, erweitert die Sicht aber niemals auf `PAUSED` oder `SOLD`. Eigene Listings in allen nicht gelöschten Status sind nur mit gültigem Bearer Token erreichbar:

- `GET /listings/mine`
- `GET /listings/mine/{id}`

Getrennte Application-Use-Cases erzwingen diese Grenze auch für interne HTTP-Clients.

## Listing-Zustandsmaschine

`SOLD` ist terminal. Requests auf den bereits gespeicherten Status sind idempotent und publizieren kein zusätzliches Status-Event.

| Von | Nach `ACTIVE` | Nach `PAUSED` | Nach `SOLD` |
|---|---:|---:|---:|
| `ACTIVE` | idempotent | erlaubt | erlaubt |
| `PAUSED` | erlaubt | idempotent | erlaubt |
| `SOLD` | abgelehnt | abgelehnt | idempotent |

Unzulässige echte Übergänge liefern `400 VALIDATION_ERROR`.

## Bild-Sichtbarkeit

Öffentliche Bildzugriffe liefern ausschließlich `WATERMARKED`:

- `GET /images`
- `GET /images/{id}`
- `GET /images/{id}/watermarked`

`ARCHIVED` wird an diesen Grenzen wie ein nicht vorhandenes Bild mit `404` behandelt. Die geschützte Verwaltungssicht leitet die Besitzer-ID ausschließlich aus dem Token ab:

- `GET /images/mine`
- `GET /images/mine/{id}`
- `GET /images/mine/{id}/watermarked`
- `PATCH /images/{id}/status`

## Konversations- und Nachrichtenregeln

Nur die beiden Teilnehmer:innen (Anzeigenbesitzer:in und Kund:in) dürfen eine Konversation lesen, Nachrichten senden/lesen oder sie entfernen; Fremde erhalten `403`:

- `GET /messages/conversations` liefert ausschließlich die eigenen, aus dem Token abgeleiteten Konversationen.
- `GET|DELETE /messages/conversations/{id}`, `GET|POST /messages/conversations/{id}/messages`

Pro Interessent:in und Anzeige existiert höchstens eine Konversation: `POST /messages/conversations` ist idempotent und liefert bei bereits bestehendem Thread `200` statt `201`. Nur zu `ACTIVE`-Anzeigen möglich (`400` sonst); die Anzeigenbesitzer:in kann keine Konversation mit sich selbst eröffnen (`403`).

`DELETE /messages/conversations/{id}` blendet den Verlauf nur für die aufrufende Person aus (`deletedByPhotographerAt`/`deletedByCustomerAt`), nicht rückgängig machbar über die API:

| Zustand | Wechsel nach `DELETE` |
|---|---|
| Beide Seiten sehen die Konversation noch | bleibt `OPEN`, nur für die aufrufende Person ausgeblendet |
| Erste Seite hat bereits entfernt, zweite entfernt jetzt | wechselt zu `CLOSED` |

`CLOSED`-Konversationen lehnen neue Nachrichten mit `403` ab, auch für weiterhin authentifizierte Teilnehmer:innen.

Der `Idempotency-Key`-Header bei `POST .../messages` ist optional (max. 64 Zeichen) und gilt je Konversation und Sender:in. Ein Retry mit demselben Key liefert die ursprüngliche Nachricht (`200` statt `201`) und erzeugt weder neue Nachricht noch neues Event.

## Vertrauensgrenzen der Schreibzugriffe

`ownerUserId` ist kein Feld der Listing- oder Image-Create-Verträge. Der Server verwendet ausschließlich die Identität aus dem geprüften Token. `POST /listings` akzeptiert außerdem kein `imageIds`-Feld; unbekannte JSON-Felder werden abgelehnt. Nur der idempotente `image-watermarked`-Consumer darf die Bildprojektion eines Listings ändern.

`role` ist ein selbst pflegbares Profilmerkmal ohne Autorisierungswirkung. Berechtigungen ergeben sich ausschließlich aus:

- authentifizierter Identität
- Besitz eines Listings oder Bildes
- Teilnahme an einer Konversation

Damit erzeugt eine Rollenänderung keine zusätzlichen Rechte.

## Upload- und Client-Grenzen

Der Multipart-Upload prüft Dateigröße, MIME-Typ und dekodierbaren Bildinhalt. Größenprüfung, Dateilesen, Wasserzeichenverarbeitung und MinIO-Zugriffe laufen außerhalb des Event-Loops; die reaktive Persistenz bleibt nicht blockierend. Nicht dekodierbare JPEG-/PNG-Daten liefern `400 VALIDATION_ERROR`.

Image und Messaging Service verwenden typisierte Quarkus REST Clients mit reaktiven `Uni`-Antworten. Connect- und Request-Timeout sind über `LENSMARKET_HTTP_CONNECT_TIMEOUT_MS` und `LENSMARKET_HTTP_REQUEST_TIMEOUT_MS` konfigurierbar. Traefik erlaubt `Idempotency-Key` in CORS-Preflights, damit Browser den Messaging-Retry-Vertrag verwenden können.

## Nachweis

```powershell
.\gradlew.bat test architectureCheck --no-daemon
.\gradlew.bat :system-tests:test "-Plensmarket.systemTests=true" `
  "-Plensmarket.baseUrl=http://localhost:8081" --no-daemon
```

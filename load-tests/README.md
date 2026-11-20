# Lasttests

Dieser Ordner enthält k6-Skripte, die den laufenden LensMarket-Stack über das Traefik-Gateway mit realistischem Read-Traffic gegen `listing-service` belasten. Ziel ist der Nachweis von Stabilität unter Last sowie der Effekt horizontaler Skalierung (`docker compose up -d --scale listing-service=N`).

Diese Tests sind bewusst kein Teil des Gradle-Builds und nicht Teil von [`system-tests`](../system-tests): Sie prüfen kein fachliches Verhalten, sondern Durchsatz, Latenz und Fehlerrate unter Last, und laufen über die k6-CLI statt über die JVM.

## Skripte

| Skript | Zweck | Ausführender |
|---|---|---|
| `loadtest.js` | Stabilitätstest bei realistischer, moderater Last (Ramp auf 200 gleichzeitige Nutzer, 30 s halten) | k6 |
| `breakpointtest.js` | Sucht die Kapazitätsgrenze durch stetigen Ramp auf bis zu 2.000 Nutzer; bricht automatisch ab, sobald die Fehlerrate über 10 % steigt | k6 |

Beide Skripte erzeugen eine realistische Mischung aus Anfragen gegen `listing-service` (über das Gateway):

- 70 % `GET /listings` mit variierenden Filtern/Pagination (Tag, Location, Preisrange, Offset)
- 25 % `GET /listings/{id}` mit IDs, die vorab per `setup()` aus einer echten Suche geladen werden
- 5 % `GET /listings/health`

## Voraussetzungen

- Stack läuft bereits lokal, siehe [Betriebsdokumentation](../docs/operations.md):

  ```powershell
  .\dc.ps1 up -d --build --wait --wait-timeout 180
  ```

- k6 ist lokal installiert: [k6.io/docs/get-started/installation](https://k6.io/docs/get-started/installation/)
- Es existieren bereits einige Listings im System (z. B. durch manuelles Anlegen oder einen vorherigen Testlauf), sonst laufen die 25 % Detail-Requests leer. `setup()` meldet die Anzahl geladener IDs zu Beginn des Laufs.

## Ausführen

```bash
k6 run load-tests/loadtest.js
k6 run load-tests/breakpointtest.js
```

`BASE_URL` (Default `http://localhost:8081`, das Traefik-Gateway) und `RUN_LABEL` (Default `baseline`, taucht als Tag in den Ergebnissen auf) lassen sich überschreiben:

```bash
k6 run -e BASE_URL=http://localhost:8081 -e RUN_LABEL=1x load-tests/loadtest.js
```

## Baseline vs. horizontale Skalierung vergleichen

```bash
# 1) Baseline: eine listing-service-Instanz
k6 run -e RUN_LABEL=1x --out json=results-1x.json load-tests/loadtest.js
k6 run -e RUN_LABEL=1x --out json=breakpoint-1x.json load-tests/breakpointtest.js

# 2) Skaliert: drei listing-service-Instanzen, Traefik verteilt automatisch
docker compose up -d --scale listing-service=3
docker compose up -d --wait

# 3) Gleiche Läufe erneut
k6 run -e RUN_LABEL=3x --out json=results-3x.json load-tests/loadtest.js
k6 run -e RUN_LABEL=3x --out json=breakpoint-3x.json load-tests/breakpointtest.js
```

Relevant für den Vergleich:

- **`loadtest.js`**: `http_req_duration` (insbesondere p95) und `http_req_failed`-Rate bei gleicher Last mit 1 vs. 3 Instanzen.
- **`breakpointtest.js`**: bei welcher VU-Zahl die Fehlerrate über 10 % steigt (k6 bricht dort automatisch ab) — verschiebt sich dieser Punkt durch Skalierung nach oben, ist das der Nachweis für horizontale Skalierbarkeit.

Parallel dazu lohnt sich während des Laufs ein Blick ins bestehende [Grafana-Dashboard](../docs/observability-security.md) (`http://localhost:8081/grafana/`) auf CPU/RAM pro Compose-Service sowie auf `lensmarket_outbox_pending` und die Auth-/Fehlermetriken. So lassen sich Last, Latenz und Ressourcenverbrauch im selben Zeitraum gegenüberstellen.

## Ergebnisse auswerten

`analyze.py` liest den JSON-Output eines oder mehrerer Läufe (`--out json=...`) und erzeugt daraus ein Latenz/VU-Zeitreihendiagramm sowie eine Latenz-Zusammenfassung je Endpunkt-Typ (`search`/`detail`/`health`) im Terminal.

Einmalig installieren:

```bash
pip install -r load-tests/requirements.txt
```

Einzelner Lauf:

```bash
python load-tests/analyze.py 1x=results-1x.json -o 1x_latency.png
```

Baseline vs. skaliert im selben Diagramm, übereinandergelegt auf einer relativen Zeitachse ab Laufstart:

```bash
python load-tests/analyze.py 1x=results-1x.json 3x=results-3x.json -o comparison.png
```

Das Skript ist unabhängig vom Gradle-Build und den k6-Skripten selbst; es setzt nur eine bestehende `--out json=...`-Ausgabedatei voraus.

## Grenzen

- `listing-db` läuft mit `max_connections=200`, `listing-service` nutzt pro Instanz einen reaktiven Pool mit `max-size=20`. Bei sehr hohen VU-Zahlen im Breakpoint-Test kann die Fehlerursache an der Datenbank statt am Service selbst liegen; ein Blick in `pg_stat_activity` schafft hier Klarheit.
- Die Ergebnisse sind nur auf der lokalen Docker-Compose-Umgebung aussagekräftig, nicht als Kapazitätsplanung für eine produktive Umgebung zu verstehen.

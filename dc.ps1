# Startet Docker Compose mit allen Projektteilen:
# - docker-compose.yml enthaelt Gateway, Services und Datenbanken.
# - Die monitoring-Dateien ergaenzen Prometheus, Grafana und Jaeger.
# Alle uebergebenen Argumente werden an docker compose weitergereicht,
# z.B. ".\dc.ps1 up -d --build" oder ".\dc.ps1 down".
docker compose `
  -f docker-compose.yml `
  -f monitoring/docker-compose-metrics.yml `
  -f monitoring/docker-compose-metrics-ui.yml `
  -f monitoring/docker-compose-tracing.yml `
  @args

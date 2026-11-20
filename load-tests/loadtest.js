import http from 'k6/http';
import { check, sleep } from 'k6';

// Traefik-Gateway, nicht der Service direkt (8081, siehe docker-compose.yml / README).
// Override möglich: k6 run -e BASE_URL=http://localhost:8081 loadtest.js
const BASE_URL = __ENV.BASE_URL || 'http://localhost:8081';

// Label für diesen Lauf, taucht in den k6-Tags auf -> hilft beim späteren
// Vergleich mehrerer Runs (z.B. "1x", "3x") in ausgeleiteten Ergebnissen.
const RUN_LABEL = __ENV.RUN_LABEL || 'baseline';

const TAGS = ['objektiv', 'kamera', 'stativ', 'blitzgeraet', 'filter'];
const LOCATIONS = ['Berlin', 'Erfurt', 'Muenchen', 'Hamburg', 'Leipzig'];

export const options = {
  scenarios: {
    stability_test: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: '10s', target: 50 },   // langsam auf 50 gleichzeitige Nutzer hochfahren
        { duration: '20s', target: 200 },  // dann auf 200 hoch
        { duration: '30s', target: 200 },  // 30s auf 200 halten (Stabilitätstest)
        { duration: '10s', target: 0 },    // wieder runterfahren
      ],
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.05'],    // Test schlägt fehl, wenn >5% der Requests scheitern
    http_req_duration: ['p(95)<2000'], // 95% der Requests sollen unter 2s bleiben
  },
};

// Holt einmalig ein paar echte Listing-IDs, damit /listings/{id} nicht nur
// 404en. Läuft vor dem eigentlichen Ramp-Up und zählt nicht in die Metriken.
export function setup() {
  const res = http.get(`${BASE_URL}/listings?limit=50`, { tags: { name: 'setup' } });
  if (res.status !== 200) {
    console.warn(`Setup: /listings lieferte Status ${res.status} - Detail-Requests werden übersprungen.`);
    return { ids: [] };
  }
  const ids = JSON.parse(res.body).map((l) => l.id);
  console.log(`Setup: ${ids.length} Listing-IDs geladen.`);
  return { ids };
}

function randomOf(arr) {
  return arr[Math.floor(Math.random() * arr.length)];
}

// Realistische Mischung aus Suchanfragen mit unterschiedlichen Filtern/Pagination,
// wie ein echter Nutzer sie beim Stöbern auslösen würde.
function searchRequest() {
  const variants = [
    () => `/listings?limit=20`,
    () => `/listings?limit=20&offset=${Math.floor(Math.random() * 100)}`,
    () => `/listings?tag=${randomOf(TAGS)}&limit=20`,
    () => `/listings?location=${randomOf(LOCATIONS)}&limit=20`,
    () => `/listings?minPrice=50&maxPrice=500&limit=20`,
    () => `/listings?q=canon&limit=20`,
  ];
  return randomOf(variants)();
}

export default function (data) {
  const roll = Math.random();
  let res;

  if (roll < 0.7 || data.ids.length === 0) {
    // 70%: Suche/Browsing - der Haupt-Traffic-Pfad auf listing-service
    res = http.get(`${BASE_URL}${searchRequest()}`, {
      tags: { name: 'search', run: RUN_LABEL },
    });
  } else if (roll < 0.95) {
    // 25%: Detailansicht eines einzelnen Listings
    const id = randomOf(data.ids);
    res = http.get(`${BASE_URL}/listings/${id}`, {
      tags: { name: 'detail', run: RUN_LABEL },
    });
  } else {
    // 5%: Health-Check, als grober Sanity-Traffic
    res = http.get(`${BASE_URL}/listings/health`, {
      tags: { name: 'health', run: RUN_LABEL },
    });
  }

  check(res, {
    'status ist 200': (r) => r.status === 200,
    'antwort unter 2s': (r) => r.timings.duration < 2000,
  });

  sleep(Math.random() * 1); // 0-1s Pause, simuliert realistischeres Nutzerverhalten
}

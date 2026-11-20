import http from 'k6/http';
import { check } from 'k6';

// Traefik-Gateway, nicht der Service direkt (8081, siehe docker-compose.yml / README).
// Override möglich: k6 run -e BASE_URL=http://localhost:8081 breakpointtest.js
const BASE_URL = __ENV.BASE_URL || 'http://localhost:8081';

// Label für diesen Lauf, taucht in den k6-Tags auf -> hilft beim späteren
// Vergleich mehrerer Runs (z.B. "1x", "3x") in ausgeleiteten Ergebnissen.
const RUN_LABEL = __ENV.RUN_LABEL || 'baseline';

const TAGS = ['objektiv', 'kamera', 'stativ', 'blitzgeraet', 'filter'];
const LOCATIONS = ['Berlin', 'Erfurt', 'Muenchen', 'Hamburg', 'Leipzig'];

export const options = {
  scenarios: {
    breakpoint: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: '3m', target: 2000 }, // sehr langsam und stetig hochfahren
      ],
    },
  },
  thresholds: {
    // Sobald die Fehlerrate über 10% steigt, bricht k6 den Test automatisch ab -
    // ab dem Punkt hat listing-service seine Kapazitätsgrenze erreicht.
    http_req_failed: [{ threshold: 'rate<0.10', abortOnFail: true }],
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

// Realistische Mischung aus Suchanfragen mit unterschiedlichen Filtern/Pagination
// statt eines einzelnen statischen Requests - trifft dieselben DB-Query-Pfade
// wie ein echter Nutzer, der stöbert.
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
  });
}

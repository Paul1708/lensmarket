// API-Config

const BASE_URL = '';

const API = {
  listings: () => fetch(`${BASE_URL}/listings`).then(r => r.ok ? r.json() : Promise.reject(r.status)),
  listingsByOwner: (ownerId) => fetch(`${BASE_URL}/listings?ownerUserId=${encodeURIComponent(ownerId)}`).then(r => r.ok ? r.json() : Promise.reject(r.status)),
  updateListing: (id, body) => fetch(`${BASE_URL}/listings/${id}`, { method: 'PUT', headers: { 'Content-Type': 'application/json', ...authHeaders() }, body: JSON.stringify(body) }).then(r => { if (!r.ok) return r.json().then(e => Promise.reject(e)); return r.json(); }),
  setListingStatus: (id, status) => fetch(`${BASE_URL}/listings/${id}/status`, { method: 'PATCH', headers: { 'Content-Type': 'application/json', ...authHeaders() }, body: JSON.stringify({ status }) }).then(r => { if (!r.ok) return r.json().then(e => Promise.reject(e)); return r.json(); }),
  deleteListing: (id) => fetch(`${BASE_URL}/listings/${id}`, { method: 'DELETE', headers: authHeaders() }).then(r => { if (!r.ok) return r.json().then(e => Promise.reject(e)).catch(() => Promise.reject(r.status)); }),
  myListings: () => fetch(`${BASE_URL}/listings/mine`, { headers: authHeaders() }).then(r => r.ok ? r.json() : Promise.reject(r.status)),
  myListing: (id) => fetch(`${BASE_URL}/listings/mine/${id}`, { headers: authHeaders() }).then(r => r.ok ? r.json() : Promise.reject(r.status)),
  listing: (id) => fetch(`${BASE_URL}/listings/${id}`).then(r => r.ok ? r.json() : Promise.reject(r.status)),
  login: (email, password) => fetch(`${BASE_URL}/users/login`, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ email, password }) }).then(r => { if (!r.ok) return r.json().then(e => Promise.reject(e)); return r.json(); }),
  me: () => fetch(`${BASE_URL}/users/me`, { headers: authHeaders() }).then(r => r.ok ? r.json() : Promise.reject(r.status)),
  updateProfile: (id, body) => fetch(`${BASE_URL}/users/${id}`, { method: 'PATCH', headers: { 'Content-Type': 'application/json', ...authHeaders() }, body: JSON.stringify(body) }).then(r => { if (!r.ok) return r.json().then(e => Promise.reject(e)); return r.json(); }),
  createConversation: (listingId) => fetch(`${BASE_URL}/messages/conversations`, { method: 'POST', headers: { 'Content-Type': 'application/json', ...authHeaders() }, body: JSON.stringify({ listingId }) }).then(r => { if (!r.ok) return r.json().then(e => Promise.reject(e)); return r.json(); }),
  conversations: () => fetch(`${BASE_URL}/messages/conversations`, { headers: authHeaders() }).then(r => r.ok ? r.json() : Promise.reject(r.status)),
  conversationMessages: (conversationId) => fetch(`${BASE_URL}/messages/conversations/${conversationId}/messages`, { headers: authHeaders() }).then(r => r.ok ? r.json() : Promise.reject(r.status)),
  sendMsg: (conversationId, body) => fetch(`${BASE_URL}/messages/conversations/${conversationId}/messages`, { method: 'POST', headers: { 'Content-Type': 'application/json', ...authHeaders() }, body: JSON.stringify({ body }) }).then(r => r.ok ? r.json() : Promise.reject(r.status)),
  removeConversation: (conversationId) => fetch(`${BASE_URL}/messages/conversations/${conversationId}`, { method: 'DELETE', headers: authHeaders() }).then(r => r.ok ? r.json() : Promise.reject(r.status)),
  user: (id) => fetch(`${BASE_URL}/users/${id}`).then(r => r.ok ? r.json() : Promise.reject(r.status)),
  users: () => fetch(`${BASE_URL}/users`).then(r => r.ok ? r.json() : Promise.reject(r.status)),
  createUser: (body) => fetch(`${BASE_URL}/users`, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body) }).then(r => { if (!r.ok) return r.json().then(e => Promise.reject(e)); return r.json(); }),
  createListing: (body) => fetch(`${BASE_URL}/listings`, { method: 'POST', headers: { 'Content-Type': 'application/json', ...authHeaders() }, body: JSON.stringify(body) }).then(r => { if (!r.ok) return r.json().then(e => Promise.reject(e)); return r.json(); }),
  uploadImage: (listingId, file) => {
    const form = new FormData();
    form.append('listingId', listingId);
    form.append('file', file);
    // Kein Content-Type-Header setzen — der Browser erzeugt für FormData die passende
    // multipart/form-data-Boundary selbst.
    return fetch(`${BASE_URL}/images/upload`, { method: 'POST', headers: authHeaders(), body: form })
      .then(r => { if (!r.ok) return r.json().then(e => Promise.reject(e)); return r.json(); });
  },
  listingImages: (listingId) => fetch(`${BASE_URL}/images?listingId=${listingId}`).then(r => r.ok ? r.json() : Promise.reject(r.status)),
};



// Session (Login mit E-Mail/Passwort, JWT vom user-service)

function readStoredUser() {
  try {
    const storedUser = JSON.parse(sessionStorage.getItem('lensmarket_user') || 'null');
    if (storedUser === null || (typeof storedUser === 'object' && storedUser.id)) return storedUser;
    throw new Error('Invalid stored user');
  } catch (e) {
    sessionStorage.removeItem('lensmarket_user');
    sessionStorage.removeItem('lensmarket_token');
    return null;
  }
}

let currentUser = readStoredUser();
let authToken = sessionStorage.getItem('lensmarket_token') || null;
let authSessionRevision = 0;

function authHeaders() {
  return authToken ? { 'Authorization': `Bearer ${authToken}` } : {};
}

function setCurrentUser(user, token) {
  const userChanged = currentUser?.id !== user?.id;
  currentUser = user;
  authToken = token || null;
  sessionStorage.setItem('lensmarket_user', JSON.stringify(user));
  if (authToken) sessionStorage.setItem('lensmarket_token', authToken);
  else sessionStorage.removeItem('lensmarket_token');
  if (userChanged) {
    authSessionRevision += 1;
    resetMessagingState();
    resetUserScopedState();
  }
  updateNavAuth();
}

function logout() {
  authSessionRevision += 1;
  currentUser = null;
  authToken = null;
  sessionStorage.removeItem('lensmarket_user');
  sessionStorage.removeItem('lensmarket_token');
  resetMessagingState('Bitte melde dich an, um deine Nachrichten zu sehen.');
  resetUserScopedState();
  pageHistory = [];
  updateNavAuth();
  setUnreadBadge(false);
  showPage('home', { isBack: true });
}

function isLoggedIn() {
  return !!currentUser && !!authToken;
}

function updateNavAuth() {
  const loggedIn = isLoggedIn();
  const set = (id, display) => { const el = document.getElementById(id); if (el) el.style.display = display; };
  set('nav-btn-login', loggedIn ? 'none' : '');
  set('nav-btn-register', loggedIn ? 'none' : '');
  set('nav-btn-create', loggedIn ? '' : 'none');
  set('nav-mine', loggedIn ? '' : 'none');
  set('nav-btn-user', loggedIn ? '' : 'none');
  set('nav-btn-logout', loggedIn ? '' : 'none');
  if (loggedIn) {
    const userBtn = document.getElementById('nav-btn-user');
    if (userBtn) userBtn.textContent = currentUser.displayName;
  }
}



let listings = [];
let convs = [];
let activeConv = -1;
let messagingStateRevision = 0;

function captureAuthSession() {
  return { revision: authSessionRevision, userId: currentUser?.id || null };
}

function isCurrentAuthSession(session) {
  return isLoggedIn()
    && session.revision === authSessionRevision
    && session.userId === currentUser.id;
}

function isCurrentMessagingRequest(session, messagingRevision) {
  return isCurrentAuthSession(session) && messagingRevision === messagingStateRevision;
}

function setConversationListStatus(message = '') {
  const list = document.getElementById('conv-list');
  if (!list) return;
  list.replaceChildren();
  if (!message) return;
  const status = document.createElement('div');
  status.style.cssText = 'padding:24px;color:#6b7280;font-size:14px;';
  status.textContent = message;
  list.appendChild(status);
}

function clearActiveConversation() {
  activeConv = -1;
  ['chat-name', 'chat-sub', 'chat-avatar'].forEach(id => {
    const element = document.getElementById(id);
    if (element) element.textContent = '';
  });
  const messages = document.getElementById('chat-messages');
  if (messages) messages.replaceChildren();
  const input = document.getElementById('msg-input');
  if (input) input.value = '';
  const deleteButton = document.getElementById('chat-delete-btn');
  if (deleteButton) deleteButton.style.display = 'none';
}

function resetMessagingState(statusMessage = '') {
  messagingStateRevision += 1;
  convs = [];
  clearActiveConversation();
  setConversationListStatus(statusMessage);
}

function resetUserScopedState() {
  myListings = [];
  editingListingId = null;

  ['profile-email', 'profile-displayname', 'profile-bio', 'profile-location'].forEach(id => {
    const input = document.getElementById(id);
    if (input) input.value = '';
  });
  const role = document.getElementById('profile-role');
  if (role) role.value = 'CUSTOMER';
  ['profile-created-at', 'profile-error', 'profile-success'].forEach(id => {
    const element = document.getElementById(id);
    if (!element) return;
    element.textContent = '';
    if (id !== 'profile-created-at') element.style.display = 'none';
  });

  const myListingsGrid = document.getElementById('my-listings-grid');
  if (myListingsGrid) myListingsGrid.replaceChildren();
  const myListingsEmpty = document.getElementById('my-listings-empty');
  if (myListingsEmpty) myListingsEmpty.style.display = 'none';
}

function escapeHtml(value) {
  return String(value ?? '').replace(/[&<>"']/g, character => ({
    '&': '&amp;',
    '<': '&lt;',
    '>': '&gt;',
    '"': '&quot;',
    "'": '&#39;'
  })[character]);
}

function inlineJsString(value) {
  const json = JSON.stringify(String(value ?? ''))
    .replace(/\u2028/g, '\\u2028')
    .replace(/\u2029/g, '\\u2029');
  return escapeHtml(json);
}

function formatPrice(priceHint) {
  return priceHint !== null && priceHint !== undefined && priceHint !== ''
    ? `${priceHint} €`
    : '';
}



// Registrieren

async function handleRegister() {
  const email = document.getElementById('reg-email').value.trim();
  const displayName = document.getElementById('reg-displayname').value.trim();
  const password = document.getElementById('reg-password').value;
  const bio = document.getElementById('reg-bio').value.trim();
  const location = document.getElementById('reg-location').value.trim();
  const errorEl = document.getElementById('reg-error');
  errorEl.style.display = 'none';

  if (!email || !displayName || !password) {
    errorEl.textContent = 'E-Mail, Anzeigename und Passwort sind Pflichtfelder.';
    errorEl.style.display = 'block';
    return;
  }

  try {
    await API.createUser({ email, displayName, password, bio: bio || null, location: location || null });
    // Die Registrierung liefert kein Token zurück, daher direkt im Anschluss einloggen.
    const { token, user } = await API.login(email, password);
    setCurrentUser(user, token);
    refreshUnreadBadge();
    showPage('home');
  } catch (e) {
    errorEl.textContent = e?.message || 'Registrierung fehlgeschlagen. E-Mail bereits vergeben?';
    errorEl.style.display = 'block';
  }
}



// Anmelden

async function handleLogin() {
  const email = document.getElementById('login-email').value.trim();
  const password = document.getElementById('login-password').value;
  const errorEl = document.getElementById('login-error');
  errorEl.style.display = 'none';

  if (!email || !password) {
    errorEl.textContent = 'Bitte E-Mail und Passwort eingeben.';
    errorEl.style.display = 'block';
    return;
  }

  try {
    const { token, user } = await API.login(email, password);
    setCurrentUser(user, token);
    refreshUnreadBadge();
    showPage('home');
  } catch (e) {
    errorEl.textContent = e?.message || 'Anmeldung fehlgeschlagen.';
    errorEl.style.display = 'block';
  }
}



// Profil

async function loadProfile() {
  const errorEl = document.getElementById('profile-error');
  errorEl.style.display = 'none';
  const session = captureAuthSession();
  try {
    const user = await API.me();
    if (!isCurrentAuthSession(session)) return;
    document.getElementById('profile-email').value = user.email || '';
    document.getElementById('profile-displayname').value = user.displayName || '';
    document.getElementById('profile-role').value = user.role || 'CUSTOMER';
    document.getElementById('profile-bio').value = user.bio || '';
    document.getElementById('profile-location').value = user.location || '';
    const createdAt = user.createdAt ? new Date(user.createdAt).toLocaleDateString('de-DE') : '';
    document.getElementById('profile-created-at').textContent = createdAt ? `Mitglied seit ${createdAt}` : '';
  } catch (e) {
    if (!isCurrentAuthSession(session)) return;
    errorEl.textContent = 'Profil konnte nicht geladen werden.';
    errorEl.style.display = 'block';
  }
}

async function handleUpdateProfile() {
  const errorEl = document.getElementById('profile-error');
  const successEl = document.getElementById('profile-success');
  errorEl.style.display = 'none';
  successEl.style.display = 'none';

  const displayName = document.getElementById('profile-displayname').value.trim();
  const role = document.getElementById('profile-role').value;
  const bio = document.getElementById('profile-bio').value.trim();
  const location = document.getElementById('profile-location').value.trim();

  if (!displayName) {
    errorEl.textContent = 'Der Anzeigename darf nicht leer sein.';
    errorEl.style.display = 'block';
    return;
  }

  const session = captureAuthSession();
  try {
    const updated = await API.updateProfile(session.userId, { displayName, role, bio: bio || null, location: location || null });
    if (!isCurrentAuthSession(session)) return;
    // Session mit den aktualisierten Daten synchron halten (z.B. für den Namen im Nav-Button)
    setCurrentUser(updated, authToken);
    successEl.textContent = 'Profil aktualisiert!';
    successEl.style.display = 'block';
  } catch (e) {
    if (!isCurrentAuthSession(session)) return;
    errorEl.textContent = e?.message || 'Profil konnte nicht aktualisiert werden.';
    errorEl.style.display = 'block';
  }
}



// User Cache

const userCache = {};

async function resolveUserIds(ids) {
  const unknownIds = [...new Set(ids.filter(id => id && !userCache[id]))];
  await Promise.allSettled(
    unknownIds.map(id =>
      API.user(id)
        .then(u => { userCache[id] = u.displayName || u.email || id; })
        .catch(() => { userCache[id] = id; }) // Fallback: ID anzeigen
    )
  );
}

async function resolveUserNames(listingList) {
  await resolveUserIds(listingList.map(l => l.ownerUserId));
}

function displayName(ownerUserId) {
  return userCache[ownerUserId] || ownerUserId || '?';
}



// Anzeige-Titel-Cache (für die Konversations-Übersicht: "Anzeige XY" statt der reinen ID)

const listingTitleCache = {};

async function resolveListingTitles(convList) {
  const unknownIds = [...new Set(convList.map(c => c.listingId).filter(id => id && !(id in listingTitleCache)))];
  await Promise.allSettled(
    unknownIds.map(id =>
      API.listing(id)
        .then(l => { listingTitleCache[id] = l.title; })
        .catch(() => { listingTitleCache[id] = null; })
    )
  );
}

function listingTitle(listingId) {
  return listingTitleCache[listingId] || (listingId ? `Anzeige ${listingId}` : '');
}

function partnerIdOf(c) {
  if (!currentUser) return c.customerUserId || c.photographerUserId;
  return c.customerUserId === currentUser.id ? c.photographerUserId : c.customerUserId;
}



// Bild-Cache

const NO_IMAGE = 'fallback/no-image.png';
const imageCache = {}; // listingId -> watermarkedUrl (oder null, wenn kein Bild vorhanden)

async function resolveListingImages(listingList) {
  // Sammle alle Anzeige-IDs, deren Bild wir noch nicht kennen
  const unknownIds = [...new Set(listingList.map(l => l.id).filter(id => id && !(id in imageCache)))];
  await Promise.allSettled(
    unknownIds.map(id =>
      API.listingImages(id)
        .then(images => { imageCache[id] = (images && images[0]) ? images[0].watermarkedUrl : null; })
        .catch(() => { imageCache[id] = null; })
    )
  );
}

function listingImageUrl(l) {
  // Echte Listings zeigen das hochgeladene Wasserzeichenbild oder den Platzhalter.
  return l.imageUrl || l.image_url || l.thumbnailUrl || imageCache[l.id] || NO_IMAGE;
}



// Anzeige erstellen

async function handleCreateListing() {
  const errorEl = document.getElementById('create-error');
  const successEl = document.getElementById('create-success');
  errorEl.style.display = 'none';
  successEl.style.display = 'none';

  if (!isLoggedIn()) {
    errorEl.textContent = 'Bitte melde dich an, um eine Anzeige zu erstellen.';
    errorEl.style.display = 'block';
    return;
  }

  const imageFile = document.getElementById('create-image').files[0];

  const MAX_FILE_SIZE = 10 * 1024 * 1024; // 10 MB, entspricht dem image-service
  // Validierung der Bildgröße, bevor die Anzeige erstellt wird
  if (imageFile && imageFile.size > MAX_FILE_SIZE) {
    errorEl.textContent = 'Das ausgewählte Bild darf maximal 10 MB groß sein.';
    errorEl.style.display = 'block';
    return;
  }
  if (imageFile && !['image/jpeg', 'image/png'].includes(imageFile.type)) {
    errorEl.textContent = 'Bitte wähle ein Bild im JPG- oder PNG-Format aus.';
    errorEl.style.display = 'block';
    return;
  }

  const title = document.getElementById('create-title').value.trim();
  const desc = document.getElementById('create-desc').value.trim();
  const price = document.getElementById('create-price').value;
  const location = document.getElementById('create-location').value.trim();
  const camera = document.getElementById('create-camera').value.trim();
  const lens = document.getElementById('create-lens').value.trim();
  const tagsRaw = document.getElementById('create-tags').value.trim();

  if (!title || !desc || !imageFile) {
    errorEl.textContent = 'Titel, Beschreibung und ein Foto sind Pflichtfelder.';
    errorEl.style.display = 'block';
    return;
  }

  const tags = tagsRaw ? tagsRaw.split(',').map(t => t.trim()).filter(Boolean) : [];

  const body = {
    title,
    description: desc,
    priceHint: price ? parseFloat(price) : null,
    location: location || null,
    cameraModel: camera || null,
    lensModel: lens || null,
    tags
  };

  const session = captureAuthSession();
  try {
    const listing = await API.createListing(body);
    if (!isCurrentAuthSession(session)) return;
    try {
      const image = await API.uploadImage(listing.id, imageFile);
      if (!isCurrentAuthSession(session)) return;
      // Bild sofort im Cache hinterlegen, statt auf das asynchrone
      // listing<->image-Event zu warten.
      imageCache[listing.id] = image.watermarkedUrl;
      successEl.textContent = 'Anzeige erfolgreich veröffentlicht!';
    } catch (uploadErr) {
      if (!isCurrentAuthSession(session)) return;
      console.warn('Bild-Upload fehlgeschlagen:', uploadErr);
      successEl.textContent = 'Anzeige veröffentlicht, aber das Foto konnte nicht hochgeladen werden.';
    }
    successEl.style.display = 'block';
    // Formular zurücksetzen
    ['create-title', 'create-desc', 'create-price', 'create-location', 'create-camera', 'create-lens', 'create-tags']
      .forEach(id => document.getElementById(id).value = '');
    document.getElementById('create-image').value = '';
    // Listings neu laden und zur Startseite wechseln
    await loadListings();
    if (!isCurrentAuthSession(session)) return;
    setTimeout(() => {
      if (isCurrentAuthSession(session)) showPage('home');
    }, 1200);
  } catch (e) {
    if (!isCurrentAuthSession(session)) return;
    errorEl.textContent = e?.message || 'Anzeige konnte nicht erstellt werden.';
    errorEl.style.display = 'block';
  }
}



// Anzeige laden

async function loadListings() {
  const gallery = document.getElementById('gallery');
  const errorBanner = document.getElementById('api-error-home');
  errorBanner.style.display = 'none';
  gallery.innerHTML = '<div class="status">Anzeigen werden geladen...</div>';
  try {
    const data = await API.listings();
    listings = Array.isArray(data) ? data : (data.content || data.listings || []);
    await Promise.all([resolveUserNames(listings), resolveListingImages(listings)]);
  } catch (e) {
    listings = [];
    errorBanner.style.display = 'block';
  }
  renderGallery();
}

function renderGallery() {
  const gallery = document.getElementById('gallery');
  const query = document.getElementById('search-input').value.toLowerCase();
  const filtered = listings.filter(l =>
    !query ||
    (l.title || '').toLowerCase().includes(query) ||
    (l.tags || []).join(' ').toLowerCase().includes(query) ||
    (l.location || '').toLowerCase().includes(query)
  );
  if (filtered.length === 0) {
    gallery.innerHTML = '<div class="status" style="grid-column:1/-1">Keine Anzeigen gefunden.</div>';
    return;
  }
  gallery.innerHTML = filtered.map(l => {
    const img = escapeHtml(listingImageUrl(l));
    const name = String(displayName(l.ownerUserId) || l.sellerName || '?');
    const tags = (l.tags || []).join(', ');
    const price = formatPrice(l.priceHint);
    return `<div class="card" onclick="showDetail(${inlineJsString(l.id)})">
      <div class="card-img">
        <img src="${img}" alt="${escapeHtml(l.title)}" onerror="this.src='${NO_IMAGE}'">
      </div>
      <div class="card-body">
        <div class="card-title">${escapeHtml(l.title)}</div>
        <div class="card-meta"><span>${escapeHtml(tags)}</span></div>
        <div class="card-user">
          <div class="avatar">${escapeHtml(name.slice(0, 2).toUpperCase())}</div>
          <span style="font-size:12px;color:#888">${escapeHtml(name)}</span>
          ${price ? `<span style="font-size:11px;color:#aaa;margin-left:auto">${escapeHtml(price)}</span>` : ''}
        </div>
      </div>
    </div>`;
  }).join('');
}

document.getElementById('search-input').addEventListener('input', renderGallery);



// Eigene Anzeigen

let myListings = [];

function statusLabel(status) {
  return { ACTIVE: 'Aktiv', PAUSED: 'Pausiert', SOLD: 'Verkauft' }[status] || status;
}

async function loadMyListings() {
  const grid = document.getElementById('my-listings-grid');
  const emptyEl = document.getElementById('my-listings-empty');
  const session = captureAuthSession();
  emptyEl.style.display = 'none';
  grid.innerHTML = '<div class="status" style="grid-column:1/-1">Anzeigen werden geladen...</div>';
  try {
    const loadedListings = await API.myListings();
    if (!isCurrentAuthSession(session)) return;
    myListings = Array.isArray(loadedListings)
      ? loadedListings
      : (loadedListings.content || loadedListings.listings || []);
  } catch (e) {
    if (!isCurrentAuthSession(session)) return;
    myListings = [];
    grid.innerHTML = '<div class="status" style="grid-column:1/-1">Anzeigen konnten nicht geladen werden.</div>';
    return;
  }
  await resolveListingImages(myListings);
  if (!isCurrentAuthSession(session)) return;
  renderMyListings();
}

function renderMyListings() {
  const grid = document.getElementById('my-listings-grid');
  const emptyEl = document.getElementById('my-listings-empty');
  if (myListings.length === 0) {
    grid.innerHTML = '';
    emptyEl.style.display = 'block';
    return;
  }
  grid.innerHTML = myListings.map(l => {
    const img = escapeHtml(listingImageUrl(l));
    const price = formatPrice(l.priceHint);
    const statusBtnLabel = l.status === 'PAUSED' ? 'Reaktivieren' : 'Pausieren';
    const listingId = inlineJsString(l.id);
    return `<div class="card" onclick="showDetail(${listingId})">
      <div class="card-img">
        <img src="${img}" alt="${escapeHtml(l.title)}" onerror="this.src='${NO_IMAGE}'">
      </div>
      <div class="card-body">
        <div class="card-title">${escapeHtml(l.title)}</div>
        <div class="card-meta"><span>${escapeHtml(statusLabel(l.status))}</span>${price ? `<span class="price-badge">${escapeHtml(price)}</span>` : ''}</div>
        <div style="display:flex;gap:6px;margin-top:8px" onclick="event.stopPropagation()">
          <button class="btn" style="flex:1;padding:6px;font-size:12px" onclick="showEditListing(${listingId})">Bearbeiten</button>
          <button class="btn" style="flex:1;padding:6px;font-size:12px" onclick="toggleListingStatus(myListings.find(x=>x.id===${listingId}))">${statusBtnLabel}</button>
          <button class="btn" style="flex:1;padding:6px;font-size:12px;color:#c0392b" onclick="confirmDeleteListing(${listingId}, 'my-listings')">Löschen</button>
        </div>
      </div>
    </div>`;
  }).join('');
}



// Detailseite einer Anzeige

async function showDetail(id, options = {}) {
  let l = listings.find(x => x.id == id) || myListings.find(x => x.id == id);
  if (!l) {
    try { l = await API.listing(id); } catch (e) { }
  }
  if (!l && isLoggedIn()) {
    // Öffentliche Endpunkte liefern nur ACTIVE-Anzeigen (siehe domain-rules.md).
    // Für eine pausierte/verkaufte eigene Anzeige greift daher der besitzergebundene
    // Endpunkt, z.B. nach dem Pausieren über "Entdecken" statt "Meine Anzeigen".
    try { l = await API.myListing(id); } catch (e) { }
  }
  if (!l) return;
  if (!(l.id in imageCache)) {
    await resolveListingImages([l]);
  }
  const img = listingImageUrl(l);
  const name = displayName(l.ownerUserId) || l.sellerName || '?';
  document.getElementById('detail-img').src = img;
  document.getElementById('detail-img').onerror = function () { this.onerror = null; this.src = NO_IMAGE; };
  document.getElementById('detail-title').textContent = l.title;
  document.getElementById('detail-tags').textContent = (l.tags || []).join(', ');
  const detailPrice = formatPrice(l.priceHint);
  const detailPriceElement = document.getElementById('detail-price');
  detailPriceElement.textContent = detailPrice;
  detailPriceElement.style.display = detailPrice ? '' : 'none';
  document.getElementById('detail-desc').textContent = l.description || '';
  document.getElementById('detail-avatar').textContent = name.slice(0, 2).toUpperCase();
  document.getElementById('detail-seller').textContent = name;
  document.getElementById('detail-since').textContent = '';
  document.getElementById('detail-meta').innerHTML = [
    l.location ? `<div class="detail-row"><span>Aufgenommen in</span><span>${escapeHtml(l.location)}</span></div>` : '',
    l.cameraModel ? `<div class="detail-row"><span>Kamera</span><span>${escapeHtml(l.cameraModel)}</span></div>` : '',
    l.lensModel ? `<div class="detail-row"><span>Objektiv</span><span>${escapeHtml(l.lensModel)}</span></div>` : '',
    l.createdAt ? `<div class="detail-row"><span>Eingestellt am</span><span>${escapeHtml(new Date(l.createdAt).toLocaleDateString('de-DE'))}</span></div>` : '',
  ].join('');

  document.getElementById('detail-seller-profile-btn').onclick = () =>
    showSellerProfile(l.ownerUserId, { previousContext: l.id });

  const isOwner = isLoggedIn() && currentUser.id === l.ownerUserId;
  document.getElementById('detail-message-btn').style.display = isOwner ? 'none' : '';
  document.getElementById('detail-message-btn').onclick = () => startConversation(l.id);
  document.getElementById('detail-owner-actions').style.display = isOwner ? 'block' : 'none';
  if (isOwner) {
    document.getElementById('detail-edit-btn').onclick = () => showEditListing(l.id);
    const statusBtn = document.getElementById('detail-status-btn');
    statusBtn.textContent = l.status === 'PAUSED' ? 'Reaktivieren' : 'Pausieren';
    statusBtn.onclick = () => toggleListingStatus(l);
    document.getElementById('detail-delete-btn').onclick = () => confirmDeleteListing(l.id, 'home');
  }

  showPage('detail', options);
}



// Anbieterprofil (öffentlich)

async function showSellerProfile(userId, options = {}) {
  const nameEl = document.getElementById('seller-profile-name');
  const roleEl = document.getElementById('seller-profile-role');
  const bioEl = document.getElementById('seller-profile-bio');
  const metaEl = document.getElementById('seller-profile-meta');
  const listEl = document.getElementById('seller-profile-listings');
  nameEl.textContent = 'Lädt...';
  roleEl.textContent = '';
  bioEl.textContent = '';
  metaEl.textContent = '';
  listEl.innerHTML = '';
  showPage('seller-profile', options);

  try {
    const user = await API.user(userId);
    document.getElementById('seller-profile-avatar').textContent = (user.displayName || '?').slice(0, 2).toUpperCase();
    nameEl.textContent = user.displayName || 'Unbekannt';
    roleEl.textContent = user.role === 'PHOTOGRAPHER' ? 'Fotograf:in' : 'Käufer:in';
    bioEl.textContent = user.bio || '';
    metaEl.textContent = [
      user.location ? `📍 ${user.location}` : '',
      user.createdAt ? `Mitglied seit ${new Date(user.createdAt).toLocaleDateString('de-DE')}` : ''
    ].filter(Boolean).join(' · ');
  } catch (e) {
    nameEl.textContent = 'Profil konnte nicht geladen werden.';
    return;
  }

  try {
    const sellerListings = await API.listingsByOwner(userId);
    await resolveListingImages(sellerListings);
    if (sellerListings.length === 0) {
      listEl.innerHTML = '<div class="status" style="grid-column:1/-1">Keine aktiven Anzeigen.</div>';
    } else {
      listEl.innerHTML = sellerListings.map(l => {
        const img = escapeHtml(listingImageUrl(l));
        const price = formatPrice(l.priceHint);
        return `<div class="card" onclick="showDetail(${inlineJsString(l.id)})">
          <div class="card-img">
            <img src="${img}" alt="${escapeHtml(l.title)}" onerror="this.src='${NO_IMAGE}'">
          </div>
          <div class="card-body">
            <div class="card-title">${escapeHtml(l.title)}</div>
            <div class="card-meta"><span>${escapeHtml((l.tags || []).join(', '))}</span>${price ? `<span class="price-badge">${escapeHtml(price)}</span>` : ''}</div>
          </div>
        </div>`;
      }).join('');
    }
  } catch (e) {
    listEl.innerHTML = '<div class="status" style="grid-column:1/-1">Anzeigen konnten nicht geladen werden.</div>';
  }
}



// Anzeige bearbeiten / Status ändern / Löschen (nur Besitzer:in)

let editingListingId = null;

function showEditListing(id) {
  const l = myListings.find(x => x.id === id) || listings.find(x => x.id === id);
  if (!l) return;
  editingListingId = id;
  document.getElementById('edit-error').style.display = 'none';
  document.getElementById('edit-success').style.display = 'none';
  document.getElementById('edit-title').value = l.title || '';
  document.getElementById('edit-desc').value = l.description || '';
  document.getElementById('edit-price').value = l.priceHint ?? '';
  document.getElementById('edit-location').value = l.location || '';
  document.getElementById('edit-camera').value = l.cameraModel || '';
  document.getElementById('edit-lens').value = l.lensModel || '';
  document.getElementById('edit-tags').value = (l.tags || []).join(', ');
  showPage('edit-listing');
}

async function handleUpdateListing() {
  const errorEl = document.getElementById('edit-error');
  const successEl = document.getElementById('edit-success');
  errorEl.style.display = 'none';
  successEl.style.display = 'none';

  const title = document.getElementById('edit-title').value.trim();
  const desc = document.getElementById('edit-desc').value.trim();
  const price = document.getElementById('edit-price').value;
  const location = document.getElementById('edit-location').value.trim();
  const camera = document.getElementById('edit-camera').value.trim();
  const lens = document.getElementById('edit-lens').value.trim();
  const tagsRaw = document.getElementById('edit-tags').value.trim();

  if (!title || !desc) {
    errorEl.textContent = 'Titel und Beschreibung sind Pflichtfelder.';
    errorEl.style.display = 'block';
    return;
  }

  const tags = tagsRaw ? tagsRaw.split(',').map(t => t.trim()).filter(Boolean) : [];

  const session = captureAuthSession();
  const listingId = editingListingId;
  try {
    const updatedListing = await API.updateListing(listingId, {
      title, description: desc,
      priceHint: price ? parseFloat(price) : null,
      location: location || null,
      cameraModel: camera || null,
      lensModel: lens || null,
      tags
    });
    if (!isCurrentAuthSession(session)) return;

    // Die aktualisierte Serverantwort sofort in beide lokalen Listen übernehmen.
    // Dadurch zeigen Karten und Detailansicht keine alten Angaben, während die
    // anschließende Synchronisierung mit dem Backend läuft.
    const mergeUpdate = listing => listing.id === listingId
      ? { ...listing, ...updatedListing }
      : listing;
    listings = listings.map(mergeUpdate);
    myListings = myListings.map(mergeUpdate);
    renderGallery();
    renderMyListings();

    successEl.textContent = 'Änderungen gespeichert!';
    successEl.style.display = 'block';
    await Promise.all([loadListings(), loadMyListings()]);
    if (!isCurrentAuthSession(session)) return;
    setTimeout(() => {
      if (isCurrentAuthSession(session)) showPage('my-listings');
    }, 900);
  } catch (e) {
    if (!isCurrentAuthSession(session)) return;
    errorEl.textContent = e?.message || 'Änderungen konnten nicht gespeichert werden.';
    errorEl.style.display = 'block';
  }
}

async function toggleListingStatus(l) {
  const newStatus = l.status === 'PAUSED' ? 'ACTIVE' : 'PAUSED';
  const session = captureAuthSession();
  try {
    await API.setListingStatus(l.id, newStatus);
    if (!isCurrentAuthSession(session)) return;
    await Promise.all([loadMyListings(), loadListings()]);
    if (!isCurrentAuthSession(session)) return;
    if (document.getElementById('page-detail').classList.contains('active')) {
      await showDetail(l.id);
    }
  } catch (e) {
    if (!isCurrentAuthSession(session)) return;
    alert(e?.message || 'Status konnte nicht geändert werden.');
  }
}

async function confirmDeleteListing(id, redirectTo) {
  if (!confirm('Diese Anzeige wirklich löschen? Das kann nicht rückgängig gemacht werden.')) return;
  const session = captureAuthSession();
  try {
    await API.deleteListing(id);
    if (!isCurrentAuthSession(session)) return;

    // Nach bestätigter Server-Löschung sofort aus beiden clientseitigen Listen
    // entfernen. So bleibt weder auf der Startseite noch unter "Meine Anzeigen"
    // eine veraltete Karte sichtbar, während die erneuten Requests laufen.
    listings = listings.filter(listing => listing.id !== id);
    myListings = myListings.filter(listing => listing.id !== id);
    delete imageCache[id];

    const destination = redirectTo || 'my-listings';
    renderGallery();
    renderMyListings();
    showPage(destination);

    await Promise.all([loadListings(), loadMyListings()]);
  } catch (e) {
    if (!isCurrentAuthSession(session)) return;
    alert(e?.message || 'Anzeige konnte nicht gelöscht werden.');
  }
}



// Nachrichten

// Ungelesen-Markierung: Da das Backend kein Gelesen-Tracking pro Nachricht kennt,
// merken wir uns clientseitig, wann eine Konversation zuletzt geöffnet wurde, und
// vergleichen das mit ihrem "updatedAt" vom Server. Eine Annäherung, kein 100%
// korrekter Gelesen-Status (z.B. nicht geräteübergreifend).

function seenMapKey() {
  return currentUser ? `lensmarket_seen_${currentUser.id}` : null;
}

function getSeenMap() {
  const key = seenMapKey();
  if (!key) return {};
  try { return JSON.parse(localStorage.getItem(key) || '{}'); }
  catch (e) { return {}; }
}

function markConvSeen(c) {
  const key = seenMapKey();
  if (!key || !c || !c.id) return;
  const map = getSeenMap();
  map[c.id] = Date.now();
  localStorage.setItem(key, JSON.stringify(map));
}

function hasUnread(convList) {
  const map = getSeenMap();
  return convList.some(c => c.updatedAt && new Date(c.updatedAt).getTime() > (map[c.id] || 0));
}

function setUnreadBadge(show) {
  const el = document.getElementById('msgs-badge');
  if (el) el.style.display = show ? 'block' : 'none';
}

// Prüft im Hintergrund auf neue Nachrichten, z.B. direkt nach dem Login,
// ohne dass die Nachrichten-Seite dafür geöffnet werden muss.
async function refreshUnreadBadge() {
  if (!isLoggedIn()) { setUnreadBadge(false); return; }
  const session = captureAuthSession();
  try {
    const data = await API.conversations();
    if (!isCurrentAuthSession(session)) return;
    const list = Array.isArray(data) ? data : (data.content || data.conversations || []);
    setUnreadBadge(hasUnread(list));
  } catch (e) { /* Badge unverändert lassen, kein hartes Fehlschlagen nötig */ }
}

async function startConversation(listingId) {
  if (!isLoggedIn()) { showPage('login'); return; }
  const session = captureAuthSession();
  showPage('messages', { skipMessageLoad: true });
  resetMessagingState('Konversation wird geöffnet...');
  const messagingRevision = messagingStateRevision;
  try {
    const conversation = await API.createConversation(listingId);
    if (!isCurrentMessagingRequest(session, messagingRevision)) return;
    await loadMessages(conversation.id);
  } catch (e) {
    if (!isCurrentMessagingRequest(session, messagingRevision)) return;
    const message = e?.status === 404
      ? 'Die Anzeige ist nicht mehr verfügbar. Bitte lade die Startseite neu.'
      : e?.status === 401
        ? 'Deine Sitzung ist abgelaufen. Bitte melde dich erneut an.'
        : (e?.message || 'Konversation konnte nicht gestartet werden.');
    const errorItem = document.createElement('div');
    errorItem.style.cssText = 'padding:24px;color:#6b7280;font-size:14px;';
    errorItem.textContent = message;
    document.getElementById('conv-list').replaceChildren(errorItem);
  }
}

async function loadMessages(preferredConversationId = null) {
  if (!isLoggedIn()) {
    resetMessagingState('Bitte melde dich an, um deine Nachrichten zu sehen.');
    return;
  }
  const session = captureAuthSession();
  resetMessagingState('Nachrichten werden geladen...');
  const messagingRevision = messagingStateRevision;
  try {
    // Der Messaging-Service liest den/die eingeloggte(n) Nutzer:in aus dem JWT und
    // liefert dadurch ohnehin nur die eigenen Konversationen zurück.
    const data = await API.conversations();
    if (!isCurrentMessagingRequest(session, messagingRevision)) return;
    const loadedConversations = Array.isArray(data) ? data : (data.content || data.conversations || []);
    await Promise.all([
      resolveUserIds(loadedConversations.map(partnerIdOf)),
      resolveListingTitles(loadedConversations)
    ]);
    if (!isCurrentMessagingRequest(session, messagingRevision)) return;
    convs = loadedConversations;
  } catch (e) {
    if (!isCurrentMessagingRequest(session, messagingRevision)) return;
    resetMessagingState('Nachrichten konnten nicht geladen werden.');
    setUnreadBadge(false);
    return;
  }
  renderConvList();
  setUnreadBadge(hasUnread(convs));
  if (convs.length > 0) {
    const preferredIndex = convs.findIndex(c => c.id === preferredConversationId);
    await selectConv(preferredIndex >= 0 ? preferredIndex : 0);
  } else {
    clearActiveConversation();
  }
}

function renderConvList() {
  if (convs.length === 0) {
    document.getElementById('conv-list').innerHTML =
      '<div style="padding:24px;color:#6b7280;font-size:14px;">Noch keine Nachrichten. Schreib jemandem über eine Anzeige!</div>';
    return;
  }
  document.getElementById('conv-list').innerHTML = convs.map((c, i) => {
    const name = String(c.partnerName || c.partner_name || c.name || displayName(partnerIdOf(c)));
    const sub = String(c.subject || c.lastMessage || listingTitle(c.listingId));
    const msgs = c.messages || [];
    const lastTime = msgs.length ? msgs[msgs.length - 1].time || '' : (c.updatedAt ? new Date(c.updatedAt).toLocaleTimeString('de-DE', { hour: '2-digit', minute: '2-digit' }) : '');
    return `<div class="conv-item${i === activeConv ? ' active' : ''}" onclick="selectConv(${i})">
      <div class="conv-avatar">${escapeHtml(name.slice(0, 2).toUpperCase())}</div>
      <div class="conv-info">
        <div class="conv-name">${escapeHtml(name)}<span class="conv-time">${escapeHtml(lastTime)}</span></div>
        <div class="conv-preview">${escapeHtml(sub)}</div>
      </div>
    </div>`;
  }).join('');
}

async function selectConv(i) {
  activeConv = i;
  renderConvList();
  const c = convs[i];
  if (!c) return;
  const session = captureAuthSession();
  const messagingRevision = messagingStateRevision;
  const name = String(c.partnerName || c.partner_name || c.name || displayName(partnerIdOf(c)));
  document.getElementById('chat-avatar').textContent = name.slice(0, 2).toUpperCase();
  document.getElementById('chat-name').textContent = name;
  document.getElementById('chat-sub').textContent = c.subject || listingTitle(c.listingId);

  const deleteBtn = document.getElementById('chat-delete-btn');
  deleteBtn.style.display = c.id ? 'inline-block' : 'none';

  if (c.id) {
    try {
      const loadedMessages = await API.conversationMessages(c.id);
      if (!isCurrentMessagingRequest(session, messagingRevision) || activeConv !== i || convs[i] !== c) return;
      c.messages = loadedMessages;
    } catch (e) {
      if (!isCurrentMessagingRequest(session, messagingRevision) || activeConv !== i || convs[i] !== c) return;
      console.warn('Nachrichten konnten nicht geladen werden:', e);
      c.messages = c.messages || [];
    }
  }

  renderChatMessages(c);

  markConvSeen(c);
  setUnreadBadge(hasUnread(convs));
}

function renderChatMessages(c) {
  const msgs = document.getElementById('chat-messages');
  const messages = c.messages || [];
  msgs.innerHTML = messages.map(m => {
    const sent = m.from === 'me' || m.direction === 'SENT' || (currentUser && m.senderUserId === currentUser.id);
    const body = m.text || m.content || m.body || '';
    const time = m.time || (m.createdAt ? new Date(m.createdAt).toLocaleTimeString('de-DE', { hour: '2-digit', minute: '2-digit' }) : '');
    return `<div>
      <div class="msg ${sent ? 'sent' : 'received'}">${escapeHtml(body)}</div>
      <div class="msg-time${sent ? ' sent' : ''}">${escapeHtml(time)}</div>
    </div>`;
  }).join('');
  msgs.scrollTop = msgs.scrollHeight;
}

async function sendMsg() {
  const input = document.getElementById('msg-input');
  const text = input.value.trim();
  if (!text) return;
  const c = convs[activeConv];
  if (!c) return;
  const selectedIndex = activeConv;
  const conversationId = c.id;
  const session = captureAuthSession();
  input.value = '';

  // Sofort optimistisch anzeigen, ohne auf den Server zu warten
  c.messages = c.messages || [];
  const optimisticMessage = { from: 'me', text, time: 'jetzt' };
  c.messages.push(optimisticMessage);
  renderChatMessages(c);

  if (!c.id) return;

  try {
    const sentMessage = await API.sendMsg(c.id, text);
    if (!isCurrentAuthSession(session) || activeConv !== selectedIndex || convs[selectedIndex]?.id !== conversationId) return;
    const optimisticIndex = c.messages.indexOf(optimisticMessage);
    if (optimisticIndex >= 0) c.messages.splice(optimisticIndex, 1, sentMessage);
    else c.messages.push(sentMessage);
    renderChatMessages(c);
    markConvSeen(c);
  } catch (e) {
    if (!isCurrentAuthSession(session)) return;
    console.warn('Message send failed:', e);
    c.messages = (c.messages || []).filter(message => message !== optimisticMessage);
    if (activeConv === selectedIndex && convs[selectedIndex]?.id === conversationId) {
      renderChatMessages(c);
      input.value = text;
      alert('Die Nachricht konnte nicht gesendet werden. Bitte versuch es erneut.');
    }
  }
}

// Entfernt den aktuell geöffneten Verlauf nur auf der eigenen Seite; die andere
// Person im Chat sieht ihn weiterhin, außer sie entfernt ihn ebenfalls.
async function removeActiveConversation() {
  const c = convs[activeConv];
  if (!c || !c.id) return;
  if (!confirm('Diesen Nachrichtenverlauf bei dir entfernen? Die andere Person sieht ihn weiterhin.')) return;
  const session = captureAuthSession();
  const conversationId = c.id;

  try {
    await API.removeConversation(c.id);
    if (!isCurrentAuthSession(session)) return;
    const conversationIndex = convs.findIndex(conversation => conversation.id === conversationId);
    if (conversationIndex < 0) return;
    const activeConversationId = convs[activeConv]?.id;
    convs.splice(conversationIndex, 1);
    if (activeConversationId === conversationId) {
      clearActiveConversation();
      renderConvList();
      if (convs.length > 0) selectConv(Math.min(conversationIndex, convs.length - 1));
    } else {
      activeConv = convs.findIndex(conversation => conversation.id === activeConversationId);
      renderConvList();
    }
  } catch (e) {
    if (!isCurrentAuthSession(session)) return;
    console.warn('Konversation konnte nicht entfernt werden:', e);
    alert('Der Verlauf konnte nicht entfernt werden. Bitte versuch es erneut.');
  }
}

document.getElementById('msg-input').addEventListener('keydown', e => { if (e.key === 'Enter') sendMsg(); });



// Navigation

let pageHistory = []; // Einfacher History-Stack für "Zurück"-Buttons

function showPage(name, options = {}) {
  if ((name === 'messages' || name === 'create' || name === 'my-listings' || name === 'profile' || name === 'edit-listing') && !isLoggedIn()) {
    name = 'login';
  }
  // Aktuelle Seite auf den Stack legen, bevor gewechselt wird (außer beim Zurückgehen selbst)
  if (!options.isBack) {
    const current = document.querySelector('.page.active');
    if (current && current.id !== 'page-' + name) {
      pageHistory.push({ id: current.id.replace('page-', ''), context: options.previousContext });
    }
  }
  document.querySelectorAll('.page').forEach(p => p.classList.remove('active'));
  document.getElementById('page-' + name).classList.add('active');
  document.querySelectorAll('.nav-link').forEach(l => l.classList.remove('active'));
  if (name === 'home' || name === 'detail') document.getElementById('nav-home').classList.add('active');
  if (name === 'my-listings') { document.getElementById('nav-mine')?.classList.add('active'); loadMyListings(); }
  if (name === 'messages') {
    document.getElementById('nav-msgs').classList.add('active');
    if (!options.skipMessageLoad) loadMessages();
  }
  if (name === 'profile') { loadProfile(); }
}

function goBack() {
  const prev = pageHistory.pop();
  if (!prev) { showPage('home'); return; }
  if (prev.id === 'detail' && prev.context) {
    showDetail(prev.context, { isBack: true });
  } else {
    showPage(prev.id, { isBack: true });
  }
}


// Start

updateNavAuth();
loadListings();
refreshUnreadBadge();

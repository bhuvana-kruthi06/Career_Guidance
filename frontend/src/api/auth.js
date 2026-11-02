const API = import.meta.env.VITE_API_BASE_URL || '/api';

async function readBody(response) {
  if (response.status === 204) return null;
  try { return await response.json(); } catch { return null; }
}

async function send(path, options = {}) {
  let response;
  try {
    response = await fetch(API + path, { credentials: 'include', ...options });
  } catch {
    throw new Error('Can’t reach the account service. Check that the backend is running.');
  }
  const body = await readBody(response);
  if (!response.ok) {
    const error = new Error(body?.error || `The account request failed (HTTP ${response.status}).`);
    error.status = response.status;
    throw error;
  }
  return body;
}

async function csrfHeader() {
  const data = await send('/auth/csrf');
  return { 'X-XSRF-TOKEN': data.token };
}

export const authApi = {
  login: (credentials, scope = 'USER') => send('/auth/login', { method: 'POST', headers: { 'Content-Type': 'application/json', 'X-Auth-Scope': scope }, body: JSON.stringify(credentials) }),
  register: (credentials, scope = 'USER') => send('/auth/register', { method: 'POST', headers: { 'Content-Type': 'application/json', 'X-Auth-Scope': scope }, body: JSON.stringify(credentials) }),
  async refresh(scope = 'USER') { return send('/auth/refresh', { method: 'POST', headers: { ...(await csrfHeader()), 'X-Auth-Scope': scope } }); },
  async logout(scope = 'USER') { return send('/auth/logout', { method: 'POST', headers: { ...(await csrfHeader()), 'X-Auth-Scope': scope } }); },
  me: (accessToken) => send('/me', { headers: { Authorization: `Bearer ${accessToken}` } }),
  async account(path, accessToken, options = {}) {
    const method = options.method || 'GET';
    const headers = { Authorization: `Bearer ${accessToken}`, ...(options.body ? { 'Content-Type': 'application/json' } : {}) };
    if (['POST', 'PUT', 'DELETE'].includes(method)) Object.assign(headers, await csrfHeader());
    return send(`/me${path}`, { ...options, method, headers: { ...headers, ...options.headers }, ...(options.body ? { body: JSON.stringify(options.body) } : {}) });
  },
  async uploadResume(file, accessToken) {
    const headers = { Authorization: `Bearer ${accessToken}`, ...(await csrfHeader()) };
    const body = new FormData(); body.append('file', file);
    return send('/me/resumes', { method: 'POST', headers, body });
  },
};

export const adminApi = {
  async request(path, accessToken, options = {}) {
    const method = options.method || 'GET';
    const headers = { Authorization: `Bearer ${accessToken}`, ...(options.body ? { 'Content-Type': 'application/json' } : {}) };
    if (['POST', 'PUT', 'PATCH', 'DELETE'].includes(method)) Object.assign(headers, await csrfHeader());
    return send(`/admin${path}`, { ...options, method, headers: { ...headers, ...options.headers }, ...(options.body ? { body: JSON.stringify(options.body) } : {}) });
  },
};

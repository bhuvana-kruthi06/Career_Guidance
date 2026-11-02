const API = import.meta.env.VITE_API_BASE_URL || '/api';

export async function request(path, options) {
  let response;
  try {
    response = await fetch(API + path, { headers: { 'Content-Type': 'application/json' }, ...options });
  } catch {
    throw new Error('NETWORK');
  }
  if (!response.ok) {
    let message = '';
    try {
      const body = await response.json();
      message = body.error || body.detail || body.message || '';
    } catch { /* response did not contain a JSON error body */ }
    const error = new Error(message);
    error.status = response.status;
    throw error;
  }
  return response.json();
}

export function matchErrorMessage(error) {
  const status = error.status;
  if (error.message === 'NETWORK') return 'Can’t reach the matching service. Check that the backend is running, then reconnect and try again.';
  if (status === 429) return error.message || 'You have made several match requests in a short time. Wait a moment and try again.';
  if (status === 413) return error.message || 'This profile request is too large. Reduce the number of skills and try again.';
  if (status === 400) return error.message ? 'Your profile needs attention: ' + error.message : 'Your profile was not accepted. Check your skill ratings and interests, then try again.';
  if (status === 503) return error.message ? 'The matching service is unavailable: ' + error.message : 'The matching service is unavailable. Start or restart the backend, then try again.';
  if (Number.isInteger(status) && status >= 500) return 'The matching service returned a server error (HTTP ' + status + '). Try again; if it continues, check the backend terminal.';
  if (Number.isInteger(status)) return 'The request was rejected (HTTP ' + status + '). Review your profile and try again.';
  return 'The matching request failed unexpectedly. Try again.';
}

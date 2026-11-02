/** Safely read browser storage without letting corrupted or blocked storage break the app. */
export function readStorage(key, fallback, isValid = () => true, storage) {
  try {
    const target = typeof storage === 'string' ? globalThis[storage] : storage ?? globalThis.localStorage;
    const raw = target.getItem(key);
    if (raw == null) return fallback;
    const parsed = JSON.parse(raw);
    return isValid(parsed) ? parsed : fallback;
  } catch {
    return fallback;
  }
}

/** Validate before writing and return whether the value was persisted. */
export function writeStorage(key, value, isValid = () => true, storage) {
  try {
    const target = typeof storage === 'string' ? globalThis[storage] : storage ?? globalThis.localStorage;
    if (!isValid(value)) return false;
    target.setItem(key, JSON.stringify(value));
    return true;
  } catch {
    return false;
  }
}

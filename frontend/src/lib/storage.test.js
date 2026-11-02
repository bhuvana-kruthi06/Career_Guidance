import { describe, expect, it } from 'vitest';
import { readStorage, writeStorage } from './storage.js';

const isString = (value) => typeof value === 'string';

describe('safe browser storage', () => {
  it('reads and validates a stored JSON value', () => {
    const storage = { getItem: () => JSON.stringify('dark') };
    expect(readStorage('theme', 'light', isString, storage)).toBe('dark');
  });

  it('returns the fallback for malformed JSON', () => {
    const storage = { getItem: () => '{invalid' };
    expect(readStorage('saved', [], Array.isArray, storage)).toEqual([]);
  });

  it('returns the fallback when a valid JSON value has the wrong shape', () => {
    const storage = { getItem: () => JSON.stringify({ theme: 'dark' }) };
    expect(readStorage('theme', 'light', isString, storage)).toBe('light');
  });

  it('returns the fallback when storage access throws', () => {
    const storage = { getItem: () => { throw new Error('blocked'); } };
    expect(readStorage('theme', 'light', isString, storage)).toBe('light');
  });

  it('writes only values accepted by the validator', () => {
    const values = new Map();
    const storage = { setItem: (key, value) => values.set(key, value) };
    expect(writeStorage('theme', 'dark', isString, storage)).toBe(true);
    expect(values.get('theme')).toBe('"dark"');
    expect(writeStorage('theme', { invalid: true }, isString, storage)).toBe(false);
    expect(values.get('theme')).toBe('"dark"');
  });

  it('reports blocked storage writes instead of throwing', () => {
    const storage = { setItem: () => { throw new Error('blocked'); } };
    expect(writeStorage('theme', 'dark', isString, storage)).toBe(false);
  });
});

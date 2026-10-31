export type ThemePreference = 'system' | 'light' | 'dark';
const key = 'penny.appearance';
export const themeChangeEvent = 'penny-appearance-change';
const valid = (value: unknown): ThemePreference => value === 'light' || value === 'dark' ? value : 'system';
let preference: ThemePreference = 'system';
function stored(): ThemePreference {
  try { return valid(localStorage.getItem(key)); } catch { return 'system'; }
}
function apply() {
  const dark = preference === 'dark' || (preference === 'system' && !!window.matchMedia?.('(prefers-color-scheme: dark)').matches);
  document.documentElement.dataset.theme = dark ? 'dark' : 'light';
  document.documentElement.dataset.appearance = preference;
  window.dispatchEvent(new Event(themeChangeEvent));
}
export function getThemePreference() { return preference; }
export function setThemePreference(value: ThemePreference) {
  preference = valid(value);
  try { localStorage.setItem(key, preference); } catch { /* Still applies for this tab. */ }
  apply();
}
export function initializeTheme() {
  preference = stored();
  const media = window.matchMedia?.('(prefers-color-scheme: dark)');
  const update = () => { if (preference === 'system') apply(); };
  const sync = (event: StorageEvent) => {
    if (event.key === key || event.key === null) {
      preference = event.key === null ? stored() : valid(event.newValue);
      apply();
    }
  };
  media?.addEventListener('change', update);
  window.addEventListener('storage', sync);
  apply();
  return () => { media?.removeEventListener('change', update); window.removeEventListener('storage', sync); };
}

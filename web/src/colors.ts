export const commonColors = ['#002FA7', '#217A55', '#A33A32', '#7C4C9B', '#B06C16', '#20242E'];
export function normalizeHex(value: string): string | null {
  const raw = value.trim().replace(/^#/, '');
  if (/^[0-9a-f]{3}$/i.test(raw)) return '#' + raw.split('').map(c => c + c).join('').toUpperCase();
  return /^[0-9a-f]{6}$/i.test(raw) ? '#' + raw.toUpperCase() : null;
}
export function hexToRgb(value: string): number[] | null {
  const hex = normalizeHex(value); return hex ? [1, 3, 5].map(i => parseInt(hex.slice(i, i + 2), 16)) : null;
}
export function rgbToHex(values: (string | number)[]): string | null {
  if (values.length !== 3 || values.some(v => !/^\d{1,3}$/.test(String(v)) || Number(v) > 255)) return null;
  return '#' + values.map(v => Number(v).toString(16).padStart(2, '0')).join('').toUpperCase();
}

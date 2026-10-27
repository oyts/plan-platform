import { describe, it, expect } from 'vitest';
import { installedRelease, visibleHistory, targetHighlights, releaseHistory } from './releaseNotes';
describe('bundled plain release notes', () => {
    it('keeps 3–5 highlights and real version/date/code per authored entry', () => {
        for (const r of releaseHistory) {
            expect(r.highlights.length).toBeGreaterThanOrEqual(3);
            expect(r.highlights.length).toBeLessThanOrEqual(5);
            expect(r.date).toMatch(/^\d{4}-\d{2}-\d{2}$/);
            expect(r.androidCode).toBeGreaterThan(0);
        }
    });
    it('matches installed Android code and never infers an unknown installation from the server', () => {
        expect(installedRelease('0.1.24', { version: '0.1.24', versionCode: 28, serverUrl: '', updatesEnabled: true })).toBeNull();
        expect(installedRelease('9.0.0')).toBeNull();
        expect(visibleHistory('unknown')).toEqual([]);
        expect(visibleHistory('0.1.23').map(r => r.version)).toEqual(['0.1.23', '0.1.22', '0.1.21']);
    });
    it('bounds missing or remote fallback notes and does not interpret markup or URLs', () => {
        const u = { version: '0.2.0', mandatory: false, notes: null };
        expect(targetHighlights(u)).toEqual([]);
        expect(targetHighlights({ ...u, notes: '<script>alert(1)</script>\nhttps://evil.example/\n' + Array(8).fill('x'.repeat(600)).join('\n') })).toHaveLength(5);
        expect(targetHighlights({ ...u, notes: '<b>仅文本</b>' })).toEqual(['<b>仅文本</b>']);
        expect(targetHighlights({ ...u, notes: 'x'.repeat(5000) })[0]).toHaveLength(400);
    });
});

import { beforeEach, describe, it, expect, vi } from 'vitest';
const fixture = vi.hoisted(() => ({ native: false, mounted: [] as Array<() => void> }));
vi.mock('@/desktop', () => ({ isNativeClient: () => fixture.native }));
vi.mock('vue', async () => ({ ...await vi.importActual('vue'), onMounted: (f: () => void) => fixture.mounted.push(f), onUnmounted: () => { } }));
beforeEach(() => { vi.resetModules(); fixture.native = false; fixture.mounted = []; });
async function setup(waiting = true) {
    const worker = { postMessage: vi.fn() }, registered = { waiting: waiting ? worker : null, addEventListener: vi.fn() }, listeners = new Map<string, () => void>();
    const serviceWorker = { register: vi.fn().mockResolvedValue(registered), addEventListener: vi.fn((name: string, f: () => void) => listeners.set(name, f)) };
    const reload = vi.fn();
    vi.stubGlobal('navigator', { onLine: true, serviceWorker });
    vi.stubGlobal('window', { confirm: vi.fn().mockReturnValue(false), location: { reload }, matchMedia: () => ({ matches: false }), addEventListener: vi.fn(), removeEventListener: vi.fn() });
    const p = await import('./pwa');
    p.setupPwa();
    await Promise.resolve();
    return { ...p, worker, registered, serviceWorker, reload, listeners };
}
describe('PWA refresh is not a native install', () => {
    it('detects an already waiting shell and leaves current input untouched on cancellation', async () => {
        const f = await setup();
        const state = f.usePwaStatus();
        expect(state.updateReady.value).toBe(true);
        state.update();
        expect(f.worker.postMessage).not.toHaveBeenCalled();
        expect(f.reload).not.toHaveBeenCalled();
    });
    it('reloads only after explicit confirmation and actual controller activation', async () => {
        const f = await setup();
        vi.mocked(window.confirm).mockReturnValue(true);
        f.usePwaStatus().update();
        expect(f.worker.postMessage).toHaveBeenCalledWith('SKIP_WAITING');
        expect(f.reload).not.toHaveBeenCalled();
        f.listeners.get('controllerchange')!();
        expect(f.reload).toHaveBeenCalledTimes(1);
    });
    it('does not register a worker in native clients or claim a refresh when none is waiting', async () => {
        fixture.native = true;
        const f = await setup(false);
        expect(f.serviceWorker.register).not.toHaveBeenCalled();
        f.usePwaStatus().update();
        expect(window.confirm).not.toHaveBeenCalled();
    });
});

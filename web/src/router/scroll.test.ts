import { afterEach, describe, expect, it, vi } from 'vitest';
import type { RouteLocationNormalized } from 'vue-router';
import { navigationPosition, navigationScroll } from './scroll';
const route = (path: string) => ({ path }) as RouteLocationNormalized;
afterEach(() => { vi.useRealTimers(); vi.unstubAllGlobals(); });

function browser(height = 5000) {
  const focus = vi.fn();
  const root = { scrollHeight: height };
  const listeners = new Map<string, () => void>();
  const disconnect = vi.fn();
  let resized = () => {};
  vi.stubGlobal('document', { documentElement: root, body: {}, querySelector: () => ({ focus }) });
  const scrollTo = vi.fn();
  vi.stubGlobal('window', { innerHeight: 900, scrollTo, addEventListener: (event: string, callback: () => void) => listeners.set(event, callback), removeEventListener: (event: string) => listeners.delete(event) });
  vi.stubGlobal('ResizeObserver', class { constructor(callback: () => void) { resized = callback; } observe() {} disconnect() { disconnect(); } });
  return { focus, root, listeners, disconnect, scrollTo, resize: () => resized() };
}

describe('navigation scroll policy', () => {
  it('opens new pages and different lesson routes at the top', () => {
    expect(navigationPosition('/learn', '/plans', null)).toEqual({ left: 0, top: 0 });
    expect(navigationPosition('/learn/route', '/learn/plan', null)).toEqual({ left: 0, top: 0 });
  });
  it('does not reset same-page query/filter navigation', () => {
    expect(navigationPosition('/mainlines', '/mainlines', null)).toBe(false);
    expect(navigationPosition('/schedule', '/schedule', null)).toBe(false);
  });
  it('restores history coordinates including zero instead of treating them as new navigation', () => {
    expect(navigationPosition('/plans', '/learn', { left: 10, top: 700 })).toEqual({ left: 10, top: 700 });
    expect(navigationPosition('/plans', '/learn', { left: 0, top: 0 })).toEqual({ left: 0, top: 0 });
  });
  it('focuses the content landmark without implicit scrolling on a new page', () => {
    const focus = vi.fn();
    vi.stubGlobal('document', { querySelector: vi.fn(() => ({ focus })) });
    expect(navigationScroll(route('/learn'), route('/plans'), null)).toEqual({ left: 0, top: 0, behavior: 'instant' });
    expect(focus).toHaveBeenCalledWith({ preventScroll: true });
  });
  it('does not steal focus for history restoration or same-page filters', async () => {
    vi.useFakeTimers();
    const { focus } = browser();
    const pending = navigationScroll(route('/plans'), route('/learn'), { left: 0, top: 600 });
    await vi.advanceTimersByTimeAsync(2000); await pending;
    navigationScroll(route('/plans'), route('/plans'), null);
    expect(focus).not.toHaveBeenCalled();
  });
  it('allows initial navigation before the application is mounted', () => {
    vi.stubGlobal('document', { querySelector: () => null });
    expect(() => navigationScroll(route('/login'), route('/'), null)).not.toThrow();
  });
  it('waits for an asynchronously loaded list to have room for its history offset', async () => {
    vi.useFakeTimers();
    const dom = browser(1000);
    const pending = navigationScroll(route('/plans'), route('/learn'), { left: 0, top: 700 });
    expect(dom.listeners.size).toBe(4);
    dom.root.scrollHeight = 3000; dom.resize();
    expect(dom.scrollTo).toHaveBeenCalledWith({ left: 0, top: 700, behavior: 'instant' });
    await vi.advanceTimersByTimeAsync(2000);
    await expect(pending).resolves.toEqual({ left: 0, top: 700, behavior: 'instant' });
    expect(dom.disconnect).toHaveBeenCalledOnce(); expect(dom.listeners.size).toBe(0);
    expect(vi.getTimerCount()).toBe(0); expect(dom.focus).not.toHaveBeenCalled();
  });
  it('bounds the wait for a page that is now shorter', async () => {
    vi.useFakeTimers(); const dom = browser(1000);
    const pending = navigationScroll(route('/plans'), route('/learn'), { left: 0, top: 700 });
    await vi.advanceTimersByTimeAsync(2000);
    await expect(pending).resolves.toEqual({ left: 0, top: 700, behavior: 'instant' });
    expect(dom.listeners.size).toBe(0); expect(dom.disconnect).toHaveBeenCalledOnce();
  });
  it('cancels delayed history scrolling as soon as the user interacts', async () => {
    vi.useFakeTimers(); const dom = browser(1000);
    const pending = navigationScroll(route('/plans'), route('/learn'), { left: 0, top: 700 });
    dom.listeners.get('pointerdown')!();
    await expect(pending).resolves.toBe(false);
    dom.root.scrollHeight = 4000; dom.resize();
    expect(dom.scrollTo).not.toHaveBeenCalled();
    expect(vi.getTimerCount()).toBe(0); expect(dom.listeners.size).toBe(0);
  });
  it('does not let an old restoration scroll over a newer navigation', async () => {
    vi.useFakeTimers(); const dom = browser(1000);
    const pending = navigationScroll(route('/plans'), route('/learn'), { left: 0, top: 700 });
    expect(navigationScroll(route('/settings'), route('/plans'), null)).toEqual({ left: 0, top: 0, behavior: 'instant' });
    await expect(pending).resolves.toBe(false);
    expect(dom.listeners.size).toBe(0); expect(vi.getTimerCount()).toBe(0);
  });
  it('corrects late layout shifts even when the initial form was tall enough', async () => {
    vi.useFakeTimers(); const dom = browser(2000);
    const pending = navigationScroll(route('/plans'), route('/learn'), { left: 0, top: 700 });
    expect(dom.scrollTo).toHaveBeenCalledTimes(1);
    dom.root.scrollHeight = 4000; dom.resize();
    expect(dom.scrollTo).toHaveBeenCalledTimes(2);
    await vi.advanceTimersByTimeAsync(2000); await pending;
    expect(dom.disconnect).toHaveBeenCalledOnce();
  });
});

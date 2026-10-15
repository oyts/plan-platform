import type { RouterScrollBehavior } from 'vue-router';

export type ScrollPoint = { left: number; top: number };
let cancelPending: (() => void) | undefined;
const direct = (point: ScrollPoint) => ({ ...point, behavior: 'instant' as const });

function restoreAfterLayout(point: ScrollPoint) {
  const hasRoom = () => document.documentElement.scrollHeight >= point.top + window.innerHeight;
  if (point.top === 0 || typeof ResizeObserver === 'undefined') return direct(point);
  // Lists are loaded asynchronously after route mounting. Returning immediately
  // would clamp the saved offset against the temporary empty page.
  return new Promise<ReturnType<typeof direct> | false>(resolve => {
    let finished = false;
    const finish = (restore: boolean) => {
      if (finished) return;
      finished = true;
      observer.disconnect();
      clearTimeout(timer);
      for (const event of interactions) window.removeEventListener(event, cancel, true);
      if (cancelPending === cancel) cancelPending = undefined;
      resolve(restore ? direct(point) : false);
    };
    const cancel = () => finish(false);
    const interactions = ['wheel', 'touchstart', 'pointerdown', 'keydown'];
    // Even when the empty form already has enough height, inserting list rows
    // above it may trigger browser scroll anchoring. Reapply during this short
    // layout window instead of treating the first sufficient height as final.
    const observer = new ResizeObserver(() => { if (!finished && hasRoom()) window.scrollTo(direct(point)); });
    // Bounded window; never keep adjusting after user intent or a newer route.
    const timer = setTimeout(() => finish(true), 2000);
    cancelPending = cancel;
    for (const event of interactions) window.addEventListener(event, cancel, { capture: true, passive: true });
    observer.observe(document.body);
    if (hasRoom()) window.scrollTo(direct(point));
  });
}

export function navigationPosition(toPath: string, fromPath: string, saved: ScrollPoint | null): ScrollPoint | false {
  if (saved) return { left: saved.left, top: saved.top };
  // Changing a filter or selected route is not entering a new page.
  if (toPath === fromPath) return false;
  return { left: 0, top: 0 };
}

export const navigationScroll: RouterScrollBehavior = (to, from, saved) => {
  cancelPending?.();
  const position = navigationPosition(to.path, from.path, saved);
  if (!position) return false;
  if (saved) return restoreAfterLayout(position);
  // Focus the stable content landmark after Vue Router has rendered the route.
  // preventScroll avoids a second, browser-dependent jump under the sticky header.
  document.querySelector<HTMLElement>('main')?.focus({ preventScroll: true });
  return direct(position);
};

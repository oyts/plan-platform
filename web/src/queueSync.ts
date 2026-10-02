import axios from 'axios';
import { apiMessage, currentQueueOwner, replayOfflineOperation } from '@/api/client';
import { deleteOperation, listOperations, updateOperation } from '@/offlineQueue';

export interface QueueSyncResult {
  synced: number;
  stopped?: 'BUSY' | 'OFFLINE' | 'IDENTITY' | 'CONFLICT' | 'FAILED';
  message?: string;
}
const activeOwners = new Set<string>();

export async function synchronizeQueue(ownerId: string, limit = Infinity): Promise<QueueSyncResult> {
  if (activeOwners.has(ownerId)) return { synced: 0, stopped: 'BUSY' };
  activeOwners.add(ownerId);
  try {
    // Web Locks coordinates multiple windows where supported; idempotency remains the server safeguard.
    if (navigator.locks) {
      return await navigator.locks.request(`plan-platform:sync:${ownerId}`, { ifAvailable: true },
        (lock) => lock ? run(ownerId, limit) : Promise.resolve({ synced: 0, stopped: 'BUSY' as const }));
    }
    return await run(ownerId, limit);
  } finally { activeOwners.delete(ownerId); }
}

async function run(ownerId: string, limit: number): Promise<QueueSyncResult> {
  let synced = 0;
  for (const row of await listOperations(ownerId)) {
    if (synced >= limit) break;
    if (!navigator.onLine) return { synced, stopped: 'OFFLINE' };
    if (currentQueueOwner() !== ownerId) return { synced, stopped: 'IDENTITY' };
    if (row.status === 'CONFLICT') return { synced, stopped: 'CONFLICT', message: row.error };
    await updateOperation(row.id, { attempts: row.attempts + 1, status: 'PENDING', error: undefined });
    try {
      await replayOfflineOperation(row);
      await deleteOperation(row.id);
      synced++;
    } catch (failure) {
      const conflict = axios.isAxiosError(failure) && failure.response?.status === 409;
      const message = apiMessage(failure);
      await updateOperation(row.id, { status: conflict ? 'CONFLICT' : 'FAILED', error: message });
      return { synced, stopped: conflict ? 'CONFLICT' : 'FAILED', message };
    }
  }
  return { synced };
}

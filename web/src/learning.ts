import { reactive } from 'vue';
import { currentQueueOwner, serverScope } from '@/api/client';
import { freshProgress, freshPractice, learningKey, readProgress, stepReady, type LearningProgress, type LessonId, type Practice } from './learningModel';
import { loadLearning, saveLearning } from './learningStorage';
export const learning = reactive({ progress: freshProgress(), ready: false, storageError: false });
let key = ''; let generation = 0; let loading: Promise<void> | undefined;
export function bindLearning(): Promise<void> {
  const owner = currentQueueOwner(); const next = owner ? learningKey(serverScope(), owner) : '';
  if (next !== key) { key = next; generation++; loading = undefined; learning.progress = freshProgress(); learning.ready = false; learning.storageError = false; }
  if (!key) return Promise.resolve();
  if (!loading) {
    const target = key; const version = generation;
    loading = loadLearning(target).then(value => { if (key === target && generation === version) learning.progress = readProgress(value); })
      .catch(() => { if (key === target && generation === version) learning.storageError = true; })
      .finally(() => { if (key === target && generation === version) learning.ready = true; });
  }
  return loading;
}
export function resetLearning(): void { key = ''; generation++; loading = undefined; learning.progress = freshProgress(); learning.ready = false; learning.storageError = false; }
function copyProgress(): LearningProgress { return JSON.parse(JSON.stringify(learning.progress)); }
async function persist(next: LearningProgress): Promise<void> {
  const target = key; const version = generation;
  if (!target) return;
  const value = JSON.stringify({ version: 1, progress: next });
  try { await saveLearning(target, value); if (key === target && generation === version) learning.storageError = false; }
  catch { if (key === target && generation === version) learning.storageError = true; }
  // Display the new step/completion/dismissal only after the durable write settles.
  if (key === target && generation === version) learning.progress = next;
}
async function ownerReady(): Promise<boolean> {
  const owner = currentQueueOwner(); const target = owner ? learningKey(serverScope(), owner) : '';
  await bindLearning();
  return !!target && key === target && currentQueueOwner() === owner && learningKey(serverScope(), owner!) === target;
}
export async function dismissLesson(id: LessonId): Promise<void> { if (!await ownerReady()) return; const next = copyProgress(); next[id].dismissed = true; await persist(next); }
export async function practiceLesson(id: LessonId, draft: Practice): Promise<void> { if (!await ownerReady()) return; const next = copyProgress(); next[id].practice = structuredClone(draft); await persist(next); }
export async function advanceLesson(id: LessonId): Promise<boolean> {
  if (!await ownerReady()) return false; const next = copyProgress(); const progress = next[id];
  if (!stepReady(id, progress.step, progress.practice)) return false;
  if (progress.step < 3) progress.step++; else progress.completed = true;
  await persist(next); return true;
}
export async function previousLesson(id: LessonId): Promise<void> { if (!await ownerReady()) return; const next = copyProgress(); const p = next[id]; p.step = Math.max(0, p.step - 1); p.completed = false; await persist(next); }
export async function restartLesson(id: LessonId): Promise<void> { if (!await ownerReady()) return; const next = copyProgress(); next[id] = { step: 0, completed: false, dismissed: true, practice: freshPractice() }; await persist(next); }

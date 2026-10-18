export interface User { id: string; email: string; displayName: string; role: string; emailVerified: boolean }
export interface TokenResponse { accessToken: string; expiresInSeconds: number; user: User }
export interface SessionInfo { id: string; createdAt: string; expiresAt: string; current: boolean }
export interface LocalRecord { localState?: 'PENDING' | 'CONFLICT' | 'FAILED'; localOnly?: boolean }
export interface Todo extends LocalRecord { id: string; title: string; note: string; dueDate: string; done: boolean; deleted: boolean; version: number; updatedAt: string }
export interface Plan extends LocalRecord { id: string; name: string; description: string; startDate: string; endDate: string; restDays?: number[]; archived: boolean; version: number; taskCount: number; completedCount: number; updatedAt: string }
export interface Tag extends LocalRecord { id: string; name: string; color: string; overall: boolean; version: number }
export interface Progress { status: string; accumulatedSeconds: number; startedAt: string | null; completedAt: string | null; acceptanceChecks: boolean[]; note: string; deferTo: string | null; skipReason: string; version: number }
export interface Task extends LocalRecord { id: string; planId: string; tagId: string | null; tagName: string | null; tagColor: string | null; date: string; startTime: string | null; endTime: string | null; plannedMinutes: number; category: string; title: string; description: string; acceptance: string[]; resourceUrl: string; required: boolean; sortOrder: number; recurrenceId: string | null; templateId: string | null; version: number; progress: Progress }
export interface PageResponse<T> { content: T[]; page: number; size: number; totalElements: number; totalPages: number }
export interface Stats { from: string; to: string; taskCount: number; completedCount: number; plannedMinutes: number; actualSeconds: number; tags: TagStat[] }
export interface TagStat { tagId: string; tagName: string; color: string | null; taskCount: number; completedCount: number; plannedMinutes: number; actualSeconds: number }
export interface DailyStat { date: string; taskCount: number; completedCount: number; partialCount: number; skippedCount: number; deferredCount: number; plannedMinutes: number; actualSeconds: number }
export interface Milestone { id: string; planId: string; name: string; date: string; description: string; acceptance: string[]; checks: boolean[]; note: string; reviewedAt: string | null; version: number }
export interface TaskTemplate { id: string; name: string; category: string; title: string; description: string; plannedMinutes: number; acceptance: string[]; resourceUrl: string; required: boolean; version: number }
export interface Reflection { id: string; date: string; completedSummary: string; unfinishedSummary: string; blockers: string; firstTaskTomorrow: string; energy: number | null; note: string; version: number }
export interface TimeEntry { id: string; planId: string; taskId: string; taskTitle: string; tagName: string; tagColor: string | null; startedAt: string; endedAt: string; seconds: number; source: string }
export interface TrashItem { type: 'TASK' | 'MILESTONE' | 'TEMPLATE' | 'PLAN' | 'TAG' | 'MAINLINE' | 'MAINLINE_ITEM'; id: string; title: string; detail: string; deletedAt: string }
export interface Today { date: string; tasks: Task[]; total: number; completed: number; plannedMinutes: number; actualSeconds: number }
export interface MainlineStage { id: string; name: string; description: string; sortOrder: number; version: number }
export interface MainlineItem extends LocalRecord { id: string; title: string; description: string; acceptance: string[]; plannedMinutes: number; sortOrder: number; status: string; unlocked: boolean; accumulatedSeconds: number; note: string; startedAt: string | null; completedAt: string | null; version: number; stageId?: string | null; dependencyMode?: 'INHERIT' | 'NONE' | 'EXPLICIT'; prerequisiteIds?: string[]; blockers?: string[] }
export interface Mainline extends LocalRecord { id: string; name: string; description: string; color: string; version: number; completed: number; total: number; items: MainlineItem[]; executionMode?: 'SEQUENTIAL' | 'FLEXIBLE'; stages?: MainlineStage[]; nextSortOrder?: number; paused?: boolean }
export interface RecurrenceBatch { recurrenceId: string; title: string; category: string; firstDate: string; lastDate: string; taskCount: number; completedCount: number; openCount: number }
export interface Invitation { id: string; email: string; expiresAt: string; acceptedAt: string | null; revokedAt: string | null; createdBy: string; registrationUrl: string | null }

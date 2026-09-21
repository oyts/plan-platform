export interface User { id: string; email: string; displayName: string; role: string }
export interface TokenResponse { accessToken: string; expiresInSeconds: number; user: User }
export interface SessionInfo { id: string; createdAt: string; expiresAt: string; current: boolean }
export interface Plan { id: string; name: string; description: string; startDate: string; endDate: string; archived: boolean; version: number; taskCount: number; completedCount: number; updatedAt: string }
export interface Tag { id: string; name: string; color: string; overall: boolean; version: number }
export interface Progress { status: string; accumulatedSeconds: number; startedAt: string | null; completedAt: string | null; acceptanceChecks: boolean[]; note: string; deferTo: string | null; skipReason: string; version: number }
export interface Task { id: string; planId: string; tagId: string | null; tagName: string | null; tagColor: string | null; date: string; startTime: string | null; endTime: string | null; plannedMinutes: number; category: string; title: string; description: string; acceptance: string[]; resourceUrl: string; required: boolean; sortOrder: number; recurrenceId: string | null; templateId: string | null; version: number; progress: Progress }
export interface PageResponse<T> { content: T[]; page: number; size: number; totalElements: number; totalPages: number }
export interface Stats { from: string; to: string; taskCount: number; completedCount: number; plannedMinutes: number; actualSeconds: number; tags: TagStat[] }
export interface TagStat { tagId: string; tagName: string; color: string | null; taskCount: number; completedCount: number; plannedMinutes: number; actualSeconds: number }
export interface DailyStat { date: string; taskCount: number; completedCount: number; partialCount: number; skippedCount: number; deferredCount: number; plannedMinutes: number; actualSeconds: number }
export interface Milestone { id: string; planId: string; name: string; date: string; description: string; acceptance: string[]; checks: boolean[]; note: string; reviewedAt: string | null; version: number }
export interface TaskTemplate { id: string; name: string; category: string; title: string; description: string; plannedMinutes: number; acceptance: string[]; resourceUrl: string; required: boolean; version: number }
export interface Reflection { id: string; date: string; completedSummary: string; unfinishedSummary: string; blockers: string; firstTaskTomorrow: string; energy: number | null; note: string; version: number }
export interface TimeEntry { id: string; planId: string; taskId: string; taskTitle: string; tagName: string; tagColor: string | null; startedAt: string; endedAt: string; seconds: number; source: string }
export interface TrashItem { type: 'TASK' | 'MILESTONE' | 'TEMPLATE' | 'PLAN' | 'TAG'; id: string; title: string; detail: string; deletedAt: string }
export interface Today { date: string; tasks: Task[]; total: number; completed: number; plannedMinutes: number; actualSeconds: number }
export interface MainlineItem { id: string; title: string; description: string; acceptance: string[]; plannedMinutes: number; sortOrder: number; status: string; unlocked: boolean; accumulatedSeconds: number; note: string; startedAt: string | null; completedAt: string | null; version: number }
export interface Mainline { id: string; name: string; description: string; color: string; version: number; completed: number; total: number; items: MainlineItem[] }

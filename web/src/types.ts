export interface User { id: string; email: string; displayName: string; role: string }
export interface TokenResponse { accessToken: string; refreshToken: string; expiresInSeconds: number; user: User }
export interface Plan { id: string; name: string; description: string; startDate: string; endDate: string; archived: boolean; version: number; taskCount: number; completedCount: number; updatedAt: string }
export interface Tag { id: string; name: string; color: string; overall: boolean; version: number }
export interface Progress { status: string; accumulatedSeconds: number; startedAt: string | null; completedAt: string | null; acceptanceChecks: boolean[]; note: string; deferTo: string | null; skipReason: string; version: number }
export interface Task { id: string; planId: string; tagId: string | null; tagName: string | null; tagColor: string | null; date: string; startTime: string | null; endTime: string | null; plannedMinutes: number; category: string; title: string; description: string; acceptance: string[]; resourceUrl: string; required: boolean; sortOrder: number; version: number; progress: Progress }
export interface Today { date: string; tasks: Task[]; total: number; completed: number; plannedMinutes: number; actualSeconds: number }
export interface MainlineItem { id: string; title: string; description: string; acceptance: string[]; plannedMinutes: number; sortOrder: number; status: string; unlocked: boolean; accumulatedSeconds: number; note: string; startedAt: string | null; completedAt: string | null; version: number }
export interface Mainline { id: string; name: string; description: string; color: string; version: number; completed: number; total: number; items: MainlineItem[] }

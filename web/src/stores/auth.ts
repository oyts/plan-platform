import { defineStore } from 'pinia';
import { ref } from 'vue';
import axios from 'axios';
import { api, clearTokens, currentQueueOwner, restoreOfflineIdentity, saveTokens, serverScope } from '@/api/client';
import { cachedIdentity, forgetScope, isSignedOut, rememberIdentity, signOutScope } from '@/offlineCache';
import { markDisconnected, requiresConnection } from '@/connectionState';
import type { TokenResponse, User } from '@/types';
import { clearNativeSession } from '@/desktop';
import { validateServerConfig, serverConnectionIssue } from '@/serverCompatibility';
export const useAuthStore = defineStore('auth', () => {
  const user = ref<User | null>(null); const ready = ref(false); const registrationEnabled = ref(false); const passwordResetEnabled = ref(false); const invitationEnabled = ref(false); const emailVerificationEnabled = ref(false);
  let sessionAttempt = 0;
  const planRestDaysEnabled = ref(false);
  const connectionError = ref('');
  async function accept(data: TokenResponse) { saveTokens(data); user.value = data.user; await rememberIdentity(serverScope(), currentQueueOwner()!, data.user); }
  async function bootstrap() {
    const attempt = ++sessionAttempt; const scope = serverScope();
    connectionError.value = '';
    try {
      const config = validateServerConfig((await api.get('/auth/config', { timeout: 3000 })).data);
      if (attempt !== sessionAttempt || scope !== serverScope()) return;
      registrationEnabled.value = config.registrationEnabled; passwordResetEnabled.value = config.passwordResetEnabled;
      invitationEnabled.value = config.invitationEnabled; emailVerificationEnabled.value = config.emailVerificationEnabled;
      planRestDaysEnabled.value = config.planRestDaysEnabled === true;
    } catch (error) {
      if (attempt !== sessionAttempt || scope !== serverScope()) return;
      connectionError.value = serverConnectionIssue(error);
      registrationEnabled.value = false; passwordResetEnabled.value = false; invitationEnabled.value = false;
      emailVerificationEnabled.value = false; planRestDaysEnabled.value = false;
    }
    if (await isSignedOut(scope)) { clearTokens(); user.value = null; ready.value = true; return; }
    try {
      const { data } = await api.post<TokenResponse>('/auth/refresh', null, { timeout: 3000 });
      if (attempt !== sessionAttempt || scope !== serverScope()) return;
      await accept(data);
    } catch (failure) {
      if (attempt !== sessionAttempt || scope !== serverScope()) return;
      const identity = axios.isAxiosError(failure) && !failure.response ? await cachedIdentity(scope) : null;
      if (attempt !== sessionAttempt) return;
      if (identity) { restoreOfflineIdentity(identity.user); user.value = identity.user; markDisconnected(); }
      else { clearTokens(); user.value = null; await forgetScope(scope).catch(() => undefined); }
    }
    ready.value = true;
  }
  async function login(email: string, password: string) { const attempt = ++sessionAttempt; const { data } = await api.post<TokenResponse>('/auth/login', { email, password }); if (attempt === sessionAttempt) await accept(data); }
  async function register(email: string, password: string, displayName: string, invitationToken?: string) { const attempt = ++sessionAttempt; const { data } = await api.post<TokenResponse>('/auth/register', { email, password, displayName, invitationToken: invitationToken || null }); if (attempt === sessionAttempt) await accept(data); }
  async function forgetSession() {
    sessionAttempt++; const scope = serverScope(); clearTokens();
    try { await signOutScope(scope); } finally { try { await clearNativeSession(); } finally { user.value = null; } }
  }
  async function logout() { try { if (!requiresConnection()) await api.post('/auth/logout'); } finally { await forgetSession(); } }
  async function logoutAll() { try { if (requiresConnection()) throw new Error('当前设备已退出；全设备会话撤销需要联网后重新执行。'); await api.post('/auth/logout-all'); } finally { await forgetSession(); } }
  function updateProfile(profile: User) { user.value = profile; }
  return { user, ready, connectionError, registrationEnabled, passwordResetEnabled, invitationEnabled, emailVerificationEnabled, planRestDaysEnabled, bootstrap, login, register, logout, logoutAll, updateProfile };
});

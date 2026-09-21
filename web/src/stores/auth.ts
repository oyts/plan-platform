import { defineStore } from 'pinia';
import { ref } from 'vue';
import { api, clearTokens, saveTokens } from '@/api/client';
import type { TokenResponse, User } from '@/types';
export const useAuthStore = defineStore('auth', () => {
  const user = ref<User | null>(null); const ready = ref(false); const registrationEnabled = ref(false); const passwordResetEnabled = ref(false); const invitationEnabled = ref(false); const emailVerificationEnabled = ref(false);
  async function bootstrap() {
    try { const config = (await api.get<{registrationEnabled:boolean;passwordResetEnabled:boolean;invitationEnabled:boolean;emailVerificationEnabled:boolean}>('/auth/config')).data; registrationEnabled.value = config.registrationEnabled; passwordResetEnabled.value = config.passwordResetEnabled; invitationEnabled.value = config.invitationEnabled; emailVerificationEnabled.value = config.emailVerificationEnabled; } catch { registrationEnabled.value = false; passwordResetEnabled.value = false; invitationEnabled.value = false; emailVerificationEnabled.value = false; }
    try { const { data } = await api.post<TokenResponse>('/auth/refresh'); saveTokens(data); user.value = data.user; } catch { clearTokens(); user.value = null; }
    ready.value = true;
  }
  async function login(email: string, password: string) { const { data } = await api.post<TokenResponse>('/auth/login', { email, password }); saveTokens(data); user.value = data.user; }
  async function register(email: string, password: string, displayName: string, invitationToken?: string) { const { data } = await api.post<TokenResponse>('/auth/register', { email, password, displayName, invitationToken: invitationToken || null }); saveTokens(data); user.value = data.user; }
  async function logout() { try { await api.post('/auth/logout'); } finally { clearTokens(); user.value = null; } }
  async function logoutAll() { try { await api.post('/auth/logout-all'); } finally { clearTokens(); user.value = null; } }
  function updateProfile(profile: User) { user.value = profile; }
  return { user, ready, registrationEnabled, passwordResetEnabled, invitationEnabled, emailVerificationEnabled, bootstrap, login, register, logout, logoutAll, updateProfile };
});

import { defineStore } from 'pinia';
import { ref } from 'vue';
import { api, clearTokens, getRefreshToken, hasToken, saveTokens } from '@/api/client';
import type { TokenResponse, User } from '@/types';
export const useAuthStore = defineStore('auth', () => {
  const user = ref<User | null>(null); const ready = ref(false);
  async function bootstrap() { if (hasToken()) { try { user.value = (await api.get<User>('/auth/me')).data; } catch { clearTokens(); } } ready.value = true; }
  async function login(email: string, password: string) { const { data } = await api.post<TokenResponse>('/auth/login', { email, password }); saveTokens(data); user.value = data.user; }
  async function register(email: string, password: string, displayName: string) { const { data } = await api.post<TokenResponse>('/auth/register', { email, password, displayName }); saveTokens(data); user.value = data.user; }
  async function logout() { const refreshToken = getRefreshToken(); try { if (refreshToken) await api.post('/auth/logout', { refreshToken }); } finally { clearTokens(); user.value = null; } }
  return { user, ready, bootstrap, login, register, logout };
});

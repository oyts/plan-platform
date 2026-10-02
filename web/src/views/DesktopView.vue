<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { useAuthStore } from '@/stores/auth';
import { forgetScope } from '@/offlineCache';
import { serverScope } from '@/api/client';
import { checkDesktopUpdate, getDesktopInfo, installDesktopUpdate, isNativeClient as isDesktop, isAndroid, probeDesktopServer, saveDesktopServer, type DesktopInfo, type DesktopUpdate } from '@/desktop';
const auth = useAuthStore();
const info = ref<DesktopInfo | null>(null);
const serverUrl = ref('');
const busy = ref(false);
const message = ref('');
const error = ref('');
const update = ref<DesktopUpdate | null>(null);
function explain(reason: unknown): string { return reason instanceof Error ? reason.message : String(reason); }
async function probe(): Promise<void> {
  busy.value = true; error.value = ''; message.value = '';
  try { message.value = await probeDesktopServer(serverUrl.value); } catch (reason) { error.value = explain(reason); }
  finally { busy.value = false; }
}
async function save(): Promise<void> {
  busy.value = true; error.value = ''; message.value = '';
  try {
    await probeDesktopServer(serverUrl.value);
    await forgetScope(serverScope());
    await forgetScope(serverUrl.value.trim().replace(/\/+$/, ''));
    await saveDesktopServer(serverUrl.value);
    window.location.hash = '/login'; window.location.reload();
  } catch (reason) { error.value = explain(reason); }
  finally { busy.value = false; }
}
async function checkUpdate(): Promise<void> {
  busy.value = true; error.value = ''; message.value = '';
  try { update.value = await checkDesktopUpdate(); if (!update.value) message.value = '当前已是最新版本。'; }
  catch (reason) { error.value = explain(reason); }
  finally { busy.value = false; }
}
async function installUpdate(): Promise<void> {
  if (!window.confirm('更新将关闭并重新启动客户端，请先保存正在编辑的内容。继续吗？')) return;
  busy.value = true; error.value = ''; message.value = '正在下载并校验更新包，安装时客户端会自动关闭。';
  try { await installDesktopUpdate(); } catch (reason) { error.value = explain(reason); message.value = ''; }
  finally { busy.value = false; }
}
onMounted(async () => {
  if (!isDesktop()) return;
  try { info.value = await getDesktopInfo(); serverUrl.value = info.value.serverUrl; } catch (reason) { error.value = explain(reason); }
});
</script>
<template>
  <div class="page desktop-page">
    <header class="page-head"><div><h1>{{ isAndroid() ? '手机客户端' : '桌面客户端' }}</h1><p>连接自己的计划舱服务器，管理安装版本。</p></div><router-link class="btn" :to="auth.user ? '/settings' : '/login'">{{ auth.user ? '返回设置' : '返回登录' }}</router-link></header>
    <p v-if="!isDesktop()" class="error-text">此页面仅在已安装的客户端中使用。</p>
    <template v-else>
      <p v-if="error" class="error-text" role="alert">{{ error }}</p><p v-if="message" class="success-text" role="status">{{ message }}</p>
      <div class="desktop-grid">
        <form class="desktop-panel" @submit.prevent="save">
          <header><span class="panel-number">01</span><h2>服务器连接</h2></header>
          <label>服务器根地址<input v-model="serverUrl" type="url" placeholder="https://plan.example.cn" required :disabled="busy" /></label>
          <p class="helper-text">云端使用 HTTPS；请勿填写 /api 或登录页面路径。{{ isAndroid() ? (info?.debug ? '测试包可通过 USB 端口转发连接 http://127.0.0.1:43129。' : '正式包仅支持 HTTPS。') : '本机开发可填写 http://127.0.0.1:8080。' }}</p>
          <div class="button-row"><button class="btn" type="button" :disabled="busy" @click="probe">测试连接</button><button class="btn primary" :disabled="busy">保存并重新连接</button></div>
          <p class="helper-text">保存前会检查新版账号接口。切换服务器会重新登录，离线队列按服务器与账号分别保存。</p>
        </form>
        <section class="desktop-panel">
          <header><span class="panel-number">02</span><h2>客户端更新</h2></header>
          <dl class="desktop-facts"><div><dt>安装版本</dt><dd>{{ info?.version ?? '正在读取' }}</dd></div><div><dt>更新状态</dt><dd>{{ info?.updatesEnabled ? '已启用签名更新' : '使用新安装包手动升级' }}</dd></div></dl>
          <button class="btn" type="button" :disabled="busy || !info?.updatesEnabled" @click="checkUpdate">检查新版本</button>
          <p v-if="!info?.updatesEnabled" class="helper-text">{{ isAndroid() ? '使用相同签名且版本号更高的 APK 覆盖安装，可保留服务器配置与登录会话。当前不申请静默安装权限。' : '当前安装包未配置发布源。后续发布版本可启用签名自动更新。' }}服务端业务数据不会保存在安装目录中。</p>
          <div v-if="update" class="desktop-update"><h3>可更新至 {{ update.version }}</h3><p v-if="update.notes">{{ update.notes }}</p><button class="btn primary" :disabled="busy" @click="installUpdate">下载并安装</button></div>
        </section>
      </div>
      <section class="desktop-note"><strong>{{ isAndroid() ? '登录会话由 Android Keystore 加密保护' : '登录会话保存在 Windows 凭据管理器中' }}</strong><p>核心页面另有按服务器和账号隔离的本地缓存，并非加密存储。最近联网验证后的 24 小时内可离线重开；退出会清除会话与页面缓存，但保留该账号未同步的操作。请仅在自己的设备使用。</p></section>
    </template>
  </div>
</template>

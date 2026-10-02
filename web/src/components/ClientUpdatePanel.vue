<script setup lang="ts">
import { clientUpdates as state, checkClientUpdate, downloadClientUpdate, installClientUpdate, setAutomaticDownload, allowAndroidUpdateInstall } from '@/clientUpdates';
import { isAndroid } from '@/desktop';
</script>
<template>
  <section class="desktop-panel" aria-label="客户端更新">
    <header><span class="panel-number">02</span><h2>客户端更新</h2></header>
    <dl class="desktop-facts"><div><dt>安装版本</dt><dd>{{ state.info?.version ?? '正在读取' }}{{ state.info?.versionCode ? ` / 构建 ${state.info.versionCode}` : '' }}</dd></div><div><dt>更新状态</dt><dd>{{ state.info?.updatesEnabled ? '已绑定可信更新源' : '尚未配置发布源' }}</dd></div></dl>
    <label class="update-consent"><input type="checkbox" :checked="state.autoDownload" :disabled="!state.info?.updatesEnabled" @change="setAutomaticDownload(($event.target as HTMLInputElement).checked)" />自动下载更新（安装前仍需确认）</label>
    <p class="helper-text">默认关闭，只有你主动勾选才启用。下载会使用网络流量；不会自动关闭应用或静默安装。</p>
    <button class="btn" type="button" :disabled="!state.info?.updatesEnabled || state.checking || state.downloading || state.installing || state.ready" @click="checkClientUpdate">{{ state.checking ? '正在检查…' : '检查新版本' }}</button>
    <p v-if="!state.info?.updatesEnabled" class="helper-text">当前测试包没有绑定发布源，仍需同签名覆盖安装。维护者配置可信 HTTPS 更新源后，可在应用内检测、下载与安装更新。</p>
    <p v-if="state.error" class="error-text" role="alert">{{ state.error }}</p><p v-if="state.message" class="helper-text" role="status">{{ state.message }}</p>
    <div v-if="state.update" class="desktop-update">
      <h3>{{ state.update.mandatory ? '必须更新至' : '可更新至' }} {{ state.update.version }}</h3>
      <p v-if="state.update.notes">{{ state.update.notes }}</p>
      <p v-if="state.update.mandatory" class="helper-text">低于最低支持版本，更新前暂停业务修改；数据和待同步队列不会清空。</p>
      <div class="button-row"><button v-if="!state.ready" class="btn primary" :disabled="state.downloading || state.checking" @click="downloadClientUpdate">{{ state.downloading ? '下载与校验中…' : '下载更新' }}</button><button v-else class="btn primary" :disabled="state.installing" @click="installClientUpdate">{{ state.installing ? '正在请求安装…' : '安装并重新打开' }}</button><button v-if="isAndroid() && state.ready" class="btn" type="button" @click="allowAndroidUpdateInstall">允许安装更新</button></div>
      <p v-if="isAndroid()" class="helper-text">Android 安装需要系统授权与确认。取消不会卸载应用；返回后可再次安装。</p>
    </div>
  </section>
</template>
<style scoped>.update-consent{display:flex!important;align-items:center;gap:10px!important}.update-consent input{width:18px;height:18px;accent-color:#002fa7;flex-shrink:0}</style>

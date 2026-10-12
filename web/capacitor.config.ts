import type { CapacitorConfig } from '@capacitor/cli';
const config: CapacitorConfig = {
  appId: process.env.PLAN_PUBLIC_RELEASE === '1' ? 'cn.zijun701.plan.app' : 'cn.zijun701.plan.mobile', appName: '计划舱', webDir: 'dist-android',
  android: { allowMixedContent: false },
  // Keep the UI bundled locally. API requests go through the restricted Java bridge.
  plugins: { CapacitorHttp: { enabled: false }, CapacitorCookies: { enabled: false }, SystemBars: { insetsHandling: 'native', style: 'LIGHT' } },
};
export default config;

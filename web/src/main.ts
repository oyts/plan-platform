import { createApp } from 'vue';
import { createPinia } from 'pinia';
import App from './App.vue';
import router from './router';
import { useAuthStore } from '@/stores/auth';
import './style.css';
const app = createApp(App);
const pinia = createPinia();
app.use(pinia);
async function bootstrap(): Promise<void> {
  await useAuthStore(pinia).bootstrap();
  app.use(router);
  await router.isReady();
  app.mount('#app');
}
void bootstrap();

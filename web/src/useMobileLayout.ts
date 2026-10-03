import { onUnmounted, ref } from 'vue';
export function useMobileLayout() {
  const media = window.matchMedia('(max-width: 700px), (max-height: 500px) and (pointer: coarse)');
  const mobile = ref(media.matches);
  const update = () => { mobile.value = media.matches; };
  media.addEventListener('change', update);
  onUnmounted(() => media.removeEventListener('change', update));
  return mobile;
}

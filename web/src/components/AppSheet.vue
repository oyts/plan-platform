<script setup lang="ts">
import { onBeforeUnmount, ref, watch } from 'vue';
import AppIcon from './AppIcon.vue';
const props = defineProps<{ open: boolean; title: string; titleId: string; busy?: boolean }>();
const emit = defineEmits<{ close: [] }>();
const dialog = ref<HTMLDialogElement>();
let oldOverflow: string | undefined;
function unlock() { if (oldOverflow !== undefined) { document.body.style.overflow = oldOverflow; oldOverflow = undefined; } }
function requestClose(event?: Event) { event?.preventDefault(); if (!props.busy) emit('close'); }
watch([() => props.open, dialog], ([open]) => {
  if (open && dialog.value && !dialog.value.open) {
    oldOverflow = document.body.style.overflow;
    document.body.style.overflow = 'hidden';
    dialog.value.showModal();
  } else if (!open) { dialog.value?.close(); unlock(); }
}, { flush: 'post', immediate: true });
onBeforeUnmount(() => { dialog.value?.close(); unlock(); });
</script>
<template>
  <dialog ref="dialog" class="app-sheet" :aria-labelledby="titleId" @cancel="requestClose" @close="unlock" @click="event => { if (event.target === dialog) requestClose(); }">
    <header class="sheet-header"><h2 :id="titleId">{{ title }}</h2><button type="button" class="sheet-close" :disabled="busy" aria-label="关闭面板" @click="requestClose()"><AppIcon name="close"/></button></header>
    <div class="sheet-content"><slot /></div>
  </dialog>
</template>

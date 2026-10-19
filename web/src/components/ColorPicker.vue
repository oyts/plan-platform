<script setup lang="ts">
import { ref, watch, useId } from 'vue';
import { commonColors, normalizeHex, hexToRgb, rgbToHex } from '@/colors';
const props = withDefaults(defineProps<{ modelValue: string; label?: string; disabled?: boolean }>(), { label: '颜色' });
const emit = defineEmits<{ 'update:modelValue': [string] }>();
const id = useId(), hex = ref(''), rgb = ref<(string | number)[]>([]), error = ref('');
watch(() => props.modelValue, value => { hex.value = value; rgb.value = hexToRgb(value) ?? ['', '', '']; }, { immediate: true });
function choose(value: string) { const next = normalizeHex(value); if (!next) { error.value = 'HEX 请填写 3 或 6 位十六进制颜色。'; return; } error.value = ''; hex.value = next; rgb.value = hexToRgb(next)!; emit('update:modelValue', next); }
function changeRgb() { const next = rgbToHex(rgb.value); if (!next) { error.value = 'R、G、B 均须为 0–255 的整数；原颜色未更改。'; return; } choose(next); }
</script>
<template>
  <fieldset class="color-picker" :disabled="disabled"><legend>{{ label }}</legend>
    <div class="color-main"><input type="color" :value="normalizeHex(modelValue) ?? '#002FA7'" :aria-label="`${label}选择器`" @input="choose(($event.target as HTMLInputElement).value)"/><label :for="id">HEX<input :id="id" v-model="hex" maxlength="7" :aria-invalid="!!error" placeholder="#002FA7" @change="choose(hex)"/></label></div>
    <div class="color-rgb"><label v-for="(channel, i) in ['R', 'G', 'B']" :key="channel">{{ channel }}<input v-model="rgb[i]" type="number" min="0" max="255" step="1" inputmode="numeric" :aria-label="`${label} ${channel}`" @input="changeRgb"/></label></div>
    <div class="color-swatches" aria-label="常用颜色"><button v-for="color in commonColors" :key="color" type="button" :style="{ backgroundColor: color }" :aria-label="`使用 ${color}`" :aria-pressed="normalizeHex(modelValue) === color" @click="choose(color)"/></div>
    <p v-if="error" class="error-text" role="alert">{{ error }}</p>
  </fieldset>
</template>
<style scoped>
.color-picker{padding:0;border:0;min-width:0;display:grid;gap:12px}.color-picker legend{font-size:.8rem;font-weight:700;margin-bottom:8px}.color-main{display:flex;gap:12px;align-items:end}.color-main>input{width:56px;min-height:48px;padding:4px;flex-shrink:0}.color-picker label{display:grid;gap:6px;font-size:.75rem;min-width:0}.color-main label{flex:1}.color-rgb{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:8px}.color-rgb input{width:100%;min-width:0;padding-inline:4px;font-variant-numeric:tabular-nums}.color-swatches{display:flex;flex-wrap:wrap;gap:8px}.color-swatches button{width:36px;min-height:36px;border:3px solid white;outline:1px solid var(--line);cursor:pointer}.color-swatches button[aria-pressed=true]{outline:2px solid currentColor}.color-picker p{margin:0}
</style>
<style scoped>
/* A narrow parent column must wrap the HEX field, not crop its seven characters. */
.color-main{flex-wrap:wrap}.color-main label{min-width:min(100%,120px)}
</style>

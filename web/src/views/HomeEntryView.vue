<script setup lang="ts">
import { useAuthStore } from '@/stores/auth';
import DashboardView from './DashboardView.vue';
import ProductHomeView from './ProductHomeView.vue';
const auth = useAuthStore();
</script>
<template><DashboardView v-if="auth.user"/><ProductHomeView v-else/></template>

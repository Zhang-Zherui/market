<template>
  <transition name="toast">
    <div
      v-if="visible"
      :class="[
        'fixed top-20 left-1/2 -translate-x-1/2 z-[200] px-6 py-3 rounded shadow-card text-sm font-medium animate-fade-in',
        type === 'success' ? 'bg-green-50 text-green-700' : 'bg-red-50 text-red-600',
      ]"
    >
      {{ message }}
    </div>
  </transition>
</template>

<script setup>
import { ref } from 'vue'

const visible = ref(false)
const message = ref('')
const type = ref('success')

let timer = null

const show = (msg, t = 'success') => {
  message.value = msg
  type.value = t
  visible.value = true
  if (timer) clearTimeout(timer)
  timer = setTimeout(() => { visible.value = false }, 2500)
}

defineExpose({ show })
</script>

<style scoped>
.toast-enter-active,
.toast-leave-active {
  transition: all 300ms ease;
}
.toast-enter-from,
.toast-leave-to {
  opacity: 0;
  transform: translate(-50%, -8px);
}
</style>

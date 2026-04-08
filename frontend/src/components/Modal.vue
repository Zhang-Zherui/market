<template>
  <div v-if="visible" class="fixed inset-0 z-[100] flex items-center justify-center bg-black/20 backdrop-blur-sm animate-fade-in">
    <div class="bg-white rounded shadow-card p-8 max-w-md w-full mx-4">
      <h3 class="font-heading text-lg font-semibold mb-6">{{ title }}</h3>
      <slot />
      <div class="flex gap-3 mt-8">
        <button
          @click="$emit('cancel')"
          class="flex-1 py-2.5 rounded text-sm font-medium text-primary/60 hover:text-primary hover:bg-primary/5 transition-all duration-300"
        >
          {{ cancelText }}
        </button>
        <button
          @click="$emit('confirm')"
          :disabled="loading"
          class="flex-1 py-2.5 rounded text-sm font-medium text-white bg-accent hover:bg-blue-600 transition-all duration-300 disabled:opacity-50"
        >
          {{ loading ? '处理中...' : confirmText }}
        </button>
      </div>
    </div>
  </div>
</template>

<script setup>
defineProps({
  title: { type: String, default: '提示' },
  confirmText: { type: String, default: '确认' },
  cancelText: { type: String, default: '取消' },
  visible: { type: Boolean, default: false },
  loading: { type: Boolean, default: false },
})
defineEmits(['confirm', 'cancel'])
</script>

<template>
  <div class="bg-white rounded shadow-card p-6 transition-all duration-300 hover:shadow-[0_8px_24px_rgba(0,0,0,0.12)]">
    <div class="flex justify-between items-start mb-4">
      <div>
        <h3 class="text-base font-heading font-semibold text-primary">{{ voucher.title }}</h3>
        <p v-if="voucher.subTitle" class="text-sm text-primary/50 mt-1">{{ voucher.subTitle }}</p>
      </div>
      <span v-if="showType" class="text-xs px-2 py-1 rounded bg-primary/5 text-primary/60 font-medium">
        {{ voucher.type === 1 ? '秒杀' : '普通' }}
      </span>
    </div>

    <div class="flex items-baseline gap-1 mb-4">
      <span class="text-2xl font-heading font-bold text-accent">
        ¥{{ (voucher.payValue / 100).toFixed(2) }}
      </span>
      <span class="text-sm text-primary/40 line-through">
        ¥{{ (voucher.actualValue / 100).toFixed(2) }}
      </span>
    </div>

    <p v-if="voucher.rules" class="text-xs text-primary/40 mb-5 leading-relaxed">{{ voucher.rules }}</p>

    <div v-if="voucher.type === 1 && voucher.stock != null" class="flex items-center gap-2 mb-5">
      <div class="flex-1 h-1 rounded-full bg-primary/5 overflow-hidden">
        <div
          class="h-full rounded-full bg-accent/30 transition-all duration-500"
          :style="{ width: stockPercent + '%' }"
        />
      </div>
      <span class="text-xs text-primary/40">剩 {{ voucher.stock }} 件</span>
    </div>

    <slot />
  </div>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  voucher: { type: Object, required: true },
  showType: { type: Boolean, default: true },
  totalStock: { type: Number, default: 200 },
})

const stockPercent = computed(() => {
  if (props.voucher.type !== 1 || props.voucher.stock == null) return 100
  return Math.min(100, (props.voucher.stock / props.totalStock) * 100)
})
</script>

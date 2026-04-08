<template>
  <div class="min-h-screen bg-gray-50/80 pt-16">
    <NavBar />
    <div class="max-w-5xl mx-auto px-16 py-8">
      <div class="flex items-center justify-between mb-8">
        <h2 class="text-xl font-heading font-semibold text-primary">优惠券商城</h2>
        <div class="flex gap-2">
          <button
            v-for="tab in tabs"
            :key="tab.value"
            @click="activeTab = tab.value; loadList()"
            :class="[
              'px-4 py-1.5 rounded-full text-xs font-medium transition-all duration-300',
              activeTab === tab.value
                ? 'bg-accent text-white'
                : 'bg-white text-primary/60 hover:text-primary shadow-sm',
            ]"
          >
            {{ tab.label }}
          </button>
        </div>
      </div>

      <!-- 加载中 -->
      <div v-if="loading" class="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
        <div v-for="i in 6" :key="i" class="bg-white rounded p-6 animate-pulse">
          <div class="h-4 bg-primary/5 rounded w-3/4 mb-3"></div>
          <div class="h-3 bg-primary/5 rounded w-1/2 mb-4"></div>
          <div class="h-6 bg-primary/5 rounded w-2/5"></div>
        </div>
      </div>

      <!-- 空状态 -->
      <div v-else-if="list.length === 0" class="text-center py-20">
        <p class="text-primary/40 text-sm">暂无优惠券</p>
      </div>

      <!-- 列表 -->
      <div v-else class="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
        <VoucherCard v-for="item in list" :key="item.id" :voucher="item">
          <button
            @click="handleBuy(item)"
            :disabled="buyingId === item.id"
            class="w-full py-2 rounded text-sm font-medium transition-all duration-300"
            :class="
              item.type === 1
                ? 'text-white bg-red-500 hover:bg-red-600 disabled:opacity-50'
                : 'text-accent border border-accent/30 hover:bg-accent/5 disabled:opacity-50'
            "
          >
            {{ buyingId === item.id ? '下单中...' : (item.type === 1 ? '立即抢购' : '购买') }}
          </button>
        </VoucherCard>
      </div>
    </div>

    <Toast ref="toastRef" />
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import NavBar from '../components/NavBar.vue'
import VoucherCard from '../components/VoucherCard.vue'
import Toast from '../components/Toast.vue'
import { getVoucherList, buyVoucher, seckillVoucher } from '../api'

const tabs = [
  { label: '全部', value: 0 },
  { label: '普通', value: 2 },
  { label: '秒杀', value: 1 },
]

const activeTab = ref(0)
const list = ref([])
const loading = ref(false)
const buyingId = ref(null)
const toastRef = ref(null)

const showToast = (msg, type = 'success') => toastRef.value?.show(msg, type)

const loadList = async () => {
  loading.value = true
  try {
    const res = await getVoucherList({ type: activeTab.value === 0 ? undefined : activeTab.value })
    if (res.success) list.value = res.data || []
  } catch {
    showToast('加载失败', 'error')
  } finally {
    loading.value = false
  }
}

const handleBuy = async (item) => {
  buyingId.value = item.id
  try {
    const api = item.type === 1 ? seckillVoucher : buyVoucher
    const res = await api(item.id)
    if (res.success) showToast('下单成功')
  } catch (e) {
    showToast(e?.response?.data?.message || '购买失败', 'error')
  } finally {
    buyingId.value = null
  }
}

onMounted(loadList)
</script>

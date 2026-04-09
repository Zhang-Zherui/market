<template>
  <div class="min-h-screen bg-gray-50/80 pt-16">
    <NavBar />
    <div class="max-w-5xl mx-auto px-16 py-8">
      <div class="bg-white rounded shadow-card p-8 mb-8 animate-fade-in">
        <div class="flex items-center gap-4">
          <div class="w-14 h-14 rounded-full bg-accent/10 flex items-center justify-center text-accent font-heading font-bold text-xl">
            {{ userInfo.nickName?.charAt(0) || 'U' }}
          </div>
          <div class="flex-1">
            <h2 class="text-lg font-heading font-semibold text-primary">{{ userInfo.nickName || '用户' }}</h2>
            <p class="text-sm text-primary/40">{{ userInfo.email }}</p>
          </div>
          <button
            @click="openEditModal"
            class="px-4 py-1.5 rounded text-xs font-medium text-primary/60 hover:text-primary border border-primary/10 hover:border-primary/20 transition-all duration-300"
          >
            编辑资料
          </button>
        </div>
      </div>

      <div class="flex items-center justify-between mb-6 gap-4 flex-wrap">
        <h3 class="text-lg font-heading font-semibold text-primary">我的订单</h3>
        <div class="flex gap-2 flex-wrap">
          <button
            v-for="s in orderStatuses"
            :key="s.value"
            @click="activeStatus = s.value; loadOrders()"
            :class="[
              'px-3 py-1 rounded-full text-xs font-medium transition-all duration-300',
              activeStatus === s.value
                ? 'bg-accent text-white'
                : 'bg-white text-primary/60 hover:text-primary shadow-sm',
            ]"
          >
            {{ s.label }}
          </button>
        </div>
      </div>

      <div v-if="orderLoading" class="space-y-4">
        <div v-for="i in 3" :key="i" class="bg-white rounded p-6 animate-pulse">
          <div class="h-4 bg-primary/5 rounded w-2/3 mb-2"></div>
          <div class="h-3 bg-primary/5 rounded w-1/3"></div>
        </div>
      </div>

      <div v-else-if="orders.length === 0" class="text-center py-16">
        <p class="text-primary/40 text-sm">暂无订单</p>
      </div>

      <div v-else class="space-y-4">
        <div
          v-for="order in orders"
          :key="order.id"
          class="bg-white rounded shadow-card p-6 transition-all duration-300 hover:shadow-[0_8px_24px_rgba(0,0,0,0.08)]"
        >
          <div class="flex justify-between items-start gap-4 mb-3">
            <div>
              <h4 class="text-sm font-semibold text-primary">{{ order.voucherTitle || '优惠券' }}</h4>
              <p class="text-xs text-primary/40 mt-0.5">
                订单号 {{ order.id }} · {{ formatTime(order.createTime) }}
              </p>
              <p v-if="order.status >= 2" class="text-xs text-primary/40 mt-1">
                支付方式：{{ payTypeLabel(order.payType) }}
              </p>
            </div>
            <span
              :class="[
                'text-xs px-2 py-1 rounded font-medium whitespace-nowrap',
                statusClass(order.status),
              ]"
            >
              {{ statusLabel(order.status) }}
            </span>
          </div>

          <div class="flex items-baseline gap-1 mb-4">
            <span class="text-lg font-heading font-bold text-accent">
              ￥{{ ((order.payValue || 0) / 100).toFixed(2) }}
            </span>
            <span class="text-xs text-primary/40 line-through">
              ￥{{ ((order.actualValue || 0) / 100).toFixed(2) }}
            </span>
          </div>

          <div class="flex gap-2 flex-wrap">
            <button
              v-if="order.status === 1"
              @click="openPayModal(order)"
              class="px-4 py-1.5 rounded text-xs font-medium text-white bg-accent hover:bg-blue-600 transition-all duration-300"
            >
              去支付
            </button>
            <button
              v-if="order.status === 1"
              @click="handleCancel(order)"
              class="px-4 py-1.5 rounded text-xs font-medium text-primary/60 border border-primary/10 hover:bg-primary/5 transition-all duration-300"
            >
              取消
            </button>
            <button
              v-if="order.status === 2"
              @click="handleUse(order)"
              class="px-4 py-1.5 rounded text-xs font-medium text-white bg-emerald-500 hover:bg-emerald-600 transition-all duration-300"
            >
              核销
            </button>
            <button
              v-if="order.status === 2"
              @click="handleRefund(order)"
              class="px-4 py-1.5 rounded text-xs font-medium text-red-500 border border-red-200 hover:bg-red-50 transition-all duration-300"
            >
              退款
            </button>
            <button
              v-if="order.status === 5"
              @click="handleConfirmRefund(order)"
              class="px-4 py-1.5 rounded text-xs font-medium text-orange-600 border border-orange-200 hover:bg-orange-50 transition-all duration-300"
            >
              确认退款
            </button>
          </div>
        </div>
      </div>
    </div>

    <Modal
      title="编辑资料"
      :visible="showEditModal"
      :loading="editLoading"
      @confirm="handleUpdateNickName"
      @cancel="showEditModal = false"
    >
      <div class="space-y-4">
        <div>
          <label class="block text-xs font-medium text-primary/60 mb-1.5">昵称</label>
          <input
            v-model="editNickName"
            type="text"
            placeholder="输入昵称"
            class="w-full px-4 py-2.5 rounded border border-primary/10 text-sm text-primary placeholder:text-primary/30 focus:outline-none focus:border-accent/40 transition-colors duration-300"
          />
        </div>
      </div>
    </Modal>

    <Modal
      title="选择支付方式"
      confirm-text="确认支付"
      :visible="showPayModal"
      :loading="payLoading"
      @confirm="submitPay"
      @cancel="closePayModal"
    >
      <div class="space-y-3">
        <p class="text-sm text-primary/60">
          订单号 {{ currentPayOrder?.id || '-' }}
        </p>
        <button
          v-for="item in payTypes"
          :key="item.value"
          @click="selectedPayType = item.value"
          :class="[
            'w-full text-left rounded border px-4 py-3 text-sm transition-all duration-300',
            selectedPayType === item.value
              ? 'border-accent bg-accent/5 text-primary'
              : 'border-primary/10 text-primary/60 hover:border-primary/20',
          ]"
        >
          {{ item.label }}
        </button>
      </div>
    </Modal>

    <Toast ref="toastRef" />
  </div>
</template>

<script setup>
import { reactive, ref, onMounted } from 'vue'
import NavBar from '../components/NavBar.vue'
import Modal from '../components/Modal.vue'
import Toast from '../components/Toast.vue'
import {
  getMe,
  updateNickName,
  getMyOrders,
  payOrder,
  cancelOrder,
  refundOrder,
  confirmRefund,
  useOrder,
} from '../api'

const userInfo = ref({})
const orders = ref([])
const orderLoading = ref(false)
const activeStatus = ref(-1)

const showEditModal = ref(false)
const editNickName = ref('')
const editLoading = ref(false)

const showPayModal = ref(false)
const currentPayOrder = ref(null)
const selectedPayType = ref(1)
const payLoading = ref(false)

const toastRef = ref(null)
const showToast = (msg, type = 'success') => toastRef.value?.show(msg, type)
const getErrorMsg = (res, fallback) => res?.errorMsg || fallback

const orderStatuses = [
  { label: '全部', value: -1 },
  { label: '未支付', value: 1 },
  { label: '已支付', value: 2 },
  { label: '已核销', value: 3 },
  { label: '已取消', value: 4 },
  { label: '退款中', value: 5 },
  { label: '已退款', value: 6 },
]

const payTypes = [
  { label: '余额支付', value: 1 },
  { label: '支付宝', value: 2 },
  { label: '微信', value: 3 },
]

const statusMap = {
  1: '未支付',
  2: '已支付',
  3: '已核销',
  4: '已取消',
  5: '退款中',
  6: '已退款',
}

const payTypeMap = {
  1: '余额支付',
  2: '支付宝',
  3: '微信',
}

const statusLabel = (status) => statusMap[status] || '未知状态'
const payTypeLabel = (payType) => payTypeMap[payType] || '未知方式'

const statusClass = (status) => {
  const map = {
    1: 'bg-yellow-50 text-yellow-700',
    2: 'bg-green-50 text-green-700',
    3: 'bg-primary/5 text-primary/60',
    4: 'bg-gray-50 text-gray-500',
    5: 'bg-orange-50 text-orange-600',
    6: 'bg-red-50 text-red-600',
  }
  return map[status] || 'bg-gray-50 text-gray-600'
}

const formatTime = (time) => {
  if (!time) return ''
  const date = new Date(time)
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')} ${String(date.getHours()).padStart(2, '0')}:${String(date.getMinutes()).padStart(2, '0')}`
}

const loadUser = async () => {
  try {
    const res = await getMe()
    if (res.success) {
      userInfo.value = res.data || {}
    }
  } catch {
    // ignore
  }
}

const loadOrders = async () => {
  orderLoading.value = true
  try {
    const res = await getMyOrders()
    if (res.success) {
      let data = res.data || []
      if (activeStatus.value >= 0) {
        data = data.filter((item) => item.status === activeStatus.value)
      }
      orders.value = data
    }
  } catch {
    showToast('加载订单失败', 'error')
  } finally {
    orderLoading.value = false
  }
}

const openEditModal = () => {
  editNickName.value = userInfo.value.nickName || ''
  showEditModal.value = true
}

const handleUpdateNickName = async () => {
  const nickName = editNickName.value.trim()
  if (!nickName) {
    showToast('请输入昵称', 'error')
    return
  }
  editLoading.value = true
  try {
    const res = await updateNickName(nickName)
    if (res.success) {
      userInfo.value.nickName = nickName
      showEditModal.value = false
      showToast('修改成功')
    }
  } catch {
    showToast('修改失败', 'error')
  } finally {
    editLoading.value = false
  }
}

const openPayModal = (order) => {
  currentPayOrder.value = order
  selectedPayType.value = order.payType || 1
  showPayModal.value = true
}

const closePayModal = () => {
  showPayModal.value = false
  currentPayOrder.value = null
  selectedPayType.value = 1
}

const submitPay = async () => {
  if (!currentPayOrder.value) return
  payLoading.value = true
  try {
    const res = await payOrder(currentPayOrder.value.id, selectedPayType.value)
    if (res.success) {
      showToast(`已使用${payTypeLabel(selectedPayType.value)}`)
      closePayModal()
      await loadOrders()
    } else {
      showToast(getErrorMsg(res, '支付失败'), 'error')
    }
  } catch (error) {
    showToast(error?.response?.data?.errorMsg || '支付失败', 'error')
  } finally {
    payLoading.value = false
  }
}

const handleCancel = async (order) => {
  try {
    const res = await cancelOrder(order.id)
    if (res.success) {
      showToast('订单已取消')
      await loadOrders()
    } else {
      showToast(getErrorMsg(res, '取消失败'), 'error')
    }
  } catch (error) {
    showToast(error?.response?.data?.errorMsg || '取消失败', 'error')
  }
}

const handleUse = async (order) => {
  try {
    const res = await useOrder(order.id)
    if (res.success) {
      showToast('核销成功')
      await loadOrders()
    } else {
      showToast(getErrorMsg(res, '核销失败'), 'error')
    }
  } catch (error) {
    showToast(error?.response?.data?.errorMsg || '核销失败', 'error')
  }
}

const handleRefund = async (order) => {
  try {
    const res = await refundOrder(order.id)
    if (res.success) {
      showToast('退款申请已提交')
      await loadOrders()
    } else {
      showToast(getErrorMsg(res, '退款失败'), 'error')
    }
  } catch (error) {
    showToast(error?.response?.data?.errorMsg || '退款失败', 'error')
  }
}

const handleConfirmRefund = async (order) => {
  try {
    const res = await confirmRefund(order.id)
    if (res.success) {
      showToast('退款已确认')
      await loadOrders()
    } else {
      showToast(getErrorMsg(res, '确认退款失败'), 'error')
    }
  } catch (error) {
    showToast(error?.response?.data?.errorMsg || '确认退款失败', 'error')
  }
}

onMounted(() => {
  loadUser()
  loadOrders()
})
</script>

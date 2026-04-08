<template>
  <div class="min-h-screen bg-gray-50/80 pt-16">
    <NavBar />
    <div class="max-w-5xl mx-auto px-16 py-8">
      <!-- 用户信息卡片 -->
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
            @click="showEditModal = true"
            class="px-4 py-1.5 rounded text-xs font-medium text-primary/60 hover:text-primary border border-primary/10 hover:border-primary/20 transition-all duration-300"
          >
            编辑资料
          </button>
        </div>
      </div>

      <!-- 订单列表 -->
      <div class="flex items-center justify-between mb-6">
        <h3 class="text-lg font-heading font-semibold text-primary">我的订单</h3>
        <div class="flex gap-2">
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
          <div class="flex justify-between items-start mb-3">
            <div>
              <h4 class="text-sm font-semibold text-primary">{{ order.voucherTitle || '优惠券' }}</h4>
              <p class="text-xs text-primary/40 mt-0.5">
                订单号: {{ order.id }} · {{ formatTime(order.createTime) }}
              </p>
            </div>
            <span
              :class="[
                'text-xs px-2 py-1 rounded font-medium',
                statusClass(order.status),
              ]"
            >
              {{ statusLabel(order.status) }}
            </span>
          </div>

          <div class="flex items-baseline gap-1 mb-4">
            <span class="text-lg font-heading font-bold text-accent">
              ¥{{ (order.payValue / 100).toFixed(2) }}
            </span>
            <span class="text-xs text-primary/40 line-through">
              ¥{{ (order.actualValue / 100).toFixed(2) }}
            </span>
          </div>

          <div class="flex gap-2">
            <!-- 待支付 -->
            <button
              v-if="order.status === 1"
              @click="handlePay(order)"
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
            <!-- 已支付 -->
            <button
              v-if="order.status === 2"
              @click="handleRefund(order)"
              class="px-4 py-1.5 rounded text-xs font-medium text-red-500 border border-red-200 hover:bg-red-50 transition-all duration-300"
            >
              退款
            </button>
          </div>
        </div>
      </div>
    </div>

    <!-- 编辑资料弹窗 -->
    <Modal title="编辑资料" :visible="showEditModal" :loading="editLoading" @confirm="handleUpdateNickName" @cancel="showEditModal = false">
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

    <!-- 修改密码弹窗 -->
    <Modal title="修改密码" :visible="showPwdModal" :loading="pwdLoading" @confirm="handleChangePwd" @cancel="showPwdModal = false; pwdForm = { oldPassword: '', newPassword: '' }">
      <div class="space-y-4">
        <div>
          <label class="block text-xs font-medium text-primary/60 mb-1.5">旧密码</label>
          <input v-model="pwdForm.oldPassword" type="password" placeholder="••••••••"
            class="w-full px-4 py-2.5 rounded border border-primary/10 text-sm text-primary placeholder:text-primary/30 focus:outline-none focus:border-accent/40 transition-colors duration-300" />
        </div>
        <div>
          <label class="block text-xs font-medium text-primary/60 mb-1.5">新密码</label>
          <input v-model="pwdForm.newPassword" type="password" placeholder="••••••••"
            class="w-full px-4 py-2.5 rounded border border-primary/10 text-sm text-primary placeholder:text-primary/30 focus:outline-none focus:border-accent/40 transition-colors duration-300" />
        </div>
      </div>
    </Modal>

    <Toast ref="toastRef" />
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import NavBar from '../components/NavBar.vue'
import Modal from '../components/Modal.vue'
import Toast from '../components/Toast.vue'
import { getMe, updateNickName, changePassword, getMyOrders, payOrder, cancelOrder, useRefund } from '../api'

const userInfo = ref({})
const orders = ref([])
const orderLoading = ref(false)
const activeStatus = ref(-1)

const showEditModal = ref(false)
const showPwdModal = ref(false)
const editNickName = ref('')
const editLoading = ref(false)
const pwdLoading = ref(false)
const pwdForm = reactive({ oldPassword: '', newPassword: '' })

const toastRef = ref(null)
const showToast = (msg, type = 'success') => toastRef.value?.show(msg, type)

const orderStatuses = [
  { label: '全部', value: -1 },
  { label: '未支付', value: 1 },
  { label: '已支付', value: 2 },
  { label: '已核销', value: 3 },
  { label: '已取消', value: 4 },
  { label: '退款中', value: 5 },
  { label: '已退款', value: 6 },
]

const statusMap = { 1: '未支付', 2: '已支付', 3: '已核销', 4: '已取消', 5: '退款中', 6: '已退款' }
const statusLabel = (s) => statusMap[s] || '未知'
const statusClass = (s) => {
  const map = {
    1: 'bg-yellow-50 text-yellow-700',
    2: 'bg-green-50 text-green-700',
    3: 'bg-primary/5 text-primary/60',
    4: 'bg-gray-50 text-gray-500',
    5: 'bg-orange-50 text-orange-600',
    6: 'bg-red-50 text-red-600',
  }
  return map[s] || 'bg-gray-50 text-gray-600'
}

const formatTime = (t) => {
  if (!t) return ''
  const d = new Date(t)
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')} ${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`
}

const loadUser = async () => {
  try {
    const res = await getMe()
    if (res.success) userInfo.value = res.data || {}
  } catch { /* ignore */ }
}

const loadOrders = async () => {
  orderLoading.value = true
  try {
    const res = await getMyOrders()
    if (res.success) {
      let data = res.data || []
      if (activeStatus.value >= 0) data = data.filter(o => o.status === activeStatus.value)
      orders.value = data
    }
  } catch {
    showToast('加载订单失败', 'error')
  } finally {
    orderLoading.value = false
  }
}

const handleUpdateNickName = async () => {
  if (!editNickName.value.trim()) return
  editLoading.value = true
  try {
    const res = await updateNickName(editNickName.value.trim())
    if (res.success) {
      userInfo.value.nickName = editNickName.value.trim()
      showEditModal.value = false
      showToast('修改成功')
    }
  } catch {
    showToast('修改失败', 'error')
  } finally {
    editLoading.value = false
  }
}

const handleChangePwd = async () => {
  if (!pwdForm.oldPassword || !pwdForm.newPassword) return
  pwdLoading.value = true
  try {
    const res = await changePassword(pwdForm)
    if (res.success) {
      showPwdModal.value = false
      pwdForm.oldPassword = ''
      pwdForm.newPassword = ''
      showToast('密码修改成功')
    }
  } catch {
    showToast('修改失败', 'error')
  } finally {
    pwdLoading.value = false
  }
}

const handlePay = async (order) => {
  try {
    const res = await payOrder(order.id, 1)
    if (res.success) {
      showToast('支付成功')
      loadOrders()
    }
  } catch {
    showToast('支付失败', 'error')
  }
}

const handleCancel = async (order) => {
  try {
    const res = await cancelOrder(order.id)
    if (res.success) {
      showToast('订单已取消')
      loadOrders()
    }
  } catch {
    showToast('取消失败', 'error')
  }
}

const handleRefund = async (order) => {
  try {
    const res = await useRefund(order.id)
    if (res.success) {
      showToast('退款申请已提交')
      loadOrders()
    }
  } catch {
    showToast('退款失败', 'error')
  }
}

onMounted(() => {
  loadUser()
  loadOrders()
})
</script>

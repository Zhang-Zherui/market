<template>
  <div class="min-h-screen bg-gray-50/80 pt-16">
    <NavBar />
    <div class="max-w-5xl mx-auto px-16 py-8">
      <div class="flex items-center justify-between mb-8">
        <h2 class="text-xl font-heading font-semibold text-primary">优惠券管理</h2>
        <button
          @click="openCreateModal(false)"
          class="px-4 py-2 rounded text-sm font-medium text-white bg-accent hover:bg-blue-600 transition-all duration-300"
        >
          新建优惠券
        </button>
      </div>

      <!-- 加载中 -->
      <div v-if="loading" class="space-y-4">
        <div v-for="i in 4" :key="i" class="bg-white rounded p-6 animate-pulse">
          <div class="h-4 bg-primary/5 rounded w-3/4 mb-3"></div>
          <div class="h-3 bg-primary/5 rounded w-1/2"></div>
        </div>
      </div>

      <!-- 空状态 -->
      <div v-else-if="list.length === 0" class="text-center py-20">
        <p class="text-primary/40 text-sm">暂无优惠券，点击右上角创建</p>
      </div>

      <!-- 管理列表 -->
      <div v-else class="space-y-4">
        <div
          v-for="item in list"
          :key="item.id"
          class="bg-white rounded shadow-card p-6 transition-all duration-300 hover:shadow-[0_8px_24px_rgba(0,0,0,0.08)]"
        >
          <div class="flex justify-between items-start mb-3">
            <div class="flex-1">
              <div class="flex items-center gap-2">
                <h4 class="text-sm font-semibold text-primary">{{ item.title }}</h4>
                <span :class="[
                  'text-xs px-2 py-0.5 rounded font-medium',
                  item.type === 1 ? 'bg-red-50 text-red-600' : 'bg-primary/5 text-primary/60',
                ]">
                  {{ item.type === 1 ? '秒杀' : '普通' }}
                </span>
                <span :class="[
                  'text-xs px-2 py-0.5 rounded font-medium',
                  item.status === 1 ? 'bg-green-50 text-green-700' : 'bg-gray-50 text-gray-500',
                ]">
                  {{ item.status === 1 ? '上架' : '下架' }}
                </span>
              </div>
              <p v-if="item.subTitle" class="text-xs text-primary/40 mt-1">{{ item.subTitle }}</p>
            </div>
            <div class="text-right">
              <div class="text-lg font-heading font-bold text-accent">¥{{ (item.payValue / 100).toFixed(2) }}</div>
              <div class="text-xs text-primary/40 line-through">¥{{ (item.actualValue / 100).toFixed(2) }}</div>
            </div>
          </div>

          <div class="flex items-center gap-4 text-xs text-primary/40 mb-4">
            <span>库存: {{ item.stock ?? '-' }}</span>
            <span>已售: {{ item.sold ?? 0 }}</span>
            <span v-if="item.type === 1">限量: {{ item.limitNum ?? '-' }}</span>
          </div>

          <div class="flex gap-2">
            <button
              @click="openEditStockModal(item)"
              class="px-3 py-1.5 rounded text-xs font-medium text-primary/60 border border-primary/10 hover:bg-primary/5 transition-all duration-300"
            >
              修改库存
            </button>
            <button
              @click="toggleStatus(item)"
              class="px-3 py-1.5 rounded text-xs font-medium transition-all duration-300"
              :class="item.status === 1
                ? 'text-yellow-600 border border-yellow-200 hover:bg-yellow-50'
                : 'text-green-600 border border-green-200 hover:bg-green-50'"
            >
              {{ item.status === 1 ? '下架' : '上架' }}
            </button>
            <button
              @click="openCreateModal(true, item)"
              class="px-3 py-1.5 rounded text-xs font-medium text-accent border border-accent/30 hover:bg-accent/5 transition-all duration-300"
            >
              编辑
            </button>
            <button
              @click="handleDelete(item)"
              class="px-3 py-1.5 rounded text-xs font-medium text-red-500 border border-red-200 hover:bg-red-50 transition-all duration-300"
            >
              删除
            </button>
          </div>
        </div>
      </div>
    </div>

    <!-- 创建/编辑弹窗 -->
    <Modal :title="editingVoucher ? '编辑优惠券' : (isSeckill ? '新建秒杀券' : '新建优惠券')" :visible="showFormModal" :loading="formLoading" @confirm="handleFormSubmit" @cancel="showFormModal = false">
      <div class="space-y-4">
        <div>
          <label class="block text-xs font-medium text-primary/60 mb-1.5">标题</label>
          <input v-model="form.title" type="text" placeholder="优惠券标题"
            class="w-full px-4 py-2.5 rounded border border-primary/10 text-sm text-primary placeholder:text-primary/30 focus:outline-none focus:border-accent/40 transition-colors duration-300" />
        </div>
        <div>
          <label class="block text-xs font-medium text-primary/60 mb-1.5">副标题</label>
          <input v-model="form.subTitle" type="text" placeholder="副标题（选填）"
            class="w-full px-4 py-2.5 rounded border border-primary/10 text-sm text-primary placeholder:text-primary/30 focus:outline-none focus:border-accent/40 transition-colors duration-300" />
        </div>
        <div class="grid grid-cols-2 gap-4">
          <div>
            <label class="block text-xs font-medium text-primary/60 mb-1.5">售价（元）</label>
            <input v-model.number="form.payValue" type="number" min="0" step="0.01" placeholder="0.00"
              class="w-full px-4 py-2.5 rounded border border-primary/10 text-sm text-primary placeholder:text-primary/30 focus:outline-none focus:border-accent/40 transition-colors duration-300" />
          </div>
          <div>
            <label class="block text-xs font-medium text-primary/60 mb-1.5">面值（元）</label>
            <input v-model.number="form.actualValue" type="number" min="0" step="0.01" placeholder="0.00"
              class="w-full px-4 py-2.5 rounded border border-primary/10 text-sm text-primary placeholder:text-primary/30 focus:outline-none focus:border-accent/40 transition-colors duration-300" />
          </div>
        </div>
        <div class="grid grid-cols-2 gap-4">
          <div>
            <label class="block text-xs font-medium text-primary/60 mb-1.5">库存</label>
            <input v-model.number="form.stock" type="number" min="0" placeholder="0"
              class="w-full px-4 py-2.5 rounded border border-primary/10 text-sm text-primary placeholder:text-primary/30 focus:outline-none focus:border-accent/40 transition-colors duration-300" />
          </div>
          <div v-if="isSeckill">
            <label class="block text-xs font-medium text-primary/60 mb-1.5">限购数量</label>
            <input v-model.number="form.limitNum" type="number" min="1" placeholder="1"
              class="w-full px-4 py-2.5 rounded border border-primary/10 text-sm text-primary placeholder:text-primary/30 focus:outline-none focus:border-accent/40 transition-colors duration-300" />
          </div>
        </div>
        <div>
          <label class="block text-xs font-medium text-primary/60 mb-1.5">使用规则</label>
          <textarea v-model="form.rules" rows="2" placeholder="使用规则说明"
            class="w-full px-4 py-2.5 rounded border border-primary/10 text-sm text-primary placeholder:text-primary/30 focus:outline-none focus:border-accent/40 transition-colors duration-300 resize-none"></textarea>
        </div>
        <div v-if="!editingVoucher" class="flex gap-2">
          <label class="text-xs font-medium text-primary/60">类型:</label>
          <button
            type="button"
            @click="isSeckill = false"
            :class="['px-3 py-1 rounded text-xs font-medium transition-all', !isSeckill ? 'bg-accent text-white' : 'bg-primary/5 text-primary/60']"
          >普通</button>
          <button
            type="button"
            @click="isSeckill = true"
            :class="['px-3 py-1 rounded text-xs font-medium transition-all', isSeckill ? 'bg-accent text-white' : 'bg-primary/5 text-primary/60']"
          >秒杀</button>
        </div>
      </div>
    </Modal>

    <!-- 修改库存弹窗 -->
    <Modal title="修改库存" :visible="showStockModal" :loading="stockLoading" @confirm="handleUpdateStock" @cancel="showStockModal = false">
      <div>
        <label class="block text-xs font-medium text-primary/60 mb-1.5">库存数量</label>
        <input v-model.number="newStock" type="number" min="0" placeholder="0"
          class="w-full px-4 py-2.5 rounded border border-primary/10 text-sm text-primary placeholder:text-primary/30 focus:outline-none focus:border-accent/40 transition-colors duration-300" />
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
import {
  getVoucherList, addVoucher, addSeckillVoucher, updateVoucher,
  deleteVoucher, updateVoucherStatus, updateStock,
} from '../api'

const list = ref([])
const loading = ref(false)
const toastRef = ref(null)
const showToast = (msg, type = 'success') => toastRef.value?.show(msg, type)

// 表单弹窗
const showFormModal = ref(false)
const formLoading = ref(false)
const editingVoucher = ref(null)
const isSeckill = ref(false)
const form = reactive({
  title: '',
  subTitle: '',
  payValue: null,
  actualValue: null,
  stock: null,
  limitNum: 1,
  rules: '',
})

const resetForm = () => {
  form.title = ''
  form.subTitle = ''
  form.payValue = null
  form.actualValue = null
  form.stock = null
  form.limitNum = 1
  form.rules = ''
}

const openEditModal = (item) => {
  editingVoucher.value = item
  isSeckill.value = item.type === 1
  form.title = item.title
  form.subTitle = item.subTitle || ''
  form.payValue = item.payValue / 100
  form.actualValue = item.actualValue / 100
  form.stock = item.stock
  form.limitNum = item.limitNum || 1
  form.rules = item.rules || ''
  showFormModal.value = true
}

// 库存弹窗
const showStockModal = ref(false)
const stockLoading = ref(false)
const stockTargetId = ref(null)
const newStock = ref(null)

const openEditStockModal = (item) => {
  stockTargetId.value = item.id
  newStock.value = item.stock
  showStockModal.value = true
}

const loadList = async () => {
  loading.value = true
  try {
    const res = await getVoucherList()
    if (res.success) list.value = res.data || []
  } catch {
    showToast('加载失败', 'error')
  } finally {
    loading.value = false
  }
}

const handleFormSubmit = async () => {
  if (!form.title || !form.payValue || !form.actualValue) {
    showToast('请填写必要信息', 'error')
    return
  }
  formLoading.value = true
  try {
    const data = {
      ...form,
      payValue: Math.round(form.payValue * 100),
      actualValue: Math.round(form.actualValue * 100),
    }
    let res
    if (editingVoucher.value) {
      data.id = editingVoucher.value.id
      res = await updateVoucher(data)
    } else if (isSeckill.value) {
      res = await addSeckillVoucher(data)
    } else {
      res = await addVoucher(data)
    }
    if (res.success) {
      showFormModal.value = false
      showToast(editingVoucher.value ? '编辑成功' : '创建成功')
      loadList()
    }
  } catch {
    showToast('操作失败', 'error')
  } finally {
    formLoading.value = false
  }
}

const handleUpdateStock = async () => {
  if (newStock.value == null) return
  stockLoading.value = true
  try {
    const res = await updateStock(stockTargetId.value, newStock.value)
    if (res.success) {
      showStockModal.value = false
      showToast('库存已更新')
      loadList()
    }
  } catch {
    showToast('更新失败', 'error')
  } finally {
    stockLoading.value = false
  }
}

const toggleStatus = async (item) => {
  try {
    const newStatus = item.status === 1 ? 0 : 1
    const res = await updateVoucherStatus(item.id, newStatus)
    if (res.success) {
      showToast(newStatus === 1 ? '已上架' : '已下架')
      loadList()
    }
  } catch {
    showToast('操作失败', 'error')
  }
}

const handleDelete = async (item) => {
  if (!confirm('确认删除该优惠券？')) return
  try {
    const res = await deleteVoucher(item.id)
    if (res.success) {
      showToast('已删除')
      loadList()
    }
  } catch {
    showToast('删除失败', 'error')
  }
}

const openCreateModal = (flag, item) => {
  if (item) {
    openEditModal(item)
  } else {
    editingVoucher.value = null
    isSeckill.value = flag
    resetForm()
    showFormModal.value = true
  }
}

onMounted(loadList)
</script>

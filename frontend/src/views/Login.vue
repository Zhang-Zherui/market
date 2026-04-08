<template>
  <div class="min-h-screen flex items-center justify-center bg-gray-50/80 px-4">
    <div class="w-full max-w-sm animate-fade-in">
      <h1 class="font-heading text-2xl font-semibold text-primary text-center mb-2">
        {{ isLogin ? '欢迎回来' : '创建账户' }}
      </h1>
      <p class="text-sm text-primary/50 text-center mb-8">
        {{ isLogin ? '登录以继续使用优惠券服务' : '注册一个新账户' }}
      </p>

      <form @submit.prevent="handleSubmit" class="bg-white rounded shadow-card p-8 space-y-5">
        <!-- 邮箱 -->
        <div>
          <label class="block text-xs font-medium text-primary/60 mb-1.5">邮箱</label>
          <input
            v-model="form.email"
            type="email"
            required
            placeholder="your@email.com"
            class="w-full px-4 py-2.5 rounded border border-primary/10 text-sm text-primary placeholder:text-primary/30 focus:outline-none focus:border-accent/40 transition-colors duration-300"
          />
        </div>

        <!-- 注册：验证码 -->
        <div v-if="!isLogin">
          <label class="block text-xs font-medium text-primary/60 mb-1.5">验证码</label>
          <div class="flex gap-3">
            <input
              v-model="form.code"
              type="text"
              required
              maxlength="6"
              placeholder="6 位验证码"
              class="flex-1 px-4 py-2.5 rounded border border-primary/10 text-sm text-primary placeholder:text-primary/30 focus:outline-none focus:border-accent/40 transition-colors duration-300"
            />
            <button
              type="button"
              :disabled="codeCooldown > 0"
              @click="handleSendCode"
              class="shrink-0 px-4 py-2.5 rounded text-sm font-medium text-accent border border-accent/30 hover:bg-accent/5 transition-all duration-300 disabled:opacity-40 disabled:cursor-not-allowed"
            >
              {{ codeCooldown > 0 ? `${codeCooldown}s` : '发送' }}
            </button>
          </div>
        </div>

        <!-- 密码 -->
        <div>
          <label class="block text-xs font-medium text-primary/60 mb-1.5">密码</label>
          <input
            v-model="form.password"
            type="password"
            required
            placeholder="••••••••"
            class="w-full px-4 py-2.5 rounded border border-primary/10 text-sm text-primary placeholder:text-primary/30 focus:outline-none focus:border-accent/40 transition-colors duration-300"
          />
        </div>

        <!-- 注册：确认密码 -->
        <div v-if="!isLogin">
          <label class="block text-xs font-medium text-primary/60 mb-1.5">确认密码</label>
          <input
            v-model="form.confirmPassword"
            type="password"
            required
            placeholder="••••••••"
            class="w-full px-4 py-2.5 rounded border border-primary/10 text-sm text-primary placeholder:text-primary/30 focus:outline-none focus:border-accent/40 transition-colors duration-300"
          />
        </div>

        <button
          type="submit"
          :disabled="submitting"
          class="w-full py-2.5 rounded text-sm font-medium text-white bg-accent hover:bg-blue-600 transition-all duration-300 disabled:opacity-50"
        >
          {{ submitting ? '处理中...' : (isLogin ? '登录' : '注册') }}
        </button>
      </form>

      <p class="text-center text-sm text-primary/50 mt-6">
        <button @click="isLogin = !isLogin" class="text-accent hover:underline">
          {{ isLogin ? '没有账户？去注册' : '已有账户？去登录' }}
        </button>
      </p>
    </div>

    <Toast ref="toastRef" />
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { sendCode, register, login } from '../api'
import Toast from '../components/Toast.vue'

const router = useRouter()
const isLogin = ref(true)
const submitting = ref(false)
const codeCooldown = ref(0)
const toastRef = ref(null)

const showToast = (msg, type = 'success') => toastRef.value?.show(msg, type)

const form = reactive({
  email: '',
  password: '',
  code: '',
  confirmPassword: '',
})

const handleSendCode = async () => {
  if (!form.email) {
    showToast('请输入邮箱', 'error')
    return
  }
  try {
    const res = await sendCode(form.email)
    if (res.success) {
      showToast('验证码已发送')
      codeCooldown.value = 60
      const timer = setInterval(() => {
        codeCooldown.value--
        if (codeCooldown.value <= 0) clearInterval(timer)
      }, 1000)
    } else {
      showToast(res.errorMsg || '发送失败', 'error')
    }
  } catch (e) {
    showToast(e?.response?.data?.errorMsg || '发送失败', 'error')
  }
}

const handleSubmit = async () => {
  submitting.value = true
  try {
    if (isLogin.value) {
      const res = await login({ email: form.email, password: form.password })
      if (res.success) {
        localStorage.setItem('access_token', res.data.access_token)
        localStorage.setItem('refresh_token', res.data.refresh_token)
        showToast('登录成功')
        router.push('/shop')
      } else {
        showToast(res.errorMsg || '登录失败', 'error')
      }
    } else {
      if (form.password !== form.confirmPassword) {
        showToast('两次密码不一致', 'error')
        submitting.value = false
        return
      }
      const res = await register({ email: form.email, password: form.password, code: form.code })
      if (res.success) {
        showToast('注册成功，请登录')
        isLogin.value = true
        form.code = ''
        form.confirmPassword = ''
      } else {
        showToast(res.errorMsg || '注册失败', 'error')
      }
    }
  } catch (e) {
    showToast(e?.response?.data?.errorMsg || '操作失败', 'error')
  } finally {
    submitting.value = false
  }
}
</script>

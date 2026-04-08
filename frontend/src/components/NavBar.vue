<template>
  <nav class="fixed top-0 left-0 right-0 z-50 bg-white/80 backdrop-blur-md">
    <div class="max-w-5xl mx-auto px-16 h-16 flex items-center justify-between">
      <router-link to="/shop" class="font-heading text-lg font-semibold tracking-tight text-primary hover:text-accent transition-colors duration-300">
        Voucher
      </router-link>
      <div class="flex items-center gap-8">
        <router-link
          v-for="item in navItems"
          :key="item.path"
          :to="item.path"
          class="text-sm text-primary/60 hover:text-primary transition-colors duration-300"
          active-class="!text-primary font-medium"
        >
          {{ item.label }}
        </router-link>
        <button
          v-if="isLoggedIn"
          @click="handleLogout"
          class="text-sm text-primary/40 hover:text-primary transition-colors duration-300"
        >
          退出
        </button>
      </div>
    </div>
  </nav>
</template>

<script setup>
import { computed } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { logout } from '../api'

const router = useRouter()
const route = useRoute()
const isLoggedIn = computed(() => !!localStorage.getItem('access_token'))

const navItems = [
  { path: '/shop', label: '优惠券' },
  { path: '/profile', label: '我的' },
  { path: '/manage', label: '管理' },
]

const handleLogout = async () => {
  try {
    await logout()
  } catch { /* ignore */ }
  localStorage.removeItem('access_token')
  localStorage.removeItem('refresh_token')
  router.push('/login')
}
</script>

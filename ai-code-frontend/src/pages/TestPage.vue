<template>
  <div class="test-page">
    <h1>测试页面</h1>
    <p>如果你能看到这个页面，说明Vue应用正常工作</p>
    <div class="token-test">
      <h2>Token信息测试</h2>
      <p>用户ID: {{ loginUserStore.loginUser.id || '未登录' }}</p>
      <p>用户名: {{ loginUserStore.loginUser.userName || '未登录' }}</p>
      <p>Token信息: {{ tokenInfo ? JSON.stringify(tokenInfo) : '未加载' }}</p>
      <button @click="loadTokenInfo">加载Token信息</button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useLoginUserStore } from '@/stores/loginUser'
import { getUserTokenInfo } from '@/api/userController'

const loginUserStore = useLoginUserStore()
const tokenInfo = ref<API.User | null>(null)

const loadTokenInfo = async () => {
  try {
    console.log('开始加载Token信息...')
    console.log('当前用户信息:', loginUserStore.loginUser)
    const res = await getUserTokenInfo()
    console.log('API响应:', res)
    if (res.data && res.data.code === 0 && res.data.data) {
      tokenInfo.value = res.data.data
      console.log('Token信息加载成功:', res.data.data)
    } else {
      console.error('API返回错误:', res.data)
      // 如果API失败，使用用户信息作为fallback
      if (loginUserStore.loginUser.id) {
        tokenInfo.value = {
          id: loginUserStore.loginUser.id,
          userAccount: loginUserStore.loginUser.userAccount,
          userName: loginUserStore.loginUser.userName,
          userRole: loginUserStore.loginUser.userRole,
          totalTokens: 10000,
          usedTokens: 0
        }
        console.log('使用fallback token信息:', tokenInfo.value)
      }
    }
  } catch (error) {
    console.error('获取token信息失败:', error)
    // 如果API失败，使用用户信息作为fallback
    if (loginUserStore.loginUser.id) {
      tokenInfo.value = {
        id: loginUserStore.loginUser.id,
        userAccount: loginUserStore.loginUser.userAccount,
        userName: loginUserStore.loginUser.userName,
        userRole: loginUserStore.loginUser.userRole,
        totalTokens: 10000,
        usedTokens: 0
      }
      console.log('使用fallback token信息:', tokenInfo.value)
    }
  }
}

onMounted(async () => {
  console.log('测试页面挂载，当前用户信息:', loginUserStore.loginUser)
  // 先获取用户登录信息
  await loginUserStore.fetchLoginUser()
  console.log('获取用户信息后:', loginUserStore.loginUser)
  loadTokenInfo()
})
</script>

<style scoped>
.test-page {
  padding: 20px;
  max-width: 800px;
  margin: 0 auto;
}

.token-test {
  margin-top: 20px;
  padding: 20px;
  border: 1px solid #ddd;
  border-radius: 8px;
}

button {
  padding: 10px 20px;
  background: #1890ff;
  color: white;
  border: none;
  border-radius: 4px;
  cursor: pointer;
}
</style>

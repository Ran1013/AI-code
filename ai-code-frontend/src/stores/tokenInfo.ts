import { defineStore } from 'pinia'
import { ref } from 'vue'
import { getUserTokenInfo } from '../api/userController'

export const useTokenInfoStore = defineStore('tokenInfo', () => {
  // token信息状态
  const tokenInfo = ref<API.User | null>(null)

  // 刷新token信息
  async function refreshTokenInfo() {
    const res = await getUserTokenInfo()
    if (res.data.code === 0 && res.data.data) {
      tokenInfo.value = res.data.data
    }
  }

  // 设置token信息
  function setTokenInfo(newTokenInfo: API.User | null) {
    tokenInfo.value = newTokenInfo
  }

  // 获取剩余tokens
  function getRemainingTokens(): number {
    if (!tokenInfo.value || !tokenInfo.value.totalTokens || !tokenInfo.value.usedTokens) {
      return 0
    }
    return tokenInfo.value.totalTokens - tokenInfo.value.usedTokens
  }

  return { 
    tokenInfo, 
    refreshTokenInfo, 
    setTokenInfo,
    getRemainingTokens
  }
})

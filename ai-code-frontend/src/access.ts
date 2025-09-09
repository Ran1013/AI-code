import { useLoginUserStore } from '@/stores/loginUser'
import { message } from 'ant-design-vue'
import router from '@/router'

/**
 * 全局权限校验
 */
router.beforeEach(async (to, from, next) => {
  const loginUserStore = useLoginUserStore()
  let loginUser = loginUserStore.loginUser
  // 如果当前 store 中没有用户信息，则尝试获取
  if (!loginUser || !loginUser.id) {
    // 加 await 是为了等用户信息获取成功后，再执行后续的权限校验
    await loginUserStore.fetchLoginUser()
    loginUser = loginUserStore.loginUser
  }
  const toUrl = to.fullPath
  if (toUrl.startsWith('/admin')) {
    if (!loginUser || loginUser.userRole !== 'admin') {
      message.error('没有权限')
      next(`/user/login?redirect=${to.fullPath}`)
      return
    }
  }
  next()
})

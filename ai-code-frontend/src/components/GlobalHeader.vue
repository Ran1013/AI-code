<template>
  <a-layout-header class="header">
    <a-row :wrap="false" align="middle" class="header-content">
      <!-- 左侧：Logo和标题 -->
      <a-col flex="200px">
        <RouterLink to="/" class="logo-link">
          <div class="header-left">
            <div class="logo-container">
              <img src="/src/assets/ran-logo.png" alt="AI Code" class="logo-image" />

            </div>
          </div>
        </RouterLink>
      </a-col>
      <!-- 中间：导航菜单 -->
      <a-col flex="auto">
        <a-menu
          v-model:selectedKeys="selectedKeys"
          mode="horizontal"
          :items="menuItems"
          @click="handleMenuClick"
          class="nav-menu"
        />
      </a-col>
      <!-- 右侧：用户操作区域 -->
      <a-col flex="200px" style="text-align: right;">
        <div class="user-login-status">
          <div v-if="loginUserStore.loginUser.id">
            <a-dropdown>
              <a-space class="user-info">
                <!-- 用户状态徽章（在用户名前） -->
                <span v-if="tokenInfo && tokenInfo.userRole === 'admin'" class="admin-badge">
                  <CrownOutlined />
                  管理员
                </span>
                <span v-else-if="tokenInfo && tokenInfo.userRole === 'member'" class="member-badge">
                  <StarOutlined />
                  VIP会员
                </span>
                <span v-else-if="tokenInfo" class="token-info">
                  {{ tokenInfo.usedTokens || 0 }}/{{ tokenInfo.totalTokens || 10000 }}
                </span>
                <span v-else class="loading-info">
                  加载中...
                </span>
                <!-- 用户头像和用户名 -->
                <a-avatar :src="loginUserStore.loginUser.userAvatar" class="user-avatar" />
                <span class="username">{{ loginUserStore.loginUser.userName ?? '无名' }}</span>
              </a-space>
              <template #overlay>
                <a-menu class="user-dropdown">
                  <a-menu-item @click="goToProfile" class="profile-item">
                    <UserOutlined />
                    个人信息
                  </a-menu-item>
                  <a-menu-item @click="doLogout" class="logout-item">
                    <LogoutOutlined />
                    退出登录
                  </a-menu-item>
                </a-menu>
              </template>
            </a-dropdown>
          </div>
          <div v-else>
            <a-button type="primary" href="/user/login" class="login-btn">登录</a-button>
          </div>
        </div>
      </a-col>
    </a-row>
  </a-layout-header>
</template>

<script setup lang="ts">
import { computed, h, ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { type MenuProps, message } from 'ant-design-vue'
import { useLoginUserStore } from '../stores/loginUser'
import { userLogout, getUserTokenInfo } from '../api/userController'
import { LogoutOutlined, HomeOutlined, UserOutlined, StarOutlined, CrownOutlined } from '@ant-design/icons-vue'

const loginUserStore = useLoginUserStore()
const router = useRouter()
// 当前选中菜单
const selectedKeys = ref<string[]>(['/'])
// token信息
const tokenInfo = ref<API.User | null>(null)
// 监听路由变化，更新当前选中菜单
router.afterEach((to, from, next) => {
  selectedKeys.value = [to.path]
})

// 获取token信息
const loadTokenInfo = async () => {
  console.log('GlobalHeader: 开始获取token信息')
  console.log('GlobalHeader: 当前用户信息:', loginUserStore.loginUser)
  
  if (loginUserStore.loginUser.id) {
    try {
      const res = await getUserTokenInfo()
      console.log('GlobalHeader: API响应:', res)
      if (res.data && res.data.code === 0 && res.data.data) {
        tokenInfo.value = res.data.data
        console.log('GlobalHeader: token信息设置成功:', tokenInfo.value)
      } else {
        console.error('GlobalHeader: API返回错误:', res.data)
        // 设置默认值作为fallback
        tokenInfo.value = {
          id: loginUserStore.loginUser.id,
          userAccount: loginUserStore.loginUser.userAccount,
          userName: loginUserStore.loginUser.userName,
          userRole: loginUserStore.loginUser.userRole || 'user',
          totalTokens: 10000,
          usedTokens: 0
        }
        console.log('GlobalHeader: 使用fallback token信息:', tokenInfo.value)
      }
    } catch (error) {
      console.error('GlobalHeader: 获取token信息失败:', error)
      // 设置默认值作为fallback
      tokenInfo.value = {
        id: loginUserStore.loginUser.id,
        userAccount: loginUserStore.loginUser.userAccount,
        userName: loginUserStore.loginUser.userName,
        userRole: loginUserStore.loginUser.userRole || 'user',
        totalTokens: 10000,
        usedTokens: 0
      }
      console.log('GlobalHeader: 使用fallback token信息:', tokenInfo.value)
    }
  } else {
    console.log('GlobalHeader: 用户未登录，跳过token信息获取')
  }
}

// 组件挂载时获取token信息
onMounted(async () => {
  console.log('GlobalHeader: 组件挂载')
  // 确保用户信息已加载
  if (!loginUserStore.loginUser.id) {
    await loginUserStore.fetchLoginUser()
  }
  loadTokenInfo()
})

// 菜单配置项
const originItems = computed(() => [
  {
    key: '/',
    icon: () => h(HomeOutlined),
    label: '首页',
    title: '首页',
  },
  {
    key: '/recommend',
    icon: () => h(StarOutlined),
    label: '推荐',
    title: '推荐',
  },
  {
    key: '/admin/userManage',
    label: '用户管理',
    title: '用户管理',
  },
  {
    key: '/admin/appManage',
    label: '应用管理',
    title: '应用管理',
  },
])

// 过滤菜单项
const filterMenus = (menus: any) => {
  // 防御性检查，确保 menus 是数组
  if (!Array.isArray(menus)) {
    console.error('filterMenus 接收到非数组类型:', menus)
    return []
  }
  
  return menus.filter((menu) => {
    const menuKey = menu?.key as string
    if (menuKey?.startsWith('/admin')) {
      const loginUser = loginUserStore.loginUser
      if (!loginUser || loginUser.userRole !== 'admin') {
        return false
      }
    }
    return true
  })
}

// 展示在菜单的路由数组
const menuItems = computed<MenuProps['items']>(() => filterMenus(originItems.value))

// 处理菜单点击
const handleMenuClick: MenuProps['onClick'] = (e) => {
  const key = e.key as string
  selectedKeys.value = [key]
  // 跳转到对应页面
  if (key.startsWith('/')) {
    router.push(key)
  }
}

// 跳转到个人信息页面
const goToProfile = () => {
  router.push('/user/profile')
}

// 退出登录
const doLogout = async () => {
  const res = await userLogout()
  if (res.data.code === 0) {
    loginUserStore.setLoginUser({
      userName: '未登录',
    })
    message.success('退出登录成功')
    await router.push('/user/login')
  } else {
    message.error('退出登录失败，' + res.data.message)
  }
}
</script>

<style scoped>
.header {
  background: rgba(255, 255, 255, 0.95);
  backdrop-filter: blur(20px);
  border-bottom: 1px solid rgba(24, 144, 255, 0.1);
  padding: 0 24px;
  height: 64px;
  min-height: 64px;
  position: sticky;
  top: 0;
  z-index: 1000;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.08);
  display: flex;
  align-items: center;
}

.header-content {
  width: 100%;
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.logo-link {
  text-decoration: none;
  color: inherit;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 12px;
}

.logo-container {
  display: flex;
  align-items: center;
  gap: 8px;
}

.logo-image {
  height: 60px;
  width: auto;
  object-fit: contain;
}


.nav-menu {
  background: transparent;
  border-bottom: none;
  justify-content: center;
}

.nav-menu :deep(.ant-menu-item) {
  border-radius: 8px;
  margin: 0 4px;
  transition: all 0.3s ease;
}

.nav-menu :deep(.ant-menu-item:hover) {
  background: rgba(24, 144, 255, 0.1);
  color: #1890ff;
}

.nav-menu :deep(.ant-menu-item-selected) {
  background: linear-gradient(135deg, #1890ff 0%, #40a9ff 100%);
  color: white;
  box-shadow: 0 2px 8px rgba(24, 144, 255, 0.3);
}

.user-login-status {
  display: flex;
  align-items: center;
}

.user-info {
  cursor: pointer;
  padding: 8px 12px;
  border-radius: 8px;
  transition: all 0.3s ease;
  display: flex;
  align-items: center;
  gap: 8px;
}

.user-info:hover {
  background: rgba(24, 144, 255, 0.1);
}

.user-avatar {
  border: 2px solid rgba(24, 144, 255, 0.2);
}

.username {
  font-weight: 500;
  color: #333;
}

.token-info {
  font-size: 14px;
  color: #1a1a1a;
  background: rgba(24, 144, 255, 0.1);
  padding: 2px 8px;
  border-radius: 4px;
  border: 1px solid rgba(24, 144, 255, 0.2);
}

.user-status {
  display: flex;
  align-items: center;
}

.admin-badge {
  font-size: 12px;
  color: #fff;
  background: linear-gradient(135deg, #ff6b6b 0%, #ee5a24 100%);
  padding: 2px 8px;
  border-radius: 12px;
  font-weight: 500;
  box-shadow: 0 1px 4px rgba(238, 90, 36, 0.3);
  display: flex;
  align-items: center;
  gap: 2px;
  white-space: nowrap;
}

.member-badge {
  font-size: 12px;
  color: #fff;
  background: linear-gradient(135deg, #ffd700 0%, #ff8c00 100%);
  padding: 2px 8px;
  border-radius: 12px;
  font-weight: 500;
  box-shadow: 0 1px 4px rgba(255, 140, 0, 0.3);
  display: flex;
  align-items: center;
  gap: 2px;
  white-space: nowrap;
}

.token-info {
  font-size: 12px;
  color: #fff;
  background: linear-gradient(135deg, #1890ff 0%, #096dd9 100%);
  padding: 2px 8px;
  border-radius: 12px;
  font-weight: 500;
  box-shadow: 0 1px 4px rgba(24, 144, 255, 0.3);
  display: flex;
  align-items: center;
  gap: 2px;
  white-space: nowrap;
}

.loading-info {
  font-size: 12px;
  color: #8c8c8c;
  font-style: italic;
  padding: 2px 8px;
  background: rgba(140, 140, 140, 0.1);
  border-radius: 12px;
  white-space: nowrap;
}

.login-btn {
  border-radius: 8px;
  font-weight: 500;
  padding: 8px 20px;
  height: auto;
}

.user-dropdown {
  border-radius: 8px;
  border: 1px solid #e8f4fd;
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.1);
}

.profile-item {
  border-radius: 6px;
  margin: 4px;
}

.profile-item:hover {
  background: rgba(24, 144, 255, 0.1);
  color: #1890ff;
}

.logout-item {
  border-radius: 6px;
  margin: 4px;
}

.logout-item:hover {
  background: rgba(24, 144, 255, 0.1);
  color: #1890ff;
}

/* 响应式设计 */
@media (max-width: 768px) {
  .header {
    padding: 0 16px;
  }

  .logo-image {
    height: 28px;
  }


  .nav-menu {
    display: none;
  }
}
</style>

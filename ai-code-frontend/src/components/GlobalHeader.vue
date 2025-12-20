<template>
  <a-layout-header class="header">
    <a-row :wrap="false" align="middle" class="header-content">
      <!-- 左侧：Logo和标题 -->
      <a-col :style="{ width: '220px', flexShrink: 0 }">
        <RouterLink to="/" class="logo-link">
          <div class="header-left">
            <div class="logo-container">
              <img src="/src/assets/ran-logo.png" alt="AI Code" class="logo-image" />

            </div>
          </div>
        </RouterLink>
      </a-col>
      <!-- 中间：导航菜单 -->
      <a-col flex="1" style="min-width: 0;">
        <a-menu
          v-model:selectedKeys="selectedKeys"
          mode="horizontal"
          :items="menuItems"
          @click="handleMenuClick"
          class="nav-menu"
        />
      </a-col>
      <!-- 右侧：用户操作区域 -->
      <a-col :style="{ width: '300px', flexShrink: 0, textAlign: 'right' }">
        <div class="user-login-status">
          <div v-if="loginUserStore.loginUser.id" class="user-status-container">
            <!-- 用户状态徽章（独立显示，不参与下拉菜单） -->
            <a-tag v-if="tokenInfoStore.tokenInfo && tokenInfoStore.tokenInfo.userRole === 'admin'" color="green">
              管理员
            </a-tag>
            <a-tag v-else-if="tokenInfoStore.tokenInfo && tokenInfoStore.tokenInfo.userRole === 'member'" color="orange">
              测试
            </a-tag>
            <a-tag v-else-if="tokenInfoStore.tokenInfo" :color="getTokenTagColor(tokenInfoStore.tokenInfo)">
              使用量: {{ tokenInfoStore.tokenInfo.usedTokens }}/{{ tokenInfoStore.tokenInfo.totalTokens }}
            </a-tag>
            <a-tag v-else color="default">
              加载中...
            </a-tag>

            <!-- 用户头像和用户名（可点击下拉菜单） -->
            <a-dropdown>
              <a-space class="user-info">
                <a-avatar :src="loginUserStore.loginUser.userAvatar" class="user-avatar" />
                <span class="username">{{ loginUserStore.loginUser.userName ?? loginUserStore.loginUser.userAccount ?? '无名' }}</span>
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
import { computed, h, ref, onMounted, watch } from 'vue'
import { useRouter } from 'vue-router'
import { type MenuProps, message } from 'ant-design-vue'
import { useLoginUserStore } from '../stores/loginUser'
import { useTokenInfoStore } from '../stores/tokenInfo'
import { userLogout } from '../api/userController'
import { LogoutOutlined, HomeOutlined, UserOutlined, StarOutlined } from '@ant-design/icons-vue'

const loginUserStore = useLoginUserStore()
const tokenInfoStore = useTokenInfoStore()
const router = useRouter()
// 当前选中菜单
const selectedKeys = ref<string[]>(['/'])
// 监听路由变化，更新当前选中菜单
router.afterEach((to, from, next) => {
  selectedKeys.value = [to.path]
})

// 监听用户登录状态变化，自动刷新token信息
watch(() => loginUserStore.loginUser.id, async (newUserId, oldUserId) => {
  if (newUserId && newUserId !== oldUserId) {
    // 用户登录或切换用户时，刷新token信息
    await tokenInfoStore.refreshTokenInfo()
  } else if (!newUserId) {
    // 用户退出登录时，清空token信息
    tokenInfoStore.setTokenInfo(null)
  }
}, { immediate: true })

// 根据token使用量计算标识颜色
const getTokenTagColor = (tokenInfo: API.User) => {
  if (!tokenInfo || !tokenInfo.totalTokens || !tokenInfo.usedTokens) {
    return 'blue' // 默认蓝色
  }
  
  const usagePercentage = (tokenInfo.usedTokens / tokenInfo.totalTokens) * 100
  
  // 使用量达到90%时显示红色
  if (usagePercentage >= 90) {
    return 'red'
  }
  
  // 使用量达到70%时显示橙色
  if (usagePercentage >= 70) {
    return 'orange'
  }
  
  // 默认蓝色
  return 'blue'
}

// 组件挂载时获取token信息
onMounted(async () => {
  console.log('GlobalHeader: 组件挂载')
  // 确保用户信息已加载
  if (!loginUserStore.loginUser.id) {
    await loginUserStore.fetchLoginUser()
  }
  // 如果用户已登录，获取token信息
  if (loginUserStore.loginUser.id) {
    await tokenInfoStore.refreshTokenInfo()
  }
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
  width: 100%;
}

.header-content {
  width: 100%;
  height: 100%;
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

.user-status-container {
  display: flex;
  align-items: center;
  gap: 12px;
  justify-content: flex-end; /* 右对齐，确保内容不会溢出 */
  flex-wrap: nowrap; /* 防止换行 */
}

/* 确保所有用户状态标签垂直对齐 */
.user-status-container .ant-tag {
  display: inline-flex;
  align-items: center;
  vertical-align: middle;
  line-height: 1;
  white-space: nowrap; /* 防止文本换行 */
  min-height: 24px; /* 确保最小高度一致 */
  flex-shrink: 0; /* 防止标签被压缩 */
}

.user-info {
  cursor: pointer;
  padding: 8px 16px;
  border-radius: 8px;
  transition: all 0.3s ease;
  display: flex;
  align-items: center;
  gap: 8px;
  flex-shrink: 0; /* 防止用户信息被压缩 */
  white-space: nowrap; /* 防止用户名换行 */
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
  font-size: 14px;
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

  .user-status-container {
    gap: 8px; /* 移动端减少间距 */
  }

  .user-status-container .ant-tag {
    font-size: 12px; /* 移动端减小字体 */
    min-height: 20px;
    padding: 2px 6px;
  }

  .user-info {
    padding: 6px 10px; /* 移动端减少内边距 */
  }

  .username {
    font-size: 13px; /* 移动端减小字体 */
    max-width: 80px; /* 仅在移动端限制宽度并截断 */
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .nav-menu {
    display: none;
  }
}
</style>

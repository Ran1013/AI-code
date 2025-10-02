<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { listFeaturedAppByPage } from '../api/appController'
import { likeApp, unlikeApp, appThumbList } from '../api/thumbController'
import { getDeployUrl } from '../config/env'
import AppCard from '../components/AppCard.vue'
import { message } from 'ant-design-vue'
import { toAppIdString } from '@/utils/appIdUtils'

// 防抖函数
const debounce = (func: Function, delay: number) => {
  let timeoutId: ReturnType<typeof setTimeout>
  return (...args: any[]) => {
    clearTimeout(timeoutId)
    timeoutId = setTimeout(() => func.apply(null, args), delay)
  }
}

const router = useRouter()

// 精选应用数据
const featuredApps = ref<API.AppThumbDetailVO[]>([])
const featuredAppsPage = reactive({
  current: 1,
  pageSize: 6,
  total: 0,
})

// 热门app数据
const hotApps = ref<API.AppThumbVO[]>([])
// 加载精选应用
const loadFeaturedApps = async () => {
  try {
    const res = await listFeaturedAppByPage({
      pageNum: featuredAppsPage.current,
      pageSize: featuredAppsPage.pageSize,
      sortField: 'createTime',
      sortOrder: 'desc',
    })

    console.log('精选应用接口返回数据：', res)
    
    if (res.data.code === 0 && res.data.data) {
      featuredApps.value = res.data.data.records || []
      featuredAppsPage.total = res.data.data.totalRow || 0
      console.log('精选应用数据已更新：', featuredApps.value)
    } else {
      console.error('精选应用接口返回错误：', res.data)
    }
  } catch (error) {
    console.error('加载精选应用失败：', error)
  }
}

// 加载热门应用
const loadHotApps = async () => {
  try {
    const res = await appThumbList()
    if (res.data && res.data.data) {
      hotApps.value = res.data.data
    }
  } catch (error) {
    console.error('加载热门应用失败：', error)
  }
}

// 查看对话
const viewChat = (appId: string | number | undefined) => {
  if (appId) {
    router.push(`/app/chat/${appId}?view=1`)
  }
}

// 查看作品
const viewWork = (app: API.AppThumbDetailVO) => {
  if (app.deployKey) {
    const url = getDeployUrl(app.deployKey)
    window.open(url, '_blank')
  }
}

// 团队管理
const viewTeamManagement = (appId: string | number | undefined) => {
  if (appId) {
    router.push(`/app/detail/${appId}`)
  }
}

// 兼容不同字段来源，安全提取榜单项的 AppId
const getHotAppId = (app: Partial<API.AppThumbVO> & { id?: number | string }): string | undefined => {
  // 优先使用 appId，其次兼容 id 字段
  const rawId = app?.appId ?? app?.id
  return toAppIdString(rawId)
}

// 查看热门应用详情（将长整型ID安全转换为字符串，避免精度问题）
const viewHotApp = (appId: number | string | undefined) => {
  const appIdStr = toAppIdString(appId as any)
  if (appIdStr) {
    router.push(`/app/chat/${appIdStr}?view=1`)
  } else {
    console.warn('无效的appId：', appId)
  }
}

// 点击热门应用（从对象中提取ID并跳转）
const onClickHotApp = (app: any) => {
  const appIdStr = getHotAppId(app)
  if (appIdStr) {
    router.push(`/app/chat/${appIdStr}?view=1`)
  } else {
    console.warn('热门应用缺少ID字段：', app)
  }
}

// 实际的点赞处理函数
const performLike = async (appId: string, liked: boolean) => {
  try {
    let success = false
    if (liked) {
      // 点赞
      const res = await likeApp({ appId: appId })
      success = res.data.code === 0 && (res.data.data === true)
    
    } else {
      // 取消点赞
      const res = await unlikeApp({ appId: appId })
      success = res.data.code === 0 && (res.data.data === true)
    }

    if (success) {
      message.success(liked ? '点赞成功' : '取消点赞成功')
    } else {
      message.error(liked ? '点赞失败' : '取消点赞失败')
    }
  } catch (error) {
    console.error('点赞操作失败：', error)
    message.error('操作失败，请稍后重试')
  }
}

// 防抖版本的点赞处理函数（500ms防抖）
const handleLike = debounce(performLike, 500)

// 页面加载时获取数据
onMounted(() => {
  loadFeaturedApps()
  loadHotApps()
})
</script>

<template>
  <div id="recommendPage">
    <div class="container">
      <!-- 页面标题 -->
      <div class="page-header">
        <h1 class="page-title">
          <span class="page-icon">⭐</span>
          推荐案例
        </h1>
        <p class="page-subtitle">精选优质应用案例，助您快速上手</p>
      </div>

      <div class="content-wrapper">
        <!-- 左侧主要内容区域 -->
        <div class="main-content">
          <div class="featured-section">
            <div class="section-header">
              <h2 class="section-title">精选案例</h2>
            </div>
            
            <div class="featured-grid">
              <AppCard
                v-for="app in featuredApps"
                :key="app.id"
                :app="app"
                :featured="true"
                @view-chat="viewChat"
                @view-work="viewWork"
                @team-management="viewTeamManagement"
                @like="handleLike"
              />
              <div v-if="featuredApps.length === 0" class="empty-state">
                <div class="empty-icon">📄</div>
                <p class="empty-text">暂无案例</p>
              </div>
            </div>
            
            <div v-if="featuredAppsPage.total > 0" class="pagination-wrapper">
              <a-pagination
                v-model:current="featuredAppsPage.current"
                v-model:page-size="featuredAppsPage.pageSize"
                :total="featuredAppsPage.total"
                :show-size-changer="false"
                :show-total="(total: number) => `共 ${total} 个案例`"
                @change="loadFeaturedApps"
              />
            </div>
          </div>
        </div>

        <!-- 右侧边栏 -->
        <div class="sidebar">
          <!-- 热门题目榜 -->
          <div class="sidebar-section">
            <div class="sidebar-header">
              <h3 class="sidebar-title">热门app榜</h3>
              <a href="#" class="more-link"></a>
            </div>
            <div class="hot-topics-list">
              <div
                v-for="(app, index) in hotApps"
                :key="app.appId ?? index"
                class="topic-item"
                @click="onClickHotApp(app)"
              >
                <span class="topic-rank">{{ index + 1 }}</span>
                <span class="topic-title">{{ app.appName || '未命名应用' }}</span>
                <span class="topic-views">{{ app.thumbCount || 0 }} 点赞</span>
              </div>
            </div>
          </div>


        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
#recommendPage {
  width: 100%;
  min-height: 100vh;
  background: linear-gradient(135deg, #f0f8ff 0%, #e6f3ff 50%, #f5f9ff 100%);
  position: relative;
}

.container {
  max-width: 1200px;
  margin: 0 auto;
  padding: 40px 24px;
  position: relative;
  z-index: 2;
}

/* 页面标题 */
.page-header {
  text-align: center;
  margin-bottom: 40px;
}

.page-title {
  font-size: 36px;
  font-weight: 700;
  margin: 0 0 16px;
  color: #1a1a1a;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 12px;
}

.page-icon {
  font-size: 32px;
}

.page-subtitle {
  font-size: 16px;
  color: #666;
  margin: 0;
}

/* 内容布局 */
.content-wrapper {
  display: grid;
  grid-template-columns: 1fr 300px;
  gap: 40px;
  align-items: start;
}

/* 左侧主要内容 */
.main-content {
  background: rgba(255, 255, 255, 0.9);
  border-radius: 16px;
  padding: 32px;
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.08);
  backdrop-filter: blur(20px);
}

.featured-section {
  width: 100%;
}

.section-header {
  margin-bottom: 32px;
}

.section-title {
  font-size: 24px;
  font-weight: 600;
  margin: 0;
  color: #1a1a1a;
}

.featured-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: 24px;
  margin-bottom: 32px;
}

/* 右侧边栏 */
.sidebar {
  display: flex;
  flex-direction: column;
  gap: 24px;
}

.sidebar-section {
  background: rgba(255, 255, 255, 0.9);
  border-radius: 12px;
  padding: 24px;
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.06);
  backdrop-filter: blur(20px);
}

.sidebar-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}

.sidebar-title {
  font-size: 18px;
  font-weight: 600;
  margin: 0;
  color: #1a1a1a;
}

.more-link {
  color: #1890ff;
  font-size: 14px;
  text-decoration: none;
  transition: color 0.3s ease;
}

.more-link:hover {
  color: #40a9ff;
}

/* 热门题目列表 */
.hot-topics-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.topic-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 8px 0;
  border-bottom: 1px solid rgba(0, 0, 0, 0.06);
  cursor: pointer;
  transition: all 0.3s ease;
}

.topic-item:hover {
  background: rgba(24, 144, 255, 0.05);
  border-radius: 8px;
  padding: 8px 12px;
  margin: 0 -12px;
}

.topic-item:last-child {
  border-bottom: none;
}

.topic-rank {
  width: 20px;
  height: 20px;
  background: #ff4d4f;
  color: white;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
  font-weight: 600;
  flex-shrink: 0;
}

.topic-title {
  flex: 1;
  font-size: 14px;
  color: #333;
  line-height: 1.4;
}

.topic-views {
  font-size: 12px;
  color: #999;
  flex-shrink: 0;
}



/* 空状态 */
.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 60px 20px;
  background: rgba(255, 255, 255, 0.6);
  border-radius: 12px;
  border: 2px dashed rgba(24, 144, 255, 0.2);
  min-height: 200px;
}

.empty-icon {
  font-size: 48px;
  margin-bottom: 16px;
  opacity: 0.5;
}

.empty-text {
  color: #999;
  font-size: 16px;
  margin: 0;
}

/* 分页 */
.pagination-wrapper {
  display: flex;
  justify-content: center;
  margin-top: 32px;
}

/* 响应式设计 */
@media (max-width: 1024px) {
  .content-wrapper {
    grid-template-columns: 1fr;
    gap: 24px;
  }
  
  .sidebar {
    order: -1;
  }
}

@media (max-width: 768px) {
  .container {
    padding: 20px 16px;
  }
  
  .page-title {
    font-size: 28px;
  }
  
  .page-icon {
    font-size: 24px;
  }
  
  .main-content {
    padding: 24px 20px;
  }
  
  .featured-grid {
    grid-template-columns: 1fr;
  }
  
  .sidebar-section {
    padding: 20px;
  }
  
  .section-title {
    font-size: 20px;
  }
}
</style>


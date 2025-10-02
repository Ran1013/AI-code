<template>
  <div id="appChatPage">
    <!-- 顶部栏 -->
    <div class="header-bar">
      <div class="header-left">
        <h1 class="app-name">
          {{ appInfo?.appName || '网站生成器' }}
          <span v-if="appInfo?.codeGenType" class="code-gen-type-tag">
            {{ formatCodeGenType(appInfo.codeGenType) }}
          </span>
        </h1>
        <!-- 显示当前在线用户（仅团队应用） -->
        <div v-if="isTeamApp && onlineUsers.length > 0" class="online-users">
          <span class="online-label">在线用户：</span>
          <a-avatar 
            v-for="user in onlineUsers" 
            :key="user.id" 
            :src="user.userAvatar" 
            :title="user.userName"
            size="small"
            :class="{ 'editing-user': currentEditingUser?.id === user.id }"
          />
          <span v-if="currentEditingUser" class="editing-indicator">
            {{ currentEditingUser.userName }} 正在编辑
          </span>
        </div>
        <!-- WebSocket连接状态（仅团队应用） -->
        <div v-if="isTeamApp" class="ws-status">
          <span class="status-dot" :class="{ 'connected': wsConnected, 'disconnected': !wsConnected }"></span>
          <span class="status-text">{{ wsConnected ? '已连接' : '连接中...' }}</span>
        </div>
      </div>
              <div class="header-right">
          <a-button type="primary" @click="goHome" class="home-btn">
            <template #icon>
              <HomeOutlined />
            </template>
            返回首页
          </a-button>
          <a-button type="default" @click="showAppDetail">
            <template #icon>
              <InfoCircleOutlined />
            </template>
            应用详情
          </a-button>
          <a-button type="default" @click="downloadCode" :loading="downloading" :disabled="!appInfo?.id || !checkDownloadPermission()">
            <template #icon>
              <DownloadOutlined />
            </template>
            下载代码
          </a-button>
          <a-button type="primary" @click="deployApp" :loading="deploying" :disabled="!appInfo?.id || !checkDeployPermission()">
            <template #icon>
              <CloudUploadOutlined />
            </template>
            部署按钮
          </a-button>
        </div>
    </div>

    <!-- 主要内容区域 -->
    <div class="main-content">
      <!-- 左侧对话区域 -->
      <div class="chat-section">
        <!-- 消息区域 -->
        <div class="messages-container" ref="messagesContainer">
          <!-- 加载更多按钮 -->
          <div v-if="hasMoreHistory" class="load-more-container">
            <a-button 
              type="link" 
              @click="loadMoreHistory" 
              :loading="loadingHistory"
              size="small"
            >
              <template #icon>
                <ReloadOutlined />
              </template>
              加载更多历史消息
            </a-button>
          </div>

          <div v-for="(message, index) in messages" :key="message.id || index" class="message-item">
            <div v-if="message.messageType === 'user'" class="user-message">
              <div class="message-content">
                <!-- 根据消息类型显示不同内容 -->
                <div v-if="message.type === 'image'" class="message-image-content">
                  <img 
                    :src="message.content" 
                    :alt="`用户上传的图片`" 
                    class="message-single-image" 
                    @click="previewImage(message.content)"
                  />
                </div>
                <div v-else class="message-text">{{ message.content }}</div>
                <div v-if="message.userName" class="message-user">
                  {{ message.userName }}
                </div>
              </div>
              <div class="message-avatar">
                <a-avatar :src="message.userAvatar || loginUserStore.loginUser.userAvatar" />
              </div>
            </div>
            <div v-else class="ai-message">
              <div class="message-avatar">
                <a-avatar :src="aiAvatar" />
              </div>
              <div class="message-content">
                <MarkdownRenderer v-if="message.content" :content="message.content" />
                <div v-if="message.loading" class="loading-indicator">
                  <a-spin size="small" />
                  <span>AI 正在思考...</span>
                </div>
                <!-- 显示信息状态和用户名 -->
                <div v-if="message.userName || (message.status && (message.status === 1 || message.status === 2))" class="message-footer">
                  <div v-if="message.userName" class="message-user ai-message-user">
                    回复 {{ message.userName }}
                    <!-- 所有用户都显示token消耗信息 -->
                    <span v-if="message.tokenInfo" class="token-consumption">
                      本次消耗: {{ message.tokenInfo.consumedTokens || 0 }} tokens | 剩余额度: {{ message.tokenInfo.remainingTokens || 0 }} tokens
                    </span>
                  </div>
                  <div v-if="message.status && (message.status === 1 || message.status === 2)" class="message-status">
                    <a-tag :color="message.status === 1 ? 'orange' : 'red'" size="small">
                      {{ message.status === 1 ? '停止生成' : 'AI异常中断' }}
                    </a-tag>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>

        <!-- 用户消息输入框 -->
        <div v-if="hasChatPermission" class="input-container">
          <!-- 选中元素信息显示 -->
          <div v-if="selectedElement" class="selected-element-info">
            <a-alert
              :message="`已选中元素：${selectedElement.tagName}`"
              :description="`选择器：${selectedElement.selector}${selectedElement.textContent ? ' | 内容：' + selectedElement.textContent : ''}`"
              type="info"
              show-icon
              closable
              @close="clearSelectedElement"
              class="element-alert"
            />
          </div>
          
          <!-- 上传的图片预览 -->
          <div v-if="uploadedImages.length > 0" class="uploaded-images">
            <div class="images-title">已上传的图片：</div>
            <div class="images-grid">
              <div 
                v-for="(imageUrl, index) in uploadedImages" 
                :key="index"
                class="image-item"
              >
                <img :src="imageUrl" :alt="`图片${index + 1}`" class="preview-image" />
                <div class="image-overlay">
                  <a-button 
                    type="text" 
                    size="small" 
                    class="delete-btn"
                    @click="uploadedImages.splice(index, 1)"
                  >
                    ✕
                  </a-button>
                </div>
              </div>
            </div>
          </div>
          
          <div class="input-wrapper">
            <a-tooltip v-if="isTeamApp && !canEdit" :title="`${currentEditingUser?.userName || '其他用户'} 正在对话中，请稍候...`" placement="top">
              <a-textarea
                v-model:value="userInput"
                placeholder="请描述你想生成的网站，越详细效果越好哦"
                :rows="4"
                :maxlength="1000"
                @keydown.enter.prevent="sendMessage"
                :disabled="isGenerating || isCancelling || !canEdit"
              />
            </a-tooltip>
            <a-textarea
              v-else
              v-model:value="userInput"
              placeholder="请描述你想生成的网站，越详细效果越好哦"
              :rows="4"
              :maxlength="1000"
              @keydown.enter.prevent="sendMessage"
              :disabled="isGenerating || isCancelling"
            />
            <div class="input-actions">
              <!-- 上传图片按钮 -->
              <UploadButton
                :disabled="uploading || isGenerating || isCancelling || (isTeamApp && !canEdit)"
                :multiple="true"
                :max-count="5"
                accept="image/*"
                button-text="上传图片"
                button-type="default"
                button-size="middle"
                :show-icon="true"
                class="upload-btn"
                @upload-success="handleUploadSuccess"
                @upload-error="handleUploadError"
                @upload-start="handleUploadStart"
                @upload-end="handleUploadEnd"
              />
              
              <a-tooltip v-if="isTeamApp && (isOtherUserGenerating || !canEdit)" :title="isOtherUserGenerating ? `${otherGeneratingUser?.userName || '其他用户'} 正在生成中，请稍候...` : `${currentEditingUser?.userName || '其他用户'} 正在对话中，请稍候...`" placement="top">
                <a-button
                  type="default"
                  @click="toggleVisualEdit"
                  :disabled="!previewUrl || isGenerating || isCancelling || (isTeamApp && (isOtherUserGenerating || !canEdit))"
                  class="visual-edit-btn"
                  :class="{ 'edit-mode-active': isVisualEditMode }"
                >
                  <template #icon>
                    <EditOutlined />
                  </template>
                  {{ isVisualEditMode ? '退出编辑' : '可视编辑' }}
                </a-button>
              </a-tooltip>
              <a-button
                v-else
                type="default"
                @click="toggleVisualEdit"
                :disabled="!previewUrl || isGenerating || isCancelling"
                class="visual-edit-btn"
                :class="{ 'edit-mode-active': isVisualEditMode }"
              >
                <template #icon>
                  <EditOutlined />
                </template>
                {{ isVisualEditMode ? '退出编辑' : '可视编辑' }}
              </a-button>
              <a-button
                v-if="!isGenerating"
                type="primary"
                @click="sendMessage"
                :disabled="(isTeamApp && !canEdit)"
              >
                <template #icon>
                  <SendOutlined />
                </template>
              </a-button>
              <a-button
                v-else
                type="primary"
                danger
                @click="stopGeneration"
                :loading="isCancelling"
              >
                <template #icon>
                  <StopOutlined />
                </template>
                停止生成
              </a-button>
            </div>
          </div>
        </div>

        <!-- 权限提示（没有权限时显示） -->
        <div v-else class="permission-container">
          <div class="permission-alert">
            <a-alert 
              message="您没有对话权限" 
              description="您需要具有编辑者或管理员权限才能进行对话。请联系应用创建者为您分配相应权限。"
              type="warning" 
              show-icon 
              banner
            />
          </div>
        </div>
      </div>

      <!-- 右侧网页展示区域 -->
      <div class="preview-section">
        <div class="preview-header">
          <h3>
            <span v-if="isOtherUserGenerating && otherGeneratingUser">
              {{ otherGeneratingUser.userName }} 生成的网页展示
            </span>
            <span v-else>
              生成后的网页展示
            </span>
          </h3>
          <div class="preview-actions">
            <a-button v-if="previewUrl && !isGenerating && !isOtherUserGenerating" type="link" @click="openInNewTab">
              <template #icon>
                <ExportOutlined />
              </template>
              新窗口打开
            </a-button>
          </div>
        </div>
        <div class="preview-content">
          <div v-if="!previewUrl && !isGenerating && !isOtherUserGenerating" class="preview-placeholder">
            <div class="placeholder-icon">🌐</div>
            <p>网站文件生成完成后将在这里展示</p>
          </div>
          <div v-else-if="isGenerating" class="preview-loading">
            <a-spin size="large" />
            <p>正在生成网站...</p>
          </div>
          <div v-else-if="isOtherUserGenerating && otherGeneratingUser" class="preview-loading">
            <a-spin size="large" />
            <p>{{ otherGeneratingUser.userName }} 正在生成网站...</p>
          </div>
          <iframe
            v-else
            ref="previewIframe"
            :src="previewUrl"
            class="preview-iframe"
            frameborder="0"
            @load="onIframeLoad"
          ></iframe>
        </div>
      </div>
    </div>

    <!-- 应用详情弹窗 -->
    <AppDetailModal
      v-model:open="appDetailVisible"
      :app="appInfo"
      :show-actions="isOwner || isAdmin || checkManagePermission()"
      @edit="editApp"
      @delete="deleteApp"
    />

    <!-- 部署成功弹窗 -->
    <DeploySuccessModal
      v-model:open="deployModalVisible"
      :deploy-url="deployUrl"
      @open-site="openDeployedSite"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, nextTick, onUnmounted, computed, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import { useLoginUserStore } from '@/stores/loginUser'
import UploadButton from '@/components/UploadButton.vue'
import {
  getAppById,
  deployApp as deployAppApi,
  deleteMyApp as deleteMyAppApi,
  downloadAppCode,
  cancelCodeGeneration,
} from '@/api/appController'

import { canEditApp, hasPermission, PERMISSIONS } from '../../utils/permissionUtils'
import { listAppChatHistory } from '../../api/chatHistoryController'
import { CodeGenTypeEnum, formatCodeGenType } from '@/utils/codeGenTypes'
import request from '@/request'
import { getUserTokenInfo } from '../../api/userController'

import MarkdownRenderer from '@/components/MarkdownRenderer.vue'
import AppDetailModal from '@/components/AppDetailModal.vue'
import DeploySuccessModal from '@/components/DeploySuccessModal.vue'
import aiAvatar from '@/assets/logo.png'
import { API_BASE_URL, getStaticPreviewUrl } from '@/config/env'
import { toAppIdNumber, toAppIdString, getAppIdForApi } from '@/utils/appIdUtils'
import VisualEditor, { type SelectedElement } from '@/utils/visualEditor'

import {
  CloudUploadOutlined,
  SendOutlined,
  ExportOutlined,
  InfoCircleOutlined,
  ReloadOutlined,
  DownloadOutlined,
  EditOutlined,
  HomeOutlined,
  StopOutlined,
} from '@ant-design/icons-vue'

const route = useRoute()
const router = useRouter()
const loginUserStore = useLoginUserStore()

// 应用信息
const appInfo = ref<API.AppVO>()
const appId = ref<string>()

// WebSocket相关
let websocket: WebSocket | null = null
let wsReconnectTimer: NodeJS.Timeout | null = null
let wsReconnectAttempts = 0
const maxReconnectAttempts = 5
const reconnectDelay = 3000 // 3秒
const onlineUsers = ref<API.UserVO[]>([])
const currentEditingUser = ref<API.UserVO | null>(null)
const canEdit = computed(() => !currentEditingUser.value || currentEditingUser.value.id === loginUserStore.loginUser.id)
const wsConnected = ref(false)

// 流式内容同步相关
const streamingContent = ref<string>('')
const streamingUser = ref<API.UserVO | null>(null)
const isStreamingFromOther = ref(false)

// 其他用户生成状态相关
const isOtherUserGenerating = ref(false)
const otherGeneratingUser = ref<API.UserVO | null>(null)

// 更新消息的token信息
const updateTokenInfoForMessage = async (messageIndex: number) => {
  try {
    const res = await getUserTokenInfo()
    if (res.data.code === 0 && res.data.data) {
      const tokenData = res.data.data
      const message = messages.value[messageIndex]
      if (message && message.messageType === 'ai') {
        // 添加用户角色信息
        message.userRole = tokenData.userRole
        
        // 所有用户都计算和显示token信息
        // 计算本次消耗的token（这里简化处理，实际应该从后端获取）
        const consumedTokens = Math.max(100, Math.floor((message.content?.length || 0) / 2))
        const remainingTokens = (tokenData.totalTokens || 10000) - (tokenData.usedTokens || 0)
        
        message.tokenInfo = {
          consumedTokens,
          remainingTokens
        }
        
        console.log(`用户 ${tokenData.userRole} 的token信息:`, message.tokenInfo)
      }
    }
  } catch (error) {
    console.error('获取token信息失败:', error)
  }
}

// 对话相关
interface Message {
  id?: number
  messageType: 'user' | 'ai'
  content: string
  loading?: boolean
  createTime?: string
  userName?: string
  userAvatar?: string
  userRole?: string // 用户角色：user/admin/member
  status?: number
  type?: string // 消息类型：text-文字，image-图片
  tokenInfo?: {
    consumedTokens?: number
    remainingTokens?: number
  }
}

const messages = ref<Message[]>([])
const userInput = ref('')
const isGenerating = ref(false)
const messagesContainer = ref<HTMLElement>()

// 上传相关状态
const uploadedImages = ref<string[]>([])
const uploading = ref(false)

// 停止生成相关
let currentEventSource: EventSource | null = null
const isCancelling = ref(false)

// 对话历史相关
const loadingHistory = ref(false)
const hasMoreHistory = ref(false)
const lastCreateTime = ref<string>('')

// 预览相关
const previewUrl = ref('')
const previewReady = ref(false)

// 部署相关
const deploying = ref(false)
const deployModalVisible = ref(false)
const deployUrl = ref('')

// 下载相关
const downloading = ref(false)

// 权限相关
const isOwner = computed(() => {
  return appInfo.value?.userId === loginUserStore.loginUser.id
})

const isAdmin = computed(() => {
  return loginUserStore.loginUser.userRole === 'admin'
})

// 用户是否有对话权限
const hasChatPermission = ref(false)

// 是否为团队应用
const isTeamApp = computed(() => {
  return appInfo.value?.isTeam === 1
})

// 应用详情相关
const appDetailVisible = ref(false)

// 图片预览相关
const imagePreviewVisible = ref(false)
const previewImageUrl = ref('')

// 可视化编辑相关
const visualEditor = new VisualEditor()
const isVisualEditMode = ref(false)
const selectedElement = ref<SelectedElement | null>(null)
const previewIframe = ref<HTMLIFrameElement>()

// WebSocket连接
const connectWebSocket = () => {
  if (!appId.value || !isTeamApp.value) return

  try {
    // 构建WebSocket URL
    let wsUrl: string
    
    // 在开发环境中，使用当前页面的 host 连接 WebSocket，这样可以通过 Vite 代理
    if (import.meta.env.DEV) {
      const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:'
      wsUrl = `${protocol}//${window.location.host}/api/ws/app?appId=${appId.value}`
    } else {
      // 生产环境使用配置的 API_BASE_URL
      wsUrl = API_BASE_URL.replace('http', 'ws') + `/ws/app?appId=${appId.value}`
    }
    
    console.log('正在连接WebSocket:', wsUrl)
    websocket = new WebSocket(wsUrl)

    websocket.onopen = () => {
      console.log('WebSocket连接已建立')
      wsConnected.value = true
      wsReconnectAttempts = 0 // 重置重连计数器
      
      // 清除重连定时器
      if (wsReconnectTimer) {
        clearTimeout(wsReconnectTimer)
        wsReconnectTimer = null
      }
      
      // WebSocket连接建立后，等待服务端发送INFO消息获取初始状态
    }

    websocket.onmessage = (event) => {
      try {
        const data = JSON.parse(event.data)
        handleWebSocketMessage(data)
      } catch (error) {
        console.error('解析WebSocket消息失败:', error)
      }
    }

    websocket.onerror = (error) => {
      console.error('WebSocket连接错误:', error)
      wsConnected.value = false
    }

    websocket.onclose = (event) => {
      console.log('WebSocket连接已关闭', event.code, event.reason)
      wsConnected.value = false

    }
  } catch (error) {
    console.error('建立WebSocket连接失败:', error)
  }
}

// 发送WebSocket消息
const sendWebSocketMessage = (message: any) => {
  if (websocket && websocket.readyState === WebSocket.OPEN) {
    websocket.send(JSON.stringify(message))
  }
}



// 处理WebSocket消息
const handleWebSocketMessage = (data: any) => {
  console.log('收到WebSocket消息:', data)
  
  switch (data.messageType) {
    case 'INFO':
      // 处理服务端初始信息
      console.log('收到服务端初始信息:', data)
      if (data.onlineUsers) {
        onlineUsers.value = data.onlineUsers
      }
      if (data.currentEditingUser) {
        currentEditingUser.value = data.currentEditingUser
      }
      break
    case 'USER_ENTER_EDIT':
      // 用户开始对话
      if (data.user) {
        currentEditingUser.value = data.user
        if (data.user.id !== loginUserStore.loginUser.id) {
          // 其他用户开始生成代码，显示加载状态
          isOtherUserGenerating.value = true
          otherGeneratingUser.value = data.user
          
          // 显示其他用户的输入消息
          if (data.editAction) {
            
            messages.value.push({
              messageType: 'user',
              content: data.editAction,
              userName: data.user.userName,
              userAvatar: data.user.userAvatar,
              type: data.type || 'text' // 根据消息类型设置
            })
            
            // 滚动到底部
            nextTick(() => {
              scrollToBottom()
            })
          }
        }
      }
      break
      
    case 'USER_EXIT_EDIT':
      // 用户结束对话
      if (data.user && data.user.id === currentEditingUser.value?.id) {
        currentEditingUser.value = null
        // 清除流式内容
        streamingContent.value = ''
        streamingUser.value = null
        isStreamingFromOther.value = false
        
        // 如果是其他用户结束生成，清除其他用户生成状态
        if (data.user.id !== loginUserStore.loginUser.id) {
          isOtherUserGenerating.value = false
          otherGeneratingUser.value = null
        }
        
        // 用户结束对话时重新调用updatePreview刷新浏览网站
        setTimeout(async () => {
        updatePreview()
      }, 1000)
      }
      break
      
    case 'AI_EDIT_ACTION':
      // AI对话内容流式推送
      if (data.user && data.user.id !== loginUserStore.loginUser.id) {
        // 其他用户的AI对话流式内容
        if (!isStreamingFromOther.value) {
          // 开始新的流式内容
          isStreamingFromOther.value = true
          streamingUser.value = data.user
          streamingContent.value = data.editAction || ''
          
          // 添加AI消息占位符
          messages.value.push({
            messageType: 'ai',
            content: '',
            loading: true,
            userName: data.user.userName,
            userAvatar: data.user.userAvatar
          })
        } else {
          // 继续追加流式内容
          streamingContent.value += data.editAction || ''
        }
        
        // 更新消息内容
        const lastMessage = messages.value[messages.value.length - 1]
        if (lastMessage && lastMessage.messageType === 'ai') {
          lastMessage.content = streamingContent.value
          lastMessage.loading = false
          scrollToBottom()
        }
      }
      break
      
    case 'ERROR':
      // 处理错误消息
      console.error('WebSocket错误:', data.message)
      message.error(data.message || 'WebSocket连接出现错误')
      break
  }
}

// 关闭WebSocket连接
const closeWebSocket = () => {
  if (websocket) {
    // 直接关闭WebSocket连接，服务端会自动处理用户离开
    websocket.close(1000, '用户主动离开') // 正常关闭码
    websocket = null
  }
  
  // 清除重连定时器
  if (wsReconnectTimer) {
    clearTimeout(wsReconnectTimer)
    wsReconnectTimer = null
  }
  
  // 重置重连计数器
  wsReconnectAttempts = 0
  wsConnected.value = false
}

// 显示应用详情
const showAppDetail = () => {
  appDetailVisible.value = true
}

// 处理上传成功
const handleUploadSuccess = (urls: string[]) => {
  uploadedImages.value = [...uploadedImages.value, ...urls]
}

// 处理上传错误
const handleUploadError = (error: string) => {
  message.error(error)
}

// 处理上传开始
const handleUploadStart = () => {
  uploading.value = true
}

// 处理上传结束
const handleUploadEnd = () => {
  uploading.value = false
}

// 检查用户对话权限 - 基于权限列表
const checkChatPermission = () => {

  
  if (!appInfo.value) return false
  
  // 获取用户权限列表
  const userPermissions = appInfo.value.permissionList || []
  
  // 创建者自动为管理员，拥有所有权限
  if (isOwner.value) {
    return true
  }

  
  // 检查是否有对话权限（app:edit）
  return canEditApp(userPermissions)
}

// 检查用户下载权限
const checkDownloadPermission = () => {
  if (!appInfo.value) return false
  
  // 创建者自动为管理员，拥有所有权限
  if (isOwner.value) {
    return true
  }
  
  const userPermissions = appInfo.value.permissionList || []
  return hasPermission(userPermissions, PERMISSIONS.APP_DOWNLOAD)
}

// 检查用户部署权限
const checkDeployPermission = () => {
  if (!appInfo.value) return false
  
  // 创建者自动为管理员，拥有所有权限
  if (isOwner.value) {
    return true
  }
  
  const userPermissions = appInfo.value.permissionList || []

  
  return hasPermission(userPermissions, PERMISSIONS.APP_DEPLOY)
}

// 检查用户管理权限
const checkManagePermission = () => {
  if (!appInfo.value) return false
  
  // 创建者自动为管理员，拥有所有权限
  if (isOwner.value) {
    return true
  }
  
  const userPermissions = appInfo.value.permissionList || []
  return hasPermission(userPermissions, PERMISSIONS.APP_USER_MANAGE)
}

// 获取应用信息
const fetchAppInfo = async () => {
  const id = route.params.id as string
  if (!id) {
    message.error('应用ID不存在')
    router.push('/')
    return
  }

  appId.value = id

  try {
    // 使用智能AppId处理，大数值保持字符串格式，小数值转换为number
    const res = await getAppById({ appId: id as string })
    if (res.data.code === 0 && res.data.data) {
      appInfo.value = res.data.data

      // 检查用户对话权限 - 基于权限列表
      hasChatPermission.value = checkChatPermission()


      // 加载对话历史
      await loadChatHistory()

      // 检查是否需要自动发送初始提示词
      if (appInfo.value?.initPrompt && isOwner.value && messages.value.length === 0) {
        await sendInitialMessage(appInfo.value.initPrompt)
      }

      // 如果有至少2条对话记录，展示网站
      if (messages.value.length >= 2) {
        updatePreview()
      }

      // 建立WebSocket连接
      connectWebSocket()
    } else {
      message.error('获取应用信息失败')
      router.push('/')
    }
  } catch (error) {
    console.error('获取应用信息失败：', error)
    message.error('获取应用信息失败')
    router.push('/')
  }
}

// 加载对话历史
const loadChatHistory = async (loadMore = false) => {
  if (!appId.value) return

  loadingHistory.value = true
  try {
    const params: API.listAppChatHistoryParams = {
      appId: appId.value,
      pageSize: 10,
    }

    // 如果是加载更多，添加游标参数
    if (loadMore && lastCreateTime.value) {
      params.lastCreateTime = lastCreateTime.value
    }

    const res = await listAppChatHistory(params)
    
    if (res.data.code === 0 && res.data.data) {
      const historyData = res.data.data
      const historyMessages = historyData.records || []
      
      // 转换历史消息格式
      const formattedMessages: Message[] = historyMessages.map(msg => {
        let content = msg.message || '';
        
        // 如果是AI消息且状态为异常中断，过滤技术错误信息
        if (msg.messageType === 'ai' && msg.status === 2) {
          content = sanitizeErrorMessage(content);
        }
        
        return {
          id: msg.id,
          messageType: msg.messageType === 'user' ? 'user' : 'ai',
          content: content,
          createTime: msg.createTime,
          userName: msg.userName,
          status: msg.status,
          type: msg.type || 'text', // 映射后端的type字段，text-文字，image-图片
        };
      })

      if (loadMore) {
        // 加载更多时，将新消息插入到现有消息前面
        // 后端返回的是按时间倒序排列（最新的在前），所以需要反转顺序
        // 这样插入到前面的就是更老的消息
        messages.value.unshift(...formattedMessages.reverse())
      } else {
        // 首次加载时，直接替换消息列表
        // 后端返回的是按时间倒序排列，需要反转确保老消息在上方
        messages.value = formattedMessages.reverse()
      }

      // 更新分页信息
      hasMoreHistory.value = historyData.totalRow ? messages.value.length < historyData.totalRow : false
      
      // 更新游标
      if (historyMessages.length > 0) {
        // 由于我们反转了消息顺序，所以最老的消息现在是数组的第一个元素
        const oldestMessage = historyMessages[0]
        lastCreateTime.value = oldestMessage.createTime || ''
      }

      await nextTick()
      if (!loadMore) {
        scrollToBottom()
      }
    } else {
      message.error('加载对话历史失败：' + res.data.message)
    }
  } catch (error) {
    console.error('加载对话历史失败：', error)
    message.error('加载对话历史失败')
  } finally {
    loadingHistory.value = false
  }
}

// 加载更多历史消息
const loadMoreHistory = async () => {
  await loadChatHistory(true)
}

// 发送初始消息
const sendInitialMessage = async (prompt: string) => {
  // 只在团队应用中发送WebSocket消息通知其他用户开始对话
  if (isTeamApp.value) {
    sendWebSocketMessage({
      messageType: 'USER_ENTER_EDIT',
      user: {
        id: loginUserStore.loginUser.id,
        userName: loginUserStore.loginUser.userName,
        userAvatar: loginUserStore.loginUser.userAvatar
      },
      editAction: prompt,
      type: 'text'
    })
  }

  // 添加用户消息
  messages.value.push({
    messageType: 'user',
    content: prompt,
    userName: loginUserStore.loginUser.userName,
    userAvatar: loginUserStore.loginUser.userAvatar,
    type: 'text' // 初始消息默认为文字类型
  })

  // 添加AI消息占位符
  const aiMessageIndex = messages.value.length
  messages.value.push({
    messageType: 'ai',
    content: '',
    loading: true,
  })

  await nextTick()
  scrollToBottom()

  // 开始生成
  isGenerating.value = true
  await generateCode(prompt, aiMessageIndex, [])
}

// 发送消息
const sendMessage = async () => {
  // 个人应用只需要检查基本条件，团队应用还需要检查编辑权限
  const canSend = isTeamApp.value 
    ? (!userInput.value.trim() || isGenerating.value || !canEdit.value || !hasChatPermission.value)
    : (!userInput.value.trim() || isGenerating.value || !hasChatPermission.value)
  
  if (canSend) {
    return
  }

  let message = userInput.value.trim()
  
  // 如果有选中的元素，将元素信息添加到提示词中
  if (selectedElement.value) {
    const elementInfo = `\n\n请针对以下选中的元素进行修改：
元素标签：${selectedElement.value.tagName}
选择器：${selectedElement.value.selector}
${selectedElement.value.id ? `ID：${selectedElement.value.id}` : ''}
${selectedElement.value.className ? `CSS类：${selectedElement.value.className}` : ''}
${selectedElement.value.textContent ? `文本内容：${selectedElement.value.textContent}` : ''}

当前元素的HTML：
${selectedElement.value.outerHTML}

请根据我的要求修改这个元素：`
    
    message = message + elementInfo
  }
  
  userInput.value = ''
  
  // 发送消息后清除选中元素并退出编辑模式
  if (selectedElement.value) {
    clearSelectedElement()
  }
  if (isVisualEditMode.value) {
    visualEditor.toggleEditMode()
  }
  
  // 保存上传的图片地址，用于API调用
  const currentUploadedImages = [...uploadedImages.value]
  
  // 发送消息后清空上传的图片
  uploadedImages.value = []

  // 只在团队应用中发送WebSocket消息通知其他用户开始对话
  if (isTeamApp.value) {
    // 发送文字消息
    sendWebSocketMessage({
      messageType: 'USER_ENTER_EDIT',
      user: {
        id: loginUserStore.loginUser.id,
        userName: loginUserStore.loginUser.userName,
        userAvatar: loginUserStore.loginUser.userAvatar
      },
      editAction: message,
      type: 'text'
    })

    // 如果有图片，分别发送图片消息
    if (currentUploadedImages.length > 0) {
      currentUploadedImages.forEach(imageUrl => {
        sendWebSocketMessage({
          messageType: 'USER_ENTER_EDIT',
          user: {
            id: loginUserStore.loginUser.id,
            userName: loginUserStore.loginUser.userName,
            userAvatar: loginUserStore.loginUser.userAvatar
          },
          editAction: imageUrl,
          type: 'image'
        })
      })
    }
  }

  // 如果有图片，先添加图片消息
  if (currentUploadedImages.length > 0) {
    currentUploadedImages.forEach(imageUrl => {
      messages.value.push({
        messageType: 'user',
        content: imageUrl,
        userName: loginUserStore.loginUser.userName,
        userAvatar: loginUserStore.loginUser.userAvatar,
        type: 'image' // 图片消息
      })
    })
  }

  // 添加文字消息
  messages.value.push({
    messageType: 'user',
    content: message,
    userName: loginUserStore.loginUser.userName,
    userAvatar: loginUserStore.loginUser.userAvatar,
    type: 'text' // 文字消息
  })

  // 添加AI消息占位符
  const aiMessageIndex = messages.value.length
  messages.value.push({
    messageType: 'ai',
    content: '',
    loading: true,
  })

  await nextTick()
  scrollToBottom()

  // 开始生成（只对文字消息进行AI响应）
  isGenerating.value = true
  await generateCode(message, aiMessageIndex, currentUploadedImages)
}

// 生成代码 - 使用 EventSource 处理流式响应
const generateCode = async (userMessage: string, aiMessageIndex: number, imageUrls: string[] = []) => {
  let streamCompleted = false

  try {
    // 获取 axios 配置的 baseURL
    const baseURL = request.defaults.baseURL || API_BASE_URL

    // 构建URL参数
    const params = new URLSearchParams({
      appId: appId.value || '',
      message: userMessage,
    })

    // 添加图片地址参数，支持多个图片
    if (imageUrls && imageUrls.length > 0) {
      imageUrls.forEach(imageUrl => {
        params.append('image', imageUrl)
      })
    }

    const url = `${baseURL}/app/chat/code?${params}`

    // 创建 EventSource 连接
    currentEventSource = new EventSource(url, {
      withCredentials: true,
    })

    let fullContent = ''

    // 处理接收到的消息
    currentEventSource.onmessage = function (event) {
      if (streamCompleted) return

      try {
        // 解析JSON包装的数据
        const parsed = JSON.parse(event.data)
        const content = parsed.d

        // 拼接内容
        if (content !== undefined && content !== null) {
          fullContent += content
          messages.value[aiMessageIndex].content = fullContent
          messages.value[aiMessageIndex].loading = false
          scrollToBottom()
          
          // 只在团队应用中实时推送流式内容到其他用户
          if (isTeamApp.value) {
            sendWebSocketMessage({
              messageType: 'AI_EDIT_ACTION',
              user: {
                id: loginUserStore.loginUser.id,
                userName: loginUserStore.loginUser.userName,
                userAvatar: loginUserStore.loginUser.userAvatar
              },
              editAction: content
            })
          }
        }
      } catch (error) {
        console.error('解析消息失败:', error)
        handleError(error, aiMessageIndex)
      }
    }
    // 处理business-error事件（后端限流等错误）
    currentEventSource.addEventListener('business-error', function (event: MessageEvent) {
      if (streamCompleted) return

      try {
        const errorData = JSON.parse(event.data)
        console.error('SSE业务错误事件:', errorData)

        // 显示具体的错误信息
        const errorMessage = errorData.message || '生成过程中出现错误'
        messages.value[aiMessageIndex].content = `❌ ${errorMessage}`
        messages.value[aiMessageIndex].loading = false
        message.error(errorMessage)

        streamCompleted = true
        isGenerating.value = false
        currentEventSource?.close()
      } catch (parseError) {
        console.error('解析错误事件失败:', parseError, '原始数据:', event.data)
        handleError(new Error('服务器返回错误'), aiMessageIndex)
      }
    })

    // 处理done事件
    currentEventSource.addEventListener('done', function () {
      if (streamCompleted) return

      streamCompleted = true
      isGenerating.value = false
      currentEventSource?.close()
      currentEventSource = null

      // 获取最新的token信息并更新到消息中
      updateTokenInfoForMessage(aiMessageIndex)

      // 只在团队应用中发送退出编辑状态消息
      if (isTeamApp.value) {
        sendWebSocketMessage({
          messageType: 'USER_EXIT_EDIT',
          user: {
            id: loginUserStore.loginUser.id,
            userName: loginUserStore.loginUser.userName,
            userAvatar: loginUserStore.loginUser.userAvatar
          },
          editAction: ''
        })
      }

      // 延迟更新预览，确保后端已完成处理
      setTimeout(async () => {
        await fetchAppInfo()
        updatePreview()
      }, 1000)
    })

    // 处理错误
    currentEventSource.onerror = function () {
      if (streamCompleted || !isGenerating.value) return
      // 检查是否是正常的连接关闭
      if (currentEventSource?.readyState === EventSource.CONNECTING) {
        streamCompleted = true
        isGenerating.value = false
        currentEventSource?.close()
        currentEventSource = null

        // 只在团队应用中发送退出编辑状态消息
        if (isTeamApp.value) {
          sendWebSocketMessage({
            type: 'USER_EXIT_EDIT',
            user: {
              id: loginUserStore.loginUser.id,
              userName: loginUserStore.loginUser.userName,
              userAvatar: loginUserStore.loginUser.userAvatar
            },
            editAction: ''
          })
        }

        setTimeout(async () => {
          await fetchAppInfo()
          updatePreview()
        }, 1000)
      } else {
        handleError(new Error('SSE连接错误'), aiMessageIndex)
      }
    }
  } catch (error) {
    console.error('创建 EventSource 失败：', error)
    handleError(error, aiMessageIndex)
  }
}

// 停止生成
const stopGeneration = async () => {
  if (!appId.value || !isGenerating.value) return

  isCancelling.value = true
  
  try {
    // 关闭当前的 EventSource 连接
    if (currentEventSource) {
      currentEventSource.close()
      currentEventSource = null
    }

    // 调用后端取消生成接口
    const res = await cancelCodeGeneration({
      appId: getAppIdForApi(appId.value) as number
    })

    if (res.data.code === 0) {
      message.success('已停止生成')
      
      // 更新最后一条AI消息，显示生成被停止
      const lastMessage = messages.value[messages.value.length - 1]
              if (lastMessage && lastMessage.messageType === 'ai') {
        lastMessage.content += '\n\n[生成已停止]'
        lastMessage.loading = false
      }
      // 只在团队应用中发送退出编辑状态消息
      if (isTeamApp.value) {
          sendWebSocketMessage({
            messageType: 'USER_EXIT_EDIT',
            user: {
              id: loginUserStore.loginUser.id,
              userName: loginUserStore.loginUser.userName,
              userAvatar: loginUserStore.loginUser.userAvatar
            },
            editAction: ''
          })
        }
    } else {
      message.error('停止生成失败：' + res.data.message)
    }
  } catch (error) {
    console.error('停止生成失败：', error)
    message.error('停止生成失败，请重试')
  } finally {
    isGenerating.value = false
    isCancelling.value = false
    
    // 只在团队应用中发送退出编辑状态消息
    if (isTeamApp.value) {
      sendWebSocketMessage({
        messageType: 'USER_EXIT_EDIT',
        user: {
          id: loginUserStore.loginUser.id,
          userName: loginUserStore.loginUser.userName,
          userAvatar: loginUserStore.loginUser.userAvatar
        },
        editAction: ''
      })
    }
  }
}

// 错误信息清洗函数（对应后端的ErrorMessageUtils.sanitize）
const sanitizeErrorMessage = (rawMessage: string): string => {
  if (!rawMessage || rawMessage.trim() === '') {
    return '系统错误，请稍后重试';
  }
  
  const msg = rawMessage.toLowerCase();
  
  // 常见错误信息清洗
  if (msg.includes('range of input length')) {
    return '输入内容过长，请精简到 6000 字符以内';
  }
  if (msg.includes('range of max_tokens') || msg.includes('max_tokens')) {
    return '生成长度超出限制，请减少输出长度或分段生成';
  }
  if (msg.includes('tool_calls') && msg.includes('must be followed')) {
    return '模型调用工具异常，请重试';
  }
  if (msg.includes('internal_server_error') || msg.includes('internalerror')) {
    return '服务繁忙，请稍后重试';
  }
  if (msg.includes('nullpointerexception') || msg.includes('null')) {
    return '系统处理异常，请重试';
  }
  if (msg.includes('argument type mismatch') || msg.includes('type mismatch')) {
    return '参数处理异常，请重试';
  }
  if (msg.includes('tool') && msg.includes('execution')) {
    return '功能调用异常，请重试';
  }
  if (msg.includes('cannot invoke') && msg.includes('null')) {
    return '系统处理异常，请重试';
  }
  if (msg.includes('ai回复失败:')) {
    // 如果是"AI回复失败:"开头的消息，提取后面的错误信息进行清洗
    const errorPart = rawMessage.substring(rawMessage.indexOf('AI回复失败:') + 6);
    return sanitizeErrorMessage(errorPart);
  }
  
  // 默认友好错误信息
  return '系统错误，请稍后重试';
};

// 错误处理函数
const handleError = (error: unknown, aiMessageIndex: number) => {
  console.error('生成代码失败：', error)
  
  // 检查是否是token不足导致的错误
  let errorMessage = '抱歉，生成过程中出现了错误，请重试。'
  let errorTitle = '生成失败，请重试'
  
  // 如果是Error对象且包含特定信息，尝试解析错误原因
  if (error instanceof Error) {
    const errorText = error.message || error.toString()
    
    // 检查是否包含token相关错误信息
    if (errorText.includes('Token') || errorText.includes('token') || 
        errorText.includes('额度') || errorText.includes('充值')) {
      errorMessage = '❌ Token额度不足，请联系管理员充值'
      errorTitle = 'Token额度不足'
    }
    // 检查是否是网络连接错误
    else if (errorText.includes('Failed to fetch') || errorText.includes('Network')) {
      errorMessage = '❌ 网络连接失败，请检查网络后重试'
      errorTitle = '网络连接失败'
    }
    // 检查是否是服务器错误
    else if (errorText.includes('500') || errorText.includes('Internal Server Error')) {
      errorMessage = '❌ 服务器内部错误，请稍后重试'
      errorTitle = '服务器错误'
    }
  }
  
  messages.value[aiMessageIndex].content = errorMessage
  messages.value[aiMessageIndex].loading = false
  message.error(errorTitle)
  isGenerating.value = false
  
  // 清理 EventSource
  if (currentEventSource) {
    currentEventSource.close()
    currentEventSource = null
  }
  
  // 只在团队应用中发送退出编辑状态消息
  if (isTeamApp.value) {
    sendWebSocketMessage({
      messageType: 'USER_EXIT_EDIT',
      user: {
        id: loginUserStore.loginUser.id,
        userName: loginUserStore.loginUser.userName,
        userAvatar: loginUserStore.loginUser.userAvatar
      },
      editAction: ''
    })
  }
}

// 更新预览
const updatePreview = () => {
  if (appId.value) {
    const codeGenType = appInfo.value?.codeGenType || CodeGenTypeEnum.HTML
    const newPreviewUrl = getStaticPreviewUrl(codeGenType, appId.value)
    previewUrl.value = newPreviewUrl
    previewReady.value = true
  }
}

// 滚动到底部
const scrollToBottom = () => {
  if (messagesContainer.value) {
    messagesContainer.value.scrollTop = messagesContainer.value.scrollHeight
  }
}

// 部署应用
const deployApp = async () => {
  // 检查部署权限
  if (!checkDeployPermission()) {
    message.error('您没有部署应用的权限')
    return
  }

  if (!appId.value) {
    message.error('应用ID不存在')
    return
  }



  deploying.value = true
  try {
    // 使用智能AppId处理，大数值保持字符串格式，小数值转换为number
    const res = await deployAppApi({
      appId: getAppIdForApi(appId.value) as string,
    })

    if (res.data.code === 0 && res.data.data) {
      deployUrl.value = res.data.data
      deployModalVisible.value = true
      message.success('部署成功')
    } else {
      message.error('部署失败：' + res.data.message)
    }
  } catch (error) {
    console.error('部署失败：', error)
    message.error('部署失败，请重试')
  } finally {
    deploying.value = false
  }
}

// 下载代码
const downloadCode = async () => {
  if (!appId.value) {
    message.error('应用ID不存在')
    return
  }

  // 检查下载权限
  if (!checkDownloadPermission()) {
    message.error('您没有下载代码的权限')
    return
  }

  downloading.value = true
  try {
    // 使用智能AppId处理，大数值保持字符串格式，小数值转换为number
    const res = await downloadAppCode({
      appId: getAppIdForApi(appId.value) as number,
    }, {
      responseType: 'blob', // 设置响应类型为blob以处理二进制数据
    })

    // 从响应头中获取文件名
    const contentDisposition = res.headers['content-disposition'] || res.headers['Content-Disposition']
    let fileName = 'app-code.zip'
    
    if (contentDisposition) {
      const fileNameMatch = contentDisposition.match(/filename[^;=\n]*=((['"]).*?\2|[^;\n]*)/)
      if (fileNameMatch && fileNameMatch[1]) {
        fileName = fileNameMatch[1].replace(/['"]/g, '')
      }
    }

    // 创建下载链接
    const url = window.URL.createObjectURL(new Blob([res.data]))
    const link = document.createElement('a')
    link.href = url
    link.download = fileName
    document.body.appendChild(link)
    link.click()
    
    // 清理资源
    document.body.removeChild(link)
    window.URL.revokeObjectURL(url)
    
    message.success('代码下载成功')
  } catch (error) {
    console.error('下载失败：', error)
    message.error('下载失败，请重试')
  } finally {
    downloading.value = false
  }
}

// 在新窗口打开预览
const openInNewTab = () => {
  if (previewUrl.value) {
    window.open(previewUrl.value, '_blank')
  }
}

// 打开部署的网站
const openDeployedSite = () => {
  if (deployUrl.value) {
    window.open(deployUrl.value, '_blank')
  }
}

// iframe加载完成
const onIframeLoad = () => {
  previewReady.value = true
  
  // iframe加载完成后设置可视化编辑器
  if (previewIframe.value) {
    visualEditor.setIframe(previewIframe.value)
    
    // 注入编辑脚本
    setTimeout(() => {
      injectVisualEditScript()
    }, 500)
  }
}

// 注入可视化编辑脚本
const injectVisualEditScript = () => {
  if (!previewIframe.value?.contentDocument) return
  
  try {
    const script = previewIframe.value.contentDocument.createElement('script')
    script.textContent = VisualEditor.getInjectionScript()
    previewIframe.value.contentDocument.head.appendChild(script)
  } catch (error) {
    console.error('注入可视化编辑脚本失败:', error)
  }
}

// 切换可视化编辑模式
const toggleVisualEdit = () => {
  visualEditor.toggleEditMode()
}

// 清除选中元素
const clearSelectedElement = () => {
  selectedElement.value = null
  visualEditor.clearSelection()
}

// 编辑应用
const editApp = () => {
  if (appInfo.value?.id) {
    router.push(`/app/edit/${appInfo.value.id}`)
  }
}

// 返回首页
const goHome = () => {
  router.push('/')
}

// 预览图片
const previewImage = (imageUrl: string) => {
  previewImageUrl.value = imageUrl
  imagePreviewVisible.value = true
}

// 删除应用（普通用户删除自己的应用走 /app/delete/my）
const deleteApp = async () => {
  if (!appInfo.value?.id) return

  try {
    const res = await deleteMyAppApi({ id: appInfo.value.id })
    if (res.data.code === 0) {
      message.success('删除成功')
      appDetailVisible.value = false
      router.push('/')
    } else {
      message.error('删除失败：' + res.data.message)
    }
  } catch (error) {
    console.error('删除失败：', error)
    message.error('删除失败')
  }
}

// 页面加载时获取应用信息
onMounted(() => {
  fetchAppInfo()
  
  // 设置可视化编辑器回调
  visualEditor.setCallbacks(
    (element: SelectedElement | null) => {
      selectedElement.value = element
    },
    (isEditMode: boolean) => {
      isVisualEditMode.value = isEditMode
    }
  )
})

// 监听其他用户生成状态，自动退出可视编辑模式
watch(isOtherUserGenerating, (newValue) => {
  if (newValue && isVisualEditMode.value && isTeamApp.value) {
    // 其他用户开始生成时，自动退出可视编辑模式
    visualEditor.toggleEditMode()
    selectedElement.value = null
    message.info(`${otherGeneratingUser.value?.userName || '其他用户'} 开始生成，已自动退出可视编辑模式`)
  }
})

// 清理资源
onUnmounted(() => {
  // 关闭WebSocket连接
  closeWebSocket()
  // 清理可视化编辑器
  visualEditor.destroy()
  // 清理 EventSource 连接
  if (currentEventSource) {
    currentEventSource.close()
    currentEventSource = null
  }
})
</script>

<style scoped>
#appChatPage {
  height: 100vh;
  width: 100vw;
  display: flex;
  flex-direction: column;
  background: #fdfdfd;
  overflow: hidden;
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  z-index: 1000;
}

/* 顶部栏 */
.header-bar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 16px;
  background: white;
  border-bottom: 1px solid #e8e8e8;
  flex-shrink: 0;
  z-index: 10;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 12px;
}

.app-name {
  margin: 0;
  font-size: 18px;
  font-weight: 600;
  color: #1a1a1a;
  display: flex;
  align-items: center;
  gap: 12px;
}

.code-gen-type-tag {
  font-size: 12px;
  font-weight: 500;
  padding: 4px 12px;
  background: linear-gradient(135deg, #1890ff, #52c41a);
  color: white;
  border-radius: 16px;
  text-transform: capitalize;
  box-shadow: 0 2px 4px rgba(24, 144, 255, 0.2);
  white-space: nowrap;
}

.online-users {
  display: flex;
  align-items: center;
  gap: 8px;
}

.online-label {
  font-size: 12px;
  color: #666;
}

.editing-user {
  border: 2px solid #1890ff;
  box-shadow: 0 0 8px rgba(24, 144, 255, 0.3);
}

.editing-indicator {
  font-size: 12px;
  color: #1890ff;
  margin-left: 8px;
  font-weight: 500;
}

.ws-status {
  display: flex;
  align-items: center;
  gap: 4px;
  margin-left: 12px;
}

.status-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  transition: all 0.3s ease;
}

.status-dot.connected {
  background-color: #52c41a;
  box-shadow: 0 0 4px rgba(82, 196, 26, 0.4);
}

.status-dot.disconnected {
  background-color: #ff4d4f;
  animation: pulse 1.5s infinite;
}

.status-text {
  font-size: 12px;
  color: #666;
}

@keyframes pulse {
  0% { opacity: 1; }
  50% { opacity: 0.5; }
  100% { opacity: 1; }
}

.header-right {
  display: flex;
  gap: 12px;
}

/* 返回首页按钮样式 */
.home-btn {
  background: linear-gradient(135deg, #1890ff 0%, #40a9ff 100%);
  border: none;
  box-shadow: 0 2px 8px rgba(24, 144, 255, 0.3);
  transition: all 0.3s ease;
}

.home-btn:hover {
  background: linear-gradient(135deg, #40a9ff 0%, #69c0ff 100%);
  transform: translateY(-1px);
  box-shadow: 0 4px 12px rgba(24, 144, 255, 0.4);
}

/* 主要内容区域 */
.main-content {
  flex: 1;
  display: flex;
  gap: 16px;
  padding: 16px;
  overflow: hidden;
  min-height: 0;
  max-height: calc(100vh - 80px); /* 减去顶部栏的高度 */
}

/* 左侧对话区域 */
.chat-section {
  flex: 2;
  display: flex;
  flex-direction: column;
  background: white;
  border-radius: 8px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
  overflow: hidden;
  min-height: 0;
  max-height: 100%;
}

.messages-container {
  flex: 1;
  padding: 16px;
  overflow-y: auto;
  overflow-x: hidden;
  scroll-behavior: smooth;
  /* 当没有权限时，让消息区域占用更多空间 */
  min-height: 0;
}

.load-more-container {
  display: flex;
  justify-content: center;
  padding: 8px 0;
  border-bottom: 1px solid #f0f0f0;
  margin-bottom: 12px;
}

.message-item {
  margin-bottom: 12px;
}

.user-message {
  display: flex;
  justify-content: flex-end;
  align-items: flex-start;
  gap: 8px;
}

.ai-message {
  display: flex;
  justify-content: flex-start;
  align-items: flex-start;
  gap: 8px;
}

.message-content {
  max-width: 70%;
  padding: 12px 16px;
  border-radius: 12px;
  line-height: 1.5;
  word-wrap: break-word;
  position: relative;
}

.user-message .message-content {
  background: #1890ff;
  color: white;
}

.ai-message .message-content {
  background: #f5f5f5;
  color: #1a1a1a;
  padding: 8px 12px;
}

.message-text {
  margin-bottom: 8px;
}



.message-image-content {
  margin-bottom: 8px;
}

.message-single-image {
  max-width: 300px;
  max-height: 300px;
  border-radius: 8px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
  transition: all 0.3s ease;
  cursor: pointer;
}

.message-single-image:hover {
  transform: scale(1.02);
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
}

/* 图片预览样式 */
.image-preview-container {
  display: flex;
  justify-content: center;
  align-items: center;
  min-height: 400px;
}

.preview-full-image {
  max-width: 100%;
  max-height: 600px;
  object-fit: contain;
  border-radius: 8px;
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.2);
}

.message-user {
  font-size: 12px;
  opacity: 0.9;
  margin-top: 6px;
  font-weight: 500;
  text-align: right;
}

.ai-message-user {
  text-align: left;
  color: #666;
  font-style: italic;
}

.token-consumption {
  color: #ff4d4f;
  font-size: 14px;
  font-style: normal;
  margin-left: 8px;
}

.message-avatar {
  flex-shrink: 0;
}

.loading-indicator {
  display: flex;
  align-items: center;
  gap: 8px;
  color: #666;
}

.message-footer {
  margin-top: 8px;
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.message-status {
  display: flex;
  align-items: center;
}

.message-status .ant-tag {
  margin: 0;
  font-size: 11px;
  padding: 2px 6px;
  border-radius: 4px;
}

/* 输入区域 */
.input-container {
  padding: 16px;
  background: white;
  border-top: 1px solid #e8e8e8;
  flex-shrink: 0;
}

/* 上传图片预览区域 */
.uploaded-images {
  margin-bottom: 16px;
  padding-bottom: 16px;
  border-bottom: 1px solid #f0f0f0;
}

.images-title {
  font-size: 14px;
  color: #666;
  margin-bottom: 12px;
  font-weight: 500;
}

.images-grid {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.image-item {
  position: relative;
  width: 60px;
  height: 60px;
  border-radius: 6px;
  overflow: hidden;
  border: 1px solid #e8e8e8;
  transition: all 0.3s ease;
}

.image-item:hover {
  border-color: #1890ff;
  transform: translateY(-1px);
  box-shadow: 0 2px 8px rgba(24, 144, 255, 0.15);
}

.preview-image {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.image-overlay {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.5);
  display: flex;
  align-items: center;
  justify-content: center;
  opacity: 0;
  transition: opacity 0.3s ease;
}

.image-item:hover .image-overlay {
  opacity: 1;
}

.delete-btn {
  color: white !important;
  background: rgba(255, 255, 255, 0.2) !important;
  border: none !important;
  border-radius: 50% !important;
  width: 20px !important;
  height: 20px !important;
  min-width: 20px !important;
  display: flex !important;
  align-items: center !important;
  justify-content: center !important;
  font-size: 10px !important;
  padding: 0 !important;
}

.delete-btn:hover {
  background: rgba(255, 255, 255, 0.3) !important;
  color: #ff4d4f !important;
}

/* 选中元素信息 */
.selected-element-info {
  margin-bottom: 12px;
}

.element-alert {
  border-radius: 6px;
}

/* 权限提示区域 */
.permission-container {
  padding: 16px;
  background: white;
  border-top: 1px solid #e8e8e8;
  flex-shrink: 0;
}

.permission-alert {
  margin: 0;
}

.input-wrapper {
  position: relative;
}

.input-wrapper .ant-input {
  padding-right: 120px;
}

.input-actions {
  position: absolute;
  bottom: 8px;
  right: 8px;
  display: flex;
  gap: 8px;
}

/* 上传按钮 */
.upload-btn {
  transition: all 0.3s ease;
}

.upload-btn:hover {
  border-color: #1890ff;
  color: #1890ff;
}

/* 可视化编辑按钮 */
.visual-edit-btn {
  transition: all 0.3s ease;
}

.visual-edit-btn.edit-mode-active {
  background-color: #1890ff;
  border-color: #1890ff;
  color: white;
}

.visual-edit-btn.edit-mode-active:hover {
  background-color: #40a9ff;
  border-color: #40a9ff;
}

/* 停止生成按钮 */
.input-actions .ant-btn-danger {
  background: linear-gradient(135deg, #ff4d4f 0%, #ff7875 100%);
  border: none;
  box-shadow: 0 2px 8px rgba(255, 77, 79, 0.3);
  transition: all 0.3s ease;
}

.input-actions .ant-btn-danger:hover {
  background: linear-gradient(135deg, #ff7875 0%, #ffa39e 100%);
  transform: translateY(-1px);
  box-shadow: 0 4px 12px rgba(255, 77, 79, 0.4);
}



/* 右侧预览区域 */
.preview-section {
  flex: 3;
  display: flex;
  flex-direction: column;
  background: white;
  border-radius: 8px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
  overflow: hidden;
  min-height: 0;
  max-height: 100%;
}

.preview-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16px;
  border-bottom: 1px solid #e8e8e8;
  flex-shrink: 0;
  background: white;
}

.preview-header h3 {
  margin: 0;
  font-size: 16px;
  font-weight: 600;
}

.preview-actions {
  display: flex;
  gap: 8px;
}

.preview-content {
  flex: 1;
  position: relative;
  overflow: hidden;
  min-height: 0;
}

.preview-placeholder {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 100%;
  color: #666;
}

.placeholder-icon {
  font-size: 48px;
  margin-bottom: 16px;
}

.preview-loading {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 100%;
  color: #666;
}

.preview-loading p {
  margin-top: 16px;
}

.preview-iframe {
  width: 100%;
  height: 100%;
  border: none;
}

/* 响应式设计 */
@media (max-width: 1024px) {
  .main-content {
    flex-direction: column;
    padding: 8px;
    max-height: calc(100vh - 70px);
  }

  .chat-section,
  .preview-section {
    flex: none;
    height: calc(50vh - 35px);
    min-height: 0;
    max-height: calc(50vh - 35px);
  }
}

@media (max-width: 768px) {
  .header-bar {
    padding: 8px 12px;
  }

  .app-name {
    font-size: 16px;
  }

  .main-content {
    padding: 8px;
    gap: 8px;
    max-height: calc(100vh - 60px);
  }

  .message-content {
    max-width: 85%;
  }
}
</style>

/**
 * 环境变量配置
 */

// 应用部署域名
export const DEPLOY_DOMAIN = import.meta.env.VITE_DEPLOY_DOMAIN || 'http://localhost'

// API 基础地址
// 开发环境走 Vite 代理，统一使用相对前缀 /api，避免直连端口导致跨域或端口不匹配
export const API_BASE_URL =
  (import.meta as any).env?.VITE_API_BASE_URL || (import.meta as any).env?.DEV ? '/api' : 'http://localhost:8101/example/api'

// 静态资源地址
export const STATIC_BASE_URL = `${API_BASE_URL}/static`

// 获取部署应用的完整URL
export const getDeployUrl = (deployKey: string) => {
  return `${DEPLOY_DOMAIN}/${deployKey}`
}

// 获取静态资源预览URL
export const getStaticPreviewUrl = (codeGenType: string, appId: string) => {
  const baseUrl = `${STATIC_BASE_URL}/${codeGenType}_${appId}/`
  
  // Vue 工程模式需要添加 dist/index.html 后缀
  if (codeGenType === 'vue_project') {
    return `${baseUrl}dist/index.html`
  }
  
  return baseUrl
}

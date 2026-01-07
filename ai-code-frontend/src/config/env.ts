/**
 * 环境变量配置
 */

// 应用部署域名
export const DEPLOY_DOMAIN =
  import.meta.env.VITE_DEPLOY_DOMAIN ||
  (typeof window !== 'undefined' ? window.location.origin : 'http://localhost')

// API 基础地址
// 开发环境走 Vite 代理，统一使用相对前缀 /api，避免直连端口导致跨域或端口不匹配
export const API_BASE_URL =
  (import.meta as any).env?.VITE_API_BASE_URL || (import.meta as any).env?.DEV ? '/api' : 'http://localhost:8101/example/api'

// 静态资源地址
export const STATIC_BASE_URL = `${API_BASE_URL}/static`

// 获取部署应用的完整URL
export const getDeployUrl = (deployKey: string) => {
  // 开发环境：走后端静态资源接口（Vite 代理 /api）
  if ((import.meta as any).env?.DEV) {
    return `${API_BASE_URL}/static/${deployKey}/`
  }

  // 线上部署：Nginx 将 `/dist/` 映射到部署目录 `/tmp/code_deploy/{deployKey}`
  // 直接返回相对路径，自动适配 v4/v6/端口，且不会出现 /dist/dist
  return `/dist/${deployKey}/`
}

// 获取静态资源预览URL
export const getStaticPreviewUrl = (codeGenType: string, appId: string) => {
  // 将枚举值转换为小写并替换下划线
  const normalizedType = codeGenType.toLowerCase().replace('_', '_')
  const baseUrl = `${STATIC_BASE_URL}/${normalizedType}_${appId}/`
  
  // Vue 工程模式需要添加 dist/index.html 后缀
  if (normalizedType === 'vue_project') {
    return `${baseUrl}dist/index.html`
  }
  
  return baseUrl
}

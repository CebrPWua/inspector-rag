import axios from 'axios'
import { message } from 'antd'

const client = axios.create({
  baseURL: '/api-proxy',
  timeout: 60000,
})

/**
 * 清洗错误消息：
 * - 若内容含 HTML 标签（后端把原始 HTTP 错误页透传过来），显示通用提示
 * - 若内容过长（>120字），截断
 */
function sanitizeMsg(raw: unknown, fallback: string): string {
  if (!raw || typeof raw !== 'string') return fallback
  if (/<[a-z][\s\S]*>/i.test(raw)) return fallback   // 含 HTML 标签
  return raw.length > 120 ? raw.slice(0, 120) + '…' : raw
}

// 统一响应解包
client.interceptors.response.use(
  (res) => {
    const data = res.data
    if (data && data.success === false) {
      const msg = sanitizeMsg(data.message, '请求失败')
      message.error(msg)
      return Promise.reject(new Error(msg))
    }
    return data?.data !== undefined ? data.data : data
  },
  (error) => {
    const status = error.response?.status
    const rawMsg = error.response?.data?.message
    const fallback = status === 413
      ? '上传文件过大，单文件最大 1GB'
      : (
        status
          ? `请求失败（${status}），请检查后端服务`
          : (error.message || '网络错误，请稍后重试')
      )
    const msg = sanitizeMsg(rawMsg, fallback)
    message.error(msg)
    return Promise.reject(error)
  }
)

export default client

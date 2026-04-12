import axios from 'axios'
import { message } from 'antd'

const client = axios.create({
  baseURL: '/api-proxy',
  timeout: 60000,
})

// 统一响应解包
client.interceptors.response.use(
  (res) => {
    const data = res.data
    if (data && data.success === false) {
      const msg = data.message || '请求失败'
      message.error(msg)
      return Promise.reject(new Error(msg))
    }
    return data?.data !== undefined ? data.data : data
  },
  (error) => {
    const msg =
      error.response?.data?.message || error.message || '网络错误，请稍后重试'
    message.error(msg)
    return Promise.reject(error)
  }
)

export default client

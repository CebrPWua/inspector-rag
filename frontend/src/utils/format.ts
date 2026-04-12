import dayjs from 'dayjs'
import relativeTime from 'dayjs/plugin/relativeTime'
import 'dayjs/locale/zh-cn'

dayjs.extend(relativeTime)
dayjs.locale('zh-cn')

/** 格式化绝对时间 */
export const formatTime = (iso: string) =>
  dayjs(iso).format('YYYY-MM-DD HH:mm:ss')

/** 格式化相对时间（如"3小时前"） */
export const fromNow = (iso: string) => dayjs(iso).fromNow()

/** 格式化耗时：921 → "921ms"，3210 → "3s 210ms" */
export const formatElapsed = (ms: number): string => {
  if (ms < 1000) return `${ms}ms`
  const s = Math.floor(ms / 1000)
  const rest = ms % 1000
  return `${s}s ${rest}ms`
}

/** 截断字符串 */
export const truncate = (str: string, maxLen = 60): string =>
  str.length > maxLen ? str.slice(0, maxLen) + '…' : str

/** 格式化百分比：0.872 → "87.2%" */
export const formatPercent = (val: number, digits = 1): string =>
  `${(val * 100).toFixed(digits)}%`

/** 格式化文件大小 */
export const formatBytes = (bytes: number): string => {
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  return `${(bytes / 1024 / 1024).toFixed(1)} MB`
}

import { Tag } from 'antd'

type StatusValue =
  | 'active'
  | 'inactive'
  | 'pending_confirm'
  | 'success'
  | 'failed'
  | 'reject'
  | 'pending'
  | 'processing'
  | 'open'
  | 'resolved'
  | 'closed'

const STATUS_CONFIG: Record<
  StatusValue,
  { color: string; text: string }
> = {
  active: { color: 'success', text: '生效中' },
  inactive: { color: 'default', text: '已废止' },
  pending_confirm: { color: 'warning', text: '待确认' },
  success: { color: 'success', text: '成功' },
  failed: { color: 'error', text: '失败' },
  reject: { color: 'warning', text: '拒答' },
  pending: { color: 'default', text: '待处理' },
  processing: { color: 'processing', text: '处理中' },
  open: { color: 'error', text: '待处理' },
  resolved: { color: 'success', text: '已解决' },
  closed: { color: 'default', text: '已关闭' },
}

interface StatusTagProps {
  status: string
}

export function StatusTag({ status }: StatusTagProps) {
  const config = STATUS_CONFIG[status as StatusValue] ?? {
    color: 'default',
    text: status,
  }
  return <Tag color={config.color}>{config.text}</Tag>
}

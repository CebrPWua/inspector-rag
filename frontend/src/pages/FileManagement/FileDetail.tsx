import { useParams, useNavigate } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { Descriptions, Badge, Alert, Button, Skeleton, Tooltip, Typography, Tag } from 'antd'
import { ArrowLeftOutlined } from '@ant-design/icons'
import { getFileDetail } from '../../api/files'
import { formatTime } from '../../utils/format'
import styles from './FileDetail.module.css'

const { Text } = Typography

const PARSE_BADGE: Record<string, { status: 'success' | 'processing' | 'error' | 'default' | 'warning' ; text: string }> = {
  success: { status: 'success', text: '解析成功' },
  processing: { status: 'processing', text: '解析中…' },
  pending: { status: 'default', text: '待解析' },
  failed: { status: 'error', text: '解析失败' },
}

export default function FileDetailPage() {
  const { docId } = useParams<{ docId: string }>()
  const navigate = useNavigate()

  const { data, isLoading } = useQuery({
    queryKey: ['file', docId],
    queryFn: () => getFileDetail(docId!),
    enabled: !!docId,
    // 轮询：解析中时每 3 秒刷新
    refetchInterval: (query) => {
      const status = query.state.data?.parseStatus
      return status === 'pending' || status === 'processing' ? 3000 : false
    },
  })

  if (isLoading) return <Skeleton active style={{ padding: 32 }} />

  if (!data) return <Alert type="error" message="文件不存在" showIcon style={{ margin: 32 }} />

  const parseBadge = PARSE_BADGE[data.parseStatus] ?? { status: 'default' as const, text: data.parseStatus }

  return (
    <div className={styles.page}>
      <Button icon={<ArrowLeftOutlined />} onClick={() => navigate('/files')} style={{ marginBottom: 24 }}>
        返回列表
      </Button>

      <div className={styles.card}>
        <Descriptions
          title="文件详情"
          bordered
          column={2}
          size="middle"
          extra={
            data.parseStatus === 'failed' && (
              <Button type="link" danger onClick={() => navigate('/tasks/dead-letter')}>
                前往任务运维处理 →
              </Button>
            )
          }
        >
          <Descriptions.Item label="法规名称" span={2}>{data.lawName}</Descriptions.Item>
          <Descriptions.Item label="法规编码">
            {data.lawCode ? <Text code copyable>{data.lawCode}</Text> : <Text type="secondary">-</Text>}
          </Descriptions.Item>
          <Descriptions.Item label="版本号">{data.versionNo || <Text type="secondary">-</Text>}</Descriptions.Item>
          <Descriptions.Item label="文档类型">{data.docType}</Descriptions.Item>
          <Descriptions.Item label="法规状态">
            {{
              active: <Tag color="success">生效中</Tag>,
              inactive: <Tag color="default">已废止</Tag>,
              pending_confirm: <Tag color="warning">待确认</Tag>,
            }[data.status] ?? <Tag>{data.status}</Tag>}
          </Descriptions.Item>
          <Descriptions.Item label="解析状态" span={2}>
            <Badge status={parseBadge.status} text={parseBadge.text} />
            {(data.parseStatus === 'pending' || data.parseStatus === 'processing') && (
              <Text type="secondary" style={{ fontSize: 12, marginLeft: 8 }}>（自动刷新中…）</Text>
            )}
          </Descriptions.Item>
          <Descriptions.Item label="原始文件名">{data.sourceFileName}</Descriptions.Item>
          <Descriptions.Item label="文件哈希">
            <Tooltip title={data.fileHash}>
              <Text code>{data.fileHash.slice(0, 16)}…</Text>
            </Tooltip>
          </Descriptions.Item>
          <Descriptions.Item label="存储路径" span={2}>
            <Text code style={{ wordBreak: 'break-all' }}>{data.storagePath}</Text>
          </Descriptions.Item>
          <Descriptions.Item label="创建时间">{formatTime(data.createdAt)}</Descriptions.Item>
          <Descriptions.Item label="文档 ID">
            <Text code copyable={{ text: data.docId }}>{data.docId}</Text>
          </Descriptions.Item>
        </Descriptions>

        {data.parseStatus === 'failed' && (
          <Alert
            type="error"
            showIcon
            message="解析失败"
            description="该文件解析失败，请前往任务运维页面查看错误详情并进行手动重试。"
            style={{ marginTop: 24 }}
          />
        )}
      </div>
    </div>
  )
}

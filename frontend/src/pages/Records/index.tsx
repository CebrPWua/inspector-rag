import { Table, Typography, Button } from 'antd'
import { useQuery } from '@tanstack/react-query'
import { useNavigate } from 'react-router-dom'
import { listQaRecords } from '../../api/records'
import type { QaRecordItemDto } from '../../types/api'
import { formatElapsed, fromNow, formatTime, truncate } from '../../utils/format'
import { StatusTag } from '../../components/StatusTag'
import styles from './index.module.css'

const { Title, Text } = Typography

export default function RecordsPage() {
  const navigate = useNavigate()

  const { data: records = [], isLoading } = useQuery({
    queryKey: ['records'],
    queryFn: () => listQaRecords(50),
    refetchInterval: 30000,
  })

  const columns = [
    {
      title: '原始问题',
      dataIndex: 'question',
      render: (v: string, row: QaRecordItemDto) => (
        <a onClick={() => navigate(`/records/${row.qaId}`)}>
          {truncate(v, 60)}
        </a>
      ),
    },
    {
      title: '回答状态',
      dataIndex: 'answerStatus',
      width: 100,
      render: (v: string) => <StatusTag status={v} />,
    },
    {
      title: '耗时',
      dataIndex: 'elapsedMs',
      width: 110,
      render: (v: number) => <Text style={{ fontSize: 12 }}>{formatElapsed(v)}</Text>,
    },
    {
      title: '时间',
      dataIndex: 'createdAt',
      width: 140,
      render: (v: string) => (
        <Typography.Text title={formatTime(v)} style={{ fontSize: 12 }}>
          {fromNow(v)}
        </Typography.Text>
      ),
    },
    {
      title: '操作',
      width: 130,
      render: (_: unknown, row: QaRecordItemDto) => (
        <div style={{ display: 'flex', gap: 4 }}>
          <Button size="small" type="link" onClick={() => navigate(`/records/${row.qaId}`)}>
            详情
          </Button>
          <Button size="small" type="link" onClick={() => navigate(`/records/${row.qaId}/replay`)}>
            回放
          </Button>
        </div>
      ),
    },
  ]

  return (
    <div className={styles.page}>
      <div className={styles.header}>
        <Title level={3} style={{ margin: 0 }}>问答记录</Title>
      </div>
      <div className={styles.tableCard}>
        <Table
          columns={columns}
          dataSource={records}
          rowKey="qaId"
          loading={isLoading}
          pagination={{ pageSize: 20 }}
          locale={{ emptyText: '暂无问答记录' }}
        />
      </div>
    </div>
  )
}

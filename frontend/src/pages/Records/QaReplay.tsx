import { useParams, useNavigate } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { Skeleton, Alert, Button, Table, Typography, Tag } from 'antd'
import { ArrowLeftOutlined } from '@ant-design/icons'
import { getQaReplay } from '../../api/records'
import type { QaReplayCandidateDto } from '../../types/api'
import { EvidenceCard } from '../../components/EvidenceCard'
import styles from './QaReplay.module.css'

const { Title, Text } = Typography

const SOURCE_COLOR: Record<string, string> = {
  vector: 'blue', keyword: 'green', hybrid: 'purple',
}

export default function QaReplayPage() {
  const { qaId } = useParams<{ qaId: string }>()
  const navigate = useNavigate()

  const { data, isLoading } = useQuery({
    queryKey: ['replay', qaId],
    queryFn: () => getQaReplay(qaId!),
    enabled: !!qaId,
    staleTime: 5 * 60 * 1000,
  })

  if (isLoading) return <Skeleton active style={{ padding: 32 }} />
  if (!data) return <Alert type="error" message="回放记录不存在" showIcon style={{ margin: 32 }} />

  const evidenceChunkIds = new Set(data.evidences.map((e) => e.chunkId))

  const candidateColumns = [
    {
      title: '排名',
      dataIndex: 'rankNo',
      width: 55,
      render: (v: number) => <Text strong>{v}</Text>,
    },
    {
      title: 'Chunk ID',
      dataIndex: 'chunkId',
      render: (v: string) => (
        <Text
          code
          style={{
            fontSize: 11,
            color: evidenceChunkIds.has(v) ? '#389e0d' : undefined,
          }}
        >
          ...{v.slice(-10)}
        </Text>
      ),
    },
    {
      title: '来源',
      dataIndex: 'sourceType',
      width: 80,
      render: (v: string) => <Tag color={SOURCE_COLOR[v] ?? 'default'} style={{ fontSize: 11 }}>{v}</Tag>,
    },
    {
      title: '原始分',
      dataIndex: 'rawScore',
      width: 80,
      render: (v: number) => v?.toFixed(4),
    },
    {
      title: '融合分',
      dataIndex: 'finalScore',
      width: 80,
      render: (v: number, row: QaReplayCandidateDto) => (
        <Text strong={evidenceChunkIds.has(row.chunkId)} style={{ color: evidenceChunkIds.has(row.chunkId) ? '#389e0d' : undefined }}>
          {v?.toFixed(4)}
        </Text>
      ),
    },
    {
      title: '采用',
      width: 55,
      render: (_: unknown, row: QaReplayCandidateDto) =>
        evidenceChunkIds.has(row.chunkId) ? <Tag color="success">✓</Tag> : null,
    },
  ]

  return (
    <div className={styles.page}>
      <Button icon={<ArrowLeftOutlined />} onClick={() => navigate(`/records/${qaId}`)} style={{ marginBottom: 24 }}>
        返回详情
      </Button>

      <div className={styles.questionBox}>
        <Text type="secondary">原始问题</Text>
        <div className={styles.question}>{data.question}</div>
        {data.normalizedQuestion !== data.question && (
          <Text type="secondary" style={{ fontSize: 12 }}>规范化：{data.normalizedQuestion}</Text>
        )}
      </div>

      <div className={styles.replayGrid}>
        {/* 左：候选列表 */}
        <div className={styles.panel}>
          <Title level={5} style={{ marginBottom: 12 }}>
            候选召回明细（{data.candidates.length} 条）
          </Title>
          <Text type="secondary" style={{ fontSize: 12, display: 'block', marginBottom: 12 }}>
            <span style={{ color: '#389e0d' }}>绿色</span> 表示该候选已被采用为最终证据
          </Text>
          <Table
            columns={candidateColumns}
            dataSource={data.candidates}
            rowKey="chunkId"
            size="small"
            pagination={false}
            scroll={{ y: 500 }}
            rowClassName={(row) => evidenceChunkIds.has(row.chunkId) ? styles.adoptedRow : ''}
          />
        </div>

        {/* 右：证据 */}
        <div className={styles.panel}>
          <Title level={5} style={{ marginBottom: 12 }}>
            最终采用证据（{data.evidences.length} 条）
          </Title>
          {data.evidences.map((ev) => (
            <EvidenceCard key={ev.citeNo} evidence={ev} readonly />
          ))}
        </div>
      </div>
    </div>
  )
}

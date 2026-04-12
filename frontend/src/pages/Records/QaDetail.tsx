import { useParams, useNavigate } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { Skeleton, Alert, Button, Descriptions, Divider, Typography } from 'antd'
import { ArrowLeftOutlined, HistoryOutlined } from '@ant-design/icons'
import { getQaDetail } from '../../api/qa'
import { AnswerContent } from '../../components/AnswerContent'
import { EvidenceCard } from '../../components/EvidenceCard'
import { useUIStore } from '../../store/uiStore'
import { formatTime } from '../../utils/format'
import { StatusTag } from '../../components/StatusTag'
import styles from './QaDetail.module.css'

const { Title, Text } = Typography

export default function QaDetailPage() {
  const { qaId } = useParams<{ qaId: string }>()
  const navigate = useNavigate()
  const { highlightedCiteNo, setHighlightedCiteNo } = useUIStore()

  const { data, isLoading } = useQuery({
    queryKey: ['qa', qaId],
    queryFn: () => getQaDetail(qaId!),
    enabled: !!qaId,
    staleTime: 5 * 60 * 1000,
  })

  if (isLoading) return <Skeleton active style={{ padding: 32 }} />
  if (!data) return <Alert type="error" message="记录不存在" showIcon style={{ margin: 32 }} />

  return (
    <div className={styles.page}>
      <div className={styles.topBar}>
        <Button icon={<ArrowLeftOutlined />} onClick={() => navigate('/records')}>
          返回列表
        </Button>
        <Button
          icon={<HistoryOutlined />}
          onClick={() => navigate(`/records/${qaId}/replay`)}
        >
          查看决策回放
        </Button>
      </div>

      {/* 概览 */}
      <div className={styles.card}>
        <Descriptions bordered column={2} size="middle" title="问答概览">
          <Descriptions.Item label="原始问题" span={2}>{data.question}</Descriptions.Item>
          <Descriptions.Item label="规范化问题" span={2}>
            <Text type="secondary">{data.normalizedQuestion}</Text>
          </Descriptions.Item>
          <Descriptions.Item label="回答状态">
            <StatusTag status={data.answerStatus} />
          </Descriptions.Item>
          <Descriptions.Item label="创建时间">{formatTime(data.createdAt)}</Descriptions.Item>
        </Descriptions>
      </div>

      {/* 结果 */}
      <div className={styles.resultGrid}>
        <div className={styles.answerCard}>
          <Title level={5} style={{ marginBottom: 16 }}>回答内容</Title>
          <AnswerContent
            answer={data.answer}
            answerStatus={data.answerStatus}
            onCiteClick={(n) => setHighlightedCiteNo(n === highlightedCiteNo ? null : n)}
          />
        </div>
        <div className={styles.evidenceCard}>
          <Title level={5} style={{ marginBottom: 12 }}>
            法规依据（{data.evidences.length} 条）
          </Title>
          {data.evidences.map((ev) => (
            <EvidenceCard
              key={ev.citeNo}
              evidence={ev}
              highlighted={highlightedCiteNo === ev.citeNo}
              readonly
              onCiteClick={(n) => setHighlightedCiteNo(n === highlightedCiteNo ? null : n)}
            />
          ))}
        </div>
      </div>
    </div>
  )
}

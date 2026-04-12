import { useState } from 'react'
import { Card, Tag, Typography, Button, Tooltip } from 'antd'
import { FileTextOutlined } from '@ant-design/icons'
import type { EvidenceDto, QaReplayEvidenceDto } from '../../types/api'
import styles from './index.module.css'

const { Text, Paragraph } = Typography

const SOURCE_TYPE_COLOR: Record<string, string> = {
  vector: 'blue',
  keyword: 'green',
  hybrid: 'purple',
}

interface EvidenceCardProps {
  evidence: EvidenceDto | QaReplayEvidenceDto
  highlighted?: boolean
  readonly?: boolean
  onCiteClick?: (citeNo: number) => void
}

export function EvidenceCard({
  evidence,
  highlighted,
  onCiteClick,
}: EvidenceCardProps) {
  const [expanded, setExpanded] = useState(false)
  const isFullEvidence = 'finalScore' in evidence

  const quotedText = evidence.quotedText ?? ''
  const isLong = quotedText.length > 120

  return (
    <Card
      className={`${styles.card} ${highlighted ? styles.highlighted : ''}`}
      size="small"
      styles={{ body: { padding: '12px 16px' } }}
    >
      {/* Header */}
      <div className={styles.header}>
        <span
          className={styles.citeNo}
          onClick={() => onCiteClick?.(evidence.citeNo)}
          style={{ cursor: onCiteClick ? 'pointer' : 'default' }}
        >
          [{evidence.citeNo}]
        </span>
        <FileTextOutlined className={styles.icon} />
        <Text strong className={styles.lawName}>
          {evidence.lawName}
        </Text>
      </div>

      {/* Meta */}
      <div className={styles.meta}>
        <Text type="secondary" className={styles.metaItem}>
          第 {evidence.articleNo} 条
        </Text>
        {isFullEvidence && (evidence as EvidenceDto).pageStart != null && (
          <Text type="secondary" className={styles.metaItem}>
            · 第 {(evidence as EvidenceDto).pageStart}
            {(evidence as EvidenceDto).pageEnd !== (evidence as EvidenceDto).pageStart
              ? `–${(evidence as EvidenceDto).pageEnd}`
              : ''}{' '}
            页
          </Text>
        )}
        {isFullEvidence && (
          <>
            <Tag
              color={
                SOURCE_TYPE_COLOR[(evidence as EvidenceDto).sourceType] ?? 'default'
              }
              className={styles.tag}
            >
              {(evidence as EvidenceDto).sourceType}
            </Tag>
            <Tooltip title="文件版本">
              <Text type="secondary" className={styles.metaItem}>
                {(evidence as EvidenceDto).fileVersion}
              </Text>
            </Tooltip>
            <Text type="secondary" className={styles.metaItem}>
              得分: {(evidence as EvidenceDto).finalScore.toFixed(3)}
            </Text>
          </>
        )}
      </div>

      {/* Quoted text */}
      <div className={styles.quoteBlock}>
        <Paragraph
          className={styles.quoteText}
          style={{
            WebkitLineClamp: expanded ? undefined : 3,
            display: '-webkit-box',
            WebkitBoxOrient: expanded ? 'initial' : 'vertical',
            overflow: expanded ? 'visible' : 'hidden',
          }}
        >
          {quotedText}
        </Paragraph>
        {isLong && (
          <Button
            type="link"
            size="small"
            className={styles.expandBtn}
            onClick={() => setExpanded((e) => !e)}
          >
            {expanded ? '收起' : '展开原文 ↓'}
          </Button>
        )}
      </div>
    </Card>
  )
}

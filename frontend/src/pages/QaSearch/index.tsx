import { useState, useRef } from 'react'
import {
  Input, Button, Collapse, Select, DatePicker,
  Form, Typography, Divider, Space, message,
} from 'antd'
import { SearchOutlined, FilterOutlined, ThunderboltOutlined } from '@ant-design/icons'
import { useMutation } from '@tanstack/react-query'
import { askQuestion } from '../../api/qa'
import { useUIStore } from '../../store/uiStore'
import { AnswerContent } from '../../components/AnswerContent'
import { EvidenceCard } from '../../components/EvidenceCard'
import type { AskResponse } from '../../types/api'
import styles from './index.module.css'

const { TextArea } = Input
const { Title, Text } = Typography

const INDUSTRY_TAGS = ['建筑施工', '民航', '交通运输', '危险化学品', '消防', '特种设备']
const DOC_TYPES = [
  { label: '标准', value: 'standard' },
  { label: '法规', value: 'regulation' },
  { label: '指引', value: 'guideline' },
]

export default function QaSearchPage() {
  const [question, setQuestion] = useState('')
  const [result, setResult] = useState<AskResponse | null>(null)
  const { highlightedCiteNo, setHighlightedCiteNo, setCurrentQaId } = useUIStore()
  const [form] = Form.useForm()
  const resultRef = useRef<HTMLDivElement>(null)

  const { mutate: doAsk, isPending } = useMutation({
    mutationFn: askQuestion,
    onSuccess: (data) => {
      setResult(data)
      setCurrentQaId(data.qaId)
      setTimeout(() => resultRef.current?.scrollIntoView({ behavior: 'smooth' }), 100)
    },
    onError: () => {
      // 拒答场景后端返回 400，拦截器已 Toast，这里设置拒答结果
      setResult(null)
    },
  })

  const handleAsk = () => {
    if (!question.trim()) return
    const filters = form.getFieldsValue()
    const cleanFilters: Record<string, unknown> = {}
    if (filters.industryTags?.length) cleanFilters.industryTags = filters.industryTags
    if (filters.docTypes?.length) cleanFilters.docTypes = filters.docTypes
    if (filters.publishOrg) cleanFilters.publishOrg = filters.publishOrg
    if (filters.effectiveOn) cleanFilters.effectiveOn = filters.effectiveOn.format('YYYY-MM-DD')
    doAsk({ question, filters: Object.keys(cleanFilters).length ? cleanFilters : undefined })
  }

  return (
    <div className={styles.page}>
      {/* Hero */}
      <div className={styles.heroSection}>
        <Title level={2} className={styles.heroTitle}>
          <ThunderboltOutlined /> 安全法规智能检索
        </Title>
        <Text type="secondary" className={styles.heroSub}>
          输入安全隐患描述，自动检索匹配的法律法规依据并生成整改建议
        </Text>
      </div>

      {/* 输入区 */}
      <div className={styles.inputCard}>
        <TextArea
          value={question}
          onChange={(e) => setQuestion(e.target.value)}
          placeholder="请输入安全隐患描述，例如：高处作业平台未设置防护栏杆"
          rows={4}
          showCount
          maxLength={500}
          disabled={isPending}
          className={styles.textarea}
          onPressEnter={(e) => { if (e.ctrlKey) handleAsk() }}
        />

        <Collapse
          ghost
          size="small"
          className={styles.filterCollapse}
          items={[{
            key: '1',
            label: <><FilterOutlined /> 高级筛选</>  ,
            children: (
              <Form form={form} layout="inline" className={styles.filterForm}>
                <Form.Item name="industryTags" label="行业标签">
                  <Select
                    mode="multiple"
                    placeholder="全部"
                    options={INDUSTRY_TAGS.map((t) => ({ label: t, value: t }))}
                    style={{ minWidth: 180 }}
                    allowClear
                  />
                </Form.Item>
                <Form.Item name="docTypes" label="文档类型">
                  <Select
                    mode="multiple"
                    placeholder="全部"
                    options={DOC_TYPES}
                    style={{ minWidth: 160 }}
                    allowClear
                  />
                </Form.Item>
                <Form.Item name="publishOrg" label="发布单位">
                  <Input placeholder="如：民航局" style={{ width: 140 }} allowClear />
                </Form.Item>
                <Form.Item name="effectiveOn" label="生效日期">
                  <DatePicker placeholder="选择日期" style={{ width: 140 }} />
                </Form.Item>
                <Form.Item>
                  <Button size="small" onClick={() => form.resetFields()}>清空</Button>
                </Form.Item>
              </Form>
            ),
          }]}
        />

        <div className={styles.inputFooter}>
          <Text type="secondary" style={{ fontSize: 12 }}>Ctrl + Enter 快速发起检索</Text>
          <Button
            type="primary"
            size="large"
            icon={<SearchOutlined />}
            loading={isPending}
            disabled={!question.trim()}
            onClick={handleAsk}
            className={styles.askBtn}
          >
            发起检索
          </Button>
        </div>
      </div>

      {/* 结果区 */}
      {result && (
        <div ref={resultRef} className={styles.resultSection}>
          <Divider>检索结果</Divider>
          <div className={styles.resultGrid}>
            {/* 回答主区域 */}
            <div className={styles.answerCol}>
              <div className={styles.answerHeader}>
                <Text strong>回答内容</Text>
                {result.normalizedQuestion && result.normalizedQuestion !== question && (
                  <Text type="secondary" style={{ fontSize: 12 }}>
                    规范化：{result.normalizedQuestion}
                  </Text>
                )}
              </div>
              <AnswerContent
                answer={result.answer}
                answerStatus={result.answerStatus}
                onCiteClick={(citeNo) => setHighlightedCiteNo(
                  citeNo === highlightedCiteNo ? null : citeNo
                )}
              />
              <Space style={{ marginTop: 16 }}>
                <Button
                  size="small"
                  onClick={() => message.success('感谢反馈！')}
                >
                  👍 有用
                </Button>
                <Button
                  size="small"
                  onClick={() => message.info('感谢反馈，我们会持续优化')}
                >
                  👎 无用
                </Button>
              </Space>
            </div>

            {/* 证据面板 */}
            <div className={styles.evidenceCol}>
              <Text strong style={{ display: 'block', marginBottom: 12 }}>
                📎 法规依据（{result.evidences.length} 条）
              </Text>
              {result.evidences.length === 0 && (
                <Text type="secondary">暂无法规依据</Text>
              )}
              {result.evidences.map((ev) => (
                <EvidenceCard
                  key={ev.citeNo}
                  evidence={ev}
                  highlighted={highlightedCiteNo === ev.citeNo}
                  onCiteClick={(n) =>
                    setHighlightedCiteNo(n === highlightedCiteNo ? null : n)
                  }
                />
              ))}
            </div>
          </div>
        </div>
      )}
    </div>
  )
}

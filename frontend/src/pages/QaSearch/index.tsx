import { useEffect, useMemo, useRef, useState } from 'react'
import {
  Alert,
  Button,
  Collapse,
  DatePicker,
  Empty,
  Form,
  Input,
  Select,
  Space,
  Tag,
  Typography,
} from 'antd'
import { MessageOutlined, SendOutlined, FilterOutlined } from '@ant-design/icons'
import { useMutation, useQuery } from '@tanstack/react-query'
import { useSearchParams } from 'react-router-dom'
import { askQuestion, getConversationMessages } from '../../api/qa'
import { AnswerContent } from '../../components/AnswerContent'
import { EvidenceCard } from '../../components/EvidenceCard'
import { useUIStore } from '../../store/uiStore'
import type { AskResponse, ConversationMessageDto } from '../../types/api'
import styles from './index.module.css'

const { Title, Text } = Typography
const { TextArea } = Input

const INDUSTRY_TAGS = ['建筑施工', '民航', '交通运输', '危险化学品', '消防', '特种设备']
const DOC_TYPES = [
  { label: '标准', value: 'standard' },
  { label: '法规', value: 'regulation' },
  { label: '指引', value: 'guideline' },
]

type ChatMessage =
  | { role: 'user'; qaId: string; turnNo: number; text: string }
  | {
      role: 'assistant'
      qaId: string
      turnNo: number
      answer: string
      answerStatus: 'success' | 'reject' | 'failed'
      normalizedQuestion: string
      rewrittenQuestion?: string | null
      rewriteQueries?: string[]
      evidences: AskResponse['evidences']
    }

const EMPTY_HISTORY: ConversationMessageDto[] = []

function mapConversationToMessages(items: ConversationMessageDto[]): ChatMessage[] {
  const result: ChatMessage[] = []
  for (const item of items) {
    result.push({ role: 'user', qaId: item.qaId, turnNo: item.turnNo, text: item.question })
    result.push({
      role: 'assistant',
      qaId: item.qaId,
      turnNo: item.turnNo,
      answer: item.answer,
      answerStatus: item.answerStatus,
      normalizedQuestion: item.normalizedQuestion,
      rewrittenQuestion: item.rewrittenQuestion,
      rewriteQueries: item.rewriteQueries,
      evidences: item.evidences,
    })
  }
  return result
}

export default function QaSearchPage() {
  const [searchParams, setSearchParams] = useSearchParams()
  const [question, setQuestion] = useState('')
  const [messages, setMessages] = useState<ChatMessage[]>([])
  const [conversationId, setConversationId] = useState<string | null>(searchParams.get('c'))
  const [form] = Form.useForm()
  const chatListRef = useRef<HTMLDivElement>(null)
  const { highlightedCiteNo, setHighlightedCiteNo, setCurrentQaId } = useUIStore()

  const { data: historyData, isFetching: historyLoading } = useQuery({
    queryKey: ['qa-conversation', conversationId],
    queryFn: () => getConversationMessages(conversationId!),
    enabled: !!conversationId,
  })
  const history = historyData ?? EMPTY_HISTORY

  useEffect(() => {
    if (!conversationId) {
      setMessages((prev) => (prev.length === 0 ? prev : []))
      return
    }
    setMessages(mapConversationToMessages(history))
  }, [conversationId, history])

  useEffect(() => {
    if (!chatListRef.current) return
    chatListRef.current.scrollTop = chatListRef.current.scrollHeight
  }, [messages, historyLoading])

  const { mutate: doAsk, isPending } = useMutation({
    mutationFn: askQuestion,
    onSuccess: (data) => {
      setCurrentQaId(data.qaId)
      setConversationId(data.conversationId)
      setSearchParams({ c: data.conversationId })

      setMessages((prev) => ([
        ...prev,
        { role: 'user', qaId: data.qaId, turnNo: data.turnNo, text: question },
        {
          role: 'assistant',
          qaId: data.qaId,
          turnNo: data.turnNo,
          answer: data.answer,
          answerStatus: data.answerStatus,
          normalizedQuestion: data.normalizedQuestion,
          rewrittenQuestion: data.rewrittenQuestion,
          rewriteQueries: data.rewriteQueries,
          evidences: data.evidences,
        },
      ]))
      setQuestion('')
    },
  })

  const canSend = question.trim().length > 0 && !isPending

  const headerTip = useMemo(() => {
    if (!conversationId) return '新会话'
    return `会话 ID: ${conversationId}`
  }, [conversationId])

  const handleAsk = () => {
    if (!canSend) return
    const filters = form.getFieldsValue()
    const cleanFilters: Record<string, unknown> = {}
    if (filters.industryTags?.length) cleanFilters.industryTags = filters.industryTags
    if (filters.docTypes?.length) cleanFilters.docTypes = filters.docTypes
    if (filters.publishOrg) cleanFilters.publishOrg = filters.publishOrg
    if (filters.effectiveOn) cleanFilters.effectiveOn = filters.effectiveOn.format('YYYY-MM-DD')

    doAsk({
      question: question.trim(),
      conversationId: conversationId ?? undefined,
      filters: Object.keys(cleanFilters).length ? cleanFilters : undefined,
    })
  }

  const resetConversation = () => {
    setConversationId(null)
    setMessages([])
    setQuestion('')
    setSearchParams({})
    setHighlightedCiteNo(null)
  }

  return (
    <div className={styles.page}>
      <div className={styles.headerRow}>
        <div>
          <Title level={3} className={styles.title}><MessageOutlined /> 法规对话助手</Title>
          <Text type="secondary">{headerTip}</Text>
        </div>
        <Button onClick={resetConversation} disabled={isPending}>新建会话</Button>
      </div>

      <div className={styles.filterCard}>
        <Collapse
          ghost
          size="small"
          items={[{
            key: '1',
            label: <><FilterOutlined /> 会话级筛选条件</>,
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
                  <Select mode="multiple" placeholder="全部" options={DOC_TYPES} style={{ minWidth: 160 }} allowClear />
                </Form.Item>
                <Form.Item name="publishOrg" label="发布单位">
                  <Input placeholder="如：民航局" style={{ width: 140 }} allowClear />
                </Form.Item>
                <Form.Item name="effectiveOn" label="生效日期">
                  <DatePicker placeholder="选择日期" style={{ width: 140 }} />
                </Form.Item>
              </Form>
            ),
          }]}
        />
      </div>

      <div className={styles.chatPanel}>
        <div ref={chatListRef} className={styles.chatList}>
          {historyLoading && <Alert type="info" showIcon message="正在加载会话历史..." style={{ marginBottom: 12 }} />}
          {messages.length === 0 && !historyLoading && (
            <Empty description="开始提问吧，例如：高处作业平台未设置防护栏杆依据是什么" image={Empty.PRESENTED_IMAGE_SIMPLE} />
          )}

          {messages.map((msg, idx) => (
            <div key={`${msg.qaId}-${msg.role}-${idx}`} className={msg.role === 'user' ? styles.userRow : styles.assistantRow}>
              <div className={msg.role === 'user' ? styles.userBubble : styles.assistantBubble}>
                {msg.role === 'user' ? (
                  <div>{msg.text}</div>
                ) : (
                  <>
                    <div className={styles.metaRow}>
                      <Tag color={msg.answerStatus === 'reject' ? 'gold' : 'blue'}>{msg.answerStatus}</Tag>
                      {msg.rewrittenQuestion && <Text type="secondary">改写：{msg.rewrittenQuestion}</Text>}
                    </div>
                    <AnswerContent
                      answer={msg.answer}
                      answerStatus={msg.answerStatus}
                      onCiteClick={(citeNo) => setHighlightedCiteNo(citeNo === highlightedCiteNo ? null : citeNo)}
                    />
                    {msg.evidences.length > 0 && (
                      <div className={styles.evidenceList}>
                        {msg.evidences.map((ev) => (
                          <EvidenceCard
                            key={`${msg.qaId}-${ev.citeNo}`}
                            evidence={ev}
                            highlighted={highlightedCiteNo === ev.citeNo}
                            onCiteClick={(n) => setHighlightedCiteNo(n === highlightedCiteNo ? null : n)}
                          />
                        ))}
                      </div>
                    )}
                  </>
                )}
              </div>
            </div>
          ))}
        </div>

        <div className={styles.inputDock}>
          <TextArea
            value={question}
            onChange={(e) => setQuestion(e.target.value)}
            placeholder="继续追问，模型会结合当前会话上下文回答..."
            rows={3}
            maxLength={500}
            disabled={isPending}
            onPressEnter={(e) => {
              if (!e.shiftKey) {
                e.preventDefault()
                handleAsk()
              }
            }}
          />
          <div className={styles.inputActions}>
            <Space>
              <Text type="secondary">Enter 发送，Shift+Enter 换行</Text>
            </Space>
            <Button type="primary" icon={<SendOutlined />} onClick={handleAsk} loading={isPending} disabled={!canSend}>
              发送
            </Button>
          </div>
        </div>
      </div>
    </div>
  )
}

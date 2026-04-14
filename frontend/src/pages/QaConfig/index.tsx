import { useEffect, useState } from 'react'
import { useMutation, useQuery } from '@tanstack/react-query'
import { Alert, Button, Card, Form, Input, InputNumber, Space, Tag, Typography, message } from 'antd'
import { getRejectThresholdConfig, updateRejectThresholdConfig } from '../../api/qa'
import type { UpdateRejectThresholdConfigRequest } from '../../types/api'

const { Title, Text } = Typography

export default function QaConfigPage() {
  const [form] = Form.useForm<UpdateRejectThresholdConfigRequest>()
  const [operator, setOperator] = useState('')

  const { data, isLoading, refetch } = useQuery({
    queryKey: ['qa-reject-threshold-config'],
    queryFn: getRejectThresholdConfig,
  })

  useEffect(() => {
    if (!data) return
    form.setFieldsValue({
      minTop1Score: data.minTop1Score,
      minTop1ScoreVectorOnly: data.minTop1ScoreVectorOnly,
      minTopGap: data.minTopGap,
      minConfidentScore: data.minConfidentScore,
      minEvidenceCount: data.minEvidenceCount,
    })
  }, [data, form])

  const { mutate: doUpdate, isPending } = useMutation({
    mutationFn: (req: UpdateRejectThresholdConfigRequest) =>
      updateRejectThresholdConfig(req, operator.trim() || undefined),
    onSuccess: async (next) => {
      message.success('拒答阈值已生效')
      form.setFieldsValue({
        minTop1Score: next.minTop1Score,
        minTop1ScoreVectorOnly: next.minTop1ScoreVectorOnly,
        minTopGap: next.minTopGap,
        minConfidentScore: next.minConfidentScore,
        minEvidenceCount: next.minEvidenceCount,
      })
      await refetch()
    },
  })

  return (
    <div style={{ padding: 24 }}>
      <Card bordered={false}>
        <Space direction="vertical" size={16} style={{ width: '100%' }}>
          <div>
            <Title level={3} style={{ margin: 0 }}>拒答阈值配置</Title>
            <Text type="secondary">修改后立即生效，无需重启后端服务。</Text>
          </div>

          <Alert
            type="info"
            showIcon
            message="仅开放 phase3.reject 阈值；建议优先小步调参并结合审计记录观察拒答变化。"
          />

          <Form
            form={form}
            layout="vertical"
            onFinish={(values) => {
              doUpdate({
                minTop1Score: Number(values.minTop1Score),
                minTop1ScoreVectorOnly: Number(values.minTop1ScoreVectorOnly),
                minTopGap: Number(values.minTopGap),
                minConfidentScore: Number(values.minConfidentScore),
                minEvidenceCount: Number(values.minEvidenceCount),
              })
            }}
          >
            <Form.Item
              label="Top1 最小阈值（混合/关键词）"
              name="minTop1Score"
              extra="当当前命中的 Top1 分数低于该值时，会直接进入拒答判断。适用于混合检索与关键词召回场景。"
              rules={[{ required: true, type: 'number', min: 0, max: 1 }]}
            >
              <InputNumber min={0} max={1} step={0.01} precision={4} style={{ width: 280 }} />
            </Form.Item>

            <Form.Item
              label="Top1 最小阈值（纯向量）"
              name="minTop1ScoreVectorOnly"
              extra="仅在纯向量候选场景生效。通常建议略低于混合/关键词阈值，以避免向量分数分布偏低导致误拒答。"
              rules={[{ required: true, type: 'number', min: 0, max: 1 }]}
            >
              <InputNumber min={0} max={1} step={0.01} precision={4} style={{ width: 280 }} />
            </Form.Item>

            <Form.Item
              label="Top1-Top2 最小分差阈值"
              name="minTopGap"
              extra="用于判断答案是否足够“明确”。当 Top1 与 Top2 分差过小，说明候选区分度不足，会倾向拒答。"
              rules={[{ required: true, type: 'number', min: 0, max: 1 }]}
            >
              <InputNumber min={0} max={1} step={0.01} precision={4} style={{ width: 280 }} />
            </Form.Item>

            <Form.Item
              label="Top1 可信阈值"
              name="minConfidentScore"
              extra="只有 Top1 分数达到该值时，才会启用分差阈值判定。用于避免低分样本因偶然分差被误判为可回答。"
              rules={[{ required: true, type: 'number', min: 0, max: 1 }]}
            >
              <InputNumber min={0} max={1} step={0.01} precision={4} style={{ width: 280 }} />
            </Form.Item>

            <Form.Item
              label="最小证据条数"
              name="minEvidenceCount"
              extra="用于控制回答至少需要几条证据支撑。证据数量不足时即使 Top1 分高，也会触发拒答策略。"
              rules={[{ required: true, type: 'number', min: 1 }]}
            >
              <InputNumber min={1} step={1} precision={0} style={{ width: 280 }} />
            </Form.Item>

            <Form.Item label="操作者（可选，写入 X-Operator）">
              <Input
                placeholder="如：qa-admin"
                value={operator}
                onChange={(e) => setOperator(e.target.value)}
                style={{ width: 280 }}
                maxLength={128}
              />
            </Form.Item>

            <Space>
              <Button type="primary" htmlType="submit" loading={isPending} disabled={isLoading}>
                保存并生效
              </Button>
              <Button onClick={() => form.resetFields()} disabled={isLoading || isPending}>
                重置输入
              </Button>
            </Space>
          </Form>

          {data && (
            <Space direction="vertical" size={4}>
              <Text>
                当前来源：
                <Tag color={data.source === 'db' ? 'green' : 'gold'} style={{ marginLeft: 8 }}>
                  {data.source}
                </Tag>
              </Text>
              <Text type="secondary">最近更新人：{data.updatedBy ?? '-'}</Text>
              <Text type="secondary">最近更新时间：{data.updatedAt ?? '-'}</Text>
            </Space>
          )}
        </Space>
      </Card>
    </div>
  )
}

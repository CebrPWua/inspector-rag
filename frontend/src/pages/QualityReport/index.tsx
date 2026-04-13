import {
  DatePicker, Button, Row, Col, Card, Statistic,
  Table, Typography, Alert, Skeleton,
} from 'antd'
import {
  BarChart, Bar, XAxis, YAxis, CartesianGrid,
  Tooltip as ReTooltip, ResponsiveContainer, Cell,
} from 'recharts'
import { useQuery } from '@tanstack/react-query'
import dayjs, { Dayjs } from 'dayjs'
import { getQualityReport } from '../../api/records'
import { useUIStore } from '../../store/uiStore'
import { formatPercent, formatElapsed } from '../../utils/format'
import styles from './index.module.css'

const { RangePicker } = DatePicker
const { Title, Text } = Typography

const PRESET_RANGES: Record<string, [Dayjs, Dayjs]> = {
  '最近7天': [dayjs().subtract(7, 'day'), dayjs()],
  '最近30天': [dayjs().subtract(30, 'day'), dayjs()],
}

export default function QualityReportPage() {
  const { reportDateRange, setReportDateRange } = useUIStore()

  const { data, isLoading, isError } = useQuery({
    queryKey: ['quality-report', reportDateRange[0].toISOString(), reportDateRange[1].toISOString()],
    queryFn: () =>
      getQualityReport({
        from: reportDateRange[0].toISOString(),
        to: reportDateRange[1].toISOString(),
      }),
    staleTime: 60 * 1000,
  })

  const rejectReasonData = data?.topRejectReasons ?? []

  const reasonTableColumns = [
    { title: '拒答原因码', dataIndex: 'reasonCode', render: (v: string) => <Text code>{v}</Text> },
    { title: '次数', dataIndex: 'count', width: 80 },
    {
      title: '占比',
      width: 90,
      render: (_: unknown, row: { count: number }) =>
        data?.reject ? formatPercent(row.count / data.reject) : '-',
    },
  ]

  return (
    <div className={styles.page}>
      {/* 顶部控制栏 */}
      <div className={styles.header}>
        <Title level={3} style={{ margin: 0 }}>质量报表</Title>
        <div className={styles.controls}>
          <RangePicker
            value={reportDateRange}
            presets={Object.entries(PRESET_RANGES).map(([label, value]) => ({ label, value }))}
            onChange={(range) => {
              if (range?.[0] && range?.[1])
                setReportDateRange([range[0], range[1]])
            }}
            allowClear={false}
          />
          <Button onClick={() => setReportDateRange([...reportDateRange])}>刷新</Button>
        </div>
      </div>

      {isLoading && <Skeleton active paragraph={{ rows: 10 }} />}
      {isError && <Alert type="error" message="报表加载失败" showIcon />}

      {data && (
        <>
          {/* 统计卡片 */}
          <Row gutter={[16, 16]} className={styles.statRow}>
            <Col xs={24} sm={12} md={6}>
              <Card className={styles.statCard}>
                <Statistic title="问答总量" value={data.total} />
              </Card>
            </Col>
            <Col xs={24} sm={12} md={6}>
              <Card className={styles.statCard}>
                <Statistic
                  title="成功率"
                  value={data.total ? ((data.success / data.total) * 100).toFixed(1) : 0}
                  suffix="%"
                  valueStyle={{ color: '#52c41a' }}
                />
              </Card>
            </Col>
            <Col xs={24} sm={12} md={6}>
              <Card className={styles.statCard}>
                <Statistic
                  title="拒答率"
                  value={((data.rejectRate ?? 0) * 100).toFixed(1)}
                  suffix="%"
                  valueStyle={{ color: '#faad14' }}
                />
              </Card>
            </Col>
            <Col xs={24} sm={12} md={6}>
              <Card className={styles.statCard}>
                <Statistic
                  title="平均响应"
                  value={formatElapsed(data.avgElapsedMs ?? 0)}
                  valueStyle={{ fontSize: 24 }}
                />
              </Card>
            </Col>
          </Row>

          {/* 次要指标 */}
          <Row gutter={[16, 16]} className={styles.statRow}>
            <Col xs={24} sm={12} md={6}>
              <Card className={styles.statCard} size="small">
                <Statistic title="P95 耗时" value={formatElapsed(data.p95ElapsedMs ?? 0)} valueStyle={{ fontSize: 20 }} />
              </Card>
            </Col>
            <Col xs={24} sm={12} md={6}>
              <Card className={styles.statCard} size="small">
                <Statistic title="平均证据数" value={(data.avgEvidenceCount ?? 0).toFixed(1)} suffix="条" valueStyle={{ fontSize: 20 }} />
              </Card>
            </Col>
            <Col xs={24} sm={12} md={6}>
              <Card className={styles.statCard} size="small">
                <Statistic title="Top1 平均融合分" value={(data.avgTop1FinalScore ?? 0).toFixed(3)} valueStyle={{ fontSize: 20 }} />
              </Card>
            </Col>
            <Col xs={24} sm={12} md={6}>
              <Card className={styles.statCard} size="small">
                <Statistic title="失败率" value={((data.failedRate ?? 0) * 100).toFixed(1)} suffix="%" valueStyle={{ fontSize: 20, color: '#ff4d4f' }} />
              </Card>
            </Col>
          </Row>

          {/* 拒答原因图 + 明细 */}
          <Row gutter={[24, 24]} style={{ marginTop: 0 }}>
            {rejectReasonData.length > 0 && (
              <Col xs={24} lg={12}>
                <div className={styles.chartCard}>
                  <Title level={5} style={{ marginBottom: 16 }}>拒答原因分布</Title>
                  <ResponsiveContainer width="100%" height={260}>
                    <BarChart data={rejectReasonData} margin={{ left: -10 }}>
                      <CartesianGrid strokeDasharray="3 3" vertical={false} />
                      <XAxis dataKey="reasonCode" tick={{ fontSize: 11 }} />
                      <YAxis tick={{ fontSize: 11 }} />
                      <ReTooltip />
                      <Bar dataKey="count" name="次数" radius={[4, 4, 0, 0]}>
                        {rejectReasonData.map((_, i) => (
                          <Cell key={i} fill={i % 2 === 0 ? '#faad14' : '#ffd666'} />
                        ))}
                      </Bar>
                    </BarChart>
                  </ResponsiveContainer>
                </div>
              </Col>
            )}
            <Col xs={24} lg={rejectReasonData.length > 0 ? 12 : 24}>
              <div className={styles.chartCard}>
                <Title level={5} style={{ marginBottom: 16 }}>拒答原因明细</Title>
                <Table
                  columns={reasonTableColumns}
                  dataSource={rejectReasonData}
                  rowKey="reasonCode"
                  size="small"
                  pagination={false}
                  locale={{ emptyText: '暂无拒答记录' }}
                />
              </div>
            </Col>
          </Row>
        </>
      )}
    </div>
  )
}

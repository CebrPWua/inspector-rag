import { useState } from 'react'
import {
  Button, Table, Modal, Form, Input, Select, Upload,
  message, Typography, Space, Tooltip, Badge, Tag, Popconfirm,
} from 'antd'
import {
  UploadOutlined, EyeOutlined, InboxOutlined,
  DeleteOutlined,
} from '@ant-design/icons'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useNavigate } from 'react-router-dom'
import { deleteFile, getFileList, uploadFile } from '../../api/files'
import type { FileListItemResponse } from '../../types/api'
import { formatTime } from '../../utils/format'
import styles from './index.module.css'

const { Title, Text } = Typography
const { Dragger } = Upload

const PARSE_STATUS_BADGE: Record<string, { status: 'success' | 'processing' | 'error' | 'default' | 'warning'; text: string }> = {
  success: { status: 'success', text: '解析成功' },
  processing: { status: 'processing', text: '解析中' },
  pending: { status: 'default', text: '待解析' },
  failed: { status: 'error', text: '解析失败' },
}

export default function FileManagementPage() {
  const [uploadModalOpen, setUploadModalOpen] = useState(false)
  const [form] = Form.useForm()
  const [uploadedFileObj, setUploadedFileObj] = useState<File | null>(null)
  const navigate = useNavigate()
  const queryClient = useQueryClient()

  const { data: fileList = [], isLoading: fileListLoading } = useQuery({
    queryKey: ['files'],
    queryFn: () => getFileList(200),
  })

  const { mutate: doUpload, isPending: uploading } = useMutation({
    mutationFn: (fd: FormData) => uploadFile(fd),
    onSuccess: (data) => {
      if (data.duplicate) {
        message.warning(`文件已存在，docId: ${data.docId}`)
      } else {
        message.success(`上传成功，docId: ${data.docId}`)
      }
      queryClient.invalidateQueries({ queryKey: ['files'] })
      navigate(`/files/${data.docId}`)
      setUploadModalOpen(false)
      form.resetFields()
      setUploadedFileObj(null)
    },
  })

  const { mutate: doDelete, isPending: deleting } = useMutation({
    mutationFn: (docId: string) => deleteFile(docId),
    onSuccess: () => {
      message.success('删除成功')
      queryClient.invalidateQueries({ queryKey: ['files'] })
    },
  })

  const handleUpload = async () => {
    try {
      const values = await form.validateFields()
      if (!uploadedFileObj) { message.error('请选择文件'); return }
      const fd = new FormData()
      fd.append('file', uploadedFileObj)
      fd.append('lawName', values.lawName)
      if (values.lawCode?.trim()) {
        fd.append('lawCode', values.lawCode.trim())
      }
      fd.append('versionNo', values.versionNo?.trim() || 'v1')
      fd.append('docType', values.docType ?? 'standard')
      fd.append('status', values.status ?? 'active')
      doUpload(fd)
    } catch {
      // Form validation error
    }
  }

  const columns = [
    {
      title: '法规编码',
      dataIndex: 'lawCode',
      width: 130,
      render: (v: string | null) => (
        v
          ? <Text code copyable style={{ fontSize: 12 }}>{v}</Text>
          : <Text type="secondary">-</Text>
      ),
    },
    {
      title: '法规名称',
      dataIndex: 'lawName',
      render: (v: string, row: FileListItemResponse) => (
        <a onClick={() => navigate(`/files/${row.docId}`)}>{v}</a>
      ),
    },
    {
      title: '版本',
      dataIndex: 'versionNo',
      width: 80,
      render: (v: string | null) => v || <Text type="secondary">-</Text>,
    },
    {
      title: '法规状态',
      dataIndex: 'status',
      width: 100,
      render: (v: string) => {
        const map: Record<string, { color: string; text: string }> = {
          active: { color: 'success', text: '生效中' },
          inactive: { color: 'default', text: '已废止' },
          pending_confirm: { color: 'warning', text: '待确认' },
        }
        const cfg = map[v] ?? { color: 'default', text: v }
        return <Tag color={cfg.color}>{cfg.text}</Tag>
      },
    },
    {
      title: '解析状态',
      dataIndex: 'parseStatus',
      width: 110,
      render: (v: string) => {
        const cfg = PARSE_STATUS_BADGE[v] ?? { status: 'default' as const, text: v }
        return <Badge status={cfg.status} text={cfg.text} />
      },
    },
    {
      title: '创建时间',
      dataIndex: 'createdAt',
      width: 170,
      render: (v: string) => formatTime(v),
    },
    {
      title: '操作',
      width: 140,
      render: (_: unknown, row: FileListItemResponse) => (
        <Space size={4}>
          <Tooltip title="查看详情">
            <Button
              type="link"
              icon={<EyeOutlined />}
              onClick={() => navigate(`/files/${row.docId}`)}
            />
          </Tooltip>
          {(['pending', 'processing'] as string[]).includes(row.parseStatus) ? (
            <Tooltip title="解析中不可删除">
              <Button type="link" danger icon={<DeleteOutlined />} disabled />
            </Tooltip>
          ) : (
            <Popconfirm
              title="确认删除该文档？"
              description="删除后将级联清理关联解析/任务/向量数据，且不可恢复。"
              okText="删除"
              cancelText="取消"
              okButtonProps={{ danger: true, loading: deleting }}
              onConfirm={() => doDelete(row.docId)}
            >
              <Button type="link" danger icon={<DeleteOutlined />} />
            </Popconfirm>
          )}
        </Space>
      ),
    },
  ]

  return (
    <div className={styles.page}>
      <div className={styles.header}>
        <Title level={3} style={{ margin: 0 }}>法规文件管理</Title>
        <Button
          type="primary"
          icon={<UploadOutlined />}
          onClick={() => setUploadModalOpen(true)}
        >
          上传法规文件
        </Button>
      </div>

      <div className={styles.tableCard}>
        <Table
          columns={columns}
          dataSource={fileList}
          loading={fileListLoading}
          rowKey="docId"
          locale={{ emptyText: '暂无文件，请先上传法规文件' }}
          pagination={{ pageSize: 20, showSizeChanger: false }}
        />
      </div>

      {/* 上传 Modal */}
      <Modal
        title="上传法规文件"
        open={uploadModalOpen}
        onOk={handleUpload}
        onCancel={() => { setUploadModalOpen(false); form.resetFields(); setUploadedFileObj(null) }}
        okText="上传"
        confirmLoading={uploading}
        width={560}
        destroyOnClose
      >
        <Form form={form} layout="vertical" style={{ marginTop: 16 }}>
          <Form.Item label="法规文件" required>
            <Dragger
              beforeUpload={(file) => { setUploadedFileObj(file); return false }}
              onRemove={() => setUploadedFileObj(null)}
              maxCount={1}
              accept=".pdf,.doc,.docx"
            >
              <p className="ant-upload-drag-icon"><InboxOutlined /></p>
              <p>点击或拖拽文件至此区域上传</p>
              <p style={{ color: '#999', fontSize: 12 }}>支持 PDF、DOC、DOCX 格式</p>
            </Dragger>
          </Form.Item>
          <Form.Item name="lawName" label="法规名称" rules={[{ required: true, message: '请输入法规名称' }]}>
            <Input placeholder="如：建筑施工高处作业安全技术规范" maxLength={512} showCount />
          </Form.Item>
          <Space style={{ width: '100%' }} size={12}>
            <Form.Item name="lawCode" label="法规编码（选填）" style={{ flex: 1, marginBottom: 0 }}>
              <Input placeholder="如：JGJ80-2016（可不填）" maxLength={128} />
            </Form.Item>
            <Form.Item name="versionNo" label="版本号（选填）" style={{ flex: 1, marginBottom: 0 }}>
              <Input placeholder="如：v1（可不填）" />
            </Form.Item>
          </Space>
          <Space style={{ width: '100%', marginTop: 16 }} size={12}>
            <Form.Item name="docType" label="文档类型" style={{ flex: 1, marginBottom: 0 }}>
              <Select defaultValue="standard" options={[
                { label: '标准', value: 'standard' },
                { label: '法规', value: 'regulation' },
                { label: '指引', value: 'guideline' },
              ]} />
            </Form.Item>
            <Form.Item name="status" label="法规状态" style={{ flex: 1, marginBottom: 0 }}>
              <Select defaultValue="active" options={[
                { label: '生效中', value: 'active' },
                { label: '待确认', value: 'pending_confirm' },
              ]} />
            </Form.Item>
          </Space>
        </Form>
      </Modal>
    </div>
  )
}

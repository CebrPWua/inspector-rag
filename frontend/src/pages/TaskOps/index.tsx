import { useState } from 'react'
import {
  Button, Table, Modal, Form, Input, Select, Tooltip,
  Tag, Typography, Popconfirm, message,
} from 'antd'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useNavigate } from 'react-router-dom'
import {
  listDeadLetters, assignDeadLetter,
  updateDeadLetterStatus, retryTask,
} from '../../api/tasks'
import type { DeadLetterTaskDto } from '../../types/api'
import { StatusTag } from '../../components/StatusTag'
import { formatTime, truncate } from '../../utils/format'
import styles from './index.module.css'

const { Title, Text } = Typography

const NEXT_STATUS: Record<string, string[]> = {
  open: ['processing'],
  processing: ['resolved', 'open'],
  resolved: ['closed', 'processing'],
  closed: [],
}

const TASK_TYPE_COLOR: Record<string, string> = {
  parse: 'blue',
  chunk: 'cyan',
  embed: 'purple',
  reindex: 'orange',
  cleanup: 'default',
}

export default function TaskOpsPage() {
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const [assignModal, setAssignModal] = useState<{ open: boolean; id: string }>({ open: false, id: '' })
  const [statusModal, setStatusModal] = useState<{ open: boolean; id: string; current: string }>({ open: false, id: '', current: '' })
  const [assignForm] = Form.useForm()
  const [statusForm] = Form.useForm()

  const { data: deadLetters = [], isLoading } = useQuery({
    queryKey: ['dead-letters'],
    queryFn: listDeadLetters,
    refetchInterval: 15000,
  })

  const invalidate = () => queryClient.invalidateQueries({ queryKey: ['dead-letters'] })

  const { mutate: doAssign, isPending: assigning } = useMutation({
    mutationFn: ({ id, assignedTo }: { id: string; assignedTo: string }) =>
      assignDeadLetter(id, assignedTo),
    onSuccess: () => { message.success('指派成功'); setAssignModal({ open: false, id: '' }); invalidate() },
  })

  const { mutate: doUpdateStatus, isPending: updatingStatus } = useMutation({
    mutationFn: ({ id, status }: { id: string; status: string }) =>
      updateDeadLetterStatus(id, status),
    onSuccess: () => { message.success('状态已更新'); setStatusModal({ open: false, id: '', current: '' }); invalidate() },
  })

  const { mutate: doRetry } = useMutation({
    mutationFn: (taskId: string) => retryTask(taskId),
    onSuccess: () => { message.success('已触发重试'); invalidate() },
  })

  const columns = [
    {
      title: '死信 ID',
      dataIndex: 'id',
      width: 90,
      render: (v: string) => <Text code style={{ fontSize: 11 }}>{v.slice(-8)}</Text>,
    },
    {
      title: '文档',
      dataIndex: 'docId',
      width: 100,
      render: (v: string) => (
        <a onClick={() => navigate(`/files/${v}`)} style={{ fontSize: 12 }}>
          {v.slice(-8)}
        </a>
      ),
    },
    {
      title: '类型',
      dataIndex: 'taskType',
      width: 90,
      render: (v: string) => (
        <Tag color={TASK_TYPE_COLOR[v] ?? 'default'} style={{ fontSize: 11 }}>{v}</Tag>
      ),
    },
    {
      title: '状态',
      dataIndex: 'resolutionStatus',
      width: 100,
      render: (v: string) => <StatusTag status={v} />,
    },
    {
      title: '处理人',
      dataIndex: 'assignedTo',
      width: 90,
      render: (v: string) => v || <Text type="secondary">-</Text>,
    },
    {
      title: '最近错误',
      dataIndex: 'lastError',
      render: (v: string) => (
        <Tooltip title={v}>
          <Text type="secondary" style={{ fontSize: 12 }}>{truncate(v, 50)}</Text>
        </Tooltip>
      ),
    },
    {
      title: '创建时间',
      dataIndex: 'createdAt',
      width: 160,
      render: (v: string) => <Text style={{ fontSize: 12 }}>{formatTime(v)}</Text>,
    },
    {
      title: '操作',
      width: 180,
      render: (_: unknown, row: DeadLetterTaskDto) => (
        <div style={{ display: 'flex', gap: 4, flexWrap: 'wrap' }}>
          <Button
            size="small"
            disabled={row.resolutionStatus !== 'open'}
            onClick={() => { setAssignModal({ open: true, id: row.id }); assignForm.resetFields() }}
          >
            指派
          </Button>
          <Button
            size="small"
            disabled={row.resolutionStatus === 'closed'}
            onClick={() => {
              setStatusModal({ open: true, id: row.id, current: row.resolutionStatus })
              statusForm.resetFields()
            }}
          >
            更新状态
          </Button>
          <Popconfirm
            title="确认触发手动重试？"
            onConfirm={() => doRetry(row.taskId)}
            okText="确认"
            cancelText="取消"
          >
            <Button size="small" type="primary" ghost>重试</Button>
          </Popconfirm>
        </div>
      ),
    },
  ]

  return (
    <div className={styles.page}>
      <div className={styles.header}>
        <Title level={3} style={{ margin: 0 }}>任务运维 · 死信队列</Title>
      </div>

      <div className={styles.tableCard}>
        <Table
          columns={columns}
          dataSource={deadLetters}
          rowKey="id"
          loading={isLoading}
          pagination={{ pageSize: 20 }}
          locale={{ emptyText: '暂无死信任务' }}
        />
      </div>

      {/* 指派 Modal */}
      <Modal
        title="指派处理人"
        open={assignModal.open}
        onOk={() => {
          assignForm.validateFields().then(({ assignedTo }) =>
            doAssign({ id: assignModal.id, assignedTo })
          )
        }}
        onCancel={() => setAssignModal({ open: false, id: '' })}
        confirmLoading={assigning}
        okText="确认"
      >
        <Form form={assignForm} layout="vertical" style={{ marginTop: 16 }}>
          <Form.Item name="assignedTo" label="处理人" rules={[{ required: true, whitespace: true, message: '请输入处理人' }]}>
            <Input placeholder="如：alice" />
          </Form.Item>
        </Form>
      </Modal>

      {/* 更新状态 Modal */}
      <Modal
        title="更新处理状态"
        open={statusModal.open}
        onOk={() => {
          statusForm.validateFields().then(({ status }) =>
            doUpdateStatus({ id: statusModal.id, status })
          )
        }}
        onCancel={() => setStatusModal({ open: false, id: '', current: '' })}
        confirmLoading={updatingStatus}
        okText="确认"
      >
        <Form form={statusForm} layout="vertical" style={{ marginTop: 16 }}>
          <Form.Item name="status" label="目标状态" rules={[{ required: true }]}>
            <Select
              placeholder="选择目标状态"
              options={(NEXT_STATUS[statusModal.current] ?? []).map((s) => ({
                label: s,
                value: s,
              }))}
            />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  )
}

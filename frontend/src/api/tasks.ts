import client from './client'
import type { DeadLetterTaskDto } from '../types/api'

export const listDeadLetters = (): Promise<DeadLetterTaskDto[]> =>
  client.get('/tasks/dead-letter')

export const updateDeadLetterStatus = (
  id: string,
  status: string
): Promise<void> =>
  client.patch(`/tasks/dead-letter/${id}/status`, { status })

export const retryTask = (taskId: string): Promise<void> =>
  client.post(`/tasks/retry/${taskId}`)

import client from './client'
import type { QaQualityReportDto, QaRecordItemDto, QaReplayDto } from '../types/api'

export const listQaRecords = (limit = 20): Promise<QaRecordItemDto[]> =>
  client.get('/records/qa', { params: { limit } })

export const getQaReplay = (qaId: string): Promise<QaReplayDto> =>
  client.get(`/records/qa/${qaId}/replay`)

export const getQualityReport = (params?: {
  from?: string
  to?: string
}): Promise<QaQualityReportDto> =>
  client.get('/records/qa/quality-report', { params })

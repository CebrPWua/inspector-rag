import client from './client'
import type {
  AskRequest,
  AskResponse,
  ConversationMessageDto,
  QaDetailResponse,
  RejectThresholdConfigDto,
  UpdateRejectThresholdConfigRequest,
} from '../types/api'

export const askQuestion = (req: AskRequest): Promise<AskResponse> =>
  client.post('/qa/ask', req)

export const getQaDetail = (qaId: string): Promise<QaDetailResponse> =>
  client.get(`/qa/${qaId}`)

export const getConversationMessages = (conversationId: string): Promise<ConversationMessageDto[]> =>
  client.get(`/qa/conversations/${conversationId}/messages`)

export const getRejectThresholdConfig = (): Promise<RejectThresholdConfigDto> =>
  client.get('/qa/config/reject-thresholds')

export const updateRejectThresholdConfig = (
  req: UpdateRejectThresholdConfigRequest,
  operator?: string,
): Promise<RejectThresholdConfigDto> =>
  client.put('/qa/config/reject-thresholds', req, {
    headers: operator ? { 'X-Operator': operator } : undefined,
  })

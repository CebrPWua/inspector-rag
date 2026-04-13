import client from './client'
import type { AskRequest, AskResponse, ConversationMessageDto, QaDetailResponse } from '../types/api'

export const askQuestion = (req: AskRequest): Promise<AskResponse> =>
  client.post('/qa/ask', req)

export const getQaDetail = (qaId: string): Promise<QaDetailResponse> =>
  client.get(`/qa/${qaId}`)

export const getConversationMessages = (conversationId: string): Promise<ConversationMessageDto[]> =>
  client.get(`/qa/conversations/${conversationId}/messages`)

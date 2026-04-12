import client from './client'
import type { AskRequest, AskResponse, QaDetailResponse } from '../types/api'

export const askQuestion = (req: AskRequest): Promise<AskResponse> =>
  client.post('/qa/ask', req)

export const getQaDetail = (qaId: string): Promise<QaDetailResponse> =>
  client.get(`/qa/${qaId}`)

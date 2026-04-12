import client from './client'
import type { FileDetailResponse, UploadFileResponse } from '../types/api'

export const uploadFile = (formData: FormData): Promise<UploadFileResponse> =>
  client.post('/files/upload', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
  })

export const getFileDetail = (docId: string): Promise<FileDetailResponse> =>
  client.get(`/files/${docId}`)

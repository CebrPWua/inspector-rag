import client from './client'
import type { FileDetailResponse, FileListItemResponse, UploadFileResponse } from '../types/api'

export const uploadFile = (formData: FormData): Promise<UploadFileResponse> =>
  client.post('/files/upload', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
  })

export const getFileDetail = (docId: string): Promise<FileDetailResponse> =>
  client.get(`/files/${docId}`)

export const getFileList = (limit = 200): Promise<FileListItemResponse[]> =>
  client.get('/files', { params: { limit } })

export const deleteFile = (docId: string): Promise<void> =>
  client.delete(`/files/${docId}`)

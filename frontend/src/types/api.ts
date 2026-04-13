// API 类型定义

export interface ApiResponse<T> {
  success: boolean
  code: string
  message: string
  data: T
}

// --- file-management ---
export interface UploadFileResponse {
  docId: string
  duplicate: boolean
  parseTaskId: string | null
}

export interface FileDetailResponse {
  docId: string
  lawName: string
  lawCode: string
  versionNo: string
  docType: string
  status: 'active' | 'inactive' | 'pending_confirm'
  parseStatus: 'pending' | 'processing' | 'success' | 'failed'
  sourceFileName: string
  fileHash: string
  storagePath: string
  createdAt: string
}

export interface FileListItemResponse {
  docId: string
  lawName: string
  lawCode: string
  versionNo: string
  docType: string
  status: 'active' | 'inactive' | 'pending_confirm'
  parseStatus: 'pending' | 'processing' | 'success' | 'failed'
  createdAt: string
}

// --- search-and-return ---
export interface EvidenceDto {
  citeNo: number
  chunkId: string
  lawName: string
  articleNo: string
  quotedText: string
  sourceType: 'vector' | 'keyword' | 'hybrid'
  finalScore: number
  pageStart: number
  pageEnd: number
  fileVersion: string
}

export interface AskRequest {
  question: string
  filters?: {
    industryTags?: string[]
    docTypes?: string[]
    publishOrg?: string
    effectiveOn?: string
  }
}

export interface AskResponse {
  qaId: string
  normalizedQuestion: string
  answer: string
  answerStatus?: 'success' | 'reject' | 'failed'
  evidences: EvidenceDto[]
}

export interface QaDetailResponse {
  qaId: string
  question: string
  normalizedQuestion: string
  answer: string
  answerStatus: 'success' | 'reject' | 'failed'
  createdAt: string
  evidences: EvidenceDto[]
}

// --- records ---
export interface QaRecordItemDto {
  qaId: string
  question: string
  answerStatus: 'success' | 'reject' | 'failed'
  elapsedMs: number
  createdAt: string
}

export interface QaReplayCandidateDto {
  chunkId: string
  sourceType: string
  rawScore: number
  finalScore: number
  rankNo: number
}

export interface QaReplayEvidenceDto {
  citeNo: number
  chunkId: string
  lawName: string
  articleNo: string
  quotedText: string
}

export interface QaReplayDto {
  qaId: string
  question: string
  normalizedQuestion: string
  answer: string
  candidates: QaReplayCandidateDto[]
  evidences: QaReplayEvidenceDto[]
}

// --- task-scheduling ---
export interface DeadLetterTaskDto {
  id: string
  taskId: string
  docId: string
  taskType: string
  lastError: string
  resolutionStatus: 'open' | 'processing' | 'resolved' | 'closed'
  assignedTo: string
  resolvedAt: string | null
  createdAt: string
  updatedAt: string
}

// --- quality report ---
export interface RejectReasonStatDto {
  reasonCode: string
  count: number
}

export interface QaQualityReportDto {
  from: string
  to: string
  total: number
  success: number
  reject: number
  failed: number
  rejectRate: number
  failedRate: number
  avgElapsedMs: number
  p95ElapsedMs: number
  avgEvidenceCount: number
  avgTop1FinalScore: number
  topRejectReasons: RejectReasonStatDto[]
}

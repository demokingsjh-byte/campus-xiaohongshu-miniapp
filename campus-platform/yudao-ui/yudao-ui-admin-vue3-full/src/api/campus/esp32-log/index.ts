import request from '@/config/axios'

export type CampusEsp32LogStatus =
  | 'CAPTURING'
  | 'SUBMITTED'
  | 'MODEL_DONE'
  | 'SPEAKING'
  | 'COMPLETED'
  | 'IGNORED'
  | 'INTERRUPTED'
  | 'FAILED'
  | 'DISCONNECTED'

export interface CampusEsp32Log {
  id: number
  sessionId: string
  deviceId: string
  requestId: string
  clientIp: string
  status: CampusEsp32LogStatus
  audioBytes: number
  imageCount: number
  captureMs?: number
  submitMs?: number
  asrMs?: number
  modelFirstTokenMs?: number
  modelTotalMs?: number
  ttsFirstAudioMs?: number
  ttsAudioMs?: number
  totalMs?: number
  errorCode?: string
  errorMessage?: string
  createTime: string
  updateTime: string
}

export interface CampusEsp32LogQuery {
  pageNo: number
  pageSize: number
  deviceId?: string
  requestId?: string
  status?: CampusEsp32LogStatus
  createTimeStart?: string
  createTimeEnd?: string
}

export interface CampusEsp32LogSummary {
  totalCount: number
  completedCount: number
  failedCount: number
  averageTotalMs?: number
  averageFirstAudioMs?: number
  averageModelMs?: number
  lastTime?: string
}

export const getCampusEsp32LogPage = (params: CampusEsp32LogQuery) =>
  request.get<PageResult<CampusEsp32Log[]>>({ url: '/campus/esp32/log/page', params })

export const getCampusEsp32Log = (id: number) =>
  request.get<CampusEsp32Log>({ url: '/campus/esp32/log/get', params: { id } })

export const getCampusEsp32LogSummary = (params: CampusEsp32LogQuery) =>
  request.get<CampusEsp32LogSummary>({ url: '/campus/esp32/log/summary', params })

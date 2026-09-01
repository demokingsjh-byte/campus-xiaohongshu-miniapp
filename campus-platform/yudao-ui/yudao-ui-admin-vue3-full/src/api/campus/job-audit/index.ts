import request from '@/config/axios'

export interface CampusJobAuditItem {
  id: number
  userId: number
  tenantId: number
  schoolName: string
  campusName: string
  title: string
  content: string
  price?: number
  location?: string
  tradeMode?: string
  contact?: string
  tagsJson?: string
  imagesJson?: string
  status: number
  auditReason?: string
  auditTime?: string
  auditorId?: number
  createTime: string
  updateTime: string
  publisherName?: string
  publisherMobile?: string
}

export interface CampusJobAuditQuery {
  pageNo: number
  pageSize: number
  keyword?: string
  status?: number
  tenantId?: number
  schoolName?: string
  campusName?: string
  createTimeStart?: string
  createTimeEnd?: string
}

export interface CampusJobAuditSummary {
  totalCount: number
  pendingCount: number
  approvedCount: number
  rejectedCount: number
}

export const getCampusJobAuditPage = (params: CampusJobAuditQuery) =>
  request.get<PageResult<CampusJobAuditItem[]>>({ url: '/campus/job-audit/page', params })

export const getCampusJobAudit = (id: number) =>
  request.get<CampusJobAuditItem>({ url: '/campus/job-audit/get', params: { id } })

export const getCampusJobAuditSummary = (tenantId?: number) =>
  request.get<CampusJobAuditSummary>({
    url: '/campus/job-audit/summary',
    params: tenantId ? { tenantId } : {}
  })

export const reviewCampusJob = (data: { id: number; approved: boolean; reason?: string }) =>
  request.post<boolean>({ url: '/campus/job-audit/review', data })

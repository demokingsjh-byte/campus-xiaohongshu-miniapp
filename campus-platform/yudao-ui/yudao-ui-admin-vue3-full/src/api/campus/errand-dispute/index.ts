import request from '@/config/axios'

export interface CampusErrandDisputeItem {
  orderId: number
  orderNo: string
  postId: number
  title: string
  coverImage?: string
  amount: number
  publisherId: number
  publisherName?: string
  publisherMobile?: string
  helperId: number
  helperName?: string
  helperMobile?: string
  tenantId: number
  orderStatus: number
  fulfillmentStatus: number
  completionNote?: string
  completionImagesJson?: string
  submittedAt?: string
  confirmExpiresAt?: string
  disputeStatus: number
  disputeReason: string
  disputeImagesJson?: string
  disputedAt?: string
  disputeResolution?: string
  disputeResolvedAt?: string
  disputeResolverId?: number
  refundStatus?: number
  messages?: Array<{ id: number; senderId: number; senderName?: string; content: string; createTime?: string }>
}

export interface CampusErrandDisputeQuery {
  pageNo: number
  pageSize: number
  keyword?: string
  status?: number
  tenantId?: number
}

export const getCampusErrandDisputePage = (params: CampusErrandDisputeQuery) =>
  request.get<PageResult<CampusErrandDisputeItem[]>>({ url: '/campus/errand-dispute/page', params })

export const getCampusErrandDispute = (orderId: number) =>
  request.get<CampusErrandDisputeItem>({ url: '/campus/errand-dispute/get', params: { orderId } })

export const resolveCampusErrandDispute = (data: { orderId: number; result: 2 | 3; resolution: string }) =>
  request.post<boolean>({ url: '/campus/errand-dispute/resolve', data })

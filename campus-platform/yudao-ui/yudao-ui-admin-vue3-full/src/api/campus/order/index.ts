import request from '@/config/axios'

export interface CampusTradeOrder {
  id: number
  orderNo: string
  productId: number
  buyerId: number
  sellerId: number
  tenantId: number
  title: string
  coverImage: string
  amount: number
  status: number
  statusText: string
  expiresAt?: string
  paidAt?: string
  completedAt?: string
  closedAt?: string
  closeReason?: string
  wechatTradeState?: string
  wechatQueryAt?: string
  wechatQueryError?: string
  wxTransactionId?: string
  refundNo?: string
  wxRefundId?: string
  refundStatus: number
  refundStatusText: string
  refundAmount?: number
  refundReason?: string
  refundRequestedAt?: string
  refundedAt?: string
  refundError?: string
  refundOperator?: string
  refundNotifyAt?: string
  createTime: string
  updateTime: string
  buyerName?: string
  buyerAvatar?: string
  buyerMobile?: string
  sellerName?: string
  sellerAvatar?: string
  sellerMobile?: string
  sellerContact?: string
  schoolName?: string
  campusName?: string
  location?: string
}

export interface CampusTradeOrderQuery {
  pageNo: number
  pageSize: number
  orderNo?: string
  keyword?: string
  status?: number
  refundStatus?: number
  tenantId?: number
  createTimeStart?: string
  createTimeEnd?: string
}

export interface CampusTradeOrderSummary {
  totalCount: number
  waitingCount: number
  paidCount: number
  refundedCount: number
  paidAmount: number
  refundedAmount: number
}

export interface CampusTradeRefundResult {
  orderId: number
  orderNo: string
  orderStatus: number
  refundNo: string
  wxRefundId?: string
  refundStatus: number
  refundStatusText: string
  refundAmount?: number
  refundReason?: string
  refundError?: string
  refundedAt?: string
}

export const getCampusTradeOrderPage = (params: CampusTradeOrderQuery) =>
  request.get<PageResult<CampusTradeOrder[]>>({ url: '/campus/trade-order/page', params })

export const getCampusTradeOrder = (id: number) =>
  request.get<CampusTradeOrder>({ url: '/campus/trade-order/get', params: { id } })

export const getCampusTradeOrderSummary = (tenantId?: number) =>
  request.get<CampusTradeOrderSummary>({
    url: '/campus/trade-order/summary',
    params: tenantId ? { tenantId } : {}
  })

export const refundCampusTradeOrder = (data: { orderId: number; reason: string }) =>
  request.post<CampusTradeRefundResult>({ url: '/campus/trade-order/refund', data })

export const syncCampusTradeRefund = (id: number) =>
  request.post<CampusTradeRefundResult>({
    url: '/campus/trade-order/refund-sync',
    params: { id }
  })

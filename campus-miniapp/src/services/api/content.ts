import type { CampusPost } from '@/mock/campus';
import { request } from '@/utils/http';

export interface CampusPostPage {
  list: CampusPost[]
  total: number
}

export interface CampusPostComment {
  id: number
  postId: number
  userId: number
  parentId?: number
  replyToUserId?: number
  author: string
  avatar?: string
  avatarText?: string
  content: string
  mentionUserIds?: number[]
  images?: string[]
  replyToAuthor?: string
  time: string
  owner?: boolean
  likeCount: number
  replyCount: number
  liked?: boolean
  createTime?: string
}

export interface CampusPostCommentPage {
  list: CampusPostComment[]
  total: number
}

export interface CampusPostCreateParams {
  type: string
  title: string
  content: string
  price?: string
  originalPrice?: string
  location?: string
  tradeMode?: string
  visibleRange?: string
  contact?: string
  anonymous?: boolean
  tags?: string[]
  images?: string[]
}

export interface CampusPostPageParams {
  tenantId?: number
  channel?: string
  keyword?: string
  pageNo?: number
  pageSize?: number
}

export interface CampusPostReportParams {
  reason: string
  detail?: string
}

export interface CampusPostCommentReportParams {
  reason: string
  detail?: string
}

export interface CampusTradePayParams {
  orderId: number
  orderNo: string
  status: number
  timeStamp?: string
  nonceStr?: string
  packageValue?: string
  signType?: string
  paySign?: string
}

export interface CampusTradeContact {
  orderId?: number
  status: number
  paid: boolean
  sellerName?: string
  contact?: string
}

export interface CampusTradeOrder {
  id: number
  orderNo: string
  postId: number
  buyerId: number
  sellerId: number
  buyerName?: string
  sellerName?: string
  title: string
  coverImage?: string
  amount: string | number
  status: number
  statusText: string
  expiresAt?: string
  paidAt?: string
  completedAt?: string
  closedAt?: string
  closeReason?: string
  expired?: boolean
}

export interface CampusTradeOrderPage {
  list: CampusTradeOrder[]
  total: number
}

export interface CampusTradePaymentStatus {
  orderId: number
  orderNo: string
  status: number
  paid: boolean
  retryable?: boolean
  wechatTradeState?: string
  wechatQueryError?: string
  wechatQueriedAt?: string
  expiresAt?: string
  paidAt?: string
}

const POST_BASE = '/campus/post';

export function createCampusPost(params: CampusPostCreateParams) {
  return request.Post<CampusPost>(`${POST_BASE}/create`, params);
}

export function getCampusPostPage(params: CampusPostPageParams) {
  return request.Get<CampusPostPage>(`${POST_BASE}/page`, { params, cacheFor: 0, meta: { ignoreAuth: true } });
}

export function getMyCampusPostPage(params: Pick<CampusPostPageParams, 'pageNo' | 'pageSize'> = {}) {
  return request.Get<CampusPostPage>(`${POST_BASE}/my-page`, { params, cacheFor: 0 });
}

export function getFavoriteCampusPostPage(params: Pick<CampusPostPageParams, 'pageNo' | 'pageSize'> = {}) {
  return request.Get<CampusPostPage>(`${POST_BASE}/favorite-page`, { params, cacheFor: 0 });
}

export function getCampusPost(id: number) {
  return request.Get<CampusPost>(`${POST_BASE}/get`, { params: { id }, cacheFor: 0, meta: { ignoreAuth: true } });
}

export function getCampusPostCommentPage(postId: number, params: { pageNo?: number, pageSize?: number, sort?: 'latest' | 'likes' } = {}) {
  return request.Get<CampusPostCommentPage>(`${POST_BASE}/comment-page`, {
    params: { postId, pageNo: 1, pageSize: 20, sort: 'latest', ...params },
    cacheFor: 0,
    meta: { ignoreAuth: true },
  });
}

export function createCampusPostComment(postId: number, payload: Pick<CampusPostCommentCreateParams, 'content' | 'parentId' | 'replyToUserId' | 'mentionUserIds' | 'images'>) {
  return request.Post<CampusPostComment>(`${POST_BASE}/comment`, payload, { params: { postId } });
}

export function replyCampusPostComment(postId: number, payload: Pick<CampusPostCommentCreateParams, 'content' | 'parentId' | 'replyToUserId' | 'mentionUserIds' | 'images'>) {
  return request.Post<CampusPostComment>(`${POST_BASE}/comment/reply`, payload, { params: { postId } });
}

export interface CampusPostCommentCreateParams {
  content: string
  parentId?: number
  replyToUserId?: number
  mentionUserIds?: number[]
  images?: string[]
}

export function setCampusCommentLike(id: number, active: boolean) {
  return request.Put<CampusPostComment>(`${POST_BASE}/comment/like`, { active }, { params: { id } });
}

export function deleteCampusComment(id: number) {
  return request.Delete<boolean>(`${POST_BASE}/comment/delete`, undefined, { params: { id } });
}

export function reportCampusComment(id: number, params: CampusPostCommentReportParams) {
  return request.Post<boolean>(`${POST_BASE}/comment/report`, params, { params: { id } });
}

export function createCampusContactRequest(postId: number) {
  return request.Post<boolean>(`${POST_BASE}/contact-request`, {}, { params: { postId } });
}

export function createCampusTradeOrder(postId: number) {
  return request.Post<CampusTradeOrder>('/campus/trade/order/create', { postId });
}

export function getCampusTradeOrder(orderId: number) {
  return request.Get<CampusTradeOrder>('/campus/trade/order/get', { params: { id: orderId }, cacheFor: 0 });
}

export function getCampusTradeOrderPage(params: { role?: 'buyer' | 'seller', status?: number, pageNo?: number, pageSize?: number } = {}) {
  return request.Get<CampusTradeOrderPage>('/campus/trade/order/page', {
    params: { role: 'buyer', pageNo: 1, pageSize: 100, ...params },
    cacheFor: 0,
  });
}

export function createCampusTradePayment(orderId: number) {
  return request.Post<CampusTradePayParams>('/campus/trade/order/pay', {}, { params: { orderId } });
}

export function getCampusTradePaymentStatus(orderId: number) {
  return request.Get<CampusTradePaymentStatus>('/campus/trade/order/payment-status', {
    params: { orderId },
    cacheFor: 0,
  });
}

export function cancelCampusTradeOrder(orderId: number) {
  return request.Post<boolean>('/campus/trade/order/cancel', {}, { params: { id: orderId } });
}

export function getCampusTradeContact(postId: number) {
  return request.Get<CampusTradeContact>('/campus/trade/contact', { params: { postId }, cacheFor: 0 });
}

export function setCampusPostLike(id: number, active: boolean) {
  return request.Put<CampusPost>(`${POST_BASE}/like`, { active }, { params: { id } });
}

export function setCampusPostCollect(id: number, active: boolean) {
  return request.Put<CampusPost>(`${POST_BASE}/collect`, { active }, { params: { id } });
}

export function deleteCampusPost(id: number) {
  return request.Delete<boolean>(`${POST_BASE}/delete`, undefined, { params: { id } });
}

export function reportCampusPost(id: number, params: CampusPostReportParams) {
  return request.Post<boolean>(`${POST_BASE}/report`, params, { params: { id } });
}

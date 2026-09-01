import type { CampusPost } from '@/mock/campus';
import { request } from '@/utils/http';
import { getCampusFollowingRecords } from '@/utils/personalRecords';

export interface CampusPostPage {
  list: CampusPost[]
  total: number
}

export interface CampusFollowUser {
  userId: number
  nickname: string
  avatar?: string
  schoolName?: string
  campusName?: string
  mutual?: boolean
  followedAt?: string
}

export interface CampusFollowPage {
  list: CampusFollowUser[]
  total: number
}

export interface CampusHomeCategory {
  key: string
  title: string
  channel: string
  icon: string
  publishType?: string
  iconVisible?: boolean
  titleVisible?: boolean
  enabled?: boolean
  sort?: number
}

export interface CampusHomeConfig {
  searchPlaceholder: string
  notice?: string
  categoryIconVisible?: boolean
  categoryTitleVisible?: boolean
  categories: CampusHomeCategory[]
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
  status?: number
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
  stockTotal?: number
  location?: string
  merchantAddress?: string
  merchantLocationName?: string
  merchantLatitude?: number
  merchantLongitude?: number
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
  participantName?: string
  contact?: string
}

export interface CampusTradeMessage {
  id: number
  orderId: number
  senderId: number
  senderName?: string
  receiverId: number
  content: string
  createTime?: string | number[]
}

export interface CampusTradeOrder {
  id: number
  orderNo: string
  postId: number
  bizType?: number
  buyerId: number
  sellerId: number
  buyerName?: string
  sellerName?: string
  title: string
  coverImage?: string
  amount: string | number
  status: number
  statusText: string
  fulfillmentStatus?: number
  fulfillmentStatusText?: string
  expiresAt?: string
  acceptExpiresAt?: string
  acceptedAt?: string
  submittedAt?: string
  completionNote?: string
  completionImages?: string[]
  confirmExpiresAt?: string
  disputeStatus?: number
  disputeStatusText?: string
  disputeReason?: string
  disputeImages?: string[]
  disputedAt?: string
  disputeResolvedAt?: string
  disputeResolution?: string
  autoConfirmed?: boolean
  paidAt?: string
  completedAt?: string
  closedAt?: string
  closeReason?: string
  refundStatus?: number
  incomeAmount?: string | number
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
const FOLLOW_BASE = '/campus/follow';

export function setCampusUserFollow(targetUserId: number, active: boolean) {
  return request.Put<boolean>(`${FOLLOW_BASE}/set`, {}, {
    params: { targetUserId, active },
    meta: { silentError: true },
  });
}

export function getCampusUserFollowStatus(targetUserId: number) {
  return request.Get<boolean>(`${FOLLOW_BASE}/status`, {
    params: { targetUserId },
    cacheFor: 0,
    meta: { silentError: true },
  });
}

export function getCampusFollowingCount() {
  return request.Get<number>(`${FOLLOW_BASE}/count`, {
    cacheFor: 0,
    meta: { silentError: true },
  });
}

export function getCampusFollowingPage(params: { pageNo?: number, pageSize?: number } = {}) {
  return request.Get<CampusFollowPage>(`${FOLLOW_BASE}/page`, {
    params: { pageNo: 1, pageSize: 100, ...params },
    cacheFor: 0,
    meta: { silentError: true },
  });
}

/** 首次升级时把旧版本保存在本机的关注关系迁移到服务端。 */
export async function migrateLocalCampusFollows(currentUserId?: number) {
  const userId = Number(currentUserId || 0);
  if (!userId)
    return;
  const marker = `campus-follow-server-migrated-v1:${userId}`;
  if (uni.getStorageSync(marker))
    return;
  const records = [
    ...getCampusFollowingRecords(userId),
    ...getCampusFollowingRecords(undefined),
  ];
  const targetIds = [...new Set(records
    .map(item => Number(item.userId || 0))
    .filter(targetUserId => targetUserId > 0 && targetUserId !== userId))];
  let completed = true;
  for (const targetUserId of targetIds) {
    try {
      await setCampusUserFollow(targetUserId, true);
    } catch {
      completed = false;
    }
  }
  if (completed)
    uni.setStorageSync(marker, Date.now());
}

export function createCampusPost(params: CampusPostCreateParams) {
  return request.Post<CampusPost>(`${POST_BASE}/create`, params);
}

export function getCampusPostPage(params: CampusPostPageParams) {
  return request.Get<CampusPostPage>(`${POST_BASE}/page`, { params, cacheFor: 0, meta: { ignoreAuth: true } });
}

/**
 * 首页导航和运营文案由后台“基础设施 / 配置管理”维护。
 * 支持 campus.home.* 全局配置，以及 campus.home.{tenantId}.* 校园级覆盖。
 */
export function getCampusHomeConfig(tenantId?: number) {
  return request.Get<CampusHomeConfig>('/campus/home/config', {
    params: tenantId ? { tenantId } : {},
    cacheFor: 0,
    // 兼容尚未部署首页配置接口的旧服务端：页面会回退到本地蓝湖配置，
    // 不应把可选运营配置的 404 暴露成全局网络错误。
    meta: { ignoreAuth: true, silentError: true },
  });
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

/**
 * 查询当前账号全部订单。接口单页最多返回 100 条，这里继续翻页，
 * 保证状态统计和订单记录不会只显示最近一页。
 */
export async function getAllCampusTradeOrders(role: 'buyer' | 'seller' = 'buyer') {
  const orders: CampusTradeOrder[] = [];
  const pageSize = 100;
  let pageNo = 1;
  let total = 0;
  do {
    const page = await getCampusTradeOrderPage({ role, pageNo, pageSize });
    const list = Array.isArray(page?.list) ? page.list : [];
    orders.push(...list);
    total = Number(page?.total || orders.length);
    if (!list.length)
      break;
    pageNo += 1;
  } while (orders.length < total && pageNo <= 100);
  return { list: orders, total: orders.length };
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

export function getCampusTradeContact(orderId: number) {
  return request.Get<CampusTradeContact>('/campus/trade/contact', {
    params: { orderId },
    cacheFor: 0,
    meta: { silentError: true },
  });
}

export function createCampusErrandOrder(postId: number) {
  return request.Post<CampusTradeOrder>('/campus/trade/order/errand/create', {}, { params: { postId } });
}

export function getCampusErrandOrderByPost(postId: number) {
  return request.Get<CampusTradeOrder>('/campus/trade/order/errand/get-by-post', {
    params: { postId },
    cacheFor: 0,
    meta: { silentError: true },
  });
}

export function acceptCampusErrandOrder(orderId: number) {
  return request.Post<CampusTradeOrder>('/campus/trade/order/errand/accept', {}, { params: { id: orderId } });
}

export function submitCampusErrandOrder(orderId: number, data: { note?: string, images?: string[] }) {
  return request.Post<CampusTradeOrder>('/campus/trade/order/errand/submit', data, { params: { id: orderId } });
}

export function confirmCampusErrandOrder(orderId: number) {
  return request.Post<CampusTradeOrder>('/campus/trade/order/errand/confirm', {}, { params: { id: orderId } });
}

export function disputeCampusErrandOrder(orderId: number, data: { reason: string, images?: string[] }) {
  return request.Post<CampusTradeOrder>('/campus/trade/order/errand/dispute', data, { params: { id: orderId } });
}

export function cancelCampusErrandOrder(orderId: number) {
  return request.Post<CampusTradeOrder>('/campus/trade/order/errand/cancel', {}, { params: { id: orderId } });
}

export function getCampusTradeMessages(orderId: number) {
  return request.Get<CampusTradeMessage[]>('/campus/trade/chat/messages', {
    params: { orderId },
    cacheFor: 0,
    meta: { silentError: true },
  });
}

export function sendCampusTradeMessage(orderId: number, content: string) {
  return request.Post<CampusTradeMessage>('/campus/trade/chat/send', { orderId, content }, {
    meta: { silentError: true },
  });
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

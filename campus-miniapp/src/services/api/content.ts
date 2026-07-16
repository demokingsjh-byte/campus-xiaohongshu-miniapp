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
  author: string
  avatar?: string
  avatarText?: string
  content: string
  time: string
  owner?: boolean
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

export function getCampusPostCommentPage(postId: number, params: { pageNo?: number, pageSize?: number } = {}) {
  return request.Get<CampusPostCommentPage>(`${POST_BASE}/comment-page`, {
    params: { postId, pageNo: 1, pageSize: 20, ...params },
    cacheFor: 0,
    meta: { ignoreAuth: true },
  });
}

export function createCampusPostComment(postId: number, content: string) {
  return request.Post<CampusPostComment>(`${POST_BASE}/comment`, { content }, { params: { postId } });
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

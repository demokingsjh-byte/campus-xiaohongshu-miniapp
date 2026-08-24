import { request } from '@/utils/http';

export type CampusNotificationType = 'INTERACTION' | 'SYSTEM';

export interface CampusNotification {
  id: number
  type: CampusNotificationType
  eventType: string
  actorNickname?: string
  actorAvatar?: string
  mutual?: boolean
  title: string
  content: string
  createdAt?: string
  time: string
  read: boolean
  targetType?: 'POST' | 'PRODUCT' | 'SYSTEM'
  targetId?: number
  targetImage?: string
}

export interface CampusNotificationPage {
  list: CampusNotification[]
  total: number
}

export function getCampusNotificationPage(params: {
  type?: CampusNotificationType
  pageNo?: number
  pageSize?: number
} = {}) {
  return request.Get<CampusNotificationPage>('/campus/notification/page', {
    params: { pageNo: 1, pageSize: 50, ...params },
    cacheFor: 0,
  });
}

export function getCampusNotificationUnreadCount() {
  return request.Get<number>('/campus/notification/unread-count', {
    cacheFor: 0,
    // 旧服务端没有通知接口时，铃铛按 0 条未读降级即可。
    meta: { silentError: true },
  });
}

export function markCampusNotificationRead(id: number) {
  return request.Put<boolean>('/campus/notification/read', {}, { params: { id } });
}

export function markAllCampusNotificationsRead() {
  return request.Put<boolean>('/campus/notification/read-all', {});
}

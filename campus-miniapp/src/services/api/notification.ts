import { request } from '@/utils/http';

export type CampusNotificationType = 'INTERACTION' | 'SYSTEM';

export interface CampusNotification {
  id: number
  type: CampusNotificationType
  eventType: string
  actorNickname?: string
  actorAvatar?: string
  title: string
  content: string
  createdAt?: string
  time: string
  read: boolean
  targetType?: 'POST' | 'PRODUCT' | 'SYSTEM'
  targetId?: number
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
  return request.Get<number>('/campus/notification/unread-count', { cacheFor: 0 });
}

export function markCampusNotificationRead(id: number) {
  return request.Put<boolean>('/campus/notification/read', {}, { params: { id } });
}

export function markAllCampusNotificationsRead() {
  return request.Put<boolean>('/campus/notification/read-all', {});
}

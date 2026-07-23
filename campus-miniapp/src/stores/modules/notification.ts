import type { CampusNotification, CampusNotificationType } from '@/services/api/notification';
import { defineStore } from 'pinia';
import {
  getCampusNotificationPage,
  getCampusNotificationUnreadCount,
  markAllCampusNotificationsRead,
  markCampusNotificationRead,
} from '@/services/api/notification';

export const useCampusNotificationStore = defineStore('CampusNotificationStore', () => {
  const notifications = ref<CampusNotification[]>([]);
  const unreadCount = ref(0);
  const loading = ref(false);
  let requestId = 0;

  async function loadUnreadCount() {
    const currentRequestId = ++requestId;
    try {
      const count = await getCampusNotificationUnreadCount();
      if (currentRequestId === requestId)
        unreadCount.value = Number(count || 0);
    } catch {
      // The bell must remain quiet when the user is offline or browsing as a guest.
    }
    return unreadCount.value;
  }

  async function load(type?: CampusNotificationType) {
    loading.value = true;
    try {
      const page = await getCampusNotificationPage({ type, pageNo: 1, pageSize: 50 });
      notifications.value = (page.list || []).sort((a, b) => {
        const left = a.createdAt ? Date.parse(a.createdAt) : 0;
        const right = b.createdAt ? Date.parse(b.createdAt) : 0;
        return right - left || Number(b.id) - Number(a.id);
      });
      await loadUnreadCount();
      return notifications.value;
    } finally {
      loading.value = false;
    }
  }

  async function markRead(item: CampusNotification) {
    if (!item.read) {
      item.read = true;
      unreadCount.value = Math.max(0, unreadCount.value - 1);
      try {
        await markCampusNotificationRead(item.id);
      } catch {
        item.read = false;
        unreadCount.value += 1;
        throw new Error('通知已读状态更新失败');
      }
    }
  }

  async function markAllRead() {
    if (!unreadCount.value)
      return;
    const previous = notifications.value.map(item => item.read);
    notifications.value.forEach(item => item.read = true);
    unreadCount.value = 0;
    try {
      await markAllCampusNotificationsRead();
    } catch {
      notifications.value.forEach((item, index) => item.read = previous[index] ?? item.read);
      unreadCount.value = notifications.value.filter(item => !item.read).length;
      throw new Error('全部已读操作失败');
    }
  }

  return { notifications, unreadCount, loading, load, loadUnreadCount, markRead, markAllRead };
});

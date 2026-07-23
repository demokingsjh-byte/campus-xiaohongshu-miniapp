import { defineMock } from '@alova/mock';
import { ResultEnum } from '@/enums/httpEnum';
import { createMock } from '@/mock/utils';

const NOTIFICATIONS_KEY = 'campus-mock-server-notifications';

interface MockNotification {
  id: number
  userId: number
  type: 'INTERACTION' | 'SYSTEM'
  eventType: string
  actorNickname?: string
  actorAvatar?: string
  title: string
  content: string
  createdAt: string
  read: boolean
  targetType?: 'POST' | 'PRODUCT' | 'SYSTEM'
  targetId?: number
}

function seedNotifications(): MockNotification[] {
  return [
    { id: 9001, userId: 10001, type: 'INTERACTION', eventType: 'COMMENT', actorNickname: '小满同学', title: '小满同学评论了你的发布', content: '请问桌子的尺寸大概是多少呀？', createdAt: '2026-07-23T08:20:00+08:00', read: false, targetType: 'POST', targetId: 2102 },
    { id: 9002, userId: 10001, type: 'INTERACTION', eventType: 'LIKE', actorNickname: '3 位同学', title: '3 位同学赞了你的内容', content: '毕业出九成新折叠桌和台灯', createdAt: '2026-07-23T07:56:00+08:00', read: false, targetType: 'POST', targetId: 2102 },
    { id: 9003, userId: 10001, type: 'SYSTEM', eventType: 'SYSTEM_NOTICE', title: '校园认证已通过', content: '你已获得当前学校的同校认证标识。', createdAt: '2026-07-22T10:00:00+08:00', read: true, targetType: 'SYSTEM' },
    { id: 9004, userId: 10001, type: 'INTERACTION', eventType: 'REPLY', actorNickname: '赶高铁', title: '赶高铁回复了你', content: '可以的，周五 18:20 东门见。', createdAt: '2026-07-18T18:20:00+08:00', read: true, targetType: 'POST', targetId: 2003 },
  ];
}

function getNotifications(): MockNotification[] {
  const value = uni.getStorageSync(NOTIFICATIONS_KEY);
  if (Array.isArray(value))
    return value;
  const seeded = seedNotifications();
  uni.setStorageSync(NOTIFICATIONS_KEY, seeded);
  return seeded;
}

function setNotifications(list: MockNotification[]) {
  uni.setStorageSync(NOTIFICATIONS_KEY, list);
}

function currentUserId() {
  return Number(uni.getStorageSync('campus-mock-profile')?.id || 10001);
}

function queryOf(params: any) {
  return params?.query || params?.params || {};
}

function response(item: MockNotification) {
  return {
    ...item,
    time: relativeTime(item.createdAt),
  };
}

function relativeTime(value: string) {
  const minutes = Math.max(Math.floor((Date.now() - Date.parse(value)) / 60000), 0);
  if (minutes < 1)
    return '刚刚';
  if (minutes < 60)
    return `${minutes}分钟前`;
  const hours = Math.floor(minutes / 60);
  if (hours < 24)
    return `${hours}小时前`;
  const days = Math.floor(hours / 24);
  return days < 7 ? `${days}天前` : value.slice(0, 10);
}

export function pushMockNotification(notification: Omit<MockNotification, 'id' | 'userId' | 'createdAt' | 'read'> & { userId: number }) {
  const list = getNotifications();
  list.unshift({ ...notification, id: Date.now(), createdAt: new Date().toISOString(), read: false });
  setNotifications(list.slice(0, 100));
}

export const notificationMocks = defineMock({
  '[GET]/api/campus/notification/page': (params) => {
    const query = queryOf(params);
    const userId = currentUserId();
    const type = query.type ? String(query.type) : '';
    const pageNo = Math.max(Number(query.pageNo || 1), 1);
    const pageSize = Math.min(Math.max(Number(query.pageSize || 50), 1), 100);
    const list = getNotifications()
      .filter(item => item.userId === userId && (!type || item.type === type))
      .sort((a, b) => Date.parse(b.createdAt) - Date.parse(a.createdAt));
    const offset = (pageNo - 1) * pageSize;
    return createMock({ data: { list: list.slice(offset, offset + pageSize).map(response), total: list.length } });
  },
  '[GET]/api/campus/notification/unread-count': () => createMock({
    data: getNotifications().filter(item => item.userId === currentUserId() && !item.read).length,
  }),
  '[PUT]/api/campus/notification/read': (params) => {
    const id = Number(queryOf(params).id);
    const list = getNotifications();
    const item = list.find(item => item.id === id && item.userId === currentUserId());
    if (!item)
      return createMock({ data: false, code: ResultEnum.FAIL, message: '通知不存在' });
    item.read = true;
    setNotifications(list);
    return createMock({ data: true });
  },
  '[PUT]/api/campus/notification/read-all': () => {
    const userId = currentUserId();
    const list = getNotifications();
    list.forEach((item) => {
      if (item.userId === userId)
        item.read = true;
    });
    setNotifications(list);
    return createMock({ data: true });
  },
});

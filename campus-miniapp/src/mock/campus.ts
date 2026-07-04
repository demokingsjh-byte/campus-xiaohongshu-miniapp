import type { CampusTenant } from '@/utils/tenant';

export interface CampusRegion {
  id: number
  name: string
  city: string
  campusCount: number
  agentName: string
}

export interface CampusPost {
  id: number
  tenantId: number
  channel: string
  title: string
  content: string
  author: string
  price?: string
  tags: string[]
  likes: number
  comments: number
  coverColor: string
}

export interface CampusPublishType {
  key: string
  title: string
  desc: string
}

export const campusRegions: CampusRegion[] = [
  { id: 1, name: '杭州大学城', city: '杭州', campusCount: 6, agentName: '宋佳豪' },
  { id: 2, name: '滨江高校圈', city: '杭州', campusCount: 4, agentName: '李同学' },
  { id: 3, name: '下沙生活区', city: '杭州', campusCount: 8, agentName: '陈代理' },
];

export const campusTenants: CampusTenant[] = [
  {
    id: 1,
    name: '云点',
    areaName: '杭州大学城',
    agentName: '宋佳豪',
    inviteCode: 'YD1001',
    slogan: '二手、拼车、探店和校园互助都在这里',
  },
  {
    id: 121,
    name: '小租户',
    areaName: '滨江高校圈',
    agentName: '李同学',
    inviteCode: 'YD121',
    slogan: '围绕校区做本地生活服务',
  },
  {
    id: 122,
    name: '测试租户',
    areaName: '下沙生活区',
    agentName: '陈代理',
    inviteCode: 'YD122',
    slogan: '测试校区运营、内容和交易流程',
  },
];

export const campusChannels = ['推荐', '二手闲置', '校园互助', '拼车出行', '探店种草'];

export const campusPosts: CampusPost[] = [
  {
    id: 1001,
    tenantId: 1,
    channel: '二手闲置',
    title: '毕业出九成新折叠桌和台灯',
    content: '宿舍自提，桌面很稳，适合备考和临时办公。',
    author: '晚风同学',
    price: '36',
    tags: ['宿舍自提', '可小刀'],
    likes: 128,
    comments: 16,
    coverColor: '#E8F5E9',
  },
  {
    id: 1002,
    tenantId: 1,
    channel: '校园互助',
    title: '图书馆三楼有人捡到校园卡吗',
    content: '卡套是透明的，里面有一张饭卡和门禁卡。',
    author: '找卡中的小周',
    tags: ['失物招领', '急'],
    likes: 32,
    comments: 9,
    coverColor: '#FFF3E0',
  },
  {
    id: 1003,
    tenantId: 1,
    channel: '探店种草',
    title: '东门新开的轻食店可以冲',
    content: '鸡胸肉不柴，学生证有折扣，午饭高峰要早点去。',
    author: '番茄不加糖',
    tags: ['东门', '学生折扣'],
    likes: 246,
    comments: 38,
    coverColor: '#FCE4EC',
  },
  {
    id: 1004,
    tenantId: 121,
    channel: '拼车出行',
    title: '周五晚滨江到杭州东站拼车',
    content: '18:30 左右出发，还差两位，女生优先。',
    author: '赶高铁',
    price: '22/人',
    tags: ['拼车', '杭州东站'],
    likes: 41,
    comments: 12,
    coverColor: '#E3F2FD',
  },
  {
    id: 1005,
    tenantId: 122,
    channel: '二手闲置',
    title: '出一辆校园代步自行车',
    content: '刹车刚调过，适合校区通勤。',
    author: '测试卖家',
    price: '120',
    tags: ['代步', '当面验车'],
    likes: 57,
    comments: 6,
    coverColor: '#F3E5F5',
  },
];

export const campusPublishTypes: CampusPublishType[] = [
  { key: 'idle', title: '发布闲置', desc: '二手商品、教材、宿舍用品' },
  { key: 'help', title: '校园互助', desc: '失物招领、代取、求助' },
  { key: 'ride', title: '拼车出行', desc: '跨校区、车站、机场拼车' },
  { key: 'shop', title: '探店种草', desc: '校门口美食和周边生活' },
];

export function getDefaultTenant() {
  return campusTenants[0];
}

export function getPostsByTenant(tenantId?: number | null, channel = '推荐') {
  const list = tenantId ? campusPosts.filter(item => item.tenantId === tenantId) : campusPosts;
  if (channel === '推荐')
    return list;
  return list.filter(item => item.channel === channel);
}

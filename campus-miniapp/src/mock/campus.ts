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
  avatarText: string
  school: string
  time: string
  price?: string
  tags: string[]
  likes: number
  comments: number
  coverColor: string
  coverEmoji: string
  coverLabel: string
  height: 'short' | 'medium' | 'tall'
}

export interface CampusPublishType {
  key: string
  title: string
  desc: string
  icon: string
}

export const campusRegions: CampusRegion[] = [
  { id: 1, name: '杭州大学城', city: '杭州', campusCount: 6, agentName: '宋佳豪' },
  { id: 2, name: '滨江高校圈', city: '杭州', campusCount: 4, agentName: '李同学' },
  { id: 3, name: '下沙生活区', city: '杭州', campusCount: 8, agentName: '陈同学' },
];

export const campusTenants: CampusTenant[] = [
  { id: 1, name: '浙江理工大学', areaName: '杭州大学城', agentName: '宋佳豪', inviteCode: 'YD1001', slogan: '今天也在认真生活，顺便认识新同学' },
  { id: 121, name: '杭州电子科技大学', areaName: '下沙生活区', agentName: '李同学', inviteCode: 'YD121', slogan: '二手、拼车、互助，都在校园附近完成' },
  { id: 122, name: '浙江传媒学院', areaName: '下沙生活区', agentName: '陈同学', inviteCode: 'YD122', slogan: '发现校门口真实好吃、好玩和好活动' },
];

export const campusChannels = ['推荐', '二手', '互助', '拼车', '探店', '失物', '社团'];

export const campusPosts: CampusPost[] = [
  { id: 1001, tenantId: 1, channel: '二手', title: '毕业出九成新折叠桌和台灯', content: '宿舍自提，桌面很稳，适合备考和临时办公。两件一起带走再减 10 元。', author: '晚风同学', avatarText: '晚', school: '浙江理工大学', time: '12分钟前', price: '36', tags: ['宿舍自提', '可小刀'], likes: 128, comments: 16, coverColor: '#DDEFE8', coverEmoji: '🪑', coverLabel: '九成新｜实拍', height: 'tall' },
  { id: 1002, tenantId: 1, channel: '失物', title: '图书馆三楼捡到校园卡', content: '透明卡套，里面有饭卡和门禁卡，已经交到一楼服务台。', author: '找卡中的小周', avatarText: '周', school: '浙江理工大学', time: '28分钟前', tags: ['失物招领', '急'], likes: 32, comments: 9, coverColor: '#FFF0D9', coverEmoji: '🪪', coverLabel: '已交服务台', height: 'short' },
  { id: 1003, tenantId: 1, channel: '探店', title: '东门新开的轻食店可以冲', content: '鸡胸肉不柴，学生证有折扣，午饭高峰要早点去。人均 22。', author: '番茄不加糖', avatarText: '茄', school: '浙江理工大学', time: '1小时前', price: '22/人', tags: ['东门', '学生折扣'], likes: 246, comments: 38, coverColor: '#FCE7DE', coverEmoji: '🥗', coverLabel: '今日探店', height: 'medium' },
  { id: 1004, tenantId: 121, channel: '拼车', title: '周五晚下沙到杭州东站拼车', content: '18:30 左右出发，还差两位，行李不要太多。', author: '赶高铁', avatarText: '赶', school: '杭州电子科技大学', time: '2小时前', price: '22/人', tags: ['杭州东站', '女生优先'], likes: 41, comments: 12, coverColor: '#DCEEF3', coverEmoji: '🚕', coverLabel: '周五 18:30', height: 'medium' },
  { id: 1005, tenantId: 1, channel: '互助', title: '求借一晚计算器，明早高数考试', content: '卡西欧 991 或同类都可以，今晚东门取，奶茶答谢。', author: '不想挂科', avatarText: '数', school: '浙江理工大学', time: '3小时前', tags: ['考试互助', '奶茶答谢'], likes: 18, comments: 21, coverColor: '#E6EEE0', coverEmoji: '🧮', coverLabel: '今晚求借', height: 'short' },
  { id: 1006, tenantId: 122, channel: '社团', title: '周日草坪飞盘，新手也欢迎', content: '下午三点操场集合，自带水，飞盘和分队背心我们准备。', author: '飞盘社阿杰', avatarText: '飞', school: '浙江传媒学院', time: '昨天', tags: ['社团活动', '零基础'], likes: 87, comments: 24, coverColor: '#E7E4F0', coverEmoji: '🥏', coverLabel: '周日 15:00', height: 'tall' },
];

export const campusPublishTypes: CampusPublishType[] = [
  { key: 'idle', title: '二手闲置', desc: '教材、数码、宿舍用品', icon: '♻' },
  { key: 'help', title: '校园互助', desc: '代取、求借、经验问答', icon: '🙌' },
  { key: 'ride', title: '拼车出行', desc: '车站、机场、跨校区', icon: '🚕' },
  { key: 'shop', title: '探店种草', desc: '真实校园周边体验', icon: '🥤' },
  { key: 'lost', title: '失物招领', desc: '丢失或捡到物品', icon: '🔎' },
  { key: 'club', title: '社团活动', desc: '招新、比赛、线下活动', icon: '🎉' },
];

export function getDefaultTenant() { return campusTenants[0]; }

export function getPostsByTenant(tenantId?: number | null, channel = '推荐') {
  const list = tenantId ? campusPosts.filter(item => item.tenantId === tenantId) : campusPosts;
  return channel === '推荐' ? list : list.filter(item => item.channel === channel);
}

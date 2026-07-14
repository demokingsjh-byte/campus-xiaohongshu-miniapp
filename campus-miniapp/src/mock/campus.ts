import type { CampusTenant } from '@/utils/tenant';

export interface CampusPost {
  id: number
  tenantId: number
  userId?: number
  type?: string
  channel: string
  title: string
  content: string
  author: string
  avatar?: string
  avatarText: string
  school: string
  campusName?: string
  time: string
  price?: string
  originalPrice?: string
  location?: string
  tradeMode?: string
  visibleRange?: string
  tags: string[]
  images?: string[]
  likes: number
  collects?: number
  comments: number
  views?: number
  status?: number
  liked?: boolean
  collected?: boolean
  owner?: boolean
  createTime?: string
  coverColor: string
  coverEmoji: string
  coverLabel: string
  height: 'short' | 'medium' | 'tall'
  coverImage?: string
}

export interface CampusPublishType {
  key: string
  title: string
  desc: string
  icon: string
}

export const campusTenants: CampusTenant[] = [
  { id: 201, name: '吉首大学', areaName: '湘西高校圈', agentName: '湘西校园站', inviteCode: 'YD201', slogan: '在熟悉的校园里，遇见靠谱同学和真实生活' },
  { id: 202, name: '长沙学院', areaName: '长沙高校圈', agentName: '长沙校园站', inviteCode: 'YD202', slogan: '好物、搭子和活动，都从同校的一次分享开始' },
];

export const campusChannels = ['推荐', '二手', '互助', '拼车', '探店', '失物', '社团'];

export const campusPosts: CampusPost[] = [
  { id: 2001, tenantId: 201, channel: '二手', title: '出四六级真题和听力耳机', content: '真题只写了两套，耳机功能正常，校内方便时可以当面取。', author: '山风同学', avatarText: '山', school: '吉首大学', time: '18分钟前', price: '28', tags: ['考试资料', '校内自提'], likes: 36, comments: 8, coverColor: '#EAF2FF', coverEmoji: '🎧', coverLabel: '实拍｜可自提', height: 'medium' },
  { id: 2002, tenantId: 201, channel: '失物', title: '图书馆二楼捡到校园卡', content: '蓝色卡套，已经交到一楼服务台，失主带姓名信息去认领。', author: '路过的阿青', avatarText: '青', school: '吉首大学', time: '26分钟前', tags: ['失物招领', '已交服务台'], likes: 22, comments: 6, coverColor: '#FFF0D9', coverEmoji: '🪪', coverLabel: '已交服务台', height: 'short' },
  { id: 2003, tenantId: 201, channel: '拼车', title: '周五晚吉首校区去高铁站拼车', content: '18:10 从东门出发，还差两位，行李箱可以放后备箱。', author: '赶车小杨', avatarText: '杨', school: '吉首大学', time: '45分钟前', price: '18/人', tags: ['吉首东站', '费用均摊'], likes: 41, comments: 12, coverColor: '#DCEEF3', coverEmoji: '🚕', coverLabel: '周五 18:10', height: 'tall' },
  { id: 2004, tenantId: 201, channel: '探店', title: '校门口这家湘西小炒很下饭', content: '两个人点三个菜刚好，酸辣口很正，学生证还能送饮料。', author: '米饭加一碗', avatarText: '饭', school: '吉首大学', time: '1小时前', price: '24/人', tags: ['校门口', '学生折扣'], likes: 126, comments: 21, coverColor: '#FCE7DE', coverEmoji: '🍲', coverLabel: '本周探店', height: 'tall' },
  { id: 2005, tenantId: 201, channel: '互助', title: '求借一晚计算器，明早考试', content: '卡西欧 991 或同类都可以，今晚宿舍区取，奶茶答谢。', author: '不想挂科', avatarText: '数', school: '吉首大学', time: '2小时前', tags: ['考试互助', '奶茶答谢'], likes: 18, comments: 21, coverColor: '#F1EEFF', coverEmoji: '🧮', coverLabel: '今晚求借', height: 'short' },
  { id: 2101, tenantId: 202, channel: '互助', title: '周三晚找羽毛球搭子', content: '水平普通，主要想运动一下，球拍和球都可以带，时间可以商量。', author: '橘子汽水', avatarText: '橘', school: '长沙学院', time: '12分钟前', tags: ['找搭子', '新手友好'], likes: 52, comments: 17, coverColor: '#FFF0D9', coverEmoji: '🏸', coverLabel: '今晚约球', height: 'tall' },
  { id: 2102, tenantId: 202, channel: '二手', title: '毕业出九成新折叠桌和台灯', content: '宿舍自提，桌面很稳，适合备考和临时办公。两件一起带走再减 10 元。', author: '晚风同学', avatarText: '晚', school: '长沙学院', time: '29分钟前', price: '36', tags: ['宿舍自提', '可小刀'], likes: 128, comments: 16, coverColor: '#E8F1FF', coverEmoji: '🪑', coverLabel: '九成新｜实拍', height: 'tall' },
  { id: 2103, tenantId: 202, channel: '社团', title: '周日草坪飞盘，新手也欢迎', content: '下午三点操场集合，自带水，飞盘和分队背心我们准备。', author: '飞盘社阿杰', avatarText: '飞', school: '长沙学院', time: '1小时前', tags: ['社团活动', '零基础'], likes: 87, comments: 24, coverColor: '#EFECFF', coverEmoji: '🥏', coverLabel: '周日 15:00', height: 'medium' },
  { id: 2104, tenantId: 202, channel: '探店', title: '洪山路新开的轻食店可以冲', content: '鸡胸肉不柴，学生证有折扣，午饭高峰要早点去。', author: '番茄不加糖', avatarText: '茄', school: '长沙学院', time: '2小时前', price: '22/人', tags: ['洪山路', '学生折扣'], likes: 96, comments: 18, coverColor: '#FCE7DE', coverEmoji: '🥗', coverLabel: '今日探店', height: 'medium' },
  { id: 2105, tenantId: 202, channel: '失物', title: '体育馆门口捡到一串钥匙', content: '有一个小熊挂件，先放在体育馆前台，需要说出钥匙数量认领。', author: '今天也运动', avatarText: '动', school: '长沙学院', time: '3小时前', tags: ['失物招领', '体育馆'], likes: 31, comments: 9, coverColor: '#EAF2F8', coverEmoji: '🔑', coverLabel: '等待认领', height: 'short' },
];

export const campusPublishTypes: CampusPublishType[] = [
  { key: 'idle', title: '二手闲置', desc: '教材、数码、宿舍用品', icon: '♻' },
  { key: 'help', title: '校园互助', desc: '代取、求借、经验问答', icon: '🙌' },
  { key: 'ride', title: '拼车出行', desc: '车站、机场、跨校区', icon: '🚕' },
  { key: 'shop', title: '探店种草', desc: '真实校园周边体验', icon: '🥤' },
  { key: 'lost', title: '失物招领', desc: '丢失或捡到物品', icon: '🔎' },
  { key: 'club', title: '社团活动', desc: '招新、比赛、线下活动', icon: '🎉' },
];

export function getDefaultTenant() {
  return campusTenants[0];
}

export function getPostsByTenant(tenantId?: number | null, channel = '推荐') {
  const list = tenantId ? campusPosts.filter(item => item.tenantId === tenantId) : campusPosts;
  return channel === '推荐' ? list : list.filter(item => item.channel === channel);
}

import request from '@/config/axios'

export interface CampusAnalyticsSummary {
  totalUsers: number
  registeredUsers: number
  onlineUsers: number
  activeUsers: number
  postCount: number
  orderCount: number
  paidOrderCount: number
  revenueAmount: number
  eventCount: number
}

export interface CampusAnalyticsTrend extends Omit<
  CampusAnalyticsSummary,
  'totalUsers' | 'onlineUsers'
> {
  date: string
}

export interface CampusAnalyticsRank {
  name: string
  count: number
}

export interface CampusAnalyticsRecentEvent {
  id: number
  userId: number
  nickname: string
  eventName: string
  pagePath: string
  createTime: string
}

export interface CampusAnalyticsCampus {
  tenantId: number
  name: string
}

export interface CampusAnalyticsOverview {
  today: CampusAnalyticsSummary
  yesterday: CampusAnalyticsSummary
  trend: CampusAnalyticsTrend[]
  topEvents: CampusAnalyticsRank[]
  topPages: CampusAnalyticsRank[]
  recentEvents: CampusAnalyticsRecentEvent[]
  campuses: CampusAnalyticsCampus[]
  onlineWindowMinutes: number
  serverTime: string
}

export interface CampusAnalyticsQuery {
  tenantId?: number
  days: number
}

export const getCampusAnalyticsOverview = (params: CampusAnalyticsQuery) => {
  return request.get<CampusAnalyticsOverview>({ url: '/campus/analytics/overview', params })
}

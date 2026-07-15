import { request } from '@/utils/http';

export type CampusAnalyticsEventName = 'APP_SHOW' | 'APP_HIDE' | 'PAGE_VIEW' | 'HEARTBEAT' | string;

export interface CampusAnalyticsTrackParams {
  sessionId: string
  eventName: CampusAnalyticsEventName
  pagePath?: string
  properties?: Record<string, unknown>
  clientVersion?: string
  sourceScene?: string
}

const ANALYTICS_TRACK = '/campus/analytics/track';

export function trackCampusAnalytics(params: CampusAnalyticsTrackParams) {
  return request.Post<boolean>(ANALYTICS_TRACK, params, {
    meta: {
      silentError: true,
    },
  });
}

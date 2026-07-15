import type { CampusAnalyticsEventName } from '@/services/api/analytics';
import { trackCampusAnalytics } from '@/services/api/analytics';
import { isLogin } from '@/utils/auth';

const HEARTBEAT_INTERVAL = 60_000;
const sessionId = `miniapp-${Date.now()}-${Math.random().toString(36).slice(2, 10)}`;

let heartbeatTimer: ReturnType<typeof setInterval> | null = null;
let sourceScene = '';
let lastTrackedPage = '';

function getCurrentPagePath() {
  const pages = getCurrentPages();
  return pages.length ? pages[pages.length - 1]?.route || '' : '';
}

function getClientVersion() {
  try {
    return uni.getAccountInfoSync().miniProgram.version || 'develop';
  } catch {
    return 'unknown';
  }
}

export async function trackCampusEvent(
  eventName: CampusAnalyticsEventName,
  properties: Record<string, unknown> = {},
) {
  if (!isLogin())
    return;
  try {
    await trackCampusAnalytics({
      sessionId,
      eventName,
      pagePath: getCurrentPagePath(),
      properties,
      clientVersion: getClientVersion(),
      sourceScene,
    });
  } catch {
    // 埋点失败不影响用户正常浏览和发布流程。
  }
}

async function sendHeartbeat() {
  const currentPage = getCurrentPagePath();
  if (currentPage && currentPage !== lastTrackedPage) {
    lastTrackedPage = currentPage;
    await trackCampusEvent('PAGE_VIEW');
    return;
  }
  await trackCampusEvent('HEARTBEAT');
}

export function startCampusAnalytics(scene?: number | string) {
  if (scene !== undefined)
    sourceScene = String(scene);
  if (!isLogin() || heartbeatTimer)
    return;
  lastTrackedPage = getCurrentPagePath();
  void trackCampusEvent('APP_SHOW');
  if (lastTrackedPage)
    void trackCampusEvent('PAGE_VIEW');
  heartbeatTimer = setInterval(() => void sendHeartbeat(), HEARTBEAT_INTERVAL);
}

export function stopCampusAnalytics() {
  if (heartbeatTimer) {
    clearInterval(heartbeatTimer);
    heartbeatTimer = null;
  }
  void trackCampusEvent('APP_HIDE');
}

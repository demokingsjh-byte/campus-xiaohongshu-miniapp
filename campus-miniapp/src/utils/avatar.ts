import { getBaseUrl } from '@/utils/env';

export const DEFAULT_CAMPUS_AVATAR = '/static/images/avatar-default-cartoon.png';

const CAMPUS_OSS_MEDIA_PATTERN = /^https:\/\/dylsjh\.oss-cn-shenzhen\.aliyuncs\.com(\/campus\/[^?#]+)(?:[?#].*)?$/i;

const legacyDefaultAvatars = new Set([
  '/static/icons/ui/avatar-default.svg',
  DEFAULT_CAMPUS_AVATAR,
]);

export function hasAuthorizedCampusAvatar(avatar?: string | null) {
  return Boolean(avatar && !legacyDefaultAvatars.has(avatar));
}

export function resolveCampusAvatar(avatar?: string | null) {
  return hasAuthorizedCampusAvatar(avatar) ? resolveCampusMediaUrl(avatar!) : DEFAULT_CAMPUS_AVATAR;
}

export function resolveCampusMediaUrl(url?: string | null) {
  const normalized = String(url || '').trim();
  const match = normalized.match(CAMPUS_OSS_MEDIA_PATTERN);
  if (!match)
    return normalized;

  const stableUrl = `https://dylsjh.oss-cn-shenzhen.aliyuncs.com${match[1]}`;
  return `${getBaseUrl()}/infra/file/proxy?url=${encodeURIComponent(stableUrl)}`;
}

export const DEFAULT_CAMPUS_AVATAR = '/static/images/avatar-default-cartoon.png';

const legacyDefaultAvatars = new Set([
  '/static/icons/ui/avatar-default.svg',
  DEFAULT_CAMPUS_AVATAR,
]);

export function hasAuthorizedCampusAvatar(avatar?: string | null) {
  return Boolean(avatar && !legacyDefaultAvatars.has(avatar));
}

export function resolveCampusAvatar(avatar?: string | null) {
  return hasAuthorizedCampusAvatar(avatar) ? avatar! : DEFAULT_CAMPUS_AVATAR;
}

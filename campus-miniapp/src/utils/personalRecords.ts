import type { CampusPost } from '@/mock/campus';

export interface CampusFollowingRecord {
  key: string
  userId?: number
  nickname: string
  avatar?: string
  school?: string
  followedAt: number
}

export interface CampusHistoryRecord {
  post: CampusPost
  viewedAt: number
}

export interface CampusDownlistedRecord {
  post: CampusPost
  downlistedAt: number
}

function accountKey(prefix: string, userId?: number) {
  return `${prefix}:${Number(userId || 0) || 'guest'}`;
}

function readList<T>(key: string): T[] {
  const stored = uni.getStorageSync(key);
  if (Array.isArray(stored))
    return stored as T[];
  if (typeof stored === 'string' && stored) {
    try {
      const parsed = JSON.parse(stored);
      return Array.isArray(parsed) ? parsed as T[] : [];
    } catch {
      return [];
    }
  }
  return [];
}

function writeList<T>(key: string, records: T[]) {
  uni.setStorageSync(key, records);
}

function followingIdentity(post: CampusPost) {
  return post.userId ? `user:${post.userId}` : `author:${post.author || 'unknown'}`;
}

export function getCampusFollowingRecords(currentUserId?: number) {
  return readList<CampusFollowingRecord>(accountKey('campus-following-records', currentUserId));
}

export function isCampusFollowing(currentUserId: number | undefined, post: CampusPost) {
  const key = followingIdentity(post);
  return getCampusFollowingRecords(currentUserId).some(item => item.key === key);
}

export function setCampusFollowing(currentUserId: number | undefined, post: CampusPost, active: boolean) {
  const storageKey = accountKey('campus-following-records', currentUserId);
  const key = followingIdentity(post);
  const records = getCampusFollowingRecords(currentUserId).filter(item => item.key !== key);
  if (active) {
    records.unshift({
      key,
      userId: post.userId,
      nickname: post.author || '同校同学',
      avatar: post.avatar,
      school: post.school,
      followedAt: Date.now(),
    });
  }
  writeList(storageKey, records.slice(0, 200));
  return records;
}

export function removeCampusFollowingRecord(currentUserId: number | undefined, recordKey: string) {
  const storageKey = accountKey('campus-following-records', currentUserId);
  const records = getCampusFollowingRecords(currentUserId).filter(item => item.key !== recordKey);
  writeList(storageKey, records);
  return records;
}

export function getCampusHistoryRecords(currentUserId?: number) {
  return readList<CampusHistoryRecord>(accountKey('campus-history-records', currentUserId));
}

export function recordCampusHistory(currentUserId: number | undefined, post: CampusPost) {
  const storageKey = accountKey('campus-history-records', currentUserId);
  const records = getCampusHistoryRecords(currentUserId).filter(item => Number(item.post?.id) !== Number(post.id));
  records.unshift({ post: { ...post }, viewedAt: Date.now() });
  writeList(storageKey, records.slice(0, 100));
  return records;
}

export function clearCampusHistory(currentUserId?: number) {
  uni.removeStorageSync(accountKey('campus-history-records', currentUserId));
}

export function getCampusDownlistedRecords(currentUserId?: number) {
  return readList<CampusDownlistedRecord>(accountKey('campus-downlisted-records', currentUserId));
}

export function addCampusDownlistedRecord(currentUserId: number | undefined, post: CampusPost) {
  const storageKey = accountKey('campus-downlisted-records', currentUserId);
  const records = getCampusDownlistedRecords(currentUserId).filter(item => Number(item.post?.id) !== Number(post.id));
  records.unshift({ post: { ...post }, downlistedAt: Date.now() });
  writeList(storageKey, records.slice(0, 100));
  return records;
}

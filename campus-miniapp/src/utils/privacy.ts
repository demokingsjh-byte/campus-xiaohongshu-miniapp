export const PRIVACY_POLICY_VERSION = '2026-07-13';

const CONSENT_KEY = 'campus-privacy-consent';

export interface PrivacyConsentRecord {
  version: string
  agreedAt: string
}

export function getPrivacyConsent(): PrivacyConsentRecord | null {
  const value = uni.getStorageSync(CONSENT_KEY) as PrivacyConsentRecord | string | undefined;
  if (!value)
    return null;
  try {
    const record = typeof value === 'string' ? JSON.parse(value) as PrivacyConsentRecord : value;
    return record?.version && record?.agreedAt ? record : null;
  } catch {
    return null;
  }
}

export function hasCurrentPrivacyConsent() {
  return getPrivacyConsent()?.version === PRIVACY_POLICY_VERSION;
}

export function recordPrivacyConsent() {
  const record: PrivacyConsentRecord = {
    version: PRIVACY_POLICY_VERSION,
    agreedAt: new Date().toISOString(),
  };
  uni.setStorageSync(CONSENT_KEY, record);
  return record;
}

export function revokePrivacyConsent() {
  uni.removeStorageSync(CONSENT_KEY);
}

export function clearCampusLocalData(options: { keepConsent?: boolean } = {}) {
  const keys = [
    'campus-search-recent',
    'campus-publish-draft',
    'campus-home-channel',
    'campus-mock-server-posts',
    'campus-mock-profile',
    'campus-mock-server-likes',
    'campus-mock-server-favorites',
  ];
  keys.forEach(key => uni.removeStorageSync(key));
  if (!options.keepConsent)
    revokePrivacyConsent();
}

export function openPolicyPage(type: 'privacy' | 'agreement' | 'community' | 'permissions') {
  uni.navigateTo({ url: `/pages/policy/index?type=${type}` });
}

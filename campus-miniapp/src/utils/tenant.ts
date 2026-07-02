import { CAMPUS_TENANT_KEY } from '@/enums/cacheEnum';
import { getCache, removeCache, setCache } from '@/utils/cache';

export interface CampusTenant {
  id: number
  name: string
  inviteCode?: string
}

export function getCampusTenant(): CampusTenant | null {
  return getCache<CampusTenant>(CAMPUS_TENANT_KEY) || null;
}

export function getCampusTenantId(): number | null {
  return getCampusTenant()?.id || null;
}

export function setCampusTenant(tenant: CampusTenant) {
  setCache(CAMPUS_TENANT_KEY, tenant);
}

export function clearCampusTenant() {
  removeCache(CAMPUS_TENANT_KEY);
}

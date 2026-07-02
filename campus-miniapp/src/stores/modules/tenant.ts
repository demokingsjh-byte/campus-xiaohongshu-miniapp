import type { CampusTenant } from '@/utils/tenant';
import { clearCampusTenant, getCampusTenant, setCampusTenant } from '@/utils/tenant';
import { defineStore } from 'pinia';

export const useTenantStore = defineStore('TenantStore', () => {
  const currentTenant = ref<CampusTenant | null>(getCampusTenant());

  const tenantId = computed(() => currentTenant.value?.id || null);
  const tenantName = computed(() => currentTenant.value?.name || '');

  function selectTenant(tenant: CampusTenant) {
    currentTenant.value = tenant;
    setCampusTenant(tenant);
  }

  function clearTenant() {
    currentTenant.value = null;
    clearCampusTenant();
  }

  return {
    currentTenant,
    tenantId,
    tenantName,
    selectTenant,
    clearTenant,
  };
});

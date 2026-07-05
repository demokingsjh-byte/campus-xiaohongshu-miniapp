<script setup lang="ts">
import { useUserStore } from '@/stores/modules/user';
import { onHide, onLaunch, onShow } from '@dcloudio/uni-app';

onLaunch(() => {
  console.log('App Launch');
});
onShow(() => {
  const userStore = useUserStore();
  userStore.initUserInfo().catch((error) => {
    console.warn('init user info failed', error);
  });
  console.log('App Show');
});
onHide(() => {
  console.log('App Hide');
});
</script>

<style>
</style>

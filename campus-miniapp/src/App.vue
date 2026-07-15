<script setup lang="ts">
import { useUserStore } from '@/stores/modules/user';
import { startCampusAnalytics, stopCampusAnalytics } from '@/utils/analytics';

defineOptions({ name: 'CampusApp' });
const userStore = useUserStore();

onLaunch((options) => {
  userStore.initUserInfo().then(() => {
    startCampusAnalytics(options?.scene);
  }).catch(() => {
    // 首页允许游客浏览；需要身份的动作会再次引导登录。
  });
});

onShow((options) => {
  startCampusAnalytics(options?.scene);
});

onHide(() => {
  stopCampusAnalytics();
});
</script>

<style>
page {
  background: #f3f5f9;
}
view,
scroll-view,
image,
input,
textarea,
button {
  box-sizing: border-box;
}
button {
  margin: 0;
}
</style>

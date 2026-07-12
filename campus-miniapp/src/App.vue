<script setup lang="ts">
import { onHide, onLaunch, onShow } from '@dcloudio/uni-app';

onLaunch(() => {
  console.log('App Launch');
});
onShow(() => {
  console.log('App Show');
});
onHide(() => {
  console.log('App Hide');
});
</script>

<style>
page {
  background: #faf8f3;
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

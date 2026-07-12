import { createSSRApp } from 'vue';
import App from './App.vue';
import { setupStore } from './stores';
import './styles/main.css';

export function createApp() {
  const app = createSSRApp(App);

  setupStore(app);

  return {
    app,
  };
}

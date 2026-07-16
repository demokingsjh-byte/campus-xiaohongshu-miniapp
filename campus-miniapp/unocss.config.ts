import { defineConfig, presetIcons } from 'unocss';
import presetWeapp from 'unocss-preset-weapp';
import { transformerAttributify, transformerClass } from 'unocss-preset-weapp/transformer';
import { isWeixinMp } from './build/platform';

const prefix = '';

const transformRules = {
  '.': '-d2e-',
  '/': '-s2f-',
  ':': '-c3a-',
  '%': '-p25-',
  '!': '-e21-',
  '#': '-w23-',
  '(': '-b28-',
  ')': '-b29-',
  '[': '-f4b-',
  ']': '-f5d-',
  '$': '-r24-',
  ',': '-r2c-',
};

export default defineConfig({
  presets: [
    // https://github.com/MellowCo/unocss-preset-weapp
    presetWeapp({
      prefix,
      whRpx: false,
      transform: isWeixinMp as boolean,
      platform: 'uniapp',
      transformRules,
    }) as any,
    presetIcons({
      scale: 1.2,
      warn: true,
    }),
  ],
  shortcuts: [
    {
      center: 'flex justify-center items-center',
    },
  ],
  theme: {
    colors: {
      primary: '#0A84FF',
      secondary: '#F2675A',
      danger: '#D94F45',
      warning: '#D69A20',
      info: '#397D92',
      light: '#F2F6FB',
      dark: '#1D2733',
    },
    fontSize: {
      mini: ['20rpx', '26rpx'],
    },
  },
  transformers: [
    // options https://github.com/MellowCo/unocss-preset-weapp/tree/main/src/transformer/transformerAttributify
    transformerAttributify({
      classPrefix: prefix,
      transformRules,
      nonValuedAttribute: true,
    }) as any,
    // options https://github.com/MellowCo/unocss-preset-weapp/tree/main/src/transformer/transformerClass
    transformerClass({
      transformRules,
    }),
  ],
});

const path = require('node:path');
const ci = require('miniprogram-ci');

const appid = 'wx3141ca27b55b0b9c';
const version = process.env.WECHAT_UPLOAD_VERSION;
const description = process.env.WECHAT_UPLOAD_DESCRIPTION || 'GitHub Actions upload';
const privateKeyPath = process.env.WECHAT_PRIVATE_KEY_PATH;
const robot = Number(process.env.WECHAT_UPLOAD_ROBOT || 1);

if (!version)
  throw new Error('WECHAT_UPLOAD_VERSION is required');
if (!privateKeyPath)
  throw new Error('WECHAT_PRIVATE_KEY_PATH is required');
if (!Number.isInteger(robot) || robot < 1 || robot > 30)
  throw new Error('WECHAT_UPLOAD_ROBOT must be an integer between 1 and 30');

const project = new ci.Project({
  appid,
  type: 'miniProgram',
  projectPath: path.resolve(__dirname, '../dist/build/mp-weixin'),
  privateKeyPath,
  ignores: ['node_modules/**/*'],
});

ci.upload({
  project,
  version,
  desc: description,
  robot,
  setting: {
    es6: true,
    minify: true,
    codeProtect: false,
  },
  onProgressUpdate: (progress) => {
    const message = progress && typeof progress === 'object' ? JSON.stringify(progress) : String(progress);
    console.log(`[miniprogram-ci] ${message}`);
  },
}).then((result) => {
  console.log(`WeChat miniapp ${version} uploaded successfully with robot ${robot}.`);
  if (result)
    console.log(JSON.stringify(result));
}).catch((error) => {
  console.error('WeChat miniapp upload failed.');
  console.error(error && error.stack ? error.stack : error);
  process.exitCode = 1;
});

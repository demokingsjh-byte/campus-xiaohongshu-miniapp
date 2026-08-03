import { getAuthorization } from '@/utils/auth';
import { getBaseUrl, isUseMock } from '@/utils/env';
import { getCampusTenantId } from '@/utils/tenant';

interface UploadResult {
  code: number
  data?: string
  msg?: string
  message?: string
}

function isTemporaryFilePath(filePath: string) {
  let normalized = filePath.trim();
  try {
    normalized = decodeURIComponent(normalized);
  }
  catch {
    // Keep the original value when it is not a valid encoded URI.
  }
  return /^(?:https?|wxfile):\/\/tmp\//i.test(normalized)
    || /^\/?tmp\//i.test(normalized)
    || /^blob:/i.test(normalized)
    || /\/(?:https?|wxfile):\/\/tmp\//i.test(normalized);
}

function uploadFailed(errorLabel: string) {
  return new Error(`${errorLabel}上传失败，请重新选择图片后重试`);
}

function uploadCampusFile(filePath: string, directory: string, errorLabel: string) {
  if (isUseMock()) {
    // 微信 chooseImage 返回的是临时文件路径，应用重启后会失效。
    // Mock 模式也保存一份本地副本，保证发布后重新打开详情仍能显示图片。
    if (!isTemporaryFilePath(filePath) && /^(?:file|wxfile):\/\//i.test(filePath))
      return Promise.resolve(filePath);

    return new Promise<string>((resolve, reject) => {
      uni.saveFile({
        tempFilePath: filePath,
        success: (result) => {
          const savedFilePath = result.savedFilePath || '';
          if (!savedFilePath || savedFilePath === filePath || isTemporaryFilePath(savedFilePath)) {
            reject(uploadFailed(errorLabel));
            return;
          }
          resolve(savedFilePath);
        },
        fail: () => reject(uploadFailed(errorLabel)),
      });
    });
  }

  return new Promise<string>((resolve, reject) => {
    const authorization = getAuthorization();
    const tenantId = getCampusTenantId();
    uni.uploadFile({
      url: `${getBaseUrl()}/infra/file/upload`,
      filePath,
      name: 'file',
      formData: { directory },
      header: {
        ...(authorization ? { Authorization: authorization } : {}),
        ...(tenantId ? { 'tenant-id': String(tenantId), 'X-Tenant-Id': String(tenantId) } : {}),
      },
      success: ({ statusCode, data }) => {
        try {
          const result = JSON.parse(data) as UploadResult;
          if (statusCode === 200 && result.code === 0 && result.data) {
            if (isTemporaryFilePath(result.data)) {
              reject(uploadFailed(errorLabel));
              return;
            }
            resolve(result.data);
            return;
          }
          reject(new Error(result.msg || result.message || `${errorLabel}上传失败`));
        } catch {
          reject(new Error(`${errorLabel}上传响应格式错误`));
        }
      },
      fail: reject,
    });
  });
}

export function uploadCampusAvatar(filePath: string) {
  return uploadCampusFile(filePath, 'campus/avatar', '头像');
}

export function uploadCampusPostImage(filePath: string) {
  return uploadCampusFile(filePath, 'campus/post', '内容图片');
}

export function uploadCampusCommentImage(filePath: string) {
  return uploadCampusFile(filePath, 'campus/comment', '评论图片');
}

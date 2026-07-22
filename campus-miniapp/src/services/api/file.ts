import { getAuthorization } from '@/utils/auth';
import { getBaseUrl, isUseMock } from '@/utils/env';
import { getCampusTenantId } from '@/utils/tenant';

interface UploadResult {
  code: number
  data?: string
  msg?: string
  message?: string
}

function uploadCampusFile(filePath: string, directory: string, errorLabel: string) {
  if (isUseMock())
    return Promise.resolve(filePath);

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

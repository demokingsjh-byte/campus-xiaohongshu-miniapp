import { getAuthorization } from '@/utils/auth';
import { getBaseUrl, isUseMock } from '@/utils/env';
import { getCampusTenantId } from '@/utils/tenant';

interface UploadResult {
  code: number
  data?: string
  message?: string
}

export function uploadCampusAvatar(filePath: string) {
  if (isUseMock())
    return Promise.resolve(filePath);

  return new Promise<string>((resolve, reject) => {
    const authorization = getAuthorization();
    const tenantId = getCampusTenantId();
    uni.uploadFile({
      url: `${getBaseUrl()}/infra/file/upload`,
      filePath,
      name: 'file',
      formData: { directory: 'campus/avatar' },
      header: {
        ...(authorization ? { Authorization: authorization } : {}),
        ...(tenantId ? { 'tenant-id': String(tenantId) } : {}),
      },
      success: ({ statusCode, data }) => {
        try {
          const result = JSON.parse(data) as UploadResult;
          if (statusCode === 200 && result.code === 0 && result.data) {
            resolve(result.data);
            return;
          }
          reject(new Error(result.message || '头像上传失败'));
        } catch {
          reject(new Error('头像上传响应格式错误'));
        }
      },
      fail: reject,
    });
  });
}

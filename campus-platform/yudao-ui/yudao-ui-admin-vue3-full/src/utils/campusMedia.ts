const CAMPUS_OSS_MEDIA_PATTERN =
  /^https:\/\/dylsjh\.oss-cn-shenzhen\.aliyuncs\.com(\/campus\/[^?#]+)(?:[?#].*)?$/i

const getCampusMediaProxyUrl = () => {
  const baseUrl = String(import.meta.env.VITE_BASE_URL || '').replace(/\/$/, '')
  const adminApiUrl = String(import.meta.env.VITE_API_URL || '/admin-api').replace(/\/$/, '')
  const appApiUrl = adminApiUrl.endsWith('/admin-api')
    ? `${adminApiUrl.slice(0, -'/admin-api'.length)}/app-api`
    : '/app-api'
  return `${baseUrl}${appApiUrl}/infra/file/proxy`
}

/**
 * 校园头像和帖子图片保存在私有 OSS 中，数据库里的历史签名地址可能已经过期。
 * 管理后台与小程序共用后端媒体代理，由后端在每次读取时生成新的短期签名。
 */
export const resolveCampusMediaUrl = (value?: string | null) => {
  const url = String(value || '').trim()
  const match = url.match(CAMPUS_OSS_MEDIA_PATTERN)
  if (!match) return url

  const stableUrl = `https://dylsjh.oss-cn-shenzhen.aliyuncs.com${match[1]}`
  return `${getCampusMediaProxyUrl()}?url=${encodeURIComponent(stableUrl)}`
}

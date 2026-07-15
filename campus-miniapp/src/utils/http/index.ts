import type { API } from '@/services/model/baseModel';
import AdapterUniapp from '@alova/adapter-uniapp';
import { createAlova } from 'alova';
import { assign } from 'lodash-es';
import { ContentTypeEnum, ResultEnum } from '@/enums/httpEnum';
import { mockAdapter } from '@/mock';
import { getAuthorization } from '@/utils/auth';
import { getBaseUrl, isUseMock } from '@/utils/env';
import { getCampusTenantId } from '@/utils/tenant';
import { handleHttpStatus, handleLogicError } from './faultTolerance';

const BASE_URL = getBaseUrl();

const ContentType = {
  'Content-Type': ContentTypeEnum.JSON,
  'Accept': 'application/json, text/plain, */*',
};

/**
 * alova 请求实例
 * @link https://github.com/alovajs/alova
 */
const alovaInstance = createAlova({
  baseURL: BASE_URL,
  ...AdapterUniapp({
    /* #ifndef APP-PLUS */
    mockRequest: isUseMock() ? mockAdapter : undefined, // APP 平台无法使用mock
    /* #endif */
  }),
  timeout: 12000,
  beforeRequest: async (method) => {
    method.config.headers = assign(method.config.headers, ContentType);
    const { config } = method;
    const needAuth = !config.meta?.ignoreAuth;
    const authorization = getAuthorization();
    if (needAuth && !authorization) {
      throw new Error('[请求错误]：未登录');
    }
    if (authorization) {
      method.config.headers.authorization = authorization;
    }
    const tenantId = getCampusTenantId();
    if (tenantId) {
      method.config.headers['X-Tenant-Id'] = String(tenantId);
      method.config.headers['tenant-id'] = String(tenantId);
    }
  },
  responded: {
    /**
     * 请求成功的拦截器
     * 第二个参数为当前请求的method实例，你可以用它同步请求前后的配置信息
     * @param response
     * @param method
     */
    onSuccess: async (response, method) => {
      const { config } = method;
      const { requestType } = config;
      const { statusCode, data: rawData, errMsg } = response as UniNamespace.RequestSuccessCallbackResult;
      if (statusCode === 200) {
        if (requestType) {
          return response;
        }
        const { code, msg, message, data } = rawData as API;
        const errorMessage = msg || message || '请求失败，请稍后重试';
        if (code === ResultEnum.SUCCESS || code === 0) {
          return data as any;
        }
        // 逻辑错误处理，与业务相关
        if (!config.meta?.silentError)
          handleLogicError(code, errorMessage);
        throw new Error(`请求错误[${code}]：${errorMessage}`);
      }
      // 处理http状态错误
      if (!config.meta?.silentError)
        handleHttpStatus(statusCode, errMsg || '');
      throw new Error(`HTTP请求错误[${statusCode}]：${errMsg}`);
    },

    /**
     * 请求失败的拦截器，请求错误时将会进入该拦截器。
     */
    onError: async (err, method) => {
      const requestUrl = `${method.baseURL || ''}${method.url || ''}`;
      throw new Error(`[${method.type}] ${requestUrl}: ${err instanceof Error ? err.message : String(err)}`);
    },
    /**
     * 请求完成的拦截器, 无论请求成功或失败都会进入该拦截器
     */
    onComplete: async () => {
      // 处理请求完成逻辑
    },
  },
});

export const request = alovaInstance;

import type { ResultEnum } from '@/enums/httpEnum';

declare interface API<T = any> {
  code: ResultEnum | number
  data?: T
  msg?: string
  message?: string
}

declare interface LoginParams {
  email: string
  password: string
}
declare interface LoginModel {
  token: string
}

declare interface WechatLoginParams {
  code: string
  scene?: string
  inviterUserId?: number
  tenantId?: number
}

declare interface CampusUserInfoModel {
  id?: number
  openid?: string
  unionid?: string
  nickname?: string
  avatar?: string
  mobile?: string
  email?: string
  schoolName?: string
  campusName?: string
  roleType?: string
  mobileBound?: boolean
  lastLoginTime?: string
}

declare interface WechatLoginModel {
  token: string
  refreshToken?: string
  expiresTime?: string
  userInfo?: CampusUserInfoModel
}

declare interface CampusProfileUpdateParams {
  nickname?: string
  avatar?: string
  schoolName?: string
  campusName?: string
  roleType?: string
}

declare interface CampusPhoneBindParams {
  phoneCode: string
}

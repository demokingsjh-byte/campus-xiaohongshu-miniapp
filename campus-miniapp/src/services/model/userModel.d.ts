export interface UserInfoModel {
  /**
   * 用户id
   */
  id?: number
  openid?: string
  unionid?: string
  /**
   * 昵称
   */
  nickname?: string
  /**
   * 头像
   */
  avatar?: string
  /**
   * 邮箱
   */
  email?: string
  mobile?: string
  schoolName?: string
  campusName?: string
  roleType?: string
  mobileBound?: boolean
  lastLoginTime?: string
}

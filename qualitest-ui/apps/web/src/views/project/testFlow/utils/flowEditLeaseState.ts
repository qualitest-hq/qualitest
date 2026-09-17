/**
 * 当前画布写锁 token。
 * 脏稿抢锁后写入；保存时读出放进请求头；释锁后清空。
 */
let currentLeaseToken: string | null = null

/** 写入或清空本地租约 token */
export function setFlowEditLeaseToken(token: string | null) {
  currentLeaseToken = token
}

/** 读取本地租约 token，供保存请求头使用 */
export function getFlowEditLeaseToken() {
  return currentLeaseToken
}

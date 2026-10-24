/** 读取运行时 ICP 网站备案号。正式镜像 config.js 留空，不展示。 */
export function readIcpNumber() {
  const raw = typeof window !== "undefined" ? window.__QUALITEST_ICP__ : null
  const number = raw && typeof raw.number === "string" ? raw.number.trim() : ""
  return number
}

/**
 * 读取运行时公安联网备案（公网安备）。正式镜像留空。
 * @returns {{ number: string, url: string }}
 */
export function readMpsBeian() {
  const raw = typeof window !== "undefined" ? window.__QUALITEST_MPS__ : null
  const number = raw && typeof raw.number === "string" ? raw.number.trim() : ""
  const url =
    raw && typeof raw.url === "string" && raw.url.trim()
      ? raw.url.trim()
      : "https://beian.mps.gov.cn/"
  return { number, url }
}

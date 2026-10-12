/** 读取运行时 ICP 网站备案号。正式镜像 config.js 留空，不展示。 */
export function readIcpNumber() {
  const raw = typeof window !== "undefined" ? window.__QUALITEST_ICP__ : null
  const number = raw && typeof raw.number === "string" ? raw.number.trim() : ""
  return number
}

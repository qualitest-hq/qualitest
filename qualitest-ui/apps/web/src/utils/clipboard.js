/**
 * 剪贴板工具：同步写入系统剪贴板。
 */

/**
 * 把文本同步写入剪贴板。
 * 使用隐藏 textarea + execCommand，避免异步 clipboard API 在部分环境下因失焦失败。
 *
 * @param {string} text 待复制文本
 * @returns {boolean} 是否写入成功
 */
export function copyTextSync(text) {
  const input = text == null ? '' : String(text)
  const el = document.createElement('textarea')
  const previouslyFocused = document.activeElement

  el.value = input
  el.setAttribute('readonly', '')
  el.style.contain = 'strict'
  el.style.position = 'absolute'
  el.style.left = '-9999px'
  el.style.fontSize = '12pt'

  const selection = document.getSelection()
  const originalRange = selection && selection.rangeCount > 0 ? selection.getRangeAt(0) : null

  document.body.appendChild(el)
  el.select()
  el.selectionStart = 0
  el.selectionEnd = input.length

  let ok = false
  try {
    ok = document.execCommand('copy')
  } catch {
    ok = false
  }

  el.remove()

  if (originalRange && selection) {
    selection.removeAllRanges()
    selection.addRange(originalRange)
  }
  if (previouslyFocused && typeof previouslyFocused.focus === 'function') {
    previouslyFocused.focus()
  }

  return ok
}

/**
 * 模板中 {{…}} 占位符的扫描与替换。
 *
 * 默认只把以 flow. / env. / asset. / http. 开头、且前缀后还有内容的内层当作占位路径。
 * 其它 {{…}} 不当占位：只越过开头的 {{，继续向后查找，避免说明性花括号吞掉后面的真占位。
 * 不把反斜杠当转义；原文里的 \ 按普通字符保留。
 * 写入替换结果后不再对结果做二次扫描，变量值里可含花括号。
 */

export type MustacheSpan = {
  /** 含定界符的起始下标 */
  start: number
  /** 含定界符的结束下标（不含） */
  endExclusive: number
  /** 定界符之间的原文（未 trim） */
  inner: string
}

/** 字符串是否以指定前缀开头，且前缀后至少还有一个字符 */
function startsWithScopeAndKey(p: string, prefix: string): boolean {
  return p.startsWith(prefix) && p.length > prefix.length
}

/**
 * 判断内层是否为可求值的占位路径。
 * 先 trim，再检查是否以 flow. / env. / http. / asset. 开头且前缀后非空。
 */
export function isMustachePlaceholderPath(inner: string | null | undefined): boolean {
  if (inner == null) return false
  const p = String(inner).trim()
  return (
    startsWithScopeAndKey(p, 'flow.') ||
    startsWithScopeAndKey(p, 'env.') ||
    startsWithScopeAndKey(p, 'http.') ||
    startsWithScopeAndKey(p, 'asset.')
  )
}

/** 按出现顺序收集默认规则下的占位内层（已 trim，空串丢弃）。 */
export function listMustacheInners(text: string | null | undefined): string[] {
  return listMustacheInnersWhere(text, isMustachePlaceholderPath)
}

/**
 * 按自定义谓词收集占位内层（已 trim，空串丢弃）。
 * @param acceptInner 是否采纳该内层（传入未 trim 原文）
 */
export function listMustacheInnersWhere(
  text: string | null | undefined,
  acceptInner: (inner: string) => boolean,
): string[] {
  const out: string[] = []
  forEachMustacheWhere(text, acceptInner, (span) => {
    const t = span.inner.trim()
    if (t) out.push(t)
  })
  return out
}

/** 按默认路径规则遍历每个占位片段。 */
export function forEachMustache(
  text: string | null | undefined,
  consumer: (span: MustacheSpan) => void,
): void {
  forEachMustacheWhere(text, isMustachePlaceholderPath, consumer)
}

/**
 * 按自定义谓词遍历占位片段。
 * 找到 {{…}} 但谓词不通过时，只前进两个字符（越过 {{），不跳到闭合 }}，以免漏掉后面的真占位。
 * 空的 {{}} 不当占位。
 */
export function forEachMustacheWhere(
  text: string | null | undefined,
  acceptInner: (inner: string) => boolean,
  consumer: (span: MustacheSpan) => void,
): void {
  if (text == null || text === '' || !acceptInner || !consumer) return
  let i = 0
  while (i < text.length) {
    const open = text.indexOf('{{', i)
    if (open < 0) return
    const close = text.indexOf('}}', open + 2)
    if (close < 0) return
    if (close === open + 2) {
      i = open + 2
      continue
    }
    const inner = text.slice(open + 2, close)
    if (acceptInner(inner)) {
      consumer({ start: open, endExclusive: close + 2, inner })
      i = close + 2
    } else {
      i = open + 2
    }
  }
}

/**
 * 按默认路径规则替换模板中的全部占位。
 * @param replacer 内层原文 → 替换文案；返回 null/undefined 时写入空串
 */
export function replaceMustache(
  template: string | null | undefined,
  replacer: (inner: string) => string | null | undefined,
): string {
  return replaceMustacheWhere(template, isMustachePlaceholderPath, replacer)
}

/**
 * 按自定义谓词替换占位：先扫描命中片段，再拼接字面区与替换结果。
 * 字面区（含反斜杠）原样拷贝；替换结果不再扫描。
 */
export function replaceMustacheWhere(
  template: string | null | undefined,
  acceptInner: (inner: string) => boolean,
  replacer: (inner: string) => string | null | undefined,
): string {
  if (template == null) return ''
  if (template === '' || !acceptInner || !replacer) return template
  let out = ''
  let cursor = 0
  forEachMustacheWhere(template, acceptInner, (span) => {
    out += template.slice(cursor, span.start)
    const replacement = replacer(span.inner)
    out += replacement != null ? String(replacement) : ''
    cursor = span.endExclusive
  })
  out += template.slice(cursor)
  return out
}

/**
 * 判断内层是否为单段简写标识（字母或下划线开头，其后为字母数字下划线，且不含点）。
 * 用于把 {{token}} 扩成完整素材占位；默认运行时求值不把它当路径。
 */
export function isShortMustacheIdentifier(inner: string | null | undefined): boolean {
  if (inner == null) return false
  const s = String(inner).trim()
  if (!s || s.includes('.')) return false
  const c0 = s.charCodeAt(0)
  const isLetter =
    (c0 >= 65 && c0 <= 90) || (c0 >= 97 && c0 <= 122) || c0 === 95 /* _ */
  if (!isLetter) return false
  for (let i = 1; i < s.length; i++) {
    const c = s.charCodeAt(i)
    const ok =
      (c >= 65 && c <= 90) ||
      (c >= 97 && c <= 122) ||
      (c >= 48 && c <= 57) ||
      c === 95
    if (!ok) return false
  }
  return true
}

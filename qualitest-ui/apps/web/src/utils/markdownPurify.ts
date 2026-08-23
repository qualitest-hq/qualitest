import createDOMPurify, { type Config, type DOMPurifyI } from 'dompurify'

/** DOMPurify 白名单：AI 回复常见 Markdown 标签，禁止 script/iframe 等 */
export const MARKDOWN_SANITIZE_CONFIG: Config = {
  USE_PROFILES: { html: true },
  ALLOWED_TAGS: [
    'p',
    'br',
    'strong',
    'em',
    'del',
    'code',
    'pre',
    'blockquote',
    'ul',
    'ol',
    'li',
    'h1',
    'h2',
    'h3',
    'h4',
    'h5',
    'h6',
    'table',
    'thead',
    'tbody',
    'tr',
    'th',
    'td',
    'a',
    'hr',
  ],
  ALLOWED_ATTR: ['href', 'title', 'target', 'rel', 'align'],
}

let purifyInstance: DOMPurifyI | null = null
let linkHookInstalled = false

/** 延迟绑定 window.document，避免 Vitest setup 阶段 window 不完整 */
function getPurify(): DOMPurifyI {
  if (purifyInstance) return purifyInstance
  if (typeof window !== 'undefined' && window.document) {
    purifyInstance = createDOMPurify(window)
    return purifyInstance
  }
  throw new Error('sanitizeMarkdownHtml 需要浏览器或 jsdom 环境')
}

function ensureLinkHook() {
  if (linkHookInstalled) return
  const purify = getPurify()
  purify.addHook('afterSanitizeAttributes', (node) => {
    if (node.tagName === 'A') {
      node.setAttribute('target', '_blank')
      node.setAttribute('rel', 'noopener noreferrer')
    }
  })
  linkHookInstalled = true
}

/** 将 marked 输出的 HTML 消毒 */
export function sanitizeMarkdownHtml(raw: string): string {
  ensureLinkHook()
  return getPurify().sanitize(raw, MARKDOWN_SANITIZE_CONFIG)
}

/** 单测重置：jsdom project 下重建 DOMPurify 实例 */
export function resetMarkdownPurifyForTests() {
  purifyInstance = null
  linkHookInstalled = false
}

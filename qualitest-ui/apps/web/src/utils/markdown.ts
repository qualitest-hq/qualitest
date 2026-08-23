import { marked } from 'marked'

import { sanitizeMarkdownHtml } from './markdownPurify'

marked.setOptions({
  gfm: true,
  breaks: true,
})

/**
 * 将 Markdown 转为已消毒的 HTML，供聊天气泡 v-html 使用。
 */
export function renderMarkdown(source: string): string {
  if (!source?.trim()) {
    return ''
  }
  const raw = marked.parse(source, { async: false }) as string
  return sanitizeMarkdownHtml(raw)
}

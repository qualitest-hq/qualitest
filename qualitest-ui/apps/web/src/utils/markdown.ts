import DOMPurify, { type Config } from 'dompurify';
import { marked } from 'marked';

marked.setOptions({
  gfm: true,
  breaks: true,
});

/** DOMPurify 白名单：AI 回复常见 Markdown 标签，禁止 script/iframe 等 */
const SANITIZE_CONFIG: Config = {
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
};

let linkHookInstalled = false;

function ensureLinkHook() {
  if (linkHookInstalled || typeof window === 'undefined') {
    return;
  }
  DOMPurify.addHook('afterSanitizeAttributes', (node) => {
    if (node.tagName === 'A') {
      node.setAttribute('target', '_blank');
      node.setAttribute('rel', 'noopener noreferrer');
    }
  });
  linkHookInstalled = true;
}

/**
 * 将 Markdown 转为已消毒的 HTML，供聊天气泡 v-html 使用。
 */
export function renderMarkdown(source: string): string {
  if (!source?.trim()) {
    return '';
  }
  ensureLinkHook();
  const raw = marked.parse(source, { async: false }) as string;
  return DOMPurify.sanitize(raw, SANITIZE_CONFIG);
}

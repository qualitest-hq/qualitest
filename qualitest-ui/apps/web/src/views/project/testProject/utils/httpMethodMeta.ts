/**
 * HTTP 方法枚举与界面徽章 CSS 类名。
 * 供 API 调试页、文档页、分组树、流程画布等共用。
 */

/** 支持的 HTTP 方法列表 */
export const HTTP_METHODS = ['GET', 'POST', 'PUT', 'PATCH', 'DELETE', 'HEAD', 'OPTIONS'] as const;

export type HttpMethod = (typeof HTTP_METHODS)[number];

const HTTP_METHOD_SET = new Set<string>(HTTP_METHODS);

/** 判断是否为列表中的已知方法 */
export function isKnownHttpMethod(method: string | null | undefined): boolean {
  return HTTP_METHOD_SET.has(String(method || '').toUpperCase());
}

/**
 * API 调试页、分组树：返回 is-GET / is-unknown / is-other 等类名（无额外前缀）。
 */
export function getApiHttpMethodBadgeClass(method: string | null | undefined): string {
  const m = String(method || '').toUpperCase();
  if (!m || m === '—') return 'is-unknown';
  return isKnownHttpMethod(m) ? `is-${m}` : 'is-other';
}

/**
 * API 文档 Tab：返回 doc-method-tag 与 is-METHOD 类名数组。
 */
export function getDocHttpMethodTagClass(method: string | null | undefined): string[] {
  const m = String(method || 'GET').toUpperCase();
  return ['doc-method-tag', isKnownHttpMethod(m) ? `is-${m}` : 'is-other'];
}

/**
 * 流程画布 HTTP 节点：返回 method-badge is-METHOD 类名字符串。
 */
export function getFlowHttpMethodBadgeClass(method: string | null | undefined): string {
  const m = String(method || '').toUpperCase();
  return isKnownHttpMethod(m) ? `method-badge is-${m}` : 'method-badge';
}

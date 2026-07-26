/**
 * HTTP 外联摘要格式化，与后端 FlowHttpRequestBuilder.formatExternalSummary 对齐。
 */
export function formatExternalSummary(method: string, externalUrl: string): string {
  const m = (method || 'GET').toUpperCase();
  const raw = (externalUrl || '').trim();
  if (!raw) return `${m} ↗ —`;
  try {
    if (raw.includes('{{')) {
      const closeIdx = raw.indexOf('}}');
      const slash = closeIdx >= 0 ? raw.indexOf('/', closeIdx + 2) : -1;
      const hostPart = slash > 0 ? raw.substring(0, slash) : raw;
      const pathPart = slash > 0 ? raw.substring(slash) : '';
      return `${m} ↗ ${hostPart}${pathPart}`;
    }
    const withScheme = raw.startsWith('http') ? raw : `https://${raw}`;
    const uri = new URL(withScheme);
    const host = uri.hostname || raw;
    const path = uri.pathname && uri.pathname !== '' ? uri.pathname : '/';
    return `${m} ↗ ${host}${path}`;
  } catch {
    return `${m} ↗ ${raw}`;
  }
}

export function isExternalCallMode(callMode: unknown): boolean {
  return String(callMode || '').trim() === 'external';
}

export function isProjectCallMode(callMode: unknown): boolean {
  return String(callMode || '').trim() === 'project';
}

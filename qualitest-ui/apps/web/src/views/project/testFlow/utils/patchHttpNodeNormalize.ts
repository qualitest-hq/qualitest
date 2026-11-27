/**
 * HTTP 节点 extracts 路径的轻量展示规范化（非 Staging 权威形状）。
 * 权威 HTTP data 规范化在服务端 FlowDesignHttpNodeNormalizer；灌入 Staging 不再走本文件。
 */
import type { ExtractTarget } from '@/utils/flow/extract';
import { normalizeExtractTargetsArray } from '@/utils/flow/extract';

/**
 * 将 LLM 等非标准提取路径规范为 $.a.b。
 * 识别前缀：http.body. / responses. / response. / body.；已是 $. 则不动。
 */
export function normalizeExtractExprPath(raw: string): string {
  const text = raw.trim();
  if (!text) return '';
  if (text.startsWith('$.')) return text;
  if (text.startsWith('http.body.')) return `$.${text.slice('http.body.'.length)}`;
  if (text.startsWith('responses.')) return `$.${text.slice('responses.'.length)}`;
  if (text.startsWith('response.')) return `$.${text.slice('response.'.length)}`;
  if (text.startsWith('body.')) return `$.${text.slice('body.'.length)}`;
  if (!text.startsWith('$') && !text.includes('{{')) return `$.${text}`;
  return text;
}

/**
 * 规范化 extracts：对 expr 做路径规范化，输出标准行。
 */
export function normalizeHttpNodeExtracts(
  extracts: Array<Record<string, unknown>> | undefined,
): ExtractTarget[] {
  if (!Array.isArray(extracts) || !extracts.length) return [];

  const rows: ExtractTarget[] = [];
  for (const row of extracts) {
    const name = String(row.name ?? row.entryKey ?? '').trim();
    let expr = String(row.expr ?? '').trim();
    if (expr) {
      expr = normalizeExtractExprPath(expr);
    }
    if (!name || !expr) continue;
    rows.push({
      from: String(row.from ?? 'body'),
      expr,
      scope: String(row.scope ?? 'flow'),
      name,
      entryKey: String(row.entryKey ?? ''),
      fieldPath: String(row.fieldPath ?? ''),
    });
  }
  return normalizeExtractTargetsArray(rows);
}

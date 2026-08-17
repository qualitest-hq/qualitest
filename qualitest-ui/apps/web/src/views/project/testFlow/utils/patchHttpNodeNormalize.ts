/**
 * AI patch 中 HTTP 节点 data 规范化（前端灌入 Staging / 离线预览用）。
 * <p>
 * project：测值写成 requestValueOverrides（相对资产默认差分），
 * 删除整份 requestConfig、临时 requestBody、apiPath；
 * 并规范化 extracts、补默认 successCheck。
 * external：只处理 extracts 与 successCheck。
 */
import type { FlowDesignPatch } from '../types/aiDesignTypes';
import type { ExtractTarget } from '@/utils/flow/extract';
import { normalizeExtractTargetsArray } from '@/utils/flow/extract';
import { isExternalCallMode } from './httpSummary';
import {
  applyRequestBodyTextToDraft,
  applyRequestValueOverridesToWorkbench,
  buildRequestValueOverridesDiff,
  buildWorkbenchFromApiDetail,
  mergeThickConfigValues,
} from './httpWorkbenchUtils';

type JsonRecord = Record<string, unknown>;

/**
 * 旧式提取路径转成 $.a.b。
 * 识别前缀：http.body. / responses. / response. / body.；已是 $. 则不动。
 * 不根据单段路径猜测补 $.data。
 */
export function convertLegacyExtractExpr(raw: string): string {
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
 * 规范化 extracts：旧字段 value/path 转 expr，输出标准行。
 * 路径保持原样，不自动补 $.data。
 */
export function normalizeHttpNodeExtracts(
  extracts: Array<Record<string, unknown>> | undefined,
): ExtractTarget[] {
  if (!Array.isArray(extracts) || !extracts.length) return [];

  const rows: ExtractTarget[] = [];
  for (const row of extracts) {
    const name = String(row.name ?? row.entryKey ?? '').trim();
    let expr = String(row.expr ?? '').trim();
    if (!expr) {
      const legacy = row.value ?? row.path;
      if (legacy != null && String(legacy).trim()) {
        expr = convertLegacyExtractExpr(String(legacy));
      }
    } else {
      expr = convertLegacyExtractExpr(expr);
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

/**
 * 缺失 successCheck 时补默认 mode：
 * project → inherit；external → off。
 */
function ensureSuccessCheckDefault(data: JsonRecord) {
  const existing = data.successCheck;
  if (
    existing &&
    typeof existing === 'object' &&
    String((existing as JsonRecord).mode ?? '').trim()
  ) {
    return;
  }
  const callMode = String(data.callMode ?? '').trim();
  data.successCheck = {
    mode: isExternalCallMode(callMode) ? 'off' : 'inherit',
  };
}

/** 节点未写 httpMethod 时，从 API 有效 requestConfig.method 补上 */
function syncHttpMethod(data: JsonRecord, apiDetail?: JsonRecord | null) {
  const existing = data.httpMethod;
  if (existing != null && String(existing).trim()) {
    data.httpMethod = String(existing).trim().toUpperCase();
    return;
  }
  const rc = apiDetail?.requestConfig;
  if (rc && typeof rc === 'object') {
    const method = String((rc as JsonRecord).method ?? '').trim();
    if (method) data.httpMethod = method.toUpperCase();
  }
}

/**
 * 汇总已有 overrides、厚 requestConfig、临时 requestBody，相对 API 有效默认做差分，
 * 得到应落盘的 requestValueOverrides。
 */
function buildThinOverrides(
  data: JsonRecord,
  apiDetail?: JsonRecord | null,
): Record<string, unknown> | null {
  const assetBaseline = buildWorkbenchFromApiDetail(apiDetail ?? null);
  const draft = JSON.parse(JSON.stringify(assetBaseline));
  applyRequestValueOverridesToWorkbench(
    draft,
    (data.requestValueOverrides as Record<string, unknown>) || null,
  );
  if (data.requestConfig && typeof data.requestConfig === 'object') {
    mergeThickConfigValues(draft, data.requestConfig as Record<string, unknown>);
  }
  applyRequestBodyTextToDraft(draft, data.requestBody);
  return buildRequestValueOverridesDiff(draft, assetBaseline);
}

/**
 * 规范化单个 HTTP 节点 data。
 *
 * @param data 节点 data
 * @param apiDetail 接口详情（含有效 requestConfig）；外联或未绑定时可省略
 */
export function normalizeHttpNodeData(
  data: JsonRecord,
  apiDetail?: JsonRecord | null,
): JsonRecord {
  const next = JSON.parse(JSON.stringify(data)) as JsonRecord;

  if (Array.isArray(next.extracts)) {
    next.extracts = normalizeHttpNodeExtracts(next.extracts as Array<Record<string, unknown>>);
  }
  ensureSuccessCheckDefault(next);

  const callMode = String(next.callMode ?? '').trim();
  if (isExternalCallMode(callMode)) {
    return next;
  }

  syncHttpMethod(next, apiDetail);

  const overrides = buildThinOverrides(next, apiDetail);
  if (overrides && Object.keys(overrides).length) {
    next.requestValueOverrides = overrides;
  } else {
    delete next.requestValueOverrides;
  }

  delete next.requestConfig;
  delete next.requestBody;
  delete next.apiPath;
  return next;
}

function clonePatch(patch: FlowDesignPatch): FlowDesignPatch {
  return JSON.parse(JSON.stringify(patch)) as FlowDesignPatch;
}

/** 遍历 patch 里 addNodes / updateNodes 的 HTTP 节点 */
function forEachHttpPatchNode(
  patch: FlowDesignPatch,
  fn: (node: { type?: string; data?: JsonRecord }) => void,
) {
  for (const node of patch.addNodes ?? []) fn(node);
  for (const node of patch.updateNodes ?? []) fn(node);
}

function normalizePatchNode(node: { type?: string; data?: JsonRecord }) {
  if (node.type !== 'http' || !node.data) return;
  node.data = normalizeHttpNodeData(node.data);
}

/** 规范化 patch 内所有 HTTP 节点（不拉接口详情，差分基线为空） */
export function normalizeFlowDesignPatchHttpNodes(patch: FlowDesignPatch): FlowDesignPatch {
  const next = clonePatch(patch);
  forEachHttpPatchNode(next, normalizePatchNode);
  return next;
}

/**
 * 按接口 ID 拉取详情后再规范化 HTTP 节点（能相对资产默认做差分）。
 * 同一 apiId 只请求一次。
 */
export async function enrichFlowDesignPatchHttpNodes(
  patch: FlowDesignPatch,
  fetchApiDetail: (apiId: string) => Promise<JsonRecord | null | undefined>,
): Promise<FlowDesignPatch> {
  const next = clonePatch(patch);
  const apiCache = new Map<string, JsonRecord | null>();

  async function resolveApi(apiId: string) {
    if (apiCache.has(apiId)) return apiCache.get(apiId) ?? null;
    const detail = await fetchApiDetail(apiId);
    apiCache.set(apiId, detail ?? null);
    return detail ?? null;
  }

  async function enrichNode(node: { type?: string; data?: JsonRecord }) {
    if (node.type !== 'http' || !node.data) return;
    const apiId = String(node.data.testProjectApiId ?? '').trim();
    const apiDetail = apiId ? await resolveApi(apiId) : null;
    node.data = normalizeHttpNodeData(node.data, apiDetail);
  }

  for (const node of next.addNodes ?? []) {
    await enrichNode(node);
  }
  for (const node of next.updateNodes ?? []) {
    await enrichNode(node);
  }
  return next;
}

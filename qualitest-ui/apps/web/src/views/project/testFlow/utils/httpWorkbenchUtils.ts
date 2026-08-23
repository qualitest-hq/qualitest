/**
 * HTTP 节点请求工作台。
 * 编辑时展示「资产有效请求 + 节点测值覆盖」合成结果；
 * 保存时只把相对资产默认不同的差分写入 requestValueOverrides，不写整份 requestConfig，路径只读。
 */
import { REQUEST_CONFIG_VERSION } from '@/views/project/testProject/utils/apiConfigConstants'
import type { NodeRequestValueOverrides } from '@/views/project/testProject/utils/apiConfigTypes'
import {
  emptyKVRow,
  ensureBodyShape,
  ensureTrailingEmptyRow,
  headersCookiesToRows,
  parseFlexibleJson,
} from '@/views/project/testProject/utils/apiDetailRequestWorkbench'
import { HTTP_METHODS } from '@/views/project/testProject/utils/httpMethodMeta'

export { emptyKVRow, ensureBodyShape, ensureTrailingEmptyRow, HTTP_METHODS };
export type { NodeRequestValueOverrides };

export type KvRow = ReturnType<typeof emptyKVRow>;

/** 规范化 KV 行：补默认字段，并保证末尾有空行便于继续填写 */
export function normalizeKvRows(rows: KvRow[] | undefined): KvRow[] {
  if (!Array.isArray(rows) || !rows.length) return [emptyKVRow()];
  const out = rows.map((r) => ({ ...emptyKVRow(), ...r, _enabled: r._enabled !== false }));
  ensureTrailingEmptyRow(out);
  return out;
}

/** 统计已启用且名称或值非空的 KV 行数 */
export function countFilledRows(rows: KvRow[] | undefined) {
  return (rows || []).filter(
    (r) => r._enabled !== false && (String(r.name || '').trim() || String(r.value || '').trim()),
  ).length;
}

/** 配置弹窗编辑草稿：内存里是完整有效请求，落盘时再差分 */
export interface HttpWorkbenchDraft {
  testProjectApiId: string;
  apiName: string;
  /** 仅展示用，来自 API 资产；保存时不写回节点 */
  apiPath: string;
  requestConfig: {
    configVersion: number;
    method: string;
    queryParams: KvRow[];
    pathParams: KvRow[];
    body: ReturnType<typeof ensureBodyShape>;
  };
  headerRows: KvRow[];
  cookieRows: KvRow[];
}

/** 节点测值覆盖类型（相对资产默认的差分） */
// 见本文件上方 import 的 NodeRequestValueOverrides

/** 从 API 详情构建编辑草稿（详情里的 requestConfig 已是有效配置） */
export function buildWorkbenchFromApiDetail(apiDetail: Record<string, unknown> | null): HttpWorkbenchDraft {
  if (!apiDetail) {
    return {
      testProjectApiId: '',
      apiName: '',
      apiPath: '',
      requestConfig: {
        configVersion: REQUEST_CONFIG_VERSION,
        method: 'GET',
        queryParams: [emptyKVRow()],
        pathParams: [emptyKVRow()],
        body: ensureBodyShape(null),
      },
      headerRows: [emptyKVRow()],
      cookieRows: [emptyKVRow()],
    };
  }
  const rc = (parseFlexibleJson(apiDetail.requestConfig) as Record<string, unknown>) || {};
  return {
    testProjectApiId: String(apiDetail.testProjectApiId ?? ''),
    apiName: String(apiDetail.apiName ?? ''),
    apiPath: String(apiDetail.apiPath ?? ''),
    requestConfig: {
      configVersion: REQUEST_CONFIG_VERSION,
      method: String(rc.method || 'GET').toUpperCase(),
      queryParams: normalizeKvRows(rc.queryParams as KvRow[]),
      pathParams: normalizeKvRows(rc.pathParams as KvRow[]),
      body: ensureBodyShape(rc.body as Record<string, unknown> | null | undefined),
    },
    headerRows: headersCookiesToRows(apiDetail.headers as string),
    cookieRows: headersCookiesToRows(apiDetail.cookies as string),
  };
}

/**
 * 把 requestValueOverrides 叠到草稿：
 * paramDefaults 按 name 写入 query / path / form-data / urlencoded 的 value；
 * bodyExample 写入 JSON body。
 */
export function applyRequestValueOverridesToWorkbench(
  draft: HttpWorkbenchDraft,
  overrides: NodeRequestValueOverrides | null | undefined,
) {
  if (!overrides || typeof overrides !== 'object') return;
  const params = overrides.paramDefaults;
  if (params && typeof params === 'object') {
    for (const rows of collectParamRowPools(draft)) {
      applyParamDefaultsToRows(rows, params);
    }
  }
  if (Object.prototype.hasOwnProperty.call(overrides, 'bodyExample')) {
    applyBodyExampleToDraft(draft, overrides.bodyExample);
  }
}

/** 按参数名把 defaults 写进 KV 行；没有对应行则追加 */
function applyParamDefaultsToRows(rows: KvRow[], params: Record<string, unknown>) {
  for (const [name, value] of Object.entries(params)) {
    if (!name) continue;
    const row = rows.find((r) => String(r.name || '').trim() === name);
    if (row) {
      row.value = value as string;
      row._enabled = true;
    } else {
      rows.push({
        ...emptyKVRow(),
        name,
        value: value as string,
        _enabled: true,
      });
    }
  }
  ensureTrailingEmptyRow(rows);
}

/** 把 bodyExample 写入草稿 JSON body */
export function applyBodyExampleToDraft(draft: HttpWorkbenchDraft, bodyExample: unknown) {
  const body = ensureBodyShape(draft.requestConfig.body) as Record<string, unknown> & {
    mode?: string;
    json?: { example?: unknown; schema?: unknown };
  };
  if (!body.mode || body.mode === 'none') body.mode = 'json';
  if (body.mode === 'json') {
    body.json = {
      ...(body.json || { example: '', schema: null }),
      example: bodyExample as string | Record<string, unknown> | unknown[],
    };
    draft.requestConfig.body = body as HttpWorkbenchDraft['requestConfig']['body'];
  }
}

/** 临时 requestBody 字符串写入草稿 body example（能 parse 则存对象） */
export function applyRequestBodyTextToDraft(draft: HttpWorkbenchDraft, requestBody: unknown) {
  if (requestBody == null || !String(requestBody).trim()) return;
  const bodyText = String(requestBody).trim();
  try {
    applyBodyExampleToDraft(draft, JSON.parse(bodyText));
  } catch {
    applyBodyExampleToDraft(draft, bodyText);
  }
}

/**
 * 用 API 有效配置 + 节点 data 拼编辑草稿。
 * 路径用资产；测值叠 requestValueOverrides。
 */
export function buildWorkbenchFromApiAndNode(
  apiDetail: Record<string, unknown> | null,
  nodeData: Record<string, unknown>,
): HttpWorkbenchDraft {
  const draft = buildWorkbenchFromApiDetail(apiDetail);
  if (!nodeData?.testProjectApiId && !draft.testProjectApiId) {
    return draft;
  }
  if (nodeData.testProjectApiId) {
    draft.testProjectApiId = String(nodeData.testProjectApiId);
  }
  if (nodeData.apiName) draft.apiName = String(nodeData.apiName);
  if (nodeData.httpMethod) {
    draft.requestConfig.method = String(nodeData.httpMethod).toUpperCase();
  }

  applyRequestValueOverridesToWorkbench(
    draft,
    (nodeData.requestValueOverrides as NodeRequestValueOverrides) || null,
  );

  if (Array.isArray(nodeData.headers) && nodeData.headers.length) {
    draft.headerRows = normalizeKvRows(nodeData.headers as KvRow[]);
  }
  if (Array.isArray(nodeData.cookies) && nodeData.cookies.length) {
    draft.cookieRows = normalizeKvRows(nodeData.cookies as KvRow[]);
  }
  return draft;
}

/** 两个测值是否相等（JSON 字符串与对象视为可等价） */
function valueEquals(a: unknown, b: unknown): boolean {
  if (a === b) return true;
  if (a == null || b == null) return false;
  return normalizeValueText(a) === normalizeValueText(b);
}

function normalizeValueText(value: unknown): string {
  if (typeof value === 'string') {
    const t = value.trim();
    if ((t.startsWith('{') && t.endsWith('}')) || (t.startsWith('[') && t.endsWith(']'))) {
      try {
        return JSON.stringify(JSON.parse(t));
      } catch {
        return t;
      }
    }
    return t;
  }
  try {
    return JSON.stringify(value);
  } catch {
    return String(value);
  }
}

/** 从草稿 KV 行收集已填参数名→值 */
function collectFilledParamDefaults(rows: KvRow[]): Record<string, unknown> {
  const out: Record<string, unknown> = {};
  for (const r of rows || []) {
    if (r._enabled === false) continue;
    const name = String(r.name || '').trim();
    if (!name) continue;
    if (r.value == null || String(r.value).trim() === '') continue;
    out[name] = r.value;
  }
  return out;
}

/** 从草稿取出 JSON body example；空则 undefined */
function extractBodyExampleFromDraft(draft: HttpWorkbenchDraft): unknown | undefined {
  const body = draft.requestConfig.body as Record<string, unknown> | undefined;
  if (!body || body.mode !== 'json') return undefined;
  const example = (body.json as Record<string, unknown> | undefined)?.example;
  if (example == null || String(example).trim() === '') return undefined;
  if (typeof example === 'string') {
    try {
      return JSON.parse(example);
    } catch {
      return example;
    }
  }
  return example;
}

/**
 * 草稿里可叠测值的参数行池：query、path、formData、urlencoded。
 * 会顺带 ensureBodyShape，保证 body 子数组存在。
 */
function collectParamRowPools(draft: HttpWorkbenchDraft): KvRow[][] {
  const body = ensureBodyShape(draft.requestConfig.body) as Record<string, unknown> & {
    formData?: KvRow[];
    urlencoded?: KvRow[];
  };
  draft.requestConfig.body = body as HttpWorkbenchDraft['requestConfig']['body'];
  const pools: KvRow[][] = [
    draft.requestConfig.queryParams,
    draft.requestConfig.pathParams,
  ];
  if (Array.isArray(body.formData)) pools.push(body.formData);
  if (Array.isArray(body.urlencoded)) pools.push(body.urlencoded);
  return pools;
}

/** 合并各参数行池中已填的 name→value，供差分或叠层使用 */
function collectAllFilledParamDefaults(draft: HttpWorkbenchDraft): Record<string, unknown> {
  const out: Record<string, unknown> = {};
  for (const rows of collectParamRowPools(draft)) {
    Object.assign(out, collectFilledParamDefaults(rows));
  }
  return out;
}

/**
 * 相对资产基线算节点测值差分：只保留与基线不同的 paramDefaults / bodyExample。
 * 无差异返回 null。form-data、urlencoded 行也会参与对比。
 */
export function buildRequestValueOverridesDiff(
  draft: HttpWorkbenchDraft,
  assetBaseline: HttpWorkbenchDraft,
): NodeRequestValueOverrides | null {
  const draftParams = collectAllFilledParamDefaults(draft);
  const assetParams = collectAllFilledParamDefaults(assetBaseline);

  const paramDefaults: Record<string, unknown> = {};
  for (const [name, value] of Object.entries(draftParams)) {
    if (!valueEquals(value, assetParams[name])) {
      paramDefaults[name] = value;
    }
  }

  const result: NodeRequestValueOverrides = {};
  if (Object.keys(paramDefaults).length) {
    result.paramDefaults = paramDefaults;
  }

  const draftBodyExample = extractBodyExampleFromDraft(draft);
  const assetBodyExample = extractBodyExampleFromDraft(assetBaseline);
  if (draftBodyExample !== undefined && !valueEquals(draftBodyExample, assetBodyExample)) {
    result.bodyExample = draftBodyExample;
  }

  return Object.keys(result).length ? result : null;
}

/**
 * 把编辑草稿写回节点 data：
 * 只写 requestValueOverrides 差分；删除 requestConfig、requestBody、apiPath。
 *
 * @param assetBaseline 打开弹窗时的资产有效配置基线；缺省则空基线（有值都进 overrides）
 */
export function applyWorkbenchToNodeData(
  data: Record<string, unknown>,
  draft: HttpWorkbenchDraft,
  assetBaseline?: HttpWorkbenchDraft | null,
) {
  data.testProjectApiId = draft.testProjectApiId;
  data.apiName = draft.apiName;
  data.httpMethod = draft.requestConfig.method;
  data.callMode = 'project';

  const baseline = assetBaseline || buildWorkbenchFromApiDetail(null);
  const overrides = buildRequestValueOverridesDiff(draft, baseline);
  if (overrides) {
    data.requestValueOverrides = overrides;
  } else {
    delete data.requestValueOverrides;
  }

  delete data.requestConfig;
  delete data.requestBody;
  delete data.apiPath;

  data.headers = draft.headerRows.filter(
    (r) => String(r.name || '').trim() || String(r.value || '').trim(),
  );
  data.cookies = draft.cookieRows.filter(
    (r) => String(r.name || '').trim() || String(r.value || '').trim(),
  );
  if (!data.name || data.name === 'HTTP 请求') data.name = draft.apiName || data.name;
}

/**
 * 统计节点参数数量，供属性面板摘要。
 * 数 requestValueOverrides；GET/HEAD 的 paramDefaults 算 Query，其余算 Body。
 */
export function countHttpParamStats(data: Record<string, unknown>) {
  const overrides = (data.requestValueOverrides as NodeRequestValueOverrides) || {};
  const paramDefaults = (overrides.paramDefaults || {}) as Record<string, unknown>;
  const paramNames = Object.keys(paramDefaults);
  const bodyExampleCount = Object.prototype.hasOwnProperty.call(overrides, 'bodyExample')
    && overrides.bodyExample != null
    && String(overrides.bodyExample).trim() !== ''
    ? 1
    : 0;

  const method = String(data.httpMethod || '').toUpperCase();
  let overrideBodyParamCount = 0;
  let overrideQueryParamCount = 0;
  if (paramNames.length) {
    if (method === 'GET' || method === 'HEAD') overrideQueryParamCount = paramNames.length;
    else overrideBodyParamCount = paramNames.length;
  }

  return {
    query: overrideQueryParamCount,
    path: 0,
    headers: countFilledRows(data.headers as KvRow[]),
    cookies: countFilledRows(data.cookies as KvRow[]),
    body: bodyExampleCount || overrideBodyParamCount,
  };
}

/** DebugKvSheet 各标签页的列标题与占位符 */
export const KV_SHEET_LABELS: Record<string, { name: string; value: string; namePh: string; valuePh: string }> = {
  headers: { name: 'Header 名', value: '值', namePh: 'Header 名', valuePh: '值' },
  query: { name: '参数名', value: '参数值', namePh: '名称', valuePh: '值' },
  path: { name: 'Path 变量', value: '参数值', namePh: '变量名', valuePh: '值' },
  cookies: { name: '名称', value: '值', namePh: 'Cookie 名', valuePh: '值' },
  urlencoded: { name: '字段名', value: '字段值', namePh: '名称', valuePh: '值' },
};

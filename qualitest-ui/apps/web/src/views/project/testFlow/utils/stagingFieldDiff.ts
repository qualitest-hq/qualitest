/**
 * Staging 属性面板「原值 / 现值」对照行构建。
 */
export interface StagingFieldRow {
  /** 唯一键，如 data.name、source */
  key: string;
  label: string;
  baselineText: string;
  draftValue: string;
  /** 对象 / 长文本用多行编辑，避免 JSON 挤在单行 input */
  multiline?: boolean;
}

const NODE_DATA_LABELS: Record<string, string> = {
  name: '节点名称',
  externalUrl: '请求 URL',
  httpMethod: 'HTTP 方法',
  callMode: '调用模式',
  summary: '摘要',
  script: '脚本',
  source: '脚本 source',
  code: '脚本 code',
  extracts: '抽取配置',
  rules: '断言规则',
  expression: '表达式',
  waitMs: '等待(ms)',
  remark: '备注',
};

function formatDisplayValue(value: unknown): string {
  if (value === undefined || value === null) return '';
  if (typeof value === 'object') return JSON.stringify(value, null, 2);
  return String(value);
}

function isMultilineValue(value: unknown, text: string): boolean {
  if (value !== null && typeof value === 'object') return true;
  return text.includes('\n') || text.length > 72;
}

function valuesEqual(a: unknown, b: unknown): boolean {
  return JSON.stringify(a) === JSON.stringify(b);
}

/** 现值框里的 JSON 文本写回对象；解析失败则保留原字符串 */
function parseJsonOrRaw(rawValue: string): unknown {
  const trimmed = rawValue.trim();
  if (!trimmed.startsWith('{') && !trimmed.startsWith('[')) {
    return rawValue;
  }
  try {
    return JSON.parse(rawValue);
  } catch {
    return rawValue;
  }
}

function pushRow(
  rows: StagingFieldRow[],
  key: string,
  label: string,
  baselineValue: unknown,
  draftValue: unknown,
) {
  if (valuesEqual(baselineValue, draftValue)) return;
  const baselineText = formatDisplayValue(baselineValue);
  const draftText = formatDisplayValue(draftValue);
  rows.push({
    key,
    label,
    baselineText,
    draftValue: draftText,
    multiline:
      isMultilineValue(baselineValue, baselineText)
      || isMultilineValue(draftValue, draftText),
  });
}

/** updateNode：对比 baseline 与 draft，生成可编辑对照行 */
export function buildNodeStagingFieldRows(
  baseline?: Record<string, unknown>,
  draft?: Record<string, unknown>,
): StagingFieldRow[] {
  const rows: StagingFieldRow[] = [];
  if (!draft) return rows;

  pushRow(rows, 'type', '节点类型', baseline?.type, draft.type);

  const basePos = baseline?.position as { x?: number; y?: number } | undefined;
  const draftPos = draft.position as { x?: number; y?: number } | undefined;
  if (!valuesEqual(basePos, draftPos)) {
    const baseText =
      basePos != null ? `${basePos.x ?? 0}, ${basePos.y ?? 0}` : '';
    const draftText =
      draftPos != null ? `${draftPos.x ?? 0}, ${draftPos.y ?? 0}` : '';
    rows.push({
      key: 'position',
      label: '位置 (x, y)',
      baselineText: baseText,
      draftValue: draftText,
    });
  }

  const baseData = (baseline?.data ?? {}) as Record<string, unknown>;
  const draftData = (draft.data ?? {}) as Record<string, unknown>;
  const dataKeys = new Set([...Object.keys(baseData), ...Object.keys(draftData)]);
  const orderedKeys = [
    ...(['name'].filter((k) => dataKeys.has(k))),
    ...[...dataKeys].filter((k) => k !== 'name').sort(),
  ];

  for (const field of orderedKeys) {
    pushRow(
      rows,
      `data.${field}`,
      NODE_DATA_LABELS[field] ?? field,
      baseData[field],
      draftData[field],
    );
  }

  return rows;
}

/** updateEdge：对比 baseline 与 draft */
export function buildEdgeStagingFieldRows(
  baseline?: Record<string, unknown>,
  draft?: Record<string, unknown>,
): StagingFieldRow[] {
  const rows: StagingFieldRow[] = [];
  if (!draft) return rows;

  pushRow(rows, 'source', '来源节点', baseline?.source, draft.source);
  pushRow(rows, 'target', '目标节点', baseline?.target, draft.target);
  pushRow(rows, 'label', '标签', baseline?.label, draft.label);
  return rows;
}

const SCENARIO_FIELD_LABELS: Record<string, string> = {
  name: '场景名称',
  remark: '说明',
  testProjectEnvId: '环境 ID',
  flowSeed: '流程变量初值',
  onNodeFailure: '节点失败时',
  onSnapshotFailure: '快照失败时',
};

/** updateScenario：对比 baseline 与 draft */
export function buildScenarioStagingFieldRows(
  baseline?: Record<string, unknown>,
  draft?: Record<string, unknown>,
): StagingFieldRow[] {
  const rows: StagingFieldRow[] = [];
  if (!draft) return rows;

  const keys = new Set([...Object.keys(baseline ?? {}), ...Object.keys(draft)]);
  const orderedKeys = [
    ...(['name'].filter((k) => keys.has(k))),
    ...[...keys].filter((k) => k !== 'name').sort(),
  ];

  for (const field of orderedKeys) {
    if (field === 'id') continue;
    pushRow(
      rows,
      field,
      SCENARIO_FIELD_LABELS[field] ?? field,
      baseline?.[field],
      draft[field],
    );
  }
  return rows;
}

/** 将对照行现值写回 draft 对象（浅层 data 字段） */
export function applyStagingFieldToDraft(
  draft: Record<string, unknown>,
  fieldKey: string,
  rawValue: string,
): Record<string, unknown> {
  const next = JSON.parse(JSON.stringify(draft)) as Record<string, unknown>;

  if (fieldKey === 'type') {
    next.type = rawValue;
    return next;
  }

  if (fieldKey === 'position') {
    const parts = rawValue.split(',').map((s) => s.trim());
    const x = Number(parts[0]);
    const y = Number(parts[1]);
    next.position = {
      x: Number.isFinite(x) ? x : 0,
      y: Number.isFinite(y) ? y : 0,
    };
    return next;
  }

  if (fieldKey.startsWith('data.')) {
    const field = fieldKey.slice(5);
    const data = { ...((next.data as Record<string, unknown> | undefined) ?? {}) };
    data[field] = parseJsonOrRaw(rawValue);
    next.data = data;
    return next;
  }

  if (fieldKey === 'source' || fieldKey === 'target' || fieldKey === 'label') {
    next[fieldKey] = rawValue;
    return next;
  }

  if (fieldKey === 'flowSeed') {
    next.flowSeed = parseJsonOrRaw(rawValue);
    return next;
  }

  if (
    fieldKey === 'name' ||
    fieldKey === 'remark' ||
    fieldKey === 'testProjectEnvId' ||
    fieldKey === 'onNodeFailure' ||
    fieldKey === 'onSnapshotFailure'
  ) {
    next[fieldKey] = rawValue;
  }

  return next;
}

/**
 * Staging confirm 失败时的错误来源分类（依赖 / 画布结构 / 本单元）。
 */
export type StagingConfirmErrorSource = 'dependency' | 'canvas' | 'unit';

export interface StagingConfirmErrorLine {
  source: StagingConfirmErrorSource;
  message: string;
}

const SOURCE_LABEL: Record<StagingConfirmErrorSource, string> = {
  dependency: '依赖未满足',
  canvas: '画布结构问题',
  unit: '本单元校验',
};

const CANVAS_ERROR_PATTERNS: RegExp[] = [
  /开始节点/,
  /未找到开始节点/,
  /meta\.scenarios/,
  /缺少 nodes/,
  /缺少 edges/,
  /^nodes\[\d+\] 缺少 id/,
  /^nodes\[\d+\] id 重复/,
  /^nodes\[\d+\] type 无效/,
  /^nodes\[\d+\] 缺少 data/,
  /^edges\[\d+\] 缺少 (source|target)/,
  /^edges\[\d+\] (source|target) 不存在/,
  /流程只能有一个/,
];

const DEPENDENCY_ERROR_PATTERNS: RegExp[] = [
  /需要先确认/,
  /^确认 addEdge:/,
];

function classifySingleError(message: string): StagingConfirmErrorSource {
  const text = message.trim();
  if (!text) return 'unit';
  if (DEPENDENCY_ERROR_PATTERNS.some((p) => p.test(text))) return 'dependency';
  if (CANVAS_ERROR_PATTERNS.some((p) => p.test(text))) return 'canvas';
  return 'unit';
}

/** 将 dependencyHints 与 validation errors 分类为带来源的错误行 */
export function classifyStagingConfirmErrors(
  dependencyHints: string[] | undefined,
  validationErrors: string[],
): StagingConfirmErrorLine[] {
  const lines: StagingConfirmErrorLine[] = [];
  const seen = new Set<string>();

  for (const hint of dependencyHints ?? []) {
    const message = hint.trim();
    if (!message || seen.has(message)) continue;
    seen.add(message);
    lines.push({ source: 'dependency', message });
  }

  for (const error of validationErrors) {
    const message = error.trim();
    if (!message || seen.has(message)) continue;
    seen.add(message);
    lines.push({ source: classifySingleError(message), message });
  }

  return lines;
}

export function formatStagingConfirmErrorLine(line: StagingConfirmErrorLine): string {
  return `[${SOURCE_LABEL[line.source]}] ${line.message}`;
}

export function groupStagingConfirmErrors(
  lines: StagingConfirmErrorLine[],
): Array<{ source: StagingConfirmErrorSource; label: string; messages: string[] }> {
  const order: StagingConfirmErrorSource[] = ['dependency', 'canvas', 'unit'];
  const buckets = new Map<StagingConfirmErrorSource, string[]>();

  for (const line of lines) {
    const list = buckets.get(line.source) ?? [];
    list.push(line.message);
    buckets.set(line.source, list);
  }

  return order
    .filter((source) => buckets.has(source))
    .map((source) => ({
      source,
      label: SOURCE_LABEL[source],
      messages: buckets.get(source) ?? [],
    }));
}

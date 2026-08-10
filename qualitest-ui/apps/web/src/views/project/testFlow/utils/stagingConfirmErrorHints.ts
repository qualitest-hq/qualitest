/**
 * Staging 确认失败时的错误分类与展示：依赖未满足 / 画布结构 / 本单元校验。
 * 展示前会去掉鉴权机器码前缀（如 AUTH_TOKEN_MISSING:），只显示可读文案。
 */
import { displayAuthCodedMessage } from './stagingAuthHints'

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

/** 归为「画布结构」的常见图校验文案特征 */
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

/** 归为「依赖未满足」的文案特征（须先确认其它 Staging 单元） */
const DEPENDENCY_ERROR_PATTERNS: RegExp[] = [
  /需要先确认/,
  /^确认 addEdge:/,
];

/** 按文案内容判断错误来源；默认为本单元校验 */
function classifySingleError(message: string): StagingConfirmErrorSource {
  const text = message.trim();
  if (!text) return 'unit';
  if (DEPENDENCY_ERROR_PATTERNS.some((p) => p.test(text))) return 'dependency';
  if (CANVAS_ERROR_PATTERNS.some((p) => p.test(text))) return 'canvas';
  return 'unit';
}

/**
 * 合并 dependencyHints 与 validation.errors，去重后分类。
 * 鉴权硬拦等带 CODE 前缀的错误会剥成纯文案再展示。
 */
export function classifyStagingConfirmErrors(
  dependencyHints: string[] | undefined,
  validationErrors: string[],
): StagingConfirmErrorLine[] {
  const lines: StagingConfirmErrorLine[] = [];
  const seen = new Set<string>();

  for (const hint of dependencyHints ?? []) {
    const message = displayAuthCodedMessage(hint);
    if (!message || seen.has(message)) continue;
    seen.add(message);
    lines.push({ source: 'dependency', message });
  }

  for (const error of validationErrors) {
    const message = displayAuthCodedMessage(error);
    if (!message || seen.has(message)) continue;
    seen.add(message);
    lines.push({ source: classifySingleError(message), message });
  }

  return lines;
}

/** 单行错误格式化：带中文来源标签 */
export function formatStagingConfirmErrorLine(line: StagingConfirmErrorLine): string {
  return `[${SOURCE_LABEL[line.source]}] ${line.message}`;
}

/**
 * 按依赖 → 画布 → 本单元顺序分组，供确认失败面板分段展示。
 */
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

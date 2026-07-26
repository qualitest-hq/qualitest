import type { AiModelOption, AiModelVendorGroup, AiModelsListResult } from '@/api/ai/chat';

/** 在厂商分组列表中查找指定模型 */
export function findModelOption(
  modelGroups: AiModelVendorGroup[],
  modelId: string,
): AiModelOption | undefined {
  const id = String(modelId || '').trim();
  if (!id) return undefined;
  for (const group of modelGroups) {
    const found = group.models.find((m) => String(m.aiLlmModelId) === id);
    if (found) return found;
  }
  return undefined;
}

/** 根据会话设置与模型能力计算 UI 思考开关状态 */
export function resolveThinkingForUi(
  sessionThinking: number | null | undefined,
  model?: AiModelOption,
): boolean {
  if (sessionThinking === 1) return true;
  if (sessionThinking === 0) return false;
  if (!model?.thinkingCapable) return false;
  return model.thinkingDefault === true;
}

/** 从模型列表响应中选取默认模型 ID */
export function pickDefaultModelId(data: AiModelsListResult, modelGroups: AiModelVendorGroup[]): string {
  if (data.defaultModelId) return String(data.defaultModelId);
  const first = modelGroups[0]?.models?.[0];
  return first ? String(first.aiLlmModelId) : '';
}

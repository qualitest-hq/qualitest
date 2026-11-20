/**
 * 画布侧 Staging 对外门面。
 * 布局壳、属性面板、落盘组装只经本模块调用，避免调用方直接翻阅单元表内部结构。
 * 能力：灌入待确认变更、确认、拒绝、生成落盘过滤规则、清空内存。
 */
import type { FlowDesignPatch } from '../types/aiDesignTypes';
import type { StagingPersistFilter } from '../types/aiStagingTypes';
import {
  hydratePatchToStaging,
  type HydratePatchToStagingOptions,
} from '../utils/hydratePatchToStaging';
import { useAiStagingStore } from './aiStagingStore';
import { useAiStagingConfirm } from './composables/useAiStagingConfirm';

/**
 * 把 AI 改图补丁灌入 Staging，仅预览到画布，不写库。
 * @returns 当前仍为 pending 的单元数量
 */
export async function applyPending(
  patch: FlowDesignPatch,
  options: HydratePatchToStagingOptions,
): Promise<number> {
  return hydratePatchToStaging(patch, options);
}

/** applyPending 的别名，供会话恢复等「回灌」语义的调用方使用 */
export const hydrate = applyPending;

/** 确认单个 Staging 单元：服务端校验通过后写入画布正式数据 */
export async function confirm(unitId: string): Promise<void> {
  await useAiStagingConfirm().confirmUnit(unitId);
}

/** 拒绝单个 Staging 单元，并回滚该提案在画布上的预览效果 */
export function reject(unitId: string): void {
  useAiStagingConfirm().rejectUnit(unitId);
}

/**
 * 生成落盘/校验用的过滤规则：
 * pending 的新增节点与边不入图；pending 的修改按基线输出，避免未确认草稿进保存。
 */
export function buildPersistFilter(): StagingPersistFilter {
  return useAiStagingStore().buildPersistFilter();
}

/**
 * 只清空 Staging 内存中的单元表与补丁缓存。
 * 切换测试流、离开画布且画布本身已重置时使用；
 * 若还需先撤掉画布上的 pending 视觉效果，应调用 clearAllStagingState。
 */
export function clear(): void {
  useAiStagingStore().reset();
}

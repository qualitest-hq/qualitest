/**
 * Staging 同步引擎：pending 收集、add/update 应用与 orphan add 修剪。
 */
import { watch } from 'vue';

import type { AiStagingCanvasMark, AiStagingUnit } from '../types/aiStagingTypes';

export interface StagingSyncContext<TItem> {
  items: TItem[];
  pendingAddIds: Set<string>;
}

export interface StagingSyncStrategy<TItem> {
  /** 是否属于本 layer 的 pending 单元 */
  isLayerKind: (unit: AiStagingUnit) => boolean;
  /** add* 类：从 unit 构建实体 */
  buildFromAddUnit: (unit: AiStagingUnit) => TItem | null;
  /** update* 类：将 draft 合并进列表 */
  applyUpdateUnit: (unit: AiStagingUnit, items: TItem[]) => TItem[] | null;
  /** 非 add/update 的副作用（如 setActiveScenario） */
  applySideEffect?: (unit: AiStagingUnit, ctx: StagingSyncContext<TItem>) => void;
  getItemId: (item: TItem) => string;
  getMark: (id: string) => AiStagingCanvasMark | undefined;
  /** orphan add 修剪后的回调（如 activeScenarioId 回退） */
  onPrunedAdd?: (prunedId: string, ctx: StagingSyncContext<TItem>) => void;
}

export function runStagingItemSync<TItem>(
  units: Record<string, AiStagingUnit>,
  initialItems: TItem[],
  strategy: StagingSyncStrategy<TItem>,
): TItem[] {
  let items = [...initialItems];
  const pendingAddIds = new Set<string>();
  const ctx: StagingSyncContext<TItem> = { items, pendingAddIds };

  for (const unit of Object.values(units)) {
    if (unit.status !== 'pending' || !strategy.isLayerKind(unit)) continue;

    const built = strategy.buildFromAddUnit(unit);
    if (built) {
      const id = strategy.getItemId(built);
      pendingAddIds.add(id);
      const index = items.findIndex((item) => strategy.getItemId(item) === id);
      if (index < 0) {
        items.push(built);
      } else {
        items[index] = { ...items[index], ...built };
      }
      ctx.items = items;
      continue;
    }

    const nextItems = strategy.applyUpdateUnit(unit, items);
    if (nextItems) {
      items = nextItems;
      ctx.items = items;
    }

    strategy.applySideEffect?.(unit, ctx);
  }

  for (const item of [...items]) {
    const id = strategy.getItemId(item);
    const mark = strategy.getMark(id);
    if (mark?.mode === 'add' && !pendingAddIds.has(id)) {
      items = items.filter((i) => strategy.getItemId(i) !== id);
      strategy.onPrunedAdd?.(id, { items, pendingAddIds });
    }
  }

  return items;
}

/**
 * 监听 unitsById 并执行同步回调。
 */
export function useStagingSync(syncFn: () => void, unitsById: () => Record<string, AiStagingUnit>) {
  watch(unitsById, () => syncFn(), { deep: true, immediate: true });
}

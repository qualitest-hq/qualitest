/**
 * 图持久化与协作域出口。
 *
 * 职责划分：
 * - 脏判定：reconcileFlowDirty、isLayoutOnlyDirty（仅挪坐标/换排版不算内容脏）
 * - 写锁：useFlowEditLease（有内容改动才抢锁；仅布局脏不抢）
 * - 保存：useFlowGraph、buildCanvasPersistGraph（落盘前套用 Staging 过滤）
 * - 外部合入：useExternalGraphSync（他端改图合入本地；本地仅布局脏时保留本机坐标）
 * - 适配：graphAdapter（graph_json ↔ vue-flow 编辑态）
 *
 * 旧路径 graphAdapter.ts、composables、utils 下的同名文件仍为兼容再导出。
 */
export * from './graphAdapter';
export * from './useFlowGraph';
export * from './useFlowEditLease';
export * from './useExternalGraphSync';
export * from './buildCanvasPersistGraph';
export * from './reconcileFlowDirty';
export * from './isLayoutOnlyDirty';

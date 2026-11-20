/**
 * Staging 域对外出口。
 * 提供门面方法，以及造流确认、画布预览、场景 Staging、会话回灌等组合式 API。
 * 旧路径 stores/composables/utils 下的同名文件仍为兼容再导出，新代码优先从本目录引入。
 */
export {
  applyPending,
  hydrate,
  confirm,
  reject,
  buildPersistFilter,
  clear,
} from './canvasStagingFacade';

export { useAiStagingStore } from './aiStagingStore';
export { useAiStagingConfirm } from './composables/useAiStagingConfirm';
export { useAiStagingCanvas } from './composables/useAiStagingCanvas';
export { useAiStagingScenario } from './composables/useAiStagingScenario';
export { createAiStagingHydration } from './composables/useAiStagingHydration';

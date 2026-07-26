import type { InjectionKey, Ref } from 'vue';

/**
 * AI API 助手侧栏 Teleport 挂载点的 provide/inject 键。
 */
export const API_AI_DOCK_KEY: InjectionKey<Ref<HTMLElement | null>> = Symbol('apiAiDock');

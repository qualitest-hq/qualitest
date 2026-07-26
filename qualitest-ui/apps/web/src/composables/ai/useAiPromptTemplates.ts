import { ref } from 'vue';

import type { AiPromptTemplateItem } from '@/api/project/testFlowAi';

/**
 * 加载并缓存 AI 助手输入区快捷提示词模板。
 * load 由调用方在合适时机触发（面板打开、场景切换等）。
 */
export function useAiPromptTemplates(loadFn: () => Promise<AiPromptTemplateItem[]>) {
  const items = ref<AiPromptTemplateItem[]>([]);
  const loading = ref(false);

  async function load() {
    loading.value = true;
    try {
      items.value = await loadFn();
    } catch {
      items.value = [];
    } finally {
      loading.value = false;
    }
  }

  return { items, loading, load };
}

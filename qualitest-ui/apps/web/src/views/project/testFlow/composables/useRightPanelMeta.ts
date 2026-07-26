/**
 * 右栏标题与描述文案。
 */
import { computed } from 'vue';

import { NODE_TYPES } from '../constants/nodeTypes';
import { useRunConfig } from './useRunConfig';
import { useFlowCanvasStore } from '../stores/flowCanvasStore';

export function useRightPanelMeta() {
  const store = useFlowCanvasStore();
  const { getActiveScenario, resolveEnvName, envOptions } = useRunConfig();

  const title = computed(() => {
    if (store.ui.rightMode === 'run') return '运行详情';
    if (store.ui.rightMode === 'scenario') return '运行配置';
    return '属性';
  });

  const desc = computed(() => {
    if (store.ui.rightMode === 'run') return '步骤时间线与 Inspector';
    if (store.ui.rightMode === 'scenario') {
      const sc = getActiveScenario();
      if (!sc) return '环境、变量初值、失败策略与流程返回值';
      // 依赖 envOptions，环境列表加载完成后刷新副标题
      void envOptions.value.length;
      return `场景「${sc.name}」· ${resolveEnvName(sc.testProjectEnvId)}`;
    }
    const sel = store.selected;
    if (!sel) return '选中节点或边以编辑';
    if (sel.kind === 'edge') return `边 · ${sel.id}`;
    const node = store.nodes.find((n) => n.id === sel.id);
    if (!node) return '选中节点或边以编辑';
    const cfg = NODE_TYPES[node.type];
    return `${cfg?.label || node.type} · ${node.id}`;
  });

  /** 右栏正文组件 key：props-node | props-edge | scenario | run */
  const contentKey = computed(() => {
    if (store.ui.rightMode === 'scenario') return 'scenario';
    if (store.ui.rightMode === 'run') return 'run';
    if (store.selected?.kind === 'edge') return 'props-edge';
    return 'props-node';
  });

  return {
    title,
    desc,
    contentKey,
  };
}

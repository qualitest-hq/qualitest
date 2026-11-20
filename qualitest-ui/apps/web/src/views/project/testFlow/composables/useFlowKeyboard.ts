/**
 * 画布全局键盘快捷键：保存、撤销、删除、Esc 停止当前跑流会话、左右键单步。
 * 在画布布局挂载时注册，卸载时注销。
 * Ctrl/Cmd+S 在输入框内也生效（拦截浏览器另存为）；其余编辑键在输入框聚焦时不拦截。
 * Esc 与方向键步进通过跑流会话中枢分发，不直接依赖回放或路径模拟模块。
 */
import { onBeforeUnmount, onMounted, type Ref } from 'vue';

import { useFlowDelete } from './useFlowDelete';
import { useFlowHistory } from './useFlowHistory';
import {
  abortActiveRunSession,
  stepActiveRunSession,
} from '../run/runSessionController';
/** 加载时注册回放 / 路径模拟 / 场景跑的会话钩子，供 Esc 与步进使用 */
import './usePlayback';
import './useFlowSimulate';
import './useFlowScenarioRun';

/** 判断当前焦点是否在可编辑输入控件内 */
export function isInputFocused(): boolean {
  const el = document.activeElement;
  if (!el) return false;
  const tag = el.tagName;
  if (tag === 'INPUT' || tag === 'TEXTAREA' || tag === 'SELECT') return true;
  if (el instanceof HTMLElement && el.isContentEditable) return true;
  return false;
}

export interface UseFlowKeyboardOptions {
  /** 快捷键帮助弹层开关，Esc 时关闭 */
  helpOpen: Ref<boolean>;
  /** 保存画布（与顶栏保存按钮同一入口） */
  onSave: () => void;
}

export function useFlowKeyboard(options: UseFlowKeyboardOptions) {
  const { undo } = useFlowHistory();
  const { deleteSelection } = useFlowDelete();

  function handleKeydown(e: KeyboardEvent) {
    if (e.key === 'Escape') {
      if (abortActiveRunSession()) {
        e.preventDefault();
        return;
      }
      if (options.helpOpen.value) {
        e.preventDefault();
        options.helpOpen.value = false;
      }
      return;
    }

    // 保存：输入框内也拦截，避免浏览器弹出「另存为」
    if ((e.ctrlKey || e.metaKey) && !e.altKey && (e.key === 's' || e.key === 'S')) {
      e.preventDefault();
      options.onSave();
      return;
    }

    if (isInputFocused()) return;

    if (e.ctrlKey && e.key === 'z') {
      e.preventDefault();
      undo();
      return;
    }

    if (e.key === 'Delete' || e.key === 'Backspace') {
      e.preventDefault();
      deleteSelection();
      return;
    }

    if (!e.ctrlKey && !e.metaKey && !e.altKey) {
      if (e.key === 'ArrowLeft') {
        if (stepActiveRunSession('prev')) e.preventDefault();
        return;
      }
      if (e.key === 'ArrowRight') {
        if (stepActiveRunSession('next')) e.preventDefault();
      }
    }
  }

  onMounted(() => {
    document.addEventListener('keydown', handleKeydown);
  });

  onBeforeUnmount(() => {
    document.removeEventListener('keydown', handleKeydown);
  });
}

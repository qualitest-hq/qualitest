/**
 * 画布全局键盘快捷键：撤销、删除、Esc 停止、方向键步进。
 * 在 FlowCanvasView 挂载时注册，卸载时注销；输入框聚焦时不拦截编辑键。
 */
import { onBeforeUnmount, onMounted, type Ref } from 'vue';

import { usePlayback } from './usePlayback';
import { useFlowDelete } from './useFlowDelete';
import { useFlowHistory } from './useFlowHistory';
import { useFlowSimulate } from './useFlowSimulate';
import { useFlowScenarioRun } from './useFlowScenarioRun';
import { useRunLibraryStore } from '../stores/runLibraryStore';

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
}

export function useFlowKeyboard(options: UseFlowKeyboardOptions) {
  const { undo } = useFlowHistory();
  const { deleteSelection } = useFlowDelete();
  const { abortScenarioRun } = useFlowScenarioRun();
  const runLib = useRunLibraryStore();
  const {
    isSimulateActive,
    abortSimulate,
    simulateStepPrev,
    simulateStepNext,
  } = useFlowSimulate();
  const {
    isReplayActive,
    abortReplay,
    replayStepPrev,
    replayStepNext,
  } = usePlayback();

  function handleKeydown(e: KeyboardEvent) {
    if (e.key === 'Escape') {
      if (runLib.scenarioRunLive) {
        e.preventDefault();
        abortScenarioRun();
        return;
      }
      if (isSimulateActive.value) {
        e.preventDefault();
        abortSimulate();
        return;
      }
      if (isReplayActive.value) {
        e.preventDefault();
        abortReplay();
        return;
      }
      if (options.helpOpen.value) {
        e.preventDefault();
        options.helpOpen.value = false;
      }
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
      if (isSimulateActive.value) {
        if (e.key === 'ArrowLeft') {
          e.preventDefault();
          simulateStepPrev();
          return;
        }
        if (e.key === 'ArrowRight') {
          e.preventDefault();
          simulateStepNext();
          return;
        }
      }
      if (isReplayActive.value) {
        if (e.key === 'ArrowLeft') {
          e.preventDefault();
          replayStepPrev();
          return;
        }
        if (e.key === 'ArrowRight') {
          e.preventDefault();
          replayStepNext();
          return;
        }
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

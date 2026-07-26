/**
 * 运行库回放：按 Run 步骤时间线自动或手动步进，驱动画布节点高亮。
 *
 * 回放高亮以 RunRecord.steps[].nodeId 为准；启动时会终止正在进行的路径模拟。
 */
import { computed, ref } from 'vue';
import { ElMessage } from 'element-plus';

import { RUN_REPLAY_STEP_MS } from '../constants/flowConfig';
import { useFlowCanvasStore } from '../stores/flowCanvasStore';
import { useRunLibraryStore } from '../stores/runLibraryStore';
import { abortableSleep } from '../utils/abortableSleep';
import { endSimulate } from './useFlowSimulate';
import { highlightRunStep } from './useFlowScenarioRun';

/** 单次回放会话状态：cursor 对应当前高亮的步骤下标 */
interface RunPlayback {
  active: boolean;
  abort: boolean;
  paused: boolean;
  runId: string;
  cursor: number;
  epoch: number;
}

/** 模块级回放会话，供底栏与键盘快捷键共享 */
const playback = ref<RunPlayback | null>(null);

/** 结束回放会话并清除画布高亮 */
export function endRunReplay() {
  playback.value = null;
  const store = useFlowCanvasStore();
  store.clearRunHighlight();
}

/**
 * 启动 Run 回放。
 *
 * @param runId 运行库记录 id（testFlowRunId）
 * @param opts.autoPlay 是否自动按 RUN_REPLAY_STEP_MS 步进，默认 true
 */
export async function startRunReplay(runId: string, opts: { autoPlay?: boolean } = {}) {
  const runLib = useRunLibraryStore();
  const store = useFlowCanvasStore();

  endSimulate();

  let run = runLib.runs.find((r) => r.id === runId);
  if (!run || !run.steps.length) {
    await runLib.selectRun(runId);
    run = runLib.selectedRun ?? undefined;
  }
  if (!run?.steps?.length) {
    ElMessage.warning('该运行无步骤可回放');
    return;
  }

  endRunReplay();
  await runLib.selectRun(runId);
  store.showRunPanel();
  store.ui.leftTab = 'runs';

  const autoPlay = opts.autoPlay !== false;
  const epoch = Date.now();
  playback.value = {
    active: true,
    abort: false,
    paused: !autoPlay,
    runId,
    cursor: 0,
    epoch,
  };

  runLib.inspectorStepIndex = 0;
  highlightRunStep(store, run, 0);

  if (!autoPlay) return;

  await runPlaybackDriver(epoch, run);
}

/**
 * 自动回放驱动循环：按 RUN_REPLAY_STEP_MS 推进 cursor，
 * 同步 Inspector 步骤索引并刷新画布高亮。
 */
async function runPlaybackDriver(epoch: number, run: { steps: { nodeId: string }[] }) {
  const store = useFlowCanvasStore();
  const runLib = useRunLibraryStore();

  while (playback.value?.epoch === epoch && !playback.value.abort) {
    if (playback.value.paused) {
      await abortableSleep(80, () => playback.value?.epoch !== epoch || !!playback.value?.abort);
      continue;
    }

    const cursor = playback.value.cursor;
    if (cursor >= run.steps.length - 1) {
      endRunReplay();
      return;
    }

    await abortableSleep(RUN_REPLAY_STEP_MS, () => playback.value?.epoch !== epoch || !!playback.value?.abort);
    if (playback.value?.epoch !== epoch || playback.value?.abort) break;

    playback.value.cursor += 1;
    runLib.inspectorStepIndex = playback.value.cursor;
    highlightRunStep(store, run as Parameters<typeof highlightRunStep>[1], playback.value.cursor);
  }
}

export function usePlayback() {
  const runLib = useRunLibraryStore();

  /** 是否有正在进行的回放会话 */
  const isReplayActive = computed(() => !!playback.value?.active);

  /**
   * 底栏状态区文案：回放活跃时展示当前步序。
   */
  const replayStatusText = computed(() => {
    const pb = playback.value;
    const run = runLib.selectedRun;
    if (!pb?.active) return '';
    if (!run?.steps.length) return '回放 · 准备中…';
    return `回放 · 步骤 ${pb.cursor + 1}/${run.steps.length}`;
  });

  /** 终止回放并清除画布高亮 */
  function abortReplay() {
    if (playback.value) playback.value.abort = true;
    endRunReplay();
  }

  /** 切换自动步进的暂停/继续 */
  function toggleReplayPause() {
    if (playback.value) playback.value.paused = !playback.value.paused;
  }

  /** 单步后退：暂停自动驱动后回退 cursor 并刷新高亮 */
  function replayStepPrev() {
    const store = useFlowCanvasStore();
    const pb = playback.value;
    const run = runLib.selectedRun;
    if (!pb || !run?.steps.length || pb.cursor <= 0) return;
    pb.paused = true;
    pb.cursor = Math.max(0, pb.cursor - 1);
    runLib.inspectorStepIndex = pb.cursor;
    highlightRunStep(store, run, pb.cursor);
  }

  /** 单步前进：暂停自动驱动后前进 cursor 并刷新高亮 */
  function replayStepNext() {
    const store = useFlowCanvasStore();
    const pb = playback.value;
    const run = runLib.selectedRun;
    if (!pb || !run?.steps.length) return;
    pb.paused = true;
    pb.cursor = Math.min(run.steps.length - 1, pb.cursor + 1);
    runLib.inspectorStepIndex = pb.cursor;
    highlightRunStep(store, run, pb.cursor);
  }

  return {
    isReplayActive,
    replayStatusText,
    startRunReplay,
    abortReplay,
    toggleReplayPause,
    replayStepPrev,
    replayStepNext,
    endRunReplay,
  };
}

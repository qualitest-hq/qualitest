/**
 * 运行库回放：按 Run 步骤时间线自动或手动步进，驱动画布节点高亮。
 *
 * 高亮依据每步的 nodeId；启动回放前会先结束路径模拟。
 * 自动回放每步停留固定间隔；播完后保留最后一步高亮，手动中止会清除高亮。
 */
import { computed, ref } from 'vue';
import { ElMessage } from 'element-plus';

import { RUN_REPLAY_STEP_MS } from '../constants/flowConfig';
import { useFlowCanvasStore } from '../stores/flowCanvasStore';
import { useRunLibraryStore } from '../stores/runLibraryStore';
import { abortableSleep } from '../utils/abortableSleep';
import { endSimulate } from './useFlowSimulate';
import { highlightRunStep } from './useFlowScenarioRun';

/** 单次回放会话：cursor 为当前高亮步骤下标，epoch 用于作废过期驱动循环 */
interface RunPlayback {
  active: boolean;
  abort: boolean;
  paused: boolean;
  runId: string;
  cursor: number;
  epoch: number;
}

/** 模块级回放会话（底栏按钮与键盘快捷键共用） */
const playback = ref<RunPlayback | null>(null);

/** 结束回放会话，并清除画布上的运行高亮 */
export function endRunReplay() {
  playback.value = null;
  const store = useFlowCanvasStore();
  store.clearRunHighlight();
}

/**
 * 启动指定 Run 的回放。
 *
 * @param runId 运行记录 id
 * @param opts.autoPlay 是否自动按间隔步进；默认 true。为 false 时只高亮第 0 步，由用户手动前进
 */
export async function startRunReplay(runId: string, opts: { autoPlay?: boolean } = {}) {
  const runLib = useRunLibraryStore();
  const store = useFlowCanvasStore();

  endSimulate();
  endRunReplay();

  await runLib.selectRun(runId);
  const run = runLib.selectedRun;
  if (!run?.steps?.length) {
    ElMessage.warning('该运行无步骤可回放');
    return;
  }

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

  // 手动模式立刻高亮第 0 步；自动模式交给驱动循环统一高亮
  if (!autoPlay) {
    runLib.inspectorStepIndex = 0;
    highlightRunStep(store, run, 0);
    return;
  }

  await runPlaybackDriver(epoch);
}

/**
 * 自动回放驱动：每步先高亮并停留 RUN_REPLAY_STEP_MS，再前进到下一步。
 * 最后一步同样停留完整一拍后结束会话，但保留末步高亮。
 */
async function runPlaybackDriver(epoch: number) {
  const store = useFlowCanvasStore();
  const runLib = useRunLibraryStore();

  while (playback.value?.epoch === epoch && !playback.value.abort) {
    if (playback.value.paused) {
      await abortableSleep(80, () => playback.value?.epoch !== epoch || !!playback.value?.abort);
      continue;
    }

    const run = runLib.selectedRun;
    const steps = run?.steps ?? [];
    if (!run || !steps.length) {
      endRunReplay();
      return;
    }

    const cursor = playback.value.cursor;
    if (cursor < 0 || cursor >= steps.length) {
      endRunReplay();
      return;
    }

    runLib.inspectorStepIndex = cursor;
    highlightRunStep(store, run, cursor);

    await abortableSleep(RUN_REPLAY_STEP_MS, () => playback.value?.epoch !== epoch || !!playback.value?.abort);
    if (playback.value?.epoch !== epoch || playback.value?.abort) break;

    if (cursor >= steps.length - 1) {
      // 自然播完：仅关闭会话，保留末步高亮
      playback.value = null;
      return;
    }

    playback.value.cursor = cursor + 1;
  }
}

/** 回放控制：状态文案、中止、暂停、单步前进/后退 */
export function usePlayback() {
  const runLib = useRunLibraryStore();

  /** 当前是否存在进行中的回放会话 */
  const isReplayActive = computed(() => !!playback.value?.active);

  /** 底栏状态文案，例如「回放 · 步骤 3/10」 */
  const replayStatusText = computed(() => {
    const pb = playback.value;
    const run = runLib.selectedRun;
    if (!pb?.active) return '';
    if (!run?.steps.length) return '回放 · 准备中…';
    return `回放 · 步骤 ${pb.cursor + 1}/${run.steps.length}`;
  });

  /** 中止回放并清除画布高亮 */
  function abortReplay() {
    if (playback.value) playback.value.abort = true;
    endRunReplay();
  }

  /** 切换自动步进的暂停 / 继续 */
  function toggleReplayPause() {
    if (playback.value) playback.value.paused = !playback.value.paused;
  }

  /** 单步后退：进入暂停，cursor 减一并刷新高亮 */
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

  /** 单步前进：进入暂停，cursor 加一并刷新高亮 */
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

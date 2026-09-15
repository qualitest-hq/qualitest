/**
 * 路径模拟：DFS 枚举全部分支路径并自动步进高亮（不评估 condition、不发起 HTTP）。
 */
import { computed, ref } from 'vue';
import { ElMessage } from 'element-plus';

import { validateStartNodes } from '@/utils/flow/graphValidate';

import {
  SIMULATE_PATH_GAP_MS,
  SIMULATE_STEP_MS,
} from '../constants/flowConfig';
import { useFlowCanvasStore } from '../stores/flowCanvasStore';
import { useRunLibraryStore } from '../stores/runLibraryStore';
import { abortableSleep } from '../utils/abortableSleep';
import { enumerateSimulatePaths, type SimulatePath } from '../utils/simulatePaths';
import { endRunReplay } from './usePlayback';

type TimelineEntry =
  | { kind: 'step'; pathIndex: number; stepIndex: number }
  | { kind: 'gap'; pathIndex: number };

interface PathPlayback {
  active: boolean;
  abort: boolean;
  paused: boolean;
  paths: SimulatePath[];
  timeline: TimelineEntry[];
  cursor: number;
  epoch: number;
}

/** 模块级路径模拟会话，供底栏与场景运行入口共享 */
const playback = ref<PathPlayback | null>(null);

/** 路径模拟是否进行中（模块级，供小地图等跨 composable 订阅） */
export const pathSimulateActive = computed(() => !!playback.value?.active);

/** 将枚举出的路径展开为步进时间线，路径之间插入 gap 用于切换间隔 */
function buildTimeline(paths: SimulatePath[]): TimelineEntry[] {
  const timeline: TimelineEntry[] = [];
  paths.forEach((path, pi) => {
    path.nodeIds.forEach((_, si) => {
      timeline.push({ kind: 'step', pathIndex: pi, stepIndex: si });
    });
    if (pi < paths.length - 1) timeline.push({ kind: 'gap', pathIndex: pi });
  });
  return timeline;
}

/** 按时间线条目刷新画布高亮：已访问节点标记 passed，当前节点写入 runHighlightNodeId */
function applySimulateHighlight(
  store: ReturnType<typeof useFlowCanvasStore>,
  paths: SimulatePath[],
  entry: TimelineEntry | undefined,
) {
  store.clearRunHighlight();
  if (!entry || entry.kind === 'gap') return;
  const path = paths[entry.pathIndex];
  if (!path) return;
  for (let i = 0; i < entry.stepIndex; i++) {
    const nodeId = path.nodeIds[i];
    if (nodeId) store.runVisitedNodeIds[nodeId] = 'passed';
  }
  const nodeId = path.nodeIds[entry.stepIndex];
  if (nodeId) store.runHighlightNodeId = nodeId;
}

/** 构建节点 id → 显示名称映射，供路径枚举结果展示 */
function buildNodeNameMap(store: ReturnType<typeof useFlowCanvasStore>) {
  const map = new Map<string, string>();
  store.nodes.forEach((n) => {
    const name = (n.data as Record<string, unknown> | undefined)?.name;
    map.set(n.id, name != null && String(name).trim() ? String(name) : n.id);
  });
  return map;
}

/** 立即终止路径模拟会话并清除画布高亮 */
export function endSimulate() {
  playback.value = null;
  useFlowCanvasStore().clearRunHighlight();
}

export function useFlowSimulate() {
  const store = useFlowCanvasStore();
  const runLib = useRunLibraryStore();

  /** 当前图可枚举的路径总数；图无效或边未就绪时为 null */
  const pathCount = computed(() => {
    if (!store.nodes.length || store.pendingEdges?.length) return null;
    const check = validateStartNodes({ nodes: store.nodes, edges: store.edges });
    if (!check.ok) return null;
    return enumerateSimulatePaths(store.edges, check.ids[0], buildNodeNameMap(store)).length;
  });

  /** 是否有正在进行的路径模拟会话 */
  const isSimulateActive = pathSimulateActive;

  /**
   * 底栏状态区文案：正式 Run 进度、路径模拟进度，或空闲时的路径/运行统计。
   */
  const statusText = computed(() => {
    const live = runLib.scenarioRunLive;
    if (live?.phase === 'running') {
      if (live.stepTotal) {
        const idx = (live.stepIndex ?? 0) + 1;
        return `正式运行 · 步骤 ${idx}/${live.stepTotal}`;
      }
      return '正式运行执行中…';
    }
    const pb = playback.value;
    if (pb?.active) {
      const entry = pb.timeline[pb.cursor];
      if (!entry) return '路径模拟 · 准备中…';
      if (entry.kind === 'gap') {
        return `路径 ${entry.pathIndex + 2}/${pb.paths.length}`;
      }
      const path = pb.paths[entry.pathIndex];
      return `路径 ${entry.pathIndex + 1}/${pb.paths.length} · 步骤 ${entry.stepIndex + 1}/${path?.nodeIds.length ?? 0}`;
    }
    const n = pathCount.value;
    const runs = runLib.runs.length;
    if (n == null) return '共 — 条路径';
    return runs ? `共 ${n} 条路径 · ${runs} 次运行` : `共 ${n} 条路径`;
  });

  /** 当前是否允许启动路径模拟 */
  const canSimulate = computed(
    () => !isSimulateActive.value && !runLib.scenarioRunLive && pathCount.value != null && pathCount.value > 0,
  );

  /** 结束指定 epoch 的路径模拟会话；未传 epoch 时强制结束当前会话 */
  function endPlayback(epoch?: number) {
    const pb = playback.value;
    if (!pb) return;
    if (epoch != null && pb.epoch !== epoch) return;
    playback.value = null;
  }

  /** 自动步进驱动循环：按 SIMULATE_STEP_MS / SIMULATE_PATH_GAP_MS 推进时间线 */
  async function runPlaybackDriver(epoch: number) {
    const pb = playback.value;
    if (!pb || pb.epoch !== epoch) return;

    while (playback.value === pb && pb.active && !pb.abort) {
      if (pb.paused) {
        await abortableSleep(80, () => playback.value !== pb || pb.abort || !pb.paused);
        continue;
      }

      const entry = pb.timeline[pb.cursor];
      const ms = entry?.kind === 'gap' ? SIMULATE_PATH_GAP_MS : SIMULATE_STEP_MS;
      await abortableSleep(ms, () => playback.value !== pb || pb.abort || pb.paused);
      if (playback.value !== pb || pb.abort) break;
      if (pb.paused) continue;

      if (pb.cursor >= pb.timeline.length - 1) break;

      pb.cursor++;
      applySimulateHighlight(store, pb.paths, pb.timeline[pb.cursor]);
    }

    // 末步已在循环内停留完整步进时长；自然结束后清除高亮，避免常亮
    if (playback.value === pb && !pb.abort) {
      store.clearRunHighlight();
    }
    endPlayback(epoch);
  }

  /** 枚举全部路径并启动自动步进高亮 */
  async function simulateRun() {
    if (playback.value?.active || runLib.scenarioRunLive) return;

    endRunReplay();
    await store.ensureEdgesHydrated();

    const startCheck = validateStartNodes({ nodes: store.nodes, edges: store.edges });
    if (!startCheck.ok) {
      ElMessage.error(startCheck.message);
      return;
    }

    const paths = enumerateSimulatePaths(
      store.edges,
      startCheck.ids[0],
      buildNodeNameMap(store),
    );
    if (!paths.length) {
      ElMessage.warning('无可模拟的路径');
      return;
    }

    const timeline = buildTimeline(paths);
    const epoch = Date.now();
    playback.value = {
      active: true,
      abort: false,
      paused: false,
      paths,
      timeline,
      cursor: 0,
      epoch,
    };
    applySimulateHighlight(store, paths, timeline[0]);
    void runPlaybackDriver(epoch);
  }

  /** 终止路径模拟并清除画布高亮 */
  function abortSimulate() {
    endSimulate();
  }

  /** 切换自动步进的暂停/继续 */
  function toggleSimulatePause() {
    const pb = playback.value;
    if (!pb?.active) return;
    pb.paused = !pb.paused;
  }

  /** 单步后退：暂停自动驱动后回退 cursor 并刷新高亮 */
  function simulateStepPrev() {
    const pb = playback.value;
    if (!pb?.active || pb.cursor <= 0) return;
    pb.paused = true;
    pb.cursor--;
    applySimulateHighlight(store, pb.paths, pb.timeline[pb.cursor]);
  }

  /** 单步前进：暂停自动驱动后前进 cursor 并刷新高亮 */
  function simulateStepNext() {
    const pb = playback.value;
    if (!pb?.active || pb.cursor >= pb.timeline.length - 1) return;
    pb.paused = true;
    pb.cursor++;
    applySimulateHighlight(store, pb.paths, pb.timeline[pb.cursor]);
  }

  return {
    pathCount,
    statusText,
    canSimulate,
    isSimulateActive,
    simulateRun,
    abortSimulate,
    toggleSimulatePause,
    simulateStepPrev,
    simulateStepNext,
  };
}

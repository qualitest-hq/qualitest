/**
 * 画布草稿 API 语义预检调度。
 * <p>
 * 在防抖后请求 API 语义预检，结果写入 apiHealth store。
 * 鉴权/必填已并入 stage=canvas 图 error（useFlowValidation），不再平行预检。
 * 全页只保留一份运行时：多处挂载共享暂停开关与监听，卸载全部挂载后再释放。
 */
import { onBeforeUnmount, watch, type Ref } from 'vue'

import { snapshotStringWithoutViewport } from '../utils/reconcileFlowDirty'
import { useApiHealthStore } from '../stores/apiHealthStore'
import { useFlowCanvasStore } from '../stores/flowCanvasStore'
import { buildCanvasPersistGraph } from './buildCanvasPersistGraph'

/** 预检防抖间隔（毫秒） */
const PREVIEW_DEBOUNCE_MS = 400

/** 跳过预检时触发键的后缀 */
const SKIP_SUFFIX = '|skip'

/** 预检调度运行时：立即预检、暂停自动预检、释放定时器 */
interface PreviewRuntime {
  /** 取消排队并立刻跑一轮预检 */
  runPreview: () => Promise<void>
  /** 暂停或恢复自动预检；暂停时清掉已排队定时器 */
  setSuspended: (value: boolean) => void
  /** 释放防抖定时器 */
  dispose: () => void
}

/** 页面级唯一运行时；未创建时为 null */
let singleton: PreviewRuntime | null = null
/** 调用方传入的测试流 id（响应式） */
let flowIdRef: Ref<string> | null = null
/** 当前挂载次数；减到 0 时销毁运行时 */
let bindCount = 0

/** 创建预检调度运行时（含监听与防抖） */
function createApiHealthDraftPreview(): PreviewRuntime {
  const store = useFlowCanvasStore()
  const apiHealth = useApiHealthStore()

  /** 防抖定时器句柄 */
  let timer: ReturnType<typeof setTimeout> | null = null
  /** 为 true 时不自动排队预检（加载图、外部灌图期间） */
  let suspended = false
  /** 上一轮已执行预检的内容键；内容未变则不再请求 */
  let lastPreviewKey = ''

  /** 取消尚未触发的防抖预检 */
  function clearTimer() {
    if (timer != null) {
      clearTimeout(timer)
      timer = null
    }
  }

  /** 解析当前测试流 id：优先调用方 Ref，其次画布 store */
  function resolveFlowId() {
    return String(flowIdRef?.value || store.testFlowId || '').trim()
  }

  /**
   * 是否跳过预检。
   * 无流 id、模板占位 id、或当前为模板画布时不请求。
   */
  function shouldSkipPreview(flowId: string) {
    return !flowId || flowId.startsWith('tpl-') || store.canvasMode === 'template'
  }

  /**
   * 计算当前预检内容键：流 id + 可落盘图快照（不含视口）。
   * 跳过预检时返回带 skip 后缀的键。
   */
  function currentPreviewKey() {
    const id = resolveFlowId()
    if (shouldSkipPreview(id)) return `${id}${SKIP_SUFFIX}`
    return `${id}|${snapshotStringWithoutViewport(buildCanvasPersistGraph())}`
  }

  /**
   * 设置是否暂停自动预检。
   * 暂停时同时取消已排队的定时预检。
   */
  function setSuspended(value: boolean) {
    suspended = value
    if (value) clearTimer()
  }

  /** 清空 API 语义告警，并记下当前内容键，避免立刻再打一轮 */
  function clearPreviewState() {
    lastPreviewKey = currentPreviewKey()
    apiHealth.clear()
  }

  /**
   * 立刻执行预检：取消排队，按当前可落盘图请求 API 语义。
   * 跳过条件下只清空告警。
   */
  async function runPreview() {
    clearTimer()
    const id = resolveFlowId()
    if (shouldSkipPreview(id)) {
      clearPreviewState()
      return
    }
    const graph = buildCanvasPersistGraph()
    lastPreviewKey = `${id}|${snapshotStringWithoutViewport(graph)}`
    await apiHealth.preview(id, graph)
  }

  /**
   * 排队一次预检：暂停中、应跳过、或内容键未变则直接返回；
   * 否则在防抖间隔后若仍需预检再执行。
   */
  function schedulePreview() {
    if (suspended) return
    const key = currentPreviewKey()
    if (key.endsWith(SKIP_SUFFIX) || key === lastPreviewKey) return
    clearTimer()
    timer = setTimeout(() => {
      timer = null
      if (suspended) return
      const latest = currentPreviewKey()
      if (latest.endsWith(SKIP_SUFFIX) || latest === lastPreviewKey) return
      void runPreview()
    }, PREVIEW_DEBOUNCE_MS)
  }

  // 可落盘图内容变化时自动排队预检
  watch(
    () => currentPreviewKey(),
    (key) => {
      if (suspended || key.endsWith(SKIP_SUFFIX)) return
      schedulePreview()
    },
  )

  return {
    runPreview,
    setSuspended,
    dispose: clearTimer,
  }
}

/**
 * 挂载画布草稿 API 语义预检调度。
 *
 * @param testFlowId 当前测试流 id（响应式）
 * @returns runPreview 立刻预检；setSuspended 暂停/恢复自动预检
 */
export function useApiHealthDraftPreview(testFlowId: Ref<string>) {
  flowIdRef = testFlowId
  if (!singleton) {
    singleton = createApiHealthDraftPreview()
  }
  bindCount += 1
  onBeforeUnmount(() => {
    bindCount -= 1
    if (bindCount > 0) return
    singleton?.dispose()
    singleton = null
    flowIdRef = null
    bindCount = 0
  })
  return {
    runPreview: singleton.runPreview,
    setSuspended: singleton.setSuspended,
  }
}

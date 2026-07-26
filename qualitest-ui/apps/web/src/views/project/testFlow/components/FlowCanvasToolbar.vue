<template>
  <div class="flow-canvas-toolbar">
    <div class="flow-canvas-toolbar__group flow-canvas-toolbar__group--view">
      <el-tooltip :content="isFullscreen ? '退出全屏' : '全屏'" placement="top">
        <button
            :class="{ 'is-active': isFullscreen }"
            class="btn btn--ghost btn--icon flow-canvas-toolbar__fullscreen"
            type="button"
            @click="emit('toggle-fullscreen')"
        >
          <svg-icon
              :icon-class="isFullscreen ? 'exit-fullscreen' : 'fullscreen'"
              class="flow-canvas-toolbar__fullscreen-icon"
          />
        </button>
      </el-tooltip>
      <button class="btn btn--ghost" title="缩小" type="button" @click="emit('zoom-out')">−</button>
      <span class="flow-canvas-toolbar__zoom">{{ zoomPercent }}%</span>
      <button class="btn btn--ghost" title="放大" type="button" @click="emit('zoom-in')">+</button>
      <button class="btn btn--ghost" title="重置缩放" type="button" @click="emit('zoom-reset')">重置</button>
      <button class="btn btn--ghost" title="适应视图" type="button" @click="emit('fit-view')">适应视图</button>
      <el-tooltip :content="minimapVisible ? '隐藏小地图' : '显示小地图'" placement="top">
        <button
            :class="{ 'is-active': minimapVisible }"
            class="btn btn--ghost flow-canvas-toolbar__minimap"
            title="小地图"
            type="button"
            @click="emit('toggle-minimap')"
        >
          地图
        </button>
      </el-tooltip>
    </div>
    <div aria-hidden="true" class="flow-canvas-toolbar__divider" />
    <div class="flow-canvas-toolbar__group flow-canvas-toolbar__group--actions">
      <button
          v-if="isReplayActive"
          class="btn btn--primary is-run-replay-stop"
          type="button"
          @click="onTransportStop()"
      >
        ■ 停止回放
      </button>
      <button
          v-else-if="!isSimulateActive"
          :disabled="!canSimulate || isScenarioRunActive"
          class="btn btn--primary flow-canvas-toolbar__path-simulate-btn"
          type="button"
          @click="simulateRun()"
      >
        ▶ 模拟路径
      </button>
      <button
          v-else
          class="btn btn--primary is-path-simulate-stop"
          type="button"
          @click="onTransportStop()"
      >
        ■ 停止模拟
      </button>
      <button
          v-if="!isScenarioRunActive"
          :disabled="!canEditFlow || isSimulateActive || isReplayActive"
          :title="canEditFlow ? `运行场景「${activeScenarioName}」` : '当前账号无编辑权限，无法运行场景'"
          class="btn btn--ghost flow-canvas-toolbar__scenario-run-btn"
          type="button"
          @click="emit('start-scenario-run')"
      >
        ▶ {{ activeScenarioName }}
      </button>
      <button
          v-else
          class="btn btn--primary is-scenario-run-stop"
          type="button"
          @click="emit('abort-scenario-run')"
      >
        ■ 停止
      </button>
      <button
          :disabled="!isTransportActive"
          aria-label="上一步"
          class="btn btn--ghost btn--icon flow-canvas-toolbar__path-step-btn"
          title="上一步"
          type="button"
          @click="onTransportStepPrev()"
      >
        ⏮
      </button>
      <button
          :disabled="!isTransportActive"
          aria-label="暂停"
          class="btn btn--ghost btn--icon flow-canvas-toolbar__path-step-btn"
          title="暂停/继续"
          type="button"
          @click="onTransportTogglePause()"
      >
        ⏸
      </button>
      <button
          :disabled="!isTransportActive"
          aria-label="下一步"
          class="btn btn--ghost btn--icon flow-canvas-toolbar__path-step-btn"
          title="下一步"
          type="button"
          @click="onTransportStepNext()"
      >
        ⏭
      </button>
    </div>
    <div class="flow-canvas-toolbar__status">
      <span class="flow-canvas-toolbar__status-text">{{ toolbarStatusText }}</span>
    </div>
  </div>
</template>

<script setup>
/**
 * 画布底栏：视图缩放、路径模拟、运行库回放传输控制、场景运行、全屏。
 *
 * 传输按钮（停止 / 暂停 / 步进）在路径模拟或回放活跃时共用同一组控件；
 * 状态区按当前播放态展示对应进度文案。
 */
import { computed } from 'vue'

import { useRunConfig } from '../composables/useRunConfig'
import { useFlowCanvasPermissions } from '../composables/useFlowCanvasPermissions'
import { useFlowSimulate } from '../composables/useFlowSimulate'
import { usePlayback } from '../composables/usePlayback'

defineProps({
  zoomPercent: { type: Number, default: 100 },
  isFullscreen: { type: Boolean, default: false },
  isScenarioRunActive: { type: Boolean, default: false },
  minimapVisible: { type: Boolean, default: true },
})

const emit = defineEmits([
  'zoom-in',
  'zoom-out',
  'zoom-reset',
  'fit-view',
  'toggle-fullscreen',
  'start-scenario-run',
  'abort-scenario-run',
  'toggle-minimap',
])

const { canEditFlow } = useFlowCanvasPermissions()

const {
  statusText,
  canSimulate,
  isSimulateActive,
  simulateRun,
  abortSimulate,
  toggleSimulatePause,
  simulateStepPrev,
  simulateStepNext,
} = useFlowSimulate()

const {
  isReplayActive,
  replayStatusText,
  abortReplay,
  toggleReplayPause,
  replayStepPrev,
  replayStepNext,
} = usePlayback()

const { activeScenarioName } = useRunConfig()

/** 路径模拟或回放进行中时，步进与暂停按钮可用 */
const isTransportActive = computed(() => isSimulateActive.value || isReplayActive.value)

/**
 * 底栏状态区文案：回放活跃时展示回放步序，否则展示场景运行或路径模拟状态。
 */
const toolbarStatusText = computed(() => {
  if (isReplayActive.value) return replayStatusText.value
  return statusText.value
})

/** 终止当前活跃的播放态（回放或路径模拟） */
function onTransportStop() {
  if (isReplayActive.value) abortReplay()
  else abortSimulate()
}

/** 切换当前活跃播放态的自动步进暂停/继续 */
function onTransportTogglePause() {
  if (isReplayActive.value) toggleReplayPause()
  else toggleSimulatePause()
}

/** 当前活跃播放态单步后退 */
function onTransportStepPrev() {
  if (isReplayActive.value) replayStepPrev()
  else simulateStepPrev()
}

/** 当前活跃播放态单步前进 */
function onTransportStepNext() {
  if (isReplayActive.value) replayStepNext()
  else simulateStepNext()
}
</script>

<style scoped lang="scss">
.flow-canvas-toolbar {
  pointer-events: auto;
  display: flex;
  align-items: center;
  flex-wrap: nowrap;
  gap: 6px;
  padding: 5px 8px;
  border-radius: 10px;
  background: rgba(255, 255, 255, 0.96);
  border: 1px solid var(--pd-border-subtle);
  box-shadow: var(--pd-shadow-card);
  backdrop-filter: blur(8px);
  width: max-content;
  max-width: min(960px, 100%);

  :deep(.btn) {
    height: 28px;
    padding: 0 8px;
    font-size: 11px;
  }
}

.flow-canvas-toolbar__group {
  display: flex;
  align-items: center;
  gap: 2px;
  flex-shrink: 0;
}

.flow-canvas-toolbar__group--actions :deep(.btn) {
  min-width: 28px;
}

.flow-canvas-toolbar__group--actions :deep(.btn.btn--icon),
.flow-canvas-toolbar__path-step-btn {
  width: 28px;
  min-width: 28px;
  height: 28px;
  padding: 0;
  font-size: 14px;
  line-height: 1;
  justify-content: center;
}

.flow-canvas-toolbar__scenario-run-btn {
  max-width: 160px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.flow-canvas-toolbar__zoom {
  min-width: 44px;
  padding: 0 8px;
  font-size: 11px;
  font-weight: 600;
  color: var(--pd-text-muted);
  text-align: center;
  user-select: none;
}

.flow-canvas-toolbar__divider {
  width: 1px;
  height: 22px;
  background: var(--pd-divider);
  flex-shrink: 0;
}

.flow-canvas-toolbar__status {
  flex: 0 0 auto;
  min-width: 88px;
  max-width: 240px;
  display: flex;
  align-items: center;
  padding-left: 8px;
  margin-left: 2px;
  border-left: 1px solid var(--pd-divider);
}

.flow-canvas-toolbar__status-text {
  font-size: 11px;
  font-weight: 600;
  color: var(--pd-text-muted);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  user-select: none;
  line-height: 1.2;
}

.flow-canvas-toolbar__fullscreen {
  width: 28px;
  min-width: 28px;
  height: 28px;
  padding: 0;
  justify-content: center;

  &.is-active {
    background: var(--pd-primary, #0b6edc);
    border-color: var(--pd-primary, #0b6edc);
    color: #fff;
  }
}

.flow-canvas-toolbar__fullscreen-icon {
  width: 14px;
  height: 14px;
  font-size: 14px;
}

.flow-canvas-toolbar__minimap {
  min-width: 36px;

  &.is-active {
    background: var(--pd-primary-soft, rgba(11, 110, 220, 0.12));
    border-color: var(--pd-primary, #0b6edc);
    color: var(--pd-primary, #0b6edc);
  }
}
</style>

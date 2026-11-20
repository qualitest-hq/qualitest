<template>
  <div class="run-steps">
    <div
      v-for="(step, idx) in steps"
      :key="idx"
      :class="{
        'is-current': idx === currentIndex,
        'is-pause-node': step.nodeId === pauseNodeId,
        'is-audit': isAuditStep(step.nodeType),
      }"
      class="run-step-item"
      @click="$emit('select-step', idx)"
    >
      <span class="run-step-item__icon">{{ stepIcon(step.nodeType) }}</span>
      <div class="run-step-item__body">
        <div class="run-step-item__main">
          <span class="run-step-item__name">{{ step.nodeName || nodeTypeLabelZh(step.nodeType) }}</span>
          <span :class="`run-badge run-badge--${step.status}`">{{ runStatusLabel(step.status) }}</span>
          <span class="run-step-item__dur">{{ formatDurationMs(step.durationMs) }}</span>
        </div>
        <div class="run-step-item__sub">{{ summarizeStep(step) }}</div>
      </div>
    </div>
  </div>
</template>

<script setup>
/**
 * Run 步骤时间线。
 * 列出每步图标、名称、状态、耗时与一行摘要；当前步高亮；
 * 暂停节点与审计步有额外样式；点击某步通知父组件选中并定位画布。
 */
import { runStatusLabel } from '../constants/runStatus'
import {
  formatDurationMs,
  isAuditStep,
  nodeTypeLabelZh,
  summarizeStep,
} from '../utils/runStepDisplay'

defineProps({
  steps: { type: Array, default: () => [] },
  currentIndex: { type: Number, default: 0 },
  pauseNodeId: { type: String, default: '' },
})

defineEmits(['select-step'])

function stepIcon(type) {
  const m = {
    http: 'H', assert: 'A', delay: 'D', condition: 'C', assign: 'S',
    script: 'P', subflow: 'F', input: 'I', runConfig: '⚙',
  }
  return m[type] || '?'
}
</script>

<style scoped lang="scss">
@use './runDetailBadges.scss' as *;

.run-steps {
  display: flex;
  flex-direction: column;
  gap: 4px;
  margin-bottom: 12px;
  max-height: 240px;
  overflow: auto;
}

.run-step-item {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  padding: 6px 8px;
  border-radius: 6px;
  border: 1px solid transparent;
  cursor: pointer;
  font-size: 11px;

  &.is-current {
    border-color: var(--pd-primary);
    background: var(--pd-primary-soft);
  }

  &.is-pause-node {
    border-color: #f59e0b;
    background: #fffbeb;
  }

  &.is-audit {
    opacity: 0.72;
  }

  &.is-audit .run-step-item__icon {
    background: transparent;
    border: 1px dashed var(--pd-divider, #d1d5db);
  }
}

.run-step-item__icon {
  width: 22px;
  height: 22px;
  border-radius: 4px;
  display: grid;
  place-items: center;
  font-size: 10px;
  font-weight: 700;
  flex-shrink: 0;
  background: var(--pd-bg-sunken);
}

.run-step-item__body {
  min-width: 0;
  flex: 1;
}

.run-step-item__main {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
}

.run-step-item__name {
  font-weight: 600;
}

.run-step-item__dur {
  color: var(--pd-text-muted);
  font-size: 10px;
}

.run-step-item__sub {
  color: var(--pd-text-muted);
  margin-top: 2px;
  word-break: break-all;
  line-height: 1.35;
}

@include run-badges;
</style>

<template>
  <div>
    <div v-if="staleWarning" class="run-stale-warn">{{ staleWarning }}</div>

    <header class="run-hero">
      <div class="run-hero__top">
        <span :class="`run-badge run-badge--${run.status}`">{{ runStatusLabel(run.status) }}</span>
        <span class="run-hero__scenario">{{ run.scenarioName || '未命名场景' }}</span>
        <button
          v-if="run.status === 'failed' || hasFailedStep"
          class="btn btn--primary btn--sm run-ai-fix-btn"
          type="button"
          @click="$emit('open-ai-fix')"
        >
          AI 修复
        </button>
      </div>
      <div class="run-hero__grid">
        <div class="run-hero__cell">
          <span class="run-hero__k">环境</span>
          <span class="run-hero__v">{{ runEnvName }}</span>
        </div>
        <div class="run-hero__cell">
          <span class="run-hero__k">触发</span>
          <span class="run-hero__v">{{ runTriggerLabel(run.triggerType) || '-' }}</span>
        </div>
        <div class="run-hero__cell">
          <span class="run-hero__k">耗时</span>
          <span class="run-hero__v">{{ formatDurationMs(run.durationMs) }}</span>
        </div>
        <div class="run-hero__cell">
          <span class="run-hero__k">开始</span>
          <span class="run-hero__v">{{ formatRunTime(run.startedAt) }}</span>
        </div>
        <div class="run-hero__cell">
          <span class="run-hero__k">结束</span>
          <span class="run-hero__v">{{ formatRunTime(run.finishedAt) }}</span>
        </div>
      </div>
      <div
        v-if="run.status === 'failed' && run.errorMessage"
        class="run-hero__error"
      >
        {{ run.errorMessage }}
      </div>
    </header>

    <div v-if="failureGroups.hasAny" class="run-failure-groups">
      <div class="run-failure-groups__title">失败摘要</div>
      <div
        v-for="group in failureGroups.sections"
        :key="group.id"
        class="run-failure-group"
      >
        <div class="run-failure-group__head">
          <span :class="`run-failure-group__badge run-failure-group__badge--${group.id}`">
            {{ group.label }}
          </span>
          <span class="run-failure-group__count">{{ group.items.length }}</span>
        </div>
        <button
          v-for="item in group.items"
          :key="item.stepIndex"
          class="run-failure-group__item"
          type="button"
          @click="$emit('go-to-step', item.stepIndex)"
        >
          <span class="run-failure-group__name">
            {{ item.nodeName }}
            <span class="run-failure-group__type">({{ item.typeLabel }})</span>
          </span>
          <span class="run-failure-group__msg">{{ item.summary }}</span>
          <div v-if="item.relatedVars.length" class="run-failure-group__vars">
            <div
              v-for="v in item.relatedVars"
              :key="v.key"
              class="run-failure-group__var"
            >
              <span class="run-failure-group__var-k">{{ v.key }}</span>
              <span class="run-failure-group__var-v">{{ formatRelatedVarValue(v.value) }}</span>
            </div>
          </div>
        </button>
      </div>
    </div>
  </div>
</template>

<script setup>
/**
 * Run 详情页眉与失败摘要。
 * 展示状态徽标、场景名、环境/触发/耗时、整次失败文案；
 * 失败摘要按业务码/断言/其它分组，点击条目跳到对应步骤；失败时可发 AI 修复。
 */
import { runStatusLabel, runTriggerLabel } from '../constants/runStatus'
import {
  formatDurationMs,
  formatRelatedVarValue,
  formatRunTime,
} from '../utils/runStepDisplay'

defineProps({
  run: { type: Object, required: true },
  staleWarning: { type: String, default: '' },
  runEnvName: { type: String, default: '-' },
  hasFailedStep: { type: Boolean, default: false },
  failureGroups: {
    type: Object,
    default: () => ({ hasAny: false, sections: [] }),
  },
})

defineEmits(['open-ai-fix', 'go-to-step'])
</script>

<style scoped lang="scss">
@use './runDetailBadges.scss' as *;

.run-ai-fix-btn {
  margin-left: auto;
  height: 26px;
  padding: 0 10px;
  border-radius: 6px;
  border: none;
  background: var(--pd-primary);
  color: #fff;
  font-size: 11px;
  font-weight: 600;
  cursor: pointer;
  flex-shrink: 0;

  &:hover {
    background: #095ec0;
  }
}

.run-stale-warn {
  padding: 8px 10px;
  margin-bottom: 8px;
  border-radius: 6px;
  font-size: 11px;
  font-weight: 600;
  color: #92400e;
  background: #fef3c7;
  border: 1px solid #fcd34d;
}

.run-hero {
  margin-bottom: 10px;
  padding: 8px 10px;
  border-radius: 8px;
  border: 1px solid var(--pd-divider, #e5e7eb);
  background: var(--pd-bg-sunken, #f8fafc);
}

.run-hero__top {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  margin-bottom: 6px;
}

.run-hero__scenario {
  font-size: 13px;
  font-weight: 700;
  color: var(--pd-text);
}

.run-hero__grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 2px 12px;
  font-size: 11px;
}

.run-hero__cell {
  display: flex;
  gap: 6px;
  min-width: 0;
}

.run-hero__k {
  color: var(--pd-text-muted);
  flex: 0 0 auto;
}

.run-hero__v {
  font-weight: 600;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.run-hero__error {
  margin-top: 10px;
  padding: 8px 10px;
  border-radius: 6px;
  border: 1px solid #fecaca;
  background: #fef2f2;
  color: #991b1b;
  font-size: 12px;
  line-height: 1.45;
  word-break: break-word;
}

.run-failure-groups {
  margin-bottom: 10px;
  padding: 10px 12px;
  border-radius: 8px;
  border: 1px solid #fecaca;
  background: #fef2f2;
}

.run-failure-groups__title {
  font-size: 12px;
  font-weight: 700;
  color: #991b1b;
  margin-bottom: 8px;
}

.run-failure-group {
  & + & {
    margin-top: 10px;
    padding-top: 8px;
    border-top: 1px dashed #fecaca;
  }
}

.run-failure-group__head {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 4px;
}

.run-failure-group__badge {
  display: inline-block;
  padding: 1px 7px;
  border-radius: 4px;
  font-size: 10px;
  font-weight: 700;

  &--bizCode {
    background: #ffedd5;
    color: #c2410c;
  }

  &--assert {
    background: #ede9fe;
    color: #6d28d9;
  }

  &--other {
    background: #e5e7eb;
    color: #374151;
  }
}

.run-failure-group__count {
  font-size: 10px;
  color: var(--pd-text-muted);
}

.run-failure-group__item {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 2px;
  width: 100%;
  margin-top: 4px;
  padding: 6px 8px;
  border: 1px solid transparent;
  border-radius: 6px;
  background: #fff;
  text-align: left;
  cursor: pointer;
  font-size: 11px;

  &:hover {
    border-color: var(--pd-primary);
    background: var(--pd-primary-soft);
  }
}

.run-failure-group__name {
  font-weight: 600;
  color: var(--pd-text);
}

.run-failure-group__type {
  margin-left: 4px;
  font-weight: 500;
  color: var(--pd-text-muted);
}

.run-failure-group__msg {
  color: #b91c1c;
  line-height: 1.4;
  word-break: break-all;
}

.run-failure-group__vars {
  display: flex;
  flex-direction: column;
  gap: 2px;
  width: 100%;
  margin-top: 4px;
  padding-top: 4px;
  border-top: 1px dashed #fecaca;
}

.run-failure-group__var {
  display: flex;
  align-items: baseline;
  gap: 6px;
  max-width: 100%;
  font-size: 10px;
  line-height: 1.35;
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
}

.run-failure-group__var-k {
  flex: 0 0 auto;
  color: #9a3412;
  font-weight: 600;
}

.run-failure-group__var-v {
  flex: 1 1 auto;
  min-width: 0;
  color: #7f1d1d;
  word-break: break-all;
}

@include run-badges;
</style>

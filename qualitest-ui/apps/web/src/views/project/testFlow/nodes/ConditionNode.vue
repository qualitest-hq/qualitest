<template>
  <BaseFlowNode
      :id="id"
      :data="data"
      :selected="selected"
      class="flow-node--condition"
      hide-source-handle
      type="condition"
  >
    <div class="cond-node__branches">
      <div
          v-for="(branch, idx) in branches"
          :key="branch.id"
          :data-branch-row="branch.id"
          class="cond-node__row"
      >
        <div class="cond-node__row-label">{{ branchKindLabel(branch) }}</div>
        <div
            :class="{ 'is-muted': branch.kind === 'else' || isMutedSummary(branch) }"
            class="cond-node__row-expr"
        >
          {{ formatBranchSummary(branch) }}
        </div>
      </div>
    </div>

    <template #source-handles>
      <Handle
          v-for="(branch, idx) in branches"
          :id="`out-${branch.id}`"
          :key="branch.id"
          :position="Position.Right"
          :style="{ top: `${handleTop(idx)}px` }"
          class="handle handle--out handle--out-branch"
          type="source"
      />
    </template>
  </BaseFlowNode>
</template>

<script setup>
/**
 * condition 节点画布卡片。
 * 按 branches 渲染多行 IF/ELIF/ELSE，每行右侧对应独立出边锚点 out-<branchId>。
 */
import { Handle, Position } from '@vue-flow/core'
import { computed } from 'vue'

import { COND_ROW_H, NODE_HEAD_H } from '../constants/flowConfig'
import {
  branchKindLabel,
  formatBranchSummary,
  getConditionBranches,
} from '../utils/conditionUtils'
import BaseFlowNode from './BaseFlowNode.vue'

const props = defineProps({
  id: { type: String, required: true },
  data: { type: Object, required: true },
  selected: { type: Boolean, default: false },
})

const branches = computed(() => getConditionBranches(props.data))

/** 分支锚点垂直居中位置：顶栏高度 + 行索引 * 行高 + 半行高 */
function handleTop(idx) {
  return NODE_HEAD_H + idx * COND_ROW_H + COND_ROW_H / 2
}

function isMutedSummary(branch) {
  return branch.kind !== 'else' && formatBranchSummary(branch) === '点击配置条件'
}
</script>

<style scoped lang="scss">
/* 类名挂在 BaseFlowNode 根元素，需 :deep 穿透子组件 */
:deep(.flow-node--condition) {
  width: var(--node-cond-w);
  min-height: calc(var(--node-head-h) + var(--node-cond-row-h) + 12px);
}

:deep(.flow-node--condition .flow-node__body) {
  padding: 0 12px 6px;
}

.cond-node__branches {
  display: flex;
  flex-direction: column;
  gap: 0;
}

.cond-node__row {
  position: relative;
  display: grid;
  grid-template-columns: 44px 1fr;
  gap: 6px;
  align-items: center;
  min-height: var(--node-cond-row-h);
  padding: 4px 6px 4px 0;
  border-top: 1px solid var(--pd-divider);

  &:first-child {
    border-top: none;
  }
}

.cond-node__row-label {
  display: flex;
  align-items: center;
  justify-content: center;
  align-self: center;
  min-width: 40px;
  min-height: 22px;
  padding: 4px 6px;
  border-radius: var(--node-chip-radius);
  font-size: var(--node-font-badge);
  font-weight: 700;
  color: var(--node-accent-fg);
  background: var(--node-accent-soft);
  border: 1px solid var(--node-accent-border);
  line-height: 1;
  letter-spacing: 0.04em;
  text-align: center;
  box-sizing: border-box;
}

.cond-node__row-expr {
  padding: var(--node-chip-pad-y) var(--node-chip-pad-x);
  border-radius: var(--node-chip-radius);
  font-size: var(--node-font-mono);
  color: var(--pd-text);
  background: var(--pd-bg-sunken);
  border: 1px solid var(--pd-divider);
  line-height: 1.4;
  word-break: break-word;

  &.is-muted {
    color: var(--pd-text-muted);
    font-style: italic;
    font-family: inherit;
    font-size: var(--node-font-body);
  }
}

:deep(.handle--out-branch) {
  /* 不覆盖 vue-flow 的 translate(50%, -50%)，仅用 top 定位各行 */
  margin-top: 0;
}
</style>

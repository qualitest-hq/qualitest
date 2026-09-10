<template>
  <BaseFlowNode
      ref="baseNodeRef"
      :id="id"
      :data="data"
      :selected="selected"
      class="flow-node--condition"
      hide-source-handle
      type="condition"
  >
    <div class="cond-node__branches">
      <div
          v-for="branch in branches"
          :key="branch.id"
          class="cond-node__row"
      >
        <!-- Handle 与文案拆开，避免进 grid 后包含块变成芯片格 -->
        <div class="cond-node__row-main">
          <div class="cond-node__row-label">{{ branchKindLabel(branch) }}</div>
          <div
              :class="{ 'is-muted': branch.kind === 'else' || isMutedSummary(branch) }"
              class="cond-node__row-expr"
          >
            {{ formatBranchSummary(branch) }}
          </div>
        </div>
        <!-- 行内出边锚点：始终可连；未绑 target 时命中即结束本流 -->
        <Handle
            :id="`out-${branch.id}`"
            :position="Position.Right"
            class="handle handle--out handle--out-branch"
            type="source"
        />
      </div>
    </div>
  </BaseFlowNode>
</template>

<script setup>
/**
 * condition 节点画布卡片。
 * 按 branches 渲染 IF/ELIF/ELSE 多行；每行右侧有出边锚点，未连线即命中后结束本流。
 */
import { Handle, Position } from '@vue-flow/core'
import { computed, ref } from 'vue'

import { useConditionHandleLayout } from '../composables/useConditionHandleLayout'
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

const baseNodeRef = ref(null)
const rootEl = computed(() => baseNodeRef.value?.rootEl ?? null)

const branches = computed(() => getConditionBranches(props.data))

useConditionHandleLayout(props.id, branches, rootEl)

/** 「点击配置条件」用弱样式；「→ 结束」等其它文案保持正常样式 */
function isMutedSummary(branch) {
  return branch.kind !== 'else' && formatBranchSummary(branch) === '点击配置条件'
}
</script>

<style scoped lang="scss">
.cond-node__branches {
  display: flex;
  flex-direction: column;
  gap: 0;
}

.cond-node__row {
  position: relative;
  min-height: var(--node-cond-row-h);
  border-top: 1px solid var(--pd-divider);

  &:first-child {
    border-top: none;
  }
}

.cond-node__row-main {
  display: grid;
  grid-template-columns: 44px 1fr;
  gap: 6px;
  align-items: center;
  min-height: var(--node-cond-row-h);
  padding: 4px 12px;
  box-sizing: border-box;
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
</style>

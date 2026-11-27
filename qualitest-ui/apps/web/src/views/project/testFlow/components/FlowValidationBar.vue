<template>
  <div v-if="hasIssues" class="flow-validation-bar">
    <div
        v-for="(issue, i) in issues"
        :key="'issue-' + i"
        :class="[
          'flow-validation-bar__item',
          issue.level === 'error'
            ? 'flow-validation-bar__item--error'
            : 'flow-validation-bar__item--warn',
        ]"
    >
      <span class="flow-validation-bar__icon">{{ issue.level === 'error' ? '✗' : '⚠' }}</span>
      <span class="flow-validation-bar__text">{{ issue.message }}</span>
      <template v-if="issue.nodeIds.length">
        <span class="flow-validation-bar__sep">·</span>
        <button
            v-for="nodeId in issue.nodeIds"
            :key="nodeId"
            type="button"
            class="flow-validation-bar__node-link"
            @click="focusIssueNode(nodeId)"
        >
          {{ displayNodeName(nodeId) }}
        </button>
      </template>
    </div>
  </div>
</template>

<script setup lang="ts">
/**
 * 画布顶部校验条。
 * 展示统一 issues（ERROR / WARNING）；节点名可点击居中并高亮该节点。
 */
import { computed } from 'vue'

import { useFlowValidation } from '../composables/useFlowValidation'
import { useFlowViewport } from '../composables/useFlowViewport'
import { useFlowCanvasStore } from '../stores/flowCanvasStore'
import { nodeDisplayName } from '../utils/flowValidationIssues'

const { issues } = useFlowValidation()
const store = useFlowCanvasStore()
const viewport = useFlowViewport()

const hasIssues = computed(() => issues.value.length > 0)

function displayNodeName(nodeId: string): string {
  const node = store.nodes.find((n) => n.id === nodeId)
  return nodeDisplayName(node, nodeId)
}

async function focusIssueNode(nodeId: string) {
  store.setAiHighlightFocus([nodeId])
  await viewport.waitForCanvasReady()
  await viewport.focusNodeIds([nodeId], { onlyIfOffscreen: false })
}
</script>

<style scoped lang="scss">
.flow-validation-bar {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  z-index: 12;
  display: flex;
  flex-direction: column;
  gap: 2px;
  padding: 6px 10px;
  background: rgba(255, 255, 255, 0.96);
  border-bottom: 1px solid var(--pd-divider);
  box-shadow: 0 2px 8px rgba(20, 60, 120, 0.06);
  max-height: 120px;
  overflow-y: auto;
  font-size: 11px;
  line-height: 1.45;
}

.flow-validation-bar__item {
  display: flex;
  flex-wrap: wrap;
  align-items: baseline;
  gap: 4px 6px;
}

.flow-validation-bar__item--error {
  color: #b91c1c;
}

.flow-validation-bar__item--warn {
  color: #92400e;
}

.flow-validation-bar__icon {
  flex: none;
  font-weight: 600;
}

.flow-validation-bar__sep {
  opacity: 0.45;
}

.flow-validation-bar__node-link {
  padding: 0;
  border: 0;
  background: transparent;
  color: inherit;
  text-decoration: underline;
  cursor: pointer;
  font: inherit;
}

.flow-validation-bar__node-link:hover {
  opacity: 0.8;
}
</style>

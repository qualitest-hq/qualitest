<template>
  <BaseFlowNode :id="id" :data="data" :selected="selected" type="http">
    <div class="flow-node__req">{{ requestLine }}</div>
    <div
        v-if="authLabel"
        :class="{ 'flow-node__auth--conflict': authScope.conflict }"
        :title="authScope.conflictReason || authLabel"
        class="flow-node__auth"
    >
      {{ authLabel }}
    </div>
    <FlowChipList
        :format-item="formatDest"
        :items="extracts"
        :title-for="chipTitle"
        block-class="flow-node__chip-block"
        more-unit="项"
    />
  </BaseFlowNode>
</template>

<script setup>
/** HTTP 节点卡片：请求摘要 + 凭证作用域 + 响应提取 chip */
import { computed } from 'vue'

import { filterFilledExtracts } from '@/utils/flow/extract'

import FlowChipList from '../components/ui/FlowChipList.vue'
import { useNodeAuthScope } from '../composables/useNodeAuthScope'
import BaseFlowNode from './BaseFlowNode.vue'
import { formatExtractTargetDest, formatHttpRequestLine, getExtractChipTitle } from '../utils/nodeDataUtils'

const props = defineProps({
  id: { type: String, required: true },
  data: { type: Object, required: true },
  selected: { type: Boolean, default: false },
})

const requestLine = computed(() => formatHttpRequestLine(props.data))

const extracts = computed(() => filterFilledExtracts(props.data.extracts || []))

const { authScope, authLabel } = useNodeAuthScope(() => props.data)

function formatDest(t) {
  return formatExtractTargetDest(t)
}

function chipTitle(t) {
  return getExtractChipTitle(t)
}
</script>

<style scoped lang="scss">
.flow-node__auth {
  margin-top: 2px;
  font-size: 11px;
  color: var(--pd-text-muted);
  line-height: 1.4;
}

.flow-node__auth--conflict {
  color: var(--el-color-danger, #f56c6c);
  font-weight: 600;
}
</style>

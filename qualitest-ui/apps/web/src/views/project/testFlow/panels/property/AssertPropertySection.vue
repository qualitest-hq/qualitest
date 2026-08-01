<template>
  <div class="assert-editor">
    <div class="assert-presets">
      <button
          v-for="p in ASSERT_PRESETS"
          :key="p.id"
          class="btn btn--ghost"
          type="button"
          @click="applyPreset(p.rule)"
      >
        {{ p.label }}
      </button>
    </div>
    <DebugAssertEditor v-model="rulesModel" :trial-body="trialBody" />
  </div>
</template>

<script setup>
/** 断言节点属性区：快捷预设 + 规则编辑（可对最近 HTTP 响应试算左值） */
import { computed } from 'vue'

import DebugAssertEditor from '@/views/project/testProject/components/DebugAssertEditor.vue'

import { ASSERT_PRESETS } from '../../constants/nodeTypes'
import { useFlowNodes } from '../../composables/useFlowNodes'
import { useFlowTrialBody } from '../../composables/useFlowTrialBody'

const props = defineProps({
  node: { type: Object, required: true },
})

const { patchNodeData } = useFlowNodes()
const { trialBody } = useFlowTrialBody()

const rulesModel = computed({
  get() {
    return props.node?.data?.rules || []
  },
  set(val) {
    patchNodeData(props.node.id, { rules: val })
  },
})

function applyPreset(rule) {
  const rules = [...(props.node.data.rules || []), { ...rule }]
  patchNodeData(props.node.id, { rules })
}
</script>

<style scoped lang="scss">
.assert-editor {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.assert-presets {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;

  :deep(.btn) {
    height: 28px;
    font-size: 11px;
    padding: 0 10px;
  }
}
</style>

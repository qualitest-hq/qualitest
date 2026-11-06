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
    <div v-if="httpNodeOptions.length" class="assert-node-ref">
      <span class="assert-node-ref__label">插入节点引用</span>
      <el-select
          placeholder="选择 HTTP 节点"
          filterable
          clearable
          class="assert-node-ref__select"
          @change="insertNodeRef"
      >
        <el-option
            v-for="opt in httpNodeOptions"
            :key="opt.id"
            :label="opt.label"
            :value="opt.id"
        />
      </el-select>
    </div>
    <DebugAssertEditor
        v-model="rulesModel"
        :trial-body="trialBody"
        :trial-source="trialSource"
        :resolve-node-body="resolveNodeBody"
    />
  </div>
</template>

<script setup>
/**
 * 断言节点属性区。
 * 提供快捷预设、从画布 HTTP 节点插入 nodes.<id>.http.body 规则、规则列表编辑；
 * 试算优先使用选中 Run 的 HTTP 响应，其次为上游接口响应示例。
 */
import { computed } from 'vue'

import DebugAssertEditor from '@/views/project/testProject/components/DebugAssertEditor.vue'

import { ASSERT_PRESETS } from '../../constants/nodeTypes'
import { useFlowNodes } from '../../composables/useFlowNodes'
import { useFlowTrialBody } from '../../composables/useFlowTrialBody'
import { useFlowCanvasStore } from '../../stores/flowCanvasStore'
import { nodeDisplayName } from '../../utils/flowValidationIssues'

const props = defineProps({
  node: { type: Object, required: true },
})

const store = useFlowCanvasStore()
const { patchNodeData } = useFlowNodes()
const { trialBody, trialSource, resolveNodeBody } = useFlowTrialBody({ node: () => props.node })

const rulesModel = computed({
  get() {
    return props.node?.data?.rules || []
  },
  set(val) {
    patchNodeData(props.node.id, { rules: val })
  },
})

/** 画布上全部 HTTP 节点选项（展示名 + id），用于插入节点响应引用 */
const httpNodeOptions = computed(() =>
  (store.nodes || [])
    .filter((n) => String(n?.type ?? '').trim().toLowerCase() === 'http')
    .map((n) => ({
      id: n.id,
      label: `${nodeDisplayName(n, n.id)}（${n.id}）`,
    })),
)

function applyPreset(rule) {
  const rules = [...(props.node.data.rules || []), { ...rule }]
  patchNodeData(props.node.id, { rules })
}

/**
 * 选中某 HTTP 节点后，追加一条左值为 nodes.<id>.http.body、运算符为 exists 的断言规则。
 */
function insertNodeRef(nodeId) {
  const id = String(nodeId ?? '').trim()
  if (!id) return
  const rules = [
    ...(props.node.data.rules || []),
    { left: `nodes.${id}.http.body`, operator: 'exists', right: '' },
  ]
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

.assert-node-ref {
  display: flex;
  align-items: center;
  gap: 8px;
}

.assert-node-ref__label {
  flex: none;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.assert-node-ref__select {
  flex: 1;
  min-width: 0;
}
</style>

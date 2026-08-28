<template>
  <div class="cond-editor">
    <div
        v-for="branch in branches"
        :key="branch.id"
        :data-branch-id="branch.id"
        class="cond-case"
    >
      <div class="cond-case__head">
        <div class="cond-case__title">{{ branchKindLabel(branch) }}</div>
        <button
            v-if="branch.kind === 'elif'"
            class="btn btn--ghost cond-case__remove"
            type="button"
            @click="removeElifBranch(branch.id)"
        >
          移除
        </button>
      </div>
      <div class="cond-case__body">
        <template v-if="branch.kind === 'else'">
          <div class="cond-case__desc">用于定义当 IF / ELIF 条件均不满足时应执行的逻辑。</div>
        </template>
        <template v-else>
          <label class="cond-case__terminal">
            <input
                :checked="branch.terminal === true"
                type="checkbox"
                @change="toggleTerminal(branch.id, $event.target.checked)"
            />
            <span>结束流程（不连线）</span>
          </label>
          <DebugAssertEditor
              v-if="!branch.terminal"
              :model-value="branch.conditions || []"
              :trial-body="trialBody"
              :trial-source="trialSource"
              @update:model-value="(val) => updateBranchConditions(branch.id, val)"
          />
          <div v-else class="cond-case__desc">命中此分支后子流正常结束，无需连接下游节点。</div>
        </template>
      </div>
    </div>

    <button
        v-if="canAddElif"
        class="cond-add-elif btn btn--ghost"
        type="button"
        @click="addElifBranch"
    >
      + ELIF
    </button>
    <div class="field__hint">左值可用 flow.* / env.* / asset.* / http.body…；同分支内多条条件为 AND</div>
  </div>
</template>

<script setup>
/**
 * condition 节点属性区：编辑各分支 conditions，支持增删 ELIF。
 * 条件左值试算优先用选中 Run 的 HTTP 响应，否则用上游接口响应示例。
 */
import { computed } from 'vue'

import DebugAssertEditor from '@/views/project/testProject/components/DebugAssertEditor.vue'
import { emptyCompareRule } from '@/utils/flow/compareRule'

import { useFlowCanvasStore } from '../../stores/flowCanvasStore'
import { useFlowNodes } from '../../composables/useFlowNodes'
import { useFlowTrialBody } from '../../composables/useFlowTrialBody'
import {
  branchKindLabel,
  canAddElifBranch,
  createElifBranchId,
  getConditionBranches,
  setBranchTerminal,
} from '../../utils/conditionUtils'

const props = defineProps({
  node: { type: Object, required: true },
})

const store = useFlowCanvasStore()
const { patchNodeData } = useFlowNodes()
const { trialBody, trialSource } = useFlowTrialBody({ node: () => props.node })

const branches = computed(() => getConditionBranches(props.node.data))

const canAddElif = computed(() => canAddElifBranch(branches.value))

/** 更新指定分支的 conditions 规则列表 */
function updateBranchConditions(branchId, conditions) {
  const next = branches.value.map((b) =>
    b.id === branchId ? { ...b, conditions } : { ...b },
  )
  patchNodeData(props.node.id, { branches: next })
}

/** 在 else 前插入一条 ELIF 分支 */
function addElifBranch() {
  const current = branches.value.map((b) => ({ ...b }))
  const elseIdx = current.findIndex((b) => b.kind === 'else')
  const insertAt = elseIdx >= 0 ? elseIdx : current.length
  current.splice(insertAt, 0, {
    id: createElifBranchId(current),
    kind: 'elif',
    conditions: [emptyCompareRule()],
  })
  patchNodeData(props.node.id, { branches: current })
}

/** 删除 ELIF 分支，并移除该分支关联的出边 */
function removeElifBranch(branchId) {
  const branch = branches.value.find((b) => b.id === branchId)
  if (!branch || branch.kind !== 'elif') return

  if (branch.target) {
    store.edges = store.edges.filter(
      (e) => !(e.source === props.node.id && e.target === branch.target),
    )
  }

  const next = branches.value.filter((b) => b.id !== branchId).map((b) => ({ ...b }))
  patchNodeData(props.node.id, { branches: next })
}

/** 切换 IF/ELIF 分支为结束流程；开启时移除该分支出边 */
function toggleTerminal(branchId, terminal) {
  const data = { ...props.node.data }
  if (!setBranchTerminal(data, branchId, terminal)) return
  if (terminal) {
    store.edges = store.edges.filter(
      (e) => !(e.source === props.node.id && e.sourceHandle === `out-${branchId}`),
    )
  }
  patchNodeData(props.node.id, { branches: data.branches })
}
</script>

<style scoped lang="scss">
.cond-editor {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.cond-case {
  border: 1px solid var(--pd-border-subtle);
  border-radius: var(--pd-radius-sm);
  background: var(--pd-bg-sunken);
  overflow: hidden;
}

.cond-case__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 6px 8px;
  background: linear-gradient(180deg, #fafcff 0%, #f5f9fd 100%);
  border-bottom: 1px solid var(--pd-divider);
}

.cond-case__title {
  font-size: 12px;
  font-weight: 700;
  color: var(--pd-text);
}

.cond-case__remove {
  height: 26px;
  padding: 0 8px;
  font-size: 11px;
}

.cond-case__body {
  padding: 6px 8px;

  :deep(.debug-assert-row) {
    padding: 6px;
  }
}

.cond-case__desc {
  font-size: 12px;
  color: var(--pd-text-muted);
  line-height: 1.6;
}

.cond-case__terminal {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
  font-size: 13px;
  color: var(--pd-text);
  cursor: pointer;

  input {
    margin: 0;
  }
}

.cond-add-elif {
  width: 100%;
  justify-content: center;
  border: 1px dashed var(--pd-border-subtle);
  height: 34px;
  font-size: 12px;
  font-weight: 600;
  color: var(--node-condition, #c2410c);

  &:hover:not(:disabled) {
    border-color: color-mix(in srgb, var(--node-condition, #c2410c) 40%, var(--pd-border-subtle));
    background: color-mix(in srgb, var(--node-condition, #c2410c) 6%, #fff);
  }
}
</style>

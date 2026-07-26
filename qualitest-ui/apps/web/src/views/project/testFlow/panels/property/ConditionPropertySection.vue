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
          <DebugAssertEditor
              :model-value="branch.conditions || []"
              @update:model-value="(val) => updateBranchConditions(branch.id, val)"
          />
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
    <div class="field__hint">左值可用 flow.* / env.* / asset.*；同分支内多条条件为 AND</div>
  </div>
</template>

<script setup>
/**
 * condition 节点右栏属性区。
 * 编辑 branches[].conditions，支持增删 ELIF；变更同步到节点 data 与关联边。
 */
import { computed } from 'vue'

import DebugAssertEditor from '@/views/project/testProject/components/DebugAssertEditor.vue'
import { emptyCompareRule } from '@/utils/flow/compareRule'

import { useFlowCanvasStore } from '../../stores/flowCanvasStore'
import { useFlowNodes } from '../../composables/useFlowNodes'
import {
  branchKindLabel,
  canAddElifBranch,
  createElifBranchId,
  getConditionBranches,
} from '../../utils/conditionUtils'

const props = defineProps({
  node: { type: Object, required: true },
})

const store = useFlowCanvasStore()
const { patchNodeData } = useFlowNodes()

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

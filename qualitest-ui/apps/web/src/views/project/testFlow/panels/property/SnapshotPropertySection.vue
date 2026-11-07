<template>
  <div class="snapshot-prop">
    <div class="field">
      <label class="snapshot-prop__toggle">
        <input
            :checked="snapshotBefore"
            type="checkbox"
            @change="onSnapshotBeforeChange"
        />
        执行前打被测数据快照（snapshotBefore）
      </label>
      <div v-if="!envAllowsReset" class="field__hint snapshot-prop__warn">
        当前场景环境未允许还原，checkpoint 将静默跳过
      </div>
      <div v-else-if="snapshotBefore" class="field__hint">
        开启数据还原时，同一环境请串行跑（避免并行 Run 互相覆盖被测数据）
      </div>
    </div>

    <template v-if="snapshotBefore">
      <div class="field">
        <label>快照表范围（逗号或换行分隔）</label>
        <textarea
            :value="tablesText"
            placeholder="orders, order_items"
            rows="3"
            @input="onTablesInput"
        />
        <div class="field__hint">scope 固定为 tables；留空表示不限制表列表</div>
      </div>
    </template>
  </div>
</template>

<script setup>
/**
 * 节点属性：执行前打被测数据快照。
 * 勾选写入 data.snapshotBefore=true（布尔）；取消写入 false。
 * 首次勾选且尚无 snapshotScope 时补默认 { scope: 'tables', tables: [] }。
 * 展示态只认布尔 true，字符串或数字不视为已勾选。
 */
import { computed, onMounted } from 'vue'

import { useRunConfig } from '../../composables/useRunConfig'
import { useFlowNodes } from '../../composables/useFlowNodes'

const props = defineProps({
  /** 当前选中的画布节点 */
  node: { type: Object, required: true },
})

const { patchNodeData } = useFlowNodes()
const { getActiveScenario, envOptions, loadProjectEnvs } = useRunConfig()

onMounted(() => {
  loadProjectEnvs()
})

/** 复选框是否勾选：只认 data.snapshotBefore 为布尔 true */
const snapshotBefore = computed(() => props.node?.data?.snapshotBefore === true)

/** 快照表范围文本：把 snapshotScope.tables 用逗号拼成输入框内容 */
const tablesText = computed(() => {
  const scope = props.node?.data?.snapshotScope
  const tables = scope?.tables
  if (!Array.isArray(tables)) return ''
  return tables.join(', ')
})

/** 当前场景绑定环境是否允许破坏性还原（allowDestructiveReset===1） */
const envAllowsReset = computed(() => {
  const sc = getActiveScenario()
  const envId = sc?.testProjectEnvId
  if (!envId) return false
  const hit = envOptions.value.find((e) => e.testProjectEnvId === envId)
  return hit?.allowDestructiveReset === 1
})

/** 勾选/取消快照开关：写入布尔 snapshotBefore；首次勾选补默认表范围 */
function onSnapshotBeforeChange(e) {
  const checked = e.target.checked
  const patch = { snapshotBefore: checked }
  if (checked && !props.node?.data?.snapshotScope) {
    patch.snapshotScope = { scope: 'tables', tables: [] }
  }
  patchNodeData(props.node.id, patch)
}

function onTablesInput(e) {
  const raw = String(e.target.value ?? '')
  const tables = raw
    .split(/[\n,]+/)
    .map((s) => s.trim())
    .filter(Boolean)
  patchNodeData(props.node.id, {
    snapshotScope: { scope: 'tables', tables },
  })
}
</script>

<style scoped lang="scss">
.snapshot-prop {
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding-top: 4px;
  border-top: 1px dashed var(--pd-border-subtle);
  margin-top: 8px;
}

.snapshot-prop__toggle {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  font-weight: 600;
  cursor: pointer;
}

.snapshot-prop__warn {
  color: var(--pd-text-muted);
}

.field {
  display: flex;
  flex-direction: column;
  gap: 4px;

  label {
    font-size: 11px;
    font-weight: 600;
    color: var(--pd-text-muted);
  }

  textarea {
    width: 100%;
    padding: 6px 8px;
    border-radius: 6px;
    border: 1px solid var(--pd-border-subtle);
    font-size: 11px;
    font-family: ui-monospace, Consolas, monospace;
    resize: vertical;
    box-sizing: border-box;
  }
}

.field__hint {
  font-size: 10px;
  color: var(--pd-text-muted);
  line-height: 1.45;
}
</style>

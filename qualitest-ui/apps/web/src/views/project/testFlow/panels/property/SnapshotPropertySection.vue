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
        依赖环境已启用「库存档点」插件与项目 bindAlias 挂载；同一环境请串行跑（避免并行 Run 互相踩库）。
        存档范围为项目挂载的库/store 整包，不是按表裁剪。
      </div>
    </div>
  </div>
</template>

<script setup>
/**
 * 节点属性：执行前打被测数据快照（测试流 HTTP 节点配置，非质衡平台自身开关）。
 * 勾选写入 data.snapshotBefore=true；取消写入 false。
 * 能力为整库存档，不再提供表范围编辑。
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

/**
 * 当前激活场景所绑环境是否允许破坏性还原。
 * 用场景 envId 在环境下拉里查找，allowDestructiveReset 为 1 时允许。
 */
const envAllowsReset = computed(() => {
  const sc = getActiveScenario()
  const envId = sc?.envId ? String(sc.envId).trim() : ''
  if (!envId) return false
  const hit = envOptions.value.find((e) => e.testProjectEnvId === envId)
  return hit?.allowDestructiveReset === 1
})

/** 勾选/取消快照开关：写入布尔 snapshotBefore */
function onSnapshotBeforeChange(e) {
  const checked = e.target.checked
  patchNodeData(props.node.id, { snapshotBefore: checked })
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
}

.field__hint {
  font-size: 10px;
  color: var(--pd-text-muted);
  line-height: 1.45;
}
</style>

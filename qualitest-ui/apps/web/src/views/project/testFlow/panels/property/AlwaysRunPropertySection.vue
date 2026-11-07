<template>
  <div class="always-run-prop">
    <div class="field">
      <label class="always-run-prop__toggle">
        <input
            :checked="alwaysRun"
            type="checkbox"
            @change="onAlwaysRunChange"
        />
        始终执行（失败后仍跑）
      </label>
      <div class="field__hint">
        上游失败后仍会执行本节点（步骤记为「失败后继续」）；路过的未标记节点记「已绕过」。整次 Run 仍为失败。适合取消预约、删测试数据、恢复配置。有分支时建议各出口汇合到同一清理节点，动作尽量幂等。
      </div>
    </div>
  </div>
</template>

<script setup>
/**
 * 节点属性：始终执行（alwaysRun）开关。
 * 勾选写入 data.alwaysRun=true（布尔）；取消勾选写 null 以删除该字段。
 * 展示态只认布尔 true。
 * 跑流：业务失败后本节点仍执行，成功记 continued，失败记 continued_failed。
 */
import { computed } from 'vue'

import { useFlowNodes } from '../../composables/useFlowNodes'

const props = defineProps({
  /** 当前选中的画布节点 */
  node: { type: Object, required: true },
})

const { patchNodeData } = useFlowNodes()

/** 是否已开启始终执行（仅认布尔 true） */
const alwaysRun = computed(() => props.node?.data?.alwaysRun === true)

/** 勾选写入 true；取消勾选写 null 以删除字段 */
function onAlwaysRunChange(e) {
  const checked = e.target.checked
  patchNodeData(props.node.id, { alwaysRun: checked || null })
}
</script>

<style scoped lang="scss">
.always-run-prop {
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding-top: 4px;
  border-top: 1px dashed var(--pd-border-subtle);
  margin-top: 8px;
}

.always-run-prop__toggle {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  font-weight: 600;
  cursor: pointer;
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
}

.field__hint {
  font-size: 10px;
  color: var(--pd-text-muted);
  line-height: 1.45;
}
</style>

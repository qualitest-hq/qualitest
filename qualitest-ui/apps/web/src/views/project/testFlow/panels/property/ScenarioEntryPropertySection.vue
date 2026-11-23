<template>
  <div class="prop-form">
    <div class="field">
      <label>入口说明（可选）</label>
      <textarea :value="node.data?.doc ?? ''" rows="3" @input="onDoc" />
    </div>
    <div class="field">
      <label>场景列表</label>
      <div class="field__hint">入参值（inSeed）在左侧选场景后于右侧编辑；保存时写入本入口</div>
      <ul class="entry-scenarios">
        <li v-for="sc in scenarios" :key="sc.id" class="entry-scenarios__item">
          <button type="button" class="entry-scenarios__btn" @click="selectScenario(sc.id)">
            {{ sc.name || sc.id }}
          </button>
        </li>
      </ul>
      <button type="button" class="entry-scenarios__add" @click="addScenario">+ 添加场景</button>
    </div>
  </div>
</template>

<script setup>
/**
 * 场景入口属性：编辑入口说明；场景列表读写 store.runConfig，
 * 保存时由 toGraphJson 写回本节点 data.scenarios（含 envId / inSeed）。
 */
import { computed } from 'vue'
import { nextSnowflakeId } from '@/utils/flow/snowflakeId'
import { useFlowNodes } from '../../composables/useFlowNodes'
import { useFlowCanvasStore } from '../../stores/flowCanvasStore'

const props = defineProps({
  node: { type: Object, required: true },
})

const store = useFlowCanvasStore()
const { patchNodeData } = useFlowNodes()
/** 画布当前场景列表（与入口落盘内容同源） */
const scenarios = computed(() => store.runConfig.scenarios)

function onDoc(e) {
  patchNodeData(props.node.id, { doc: e.target.value })
}

/** 选中场景并打开右侧场景配置面板 */
function selectScenario(id) {
  store.runConfig.activeScenarioId = id
  store.showScenarioPanel()
}

/** 追加空场景：默认继承首条 envId，inSeed 为空对象 */
function addScenario() {
  const id = nextSnowflakeId()
  const active = scenarios.value[0]
  store.runConfig.scenarios.push({
    id,
    name: `新场景 ${store.runConfig.scenarios.length + 1}`,
    envId: active?.envId ?? '',
    inSeed: {},
    remark: '',
  })
  store.runConfig.activeScenarioId = id
  store.markDirty()
  store.showScenarioPanel()
}
</script>

<style scoped lang="scss">
@use '../../styles/propPanel.scss' as prop;
@include prop.flow-prop-panel-root;

.entry-scenarios {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.entry-scenarios__btn {
  width: 100%;
  text-align: left;
  padding: 6px 8px;
  border: 1px solid var(--el-border-color, #dcdfe6);
  border-radius: 4px;
  background: #fff;
  cursor: pointer;
}

.entry-scenarios__add {
  margin-top: 8px;
  border: none;
  background: transparent;
  color: var(--el-color-primary, #409eff);
  cursor: pointer;
  padding: 0;
}
</style>

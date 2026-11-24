<template>
  <div class="prop-form">
    <EntryDocMarkdownField
      :model-value="node.data?.doc ?? ''"
      :rows="4"
      @update:model-value="onDoc"
    />
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
 * 场景入口节点属性面板。
 * 可改入口说明（编辑 / Markdown 预览）；下方列出本入口的场景，点选后打开右侧场景配置面板改环境与入参值。
 * 打开本面板时切到该入口为跑前选中，编辑态场景列表与本入口 data.scenarios 对齐。
 */
import { computed, watch } from 'vue'
import { nextSnowflakeId } from '@/utils/flow/snowflakeId'
import EntryDocMarkdownField from '../../components/EntryDocMarkdownField.vue'
import { useFlowNodes } from '../../composables/useFlowNodes'
import { useFlowCanvasStore } from '../../stores/flowCanvasStore'
import { bindScenarioEntry } from '../../utils/stagingGraphInput'

const props = defineProps({
  node: { type: Object, required: true },
})

const store = useFlowCanvasStore()
const { patchNodeData } = useFlowNodes()
/** 当前画布运行配置中的场景列表（编辑中；须已绑到本入口） */
const scenarios = computed(() => store.runConfig.scenarios)

/** 属性面板打开本场景入口时，刷回上一入口并载入本节点场景 */
watch(
  () => props.node?.id,
  (entryId) => {
    if (entryId) bindScenarioEntry(store, entryId)
  },
  { immediate: true },
)

/** 写回入口说明原文 */
function onDoc(doc) {
  patchNodeData(props.node.id, { doc })
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

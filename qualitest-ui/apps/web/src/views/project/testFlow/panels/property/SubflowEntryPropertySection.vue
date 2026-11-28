<template>
  <div class="prop-form">
    <EntryMaxStepsField :node="node" />
    <EntryDocMarkdownField
      :model-value="node.data?.doc ?? ''"
      :rows="4"
      @update:model-value="(v) => patch({ doc: v })"
    />
    <SubflowInputDeclSection
      :model-value="inputDecl"
      @update:model-value="onInputDecl"
    />
    <FallbackOutJsonField
      :model-value="fallbackOutEdit"
      hint="未走到返回时挂给父流（Run 仍失败）；只填业务对象，引擎会包成 out.body"
      @update:model-value="onFallbackOut"
    />
  </div>
</template>

<script setup>
/**
 * 子流入口节点属性面板。
 * - inputDecl：扁平入参键声明。
 * - fallbackOut：业务兜底对象（无 status/headers）；整次未碰到返回节点时，
 *   引擎收束把它包成 { body: 该对象 } 挂到父流 nodes.<子流节点id>.out，Run 仍记失败。
 * 打开面板时若节点仍带旧三节 inputShape 且尚无 inputDecl，会一次性展平写入 inputDecl 并清掉 inputShape。
 */
import { computed, watch } from 'vue'

import { defaultSubflowFallbackOut } from '@/utils/flow/subflowEntryDefaults'
import EntryDocMarkdownField from '../../components/EntryDocMarkdownField.vue'
import EntryMaxStepsField from '../../components/EntryMaxStepsField.vue'
import FallbackOutJsonField from '../../components/FallbackOutJsonField.vue'
import { useFlowNodes } from '../../composables/useFlowNodes'
import {
  migrateSubflowInputDecl,
  subflowFallbackOutForEdit,
} from '../../utils/subflowEntryCompat'
import SubflowInputDeclSection from './SubflowInputDeclSection.vue'

const props = defineProps({
  node: { type: Object, required: true },
})

const { patchNodeData } = useFlowNodes()

/** 当前入参声明（含从旧 inputShape 展平的只读展示） */
const inputDecl = computed(() => migrateSubflowInputDecl(props.node?.data) ?? [])

/** 编辑态兜底对象：完整 out 只取 body；非法则用默认业务对象 */
const fallbackOutEdit = computed(() => subflowFallbackOutForEdit(props.node?.data?.fallbackOut))

/**
 * 切到本节点时：若只有旧 inputShape、没有 inputDecl，则展平落盘并删除 inputShape。
 */
watch(
  () => props.node?.id,
  () => {
    const data = props.node?.data
    if (!data || Array.isArray(data.inputDecl)) return
    if (!data.inputShape) return
    const migrated = migrateSubflowInputDecl(data)
    if (migrated == null) return
    patchNodeData(props.node.id, { inputDecl: migrated, inputShape: undefined })
  },
  { immediate: true },
)

function patch(p) {
  patchNodeData(props.node.id, p)
}

/** 保存声明时顺带清掉可能残留的旧 inputShape */
function onInputDecl(v) {
  patch({ inputDecl: v, inputShape: undefined })
}

/** 兜底只落业务对象；空值用默认 code/message */
function onFallbackOut(v) {
  patch({ fallbackOut: v && typeof v === 'object' ? v : defaultSubflowFallbackOut() })
}
</script>

<style scoped lang="scss">
@use '../../styles/propPanel.scss' as prop;
@include prop.flow-prop-panel-root;
</style>

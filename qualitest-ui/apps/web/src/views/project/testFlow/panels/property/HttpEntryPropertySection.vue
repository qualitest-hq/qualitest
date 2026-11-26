<template>
  <div class="prop-form">
    <div class="field">
      <label>Method</label>
      <select
        :value="node.data?.method ?? 'POST'"
        @change="(e) => patch({ method: e.target.value })"
      >
        <option v-for="m in methods" :key="m" :value="m">{{ m }}</option>
      </select>
    </div>
    <div class="field">
      <label>Path</label>
      <input
        :value="node.data?.path ?? ''"
        type="text"
        placeholder="/api/demo"
        @input="(e) => patch({ path: e.target.value })"
      />
    </div>
    <div class="field">
      <label>环境</label>
      <el-select
        :loading="envLoading"
        :model-value="envIdValue"
        class="prop-el-select"
        placeholder="选择测试环境"
        @change="onEnvChange"
      >
        <el-option
          v-for="env in envOptions"
          :key="env.testProjectEnvId"
          :label="env.envName"
          :value="env.testProjectEnvId"
        />
      </el-select>
      <div v-if="!envOptions.length && !envLoading" class="field__hint">
        当前项目暂无环境，请先在场景配置或环境管理中创建
      </div>
    </div>
    <EntryMaxStepsField :node="node" />
    <EntryDocMarkdownField
      :model-value="node.data?.doc ?? ''"
      :rows="4"
      @update:model-value="(v) => patch({ doc: v })"
    />
    <EntryInputShapeSection
      :model-value="node.data?.inputShape ?? null"
      @update:model-value="(v) => patch({ inputShape: v })"
    />
    <FallbackOutJsonField
      :model-value="node.data?.fallbackOut ?? null"
      hint="零次 return 时收束交付；须含 status / headers / body"
      @update:model-value="(v) => patch({ fallbackOut: v })"
    />
  </div>
</template>

<script setup>
/**
 * HTTP 入口节点属性面板。
 * 可改：method、path、envId（项目环境下拉）、步数上限、入口说明、
 * inputShape（body 可嵌套；query/headers 扁平；入站不符返回 400）、
 * fallbackOut（完整 status/headers/body；整次未碰到返回节点时收束对外写出）。
 */
import { computed, onMounted } from 'vue'

import EntryDocMarkdownField from '../../components/EntryDocMarkdownField.vue'
import EntryMaxStepsField from '../../components/EntryMaxStepsField.vue'
import FallbackOutJsonField from '../../components/FallbackOutJsonField.vue'
import { useFlowNodes } from '../../composables/useFlowNodes'
import { useRunConfig } from '../../composables/useRunConfig'
import EntryInputShapeSection from './EntryInputShapeSection.vue'

const props = defineProps({
  node: { type: Object, required: true },
})

const { patchNodeData } = useFlowNodes()
const { envOptions, envLoading, loadProjectEnvs } = useRunConfig()
const methods = ['GET', 'POST', 'PUT', 'PATCH', 'DELETE', 'HEAD', 'OPTIONS']

const envIdValue = computed(() => String(props.node.data?.envId ?? '').trim())

onMounted(() => {
  void loadProjectEnvs()
})

/** 把局部字段合并写回当前节点数据（含入参形状） */
function patch(p) {
  patchNodeData(props.node.id, p)
}

/** 环境下拉变更：写入项目环境主键字符串 */
function onEnvChange(val) {
  patch({ envId: String(val ?? '').trim() })
}
</script>

<style scoped lang="scss">
@use '../../styles/propPanel.scss' as prop;
@include prop.flow-prop-panel-root;
</style>

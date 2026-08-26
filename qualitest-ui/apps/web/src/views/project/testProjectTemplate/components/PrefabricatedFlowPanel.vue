<template>
  <div class="tpl-prefab-section prefab-flow-panel">
    <div class="tpl-prefab-section__head">
      <span class="tpl-prefab-section__title">预制测试流</span>
      <div v-if="!readOnly" class="tpl-prefab-section__actions">
        <el-button icon="Plus" size="small" type="primary" @click="handleAdd">新增</el-button>
        <el-button :disabled="selectedIndex < 0" icon="Delete" size="small" @click="handleRemove">删除</el-button>
      </div>
    </div>
    <div class="tpl-prefab-section__body">
      <p v-if="!readOnly" class="tpl-prefab-section__hint">
        登录等测试流可在此维护；勾选模板时种子到项目，extracts 用于派生托管头与 credentialApi。
      </p>
      <el-table
        v-if="rows.length"
        :data="rows"
        border
        class="tpl-prefab-section__table"
        highlight-current-row
        row-key="_index"
        size="small"
        @current-change="onCurrentChange"
      >
        <el-table-column label="流名称" min-width="140" prop="flowName" show-overflow-tooltip />
        <el-table-column label="方法" prop="method" width="72" />
        <el-table-column label="路径" min-width="140" prop="apiPath" show-overflow-tooltip />
        <el-table-column label="抽取" min-width="160" prop="extractLabel" show-overflow-tooltip />
        <el-table-column align="center" label="操作" width="100">
          <template #default="scope">
            <el-button link type="primary" @click.stop="openCanvas(scope.row._index)">
              {{ readOnly ? '查看画布' : '打开画布' }}
            </el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-empty
        v-else
        :class="readOnly ? 'tpl-prefab-section__empty--compact' : 'tpl-prefab-section__empty'"
        :image-size="48"
        description="可选：登录等测试流，勾选模板时种子到项目"
      />
    </div>
  </div>
</template>

<script setup>
/**
 * 预制测试流面板：编辑 templateFlows。
 * 主操作「打开画布」进入完整画布；小 Dialog 已去掉，避免双通道。
 */
import { computed, ref, watch } from 'vue'
import {
  emptyPrefabFlow,
  parseFlows,
  summarizePrefabFlow,
} from '../utils/templateForm'

const props = defineProps({
  /** 同模板预制接口，供画布合成 API 树（由父级写入草稿）。 */
  templateApis: { type: Array, default: () => [] },
  /** 内置模板查看时只读。 */
  readOnly: { type: Boolean, default: false },
})

const emit = defineEmits(['open-canvas'])

const list = defineModel({ type: Array, default: () => [] })

const selectedIndex = ref(-1)

const flows = computed(() => parseFlows(list.value))

const rows = computed(() =>
  flows.value.map((flow, index) => ({
    _index: index,
    ...summarizePrefabFlow(flow),
  })),
)

watch(
  () => list.value,
  () => {
    if (selectedIndex.value >= flows.value.length) selectedIndex.value = -1
  },
)

function commit(next) {
  list.value = next
}

function handleAdd() {
  const next = [...flows.value, emptyPrefabFlow()]
  commit(next)
  openCanvas(next.length - 1)
}

function handleRemove() {
  if (selectedIndex.value < 0) return
  const next = flows.value.filter((_, i) => i !== selectedIndex.value)
  selectedIndex.value = -1
  commit(next)
}

function onCurrentChange(row) {
  selectedIndex.value = row?._index ?? -1
}

function openCanvas(index) {
  emit('open-canvas', { flowIndex: index, readOnly: props.readOnly })
}
</script>

<style lang="scss">
@use '../styles/templatePrefabPanel.scss';
</style>

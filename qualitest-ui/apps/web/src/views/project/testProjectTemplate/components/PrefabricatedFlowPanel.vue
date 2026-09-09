<template>
  <!--
    预制测试流列表：只读。
    不能在此新增、删除或改图；有流时只能「查看画布」。
  -->
  <div class="tpl-prefab-section prefab-flow-panel">
    <div class="tpl-prefab-section__head">
      <span class="tpl-prefab-section__title">预制测试流</span>
    </div>
    <div class="tpl-prefab-section__body">
      <p class="tpl-prefab-section__hint">
        仅查看。预制流来自「项目另存为模板」或完整包导入；勾选模板时种子到项目，登录流抽取会派生托管头。
      </p>
      <el-table
        v-if="rows.length"
        :data="rows"
        border
        class="tpl-prefab-section__table"
        row-key="_index"
        size="small"
      >
        <el-table-column label="流名称" min-width="160" prop="flowName" show-overflow-tooltip />
        <el-table-column label="说明" min-width="220" prop="description" show-overflow-tooltip>
          <template #default="scope">
            {{ scope.row.description || '—' }}
          </template>
        </el-table-column>
        <el-table-column align="center" label="操作" width="100">
          <template #default="scope">
            <el-button link type="primary" @click.stop="openCanvas(scope.row._index)">
              查看画布
            </el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-empty
        v-else
        :class="readOnly ? 'tpl-prefab-section__empty--compact' : 'tpl-prefab-section__empty'"
        :image-size="48"
        description="暂无预制流；请从跑通登录的项目另存为模板，或导入含 flows 的完整包"
      />
    </div>
  </div>
</template>

<script setup>
/**
 * 模板抽屉内的预制测试流面板。
 *
 * 展示 form.templateFlows；不提供增删改。
 * 「查看画布」把当前流下标交给父组件，由父组件跳转只读画布页。
 *
 * 预制流内容从哪里来：测试项目「另存为项目模板」，或导入带 templateFlows 的完整包。
 * 管理端新增/编辑模板不会在此造流。
 */
import { computed } from 'vue'
import { parseFlows } from '../utils/templateForm'

defineProps({
  /**
   * 整份模板抽屉是否只读（例如内置模板「查看」）。
   * 只影响空态样式；流列表本身无论是否只读都不能改。
   */
  readOnly: { type: Boolean, default: false },
})

const emit = defineEmits(['open-canvas'])

/** 双向绑定：父级 form.templateFlows 数组 */
const list = defineModel({ type: Array, default: () => [] })

const flows = computed(() => parseFlows(list.value))

/** 表格行：下标 + 名称 + 说明 */
const rows = computed(() =>
  flows.value.map((flow, index) => ({
    _index: index,
    flowName: String(flow?.flowName || '').trim(),
    description: String(flow?.description || '').trim(),
  })),
)

/** 通知父组件打开第 index 条流的只读画布 */
function openCanvas(index) {
  emit('open-canvas', { flowIndex: index })
}
</script>

<style lang="scss">
@use '../styles/templatePrefabPanel.scss';
</style>

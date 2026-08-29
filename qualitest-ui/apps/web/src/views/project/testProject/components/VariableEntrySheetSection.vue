<template>
  <div
      class="variable-entry-sheet-wrap"
      :class="[wrapClass, { 'is-readonly': readOnly }]"
  >
    <DebugKvSheet
        v-if="rows.length"
        :rows="rows"
        bordered-editable-fields
        :disable-value-for-composite-types="true"
        :enable-file-upload-for-file-type="enableFileUpload"
        :persist-file-upload="persistFileUpload"
        :show-add-child-for-composite="!readOnly"
        remark-expandable
        :show-remark-column="showRemarkColumn"
        :show-type-column="true"
        :virtual-min-rows="0"
        key-column-field="key"
        :name-label="nameLabel"
        :name-placeholder="namePlaceholder"
        remark-placeholder="备注说明"
        value-label="value"
        :value-placeholder="valuePlaceholder"
        @add-child="onAddChild"
        @remove="onRemove"
    >
      <template #type="{ row }">
        <DebugParamTypeCell
            :allow-type-create="false"
            :row="row"
            :popper-class="typePopperClass"
            :show-required-star="false"
            :show-schema-gear="false"
            :teleported="true"
            :type-class-fn="paramTypeSelectClass"
            :type-options="VARIABLE_ENTRY_PARAM_TYPES"
            @type-change="onTypeChange(row)"
        />
      </template>
    </DebugKvSheet>
    <div v-else-if="showEmpty" class="variable-entry-sheet-empty">
      <slot name="empty">
        {{ emptyText }}
      </slot>
    </div>
  </div>
</template>

<script setup>
/**
 * 变量条目（素材库 / 环境变量）扁平行编辑表。
 * 默认 file 类型可上传落盘；模板预制等场景可关上传。
 */
import DebugKvSheet from './DebugKvSheet.vue'
import DebugParamTypeCell from './DebugParamTypeCell.vue'
import {
  paramTypeSelectClass,
  VARIABLE_ENTRY_PARAM_TYPES
} from '@/views/project/testProject/utils/variableEntryUtils'

const props = defineProps({
  rows: {
    type: Array,
    default: () => []
  },
  nameLabel: {
    type: String,
    default: 'key'
  },
  namePlaceholder: {
    type: String,
    default: 'key'
  },
  showRemarkColumn: {
    type: Boolean,
    default: true
  },
  showEmpty: {
    type: Boolean,
    default: false
  },
  emptyText: {
    type: String,
    default: '暂无变量'
  },
  wrapClass: {
    type: String,
    default: ''
  },
  typePopperClass: {
    type: String,
    default: 'variable-entry-type-select-popper'
  },
  /** 是否允许 file 类型选文件上传；模板预制等无 projectId 场景应关闭。 */
  enableFileUpload: {
    type: Boolean,
    default: true
  },
  /** 选文件后是否走平台上传落盘。 */
  persistFileUpload: {
    type: Boolean,
    default: true
  },
  valuePlaceholder: {
    type: String,
    default: 'value 或选择文件'
  },
  /** 只读：禁止增删改交互（查看内置模板）。 */
  readOnly: {
    type: Boolean,
    default: false
  }
})

const emit = defineEmits(['add-child', 'remove', 'type-change'])

function onAddChild(idx) {
  if (props.readOnly) return
  emit('add-child', idx)
}

function onRemove(idx) {
  if (props.readOnly) return
  emit('remove', idx)
}

function onTypeChange(row) {
  if (props.readOnly) return
  emit('type-change', row)
}
</script>

<style lang="scss" scoped>
.variable-entry-sheet-wrap {
  border: 1px solid var(--pd-border-subtle, var(--el-border-color-lighter));
  border-radius: var(--pd-radius, 10px);
  background: var(--pd-surface-elevated, var(--el-bg-color));
  box-shadow: var(--pd-shadow-card, 0 1px 2px rgba(20, 60, 120, 0.05));
  overflow: visible;

  :deep(.debug-kv-cell--chk) {
    display: none;
  }

  :deep(.debug-kv-head),
  :deep(.debug-kv-row) {
    grid-template-columns: repeat(3, minmax(120px, 1fr)) 120px 72px !important;
  }

  :deep(.debug-kv-sheet) {
    border-radius: var(--pd-radius, 10px);
  }

  &.is-readonly {
    pointer-events: none;

    :deep(.debug-kv-cell--action) {
      visibility: hidden;
    }
  }
}

.variable-entry-sheet-empty {
  padding: 24px 16px;
  text-align: center;
  font-size: 13px;
  color: var(--el-text-color-secondary);
}
</style>

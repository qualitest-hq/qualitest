<template>
  <div class="debug-param-type-cell">
    <el-select
        v-model="row.type"
        :class="typeClassFn(row.type)"
        :allow-create="allowTypeCreate"
        :popper-class="popperClass"
        :teleported="teleported"
        default-first-option
        filterable
        placeholder="类型"
        size="small"
        @change="onTypeSelectChange"
    >
      <el-option v-for="t in typeOptions" :key="t" :label="t" :value="t"/>
    </el-select>
    <el-tooltip v-if="showRequiredStar" placement="top">
      <template #content>
        {{ row.required ? '必传' : '非必传' }}
      </template>
      <button
          :class="{ 'is-on': row.required }"
          aria-label="是否必传"
          class="debug-param-required-star"
          type="button"
          @click.stop="$emit('toggle-required')"
      >
        <span class="debug-param-required-star-char">*</span>
      </button>
    </el-tooltip>
    <el-tooltip v-if="showSchemaGear" content="类型高级设置" placement="top">
      <el-button class="debug-param-schema-btn" link type="primary" @click="$emit('open-schema')">
        <el-icon><Setting/></el-icon>
      </el-button>
    </el-tooltip>
  </div>
</template>

<script setup>
import {Setting} from '@element-plus/icons-vue'

defineProps({
  row: {
    type: Object,
    required: true
  },
  typeOptions: {
    type: Array,
    default: () => []
  },
  typeClassFn: {
    type: Function,
    required: true
  },
  /** x-www-form-urlencoded 等场景不允许 file，亦不允许自定义填出 file */
  allowTypeCreate: {
    type: Boolean,
    default: true
  },
  /** object/array 等结构类型可在父组件设为 false，隐藏「高级设置」齿轮 */
  showSchemaGear: {
    type: Boolean,
    default: true
  },
  /** 是否展示必传星标（素材库等场景可关闭） */
  showRequiredStar: {
    type: Boolean,
    default: true
  },
  /** 下拉挂载到 body，弹框内需配合更高 z-index 的 popper-class */
  teleported: {
    type: Boolean,
    default: true
  },
  popperClass: {
    type: String,
    default: ''
  }
})

const emit = defineEmits(['toggle-required', 'open-schema', 'type-change'])

function onTypeSelectChange() {
  emit('type-change')
}
</script>

<style lang="scss" scoped>
.debug-param-type-cell {
  display: flex;
  align-items: center;
  gap: 6px;
  min-width: 0;
  width: 100%;
  overflow: visible;

  .param-type-select {
    flex: 1;
    min-width: 0;

    :deep(.el-select__wrapper),
    :deep(.el-input__wrapper) {
      min-height: 30px;
      align-items: center;
    }

    :deep(.el-input__inner),
    :deep(.el-select__selected-item),
    :deep(.el-select__placeholder) {
      font-size: 12px;
      font-weight: 500;
    }

    &.is-type-string :deep(.el-input__inner),
    &.is-type-string :deep(.el-select__selected-item) {
      color: #16a34a;
    }

    &.is-type-integer :deep(.el-input__inner),
    &.is-type-integer :deep(.el-select__selected-item),
    &.is-type-number :deep(.el-input__inner),
    &.is-type-number :deep(.el-select__selected-item) {
      color: #0b6edc;
    }

    &.is-type-boolean :deep(.el-input__inner),
    &.is-type-boolean :deep(.el-select__selected-item) {
      color: #7c3aed;
    }

    &.is-type-array :deep(.el-input__inner),
    &.is-type-array :deep(.el-select__selected-item) {
      color: #c2410c;
    }

    &.is-type-file :deep(.el-input__inner),
    &.is-type-file :deep(.el-select__selected-item) {
      color: #db2777;
    }

    &.is-type-object :deep(.el-input__inner),
    &.is-type-object :deep(.el-select__selected-item) {
      color: #b45309;
    }

    &.is-type-any :deep(.el-input__inner),
    &.is-type-any :deep(.el-select__selected-item) {
      color: #64748b;
    }

    &.is-type-null :deep(.el-input__inner),
    &.is-type-null :deep(.el-select__selected-item) {
      color: #94a3b8;
    }
  }

  .debug-param-required-star {
    flex-shrink: 0;
    display: inline-flex;
    align-items: center;
    justify-content: center;
    box-sizing: border-box;
    width: 28px;
    height: 28px;
    margin: 0;
    padding: 0;
    border: 1px solid var(--pd-border-subtle, #e4e7ec);
    border-radius: 6px;
    background: #fff;
    cursor: pointer;
    color: #d1d5db;
    line-height: 0;
    transition: color 0.15s ease, background-color 0.15s ease, border-color 0.15s ease;

    &:hover {
      color: #f89898;
      border-color: #fbc4c4;
      background-color: rgba(245, 108, 108, 0.06);
    }

    &.is-on {
      color: var(--el-color-danger, #f56c6c);
      border-color: var(--el-color-danger-light-5, #fab6b6);
      background-color: rgba(245, 108, 108, 0.06);

      &:hover {
        color: var(--el-color-danger-light-3, #f78989);
        border-color: var(--el-color-danger-light-3, #f78989);
      }
    }

    .debug-param-required-star-char {
      display: block;
      width: 28px;
      height: 28px;
      margin: 0;
      padding: 0;
      font-size: 22px;
      font-weight: 700;
      line-height: 28px;
      text-align: center;
      font-family: system-ui, -apple-system, 'Segoe UI', sans-serif;
      user-select: none;
      pointer-events: none;
      transform: translateY(4px);
    }
  }

  .debug-param-schema-btn {
    flex-shrink: 0;
    box-sizing: border-box;
    width: 28px;
    height: 28px;
    padding: 0 !important;
    min-height: 28px;
    border: 1px solid var(--pd-border-subtle, #e4e7ec) !important;
    border-radius: 6px;
    background-color: #fff !important;

    &:hover {
      border-color: #c6e2ff !important;
      background-color: var(--pd-primary-soft, rgba(64, 158, 255, 0.06)) !important;
    }

    :deep(.el-icon) {
      font-size: 16px;
    }
  }
}
</style>

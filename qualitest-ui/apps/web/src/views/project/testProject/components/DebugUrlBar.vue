<template>
  <div class="debug-url-bar">
    <el-select
        :model-value="method"
        :class="getApiHttpMethodBadgeClass(method)"
        class="debug-method-select"
        placeholder="方法"
        @update:model-value="(v) => emit('update:method', v)"
    >
      <el-option v-for="m in HTTP_METHODS" :key="m" :label="m" :value="m"/>
    </el-select>
    <div class="debug-url-input-wrap">
      <el-input
          :model-value="draftApiPath"
          class="debug-url-input"
          clearable
          placeholder="路径，如 /api/foo"
          @update:model-value="(v) => emit('update:draftApiPath', v)"
      />
    </div>
    <el-button
        v-if="!embedInDesign"
        :loading="debugSending"
        class="debug-send-btn"
        type="primary"
        @click="emit('send')"
    >发送
    </el-button>
    <el-button
        v-if="!embedInDesign"
        v-hasPermi="['project:testProject:edit']"
        :loading="apiDebugSaving"
        class="debug-save-btn"
        @click="emit('save')"
    >保存
    </el-button>
  </div>
</template>

<script setup>
import {HTTP_METHODS, getApiHttpMethodBadgeClass} from '@/views/project/testProject/utils/httpMethodMeta'

defineProps({
  method: {type: String, default: 'GET'},
  draftApiPath: {type: String, default: ''},
  embedInDesign: {type: Boolean, default: false},
  debugSending: {type: Boolean, default: false},
  apiDebugSaving: {type: Boolean, default: false}
})

const emit = defineEmits(['send', 'save', 'update:draftApiPath', 'update:method'])
</script>

<style lang="scss" scoped>
.debug-url-bar {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-shrink: 0;
  min-width: 0;
  width: 100%;
  padding: 8px 6px;
  border-radius: 0;
  background: var(--pd-bg-sunken, #e9f2fc);
  border: none;
  border-bottom: 1px solid var(--pd-divider, var(--pd-border-muted));
  box-shadow: none;

  .debug-method-select {
    flex: 0 0 118px;
    width: 118px;

    :deep(.el-select__wrapper) {
      min-height: 38px;
      height: 38px;
      padding-left: 12px;
      padding-right: 12px;
      align-items: center;
    }

    :deep(.el-select__selection),
    :deep(.el-select__placeholder),
    :deep(.el-select__selected-item) {
      font-size: 14px;
      font-weight: 600;
    }

    &.is-POST :deep(.el-select__wrapper) {
      background: #fff7ed;
      box-shadow: 0 0 0 1px #fdba74 inset;
    }

    &.is-GET :deep(.el-select__wrapper) {
      background: #ecfdf5;
      box-shadow: 0 0 0 1px #6ee7b7 inset;
    }

    &.is-PUT :deep(.el-select__wrapper) {
      background: #eff6ff;
      box-shadow: 0 0 0 1px #93c5fd inset;
    }

    &.is-PATCH :deep(.el-select__wrapper) {
      background: #faf5ff;
      box-shadow: 0 0 0 1px #d8b4fe inset;
    }

    &.is-DELETE :deep(.el-select__wrapper) {
      background: #fef2f2;
      box-shadow: 0 0 0 1px #fca5a5 inset;
    }
  }

  /* flex 落在 wrap 上，EP .el-input width:100% 只相对 wrap，不再盖住方法选择 */
  .debug-url-input-wrap {
    flex: 1 1 0%;
    min-width: 0;
  }

  .debug-url-input {
    width: 100%;

    :deep(.el-input__wrapper) {
      border-radius: var(--pd-radius-sm, 8px);
      font-family: ui-monospace, Consolas, monospace;
    }
  }

  .debug-send-btn,
  .debug-save-btn {
    flex-shrink: 0;
  }

  .debug-send-btn {
    min-width: 88px;
  }

  .debug-save-btn {
    border-color: var(--pd-border-subtle);
    background: var(--pd-surface-elevated);
    color: var(--pd-text);

    &:hover {
      border-color: var(--pd-primary);
      color: var(--pd-primary);
      background: var(--pd-primary-soft);
    }
  }
}
</style>

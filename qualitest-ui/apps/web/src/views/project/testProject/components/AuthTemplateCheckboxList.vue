<template>
  <div v-loading="loading" class="auth-template-checkbox-list">
    <el-checkbox-group v-model="selectedIds" class="auth-template-checkbox-list__group">
      <el-checkbox
          v-for="tpl in templates"
          :key="tpl.testProjectTemplateId"
          :label="String(tpl.testProjectTemplateId)"
          class="auth-template-checkbox-list__item"
      >
        <span class="auth-template-checkbox-list__name">{{ tpl.templateName }}</span>
        <span class="auth-template-checkbox-list__meta">
          {{ tpl.headerName }} · {{ tpl.headerValueTemplate }}
          <template v-if="pathPrefixHint(tpl)"> · {{ pathPrefixHint(tpl) }}</template>
        </span>
      </el-checkbox>
    </el-checkbox-group>
    <el-empty
        v-if="!loading && !templates.length"
        :description="emptyText"
        :image-size="emptySize"
    />
  </div>
</template>

<script setup>
/**
 * 启用模板勾选列表：新建项目、设置页「从项目模板添加」共用。
 */
import { formatPathPrefixHint } from '../../testProjectTemplate/utils/templateForm'

defineProps({
  templates: {
    type: Array,
    default: () => [],
  },
  loading: {
    type: Boolean,
    default: false,
  },
  emptyText: {
    type: String,
    default: '暂无可用模板',
  },
  emptySize: {
    type: Number,
    default: 56,
  },
})

const selectedIds = defineModel({ type: Array, default: () => [] })

function pathPrefixHint(tpl) {
  return formatPathPrefixHint(tpl?.matchConfig)
}
</script>

<style scoped lang="scss">
.auth-template-checkbox-list {
  width: 100%;
  min-height: 72px;
}

.auth-template-checkbox-list__group {
  display: flex;
  flex-direction: column;
  gap: 10px;
  width: 100%;
}

.auth-template-checkbox-list__item {
  display: flex;
  align-items: flex-start;
  height: auto;
  margin-right: 0;
  white-space: normal;

  :deep(.el-checkbox__label) {
    display: flex;
    flex-direction: column;
    gap: 4px;
    line-height: 1.4;
    white-space: normal;
  }
}

.auth-template-checkbox-list__name {
  font-size: 13px;
  font-weight: 500;
  color: var(--el-text-color-primary);
}

.auth-template-checkbox-list__meta {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
</style>

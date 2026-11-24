<template>
  <div class="entry-doc-field">
    <div class="entry-doc-field__head">
      <label class="entry-doc-field__label">入口说明（可选）</label>
      <div class="entry-doc-field__tabs" role="tablist">
        <button
          type="button"
          class="entry-doc-field__tab"
          :class="{ 'is-active': mode === 'edit' }"
          role="tab"
          @click="mode = 'edit'"
        >
          编辑
        </button>
        <button
          type="button"
          class="entry-doc-field__tab"
          :class="{ 'is-active': mode === 'preview' }"
          role="tab"
          @click="mode = 'preview'"
        >
          预览
        </button>
      </div>
    </div>
    <textarea
      v-if="mode === 'edit'"
      class="entry-doc-field__textarea"
      :value="modelValue"
      :rows="rows"
      placeholder="可选 Markdown：对接注意、changelog、举例…"
      @input="onInput"
    />
    <div v-else class="entry-doc-field__preview">
      <EntryDocReadonly :source="modelValue" />
    </div>
  </div>
</template>

<script setup lang="ts">
/**
 * 入口说明可编辑字段：顶部切换「编辑 / 预览」。
 * 编辑态为多行文本；预览态把同一内容渲染成 Markdown。
 * 变更通过 v-model（modelValue / update:modelValue）回写父组件。
 */
import { ref } from 'vue';

import EntryDocReadonly from './EntryDocReadonly.vue';

withDefaults(
  defineProps<{
    /** 当前说明原文 */
    modelValue?: string;
    /** 编辑区行数 */
    rows?: number;
  }>(),
  { modelValue: '', rows: 4 },
);

const emit = defineEmits<{
  'update:modelValue': [string];
}>();

/** 当前面板模式：edit 编辑，preview 预览 */
const mode = ref<'edit' | 'preview'>('edit');

/** 文本框输入时同步写回父级 */
function onInput(e: Event) {
  const target = e.target as HTMLTextAreaElement;
  emit('update:modelValue', target.value);
}
</script>

<style scoped lang="scss">
.entry-doc-field {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.entry-doc-field__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.entry-doc-field__label {
  font-size: 12px;
  color: var(--el-text-color-regular, #606266);
}

.entry-doc-field__tabs {
  display: inline-flex;
  border: 1px solid var(--el-border-color, #dcdfe6);
  border-radius: 4px;
  overflow: hidden;
}

.entry-doc-field__tab {
  border: none;
  background: #fff;
  padding: 2px 10px;
  font-size: 12px;
  cursor: pointer;
  color: var(--el-text-color-secondary, #909399);

  &.is-active {
    background: var(--el-color-primary-light-9, #ecf5ff);
    color: var(--el-color-primary, #409eff);
  }
}

.entry-doc-field__textarea {
  width: 100%;
  box-sizing: border-box;
  resize: vertical;
  font: inherit;
  padding: 6px 8px;
  border: 1px solid var(--el-border-color, #dcdfe6);
  border-radius: 4px;
}

.entry-doc-field__preview {
  min-height: 72px;
  padding: 8px;
  border: 1px solid var(--el-border-color-lighter, #ebeef5);
  border-radius: 4px;
  background: var(--el-fill-color-blank, #fff);
  font-size: 13px;
}
</style>

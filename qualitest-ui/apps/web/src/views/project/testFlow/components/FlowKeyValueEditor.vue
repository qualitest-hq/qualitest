<template>
  <div class="flow-kv-editor">
    <div class="flow-kv-editor__head">
      <span v-for="col in columns" :key="col.field" class="flow-kv-editor__col">{{ col.label }}</span>
      <span aria-hidden="true" class="flow-kv-editor__col flow-kv-editor__col--action" />
    </div>
    <div v-for="(row, idx) in localRows" :key="idx" class="flow-kv-editor__row">
      <input
          v-for="col in columns"
          :key="col.field"
          v-model="row[col.field]"
          :placeholder="col.placeholder"
          class="flow-kv-editor__input"
          type="text"
          @input="emitRows"
      />
      <button
          :disabled="localRows.length <= minRows"
          class="btn btn--ghost flow-kv-editor__remove"
          title="删除行"
          type="button"
          @click="removeRow(idx)"
      >
        ×
      </button>
    </div>
    <button class="btn btn--ghost flow-kv-editor__add" type="button" @click="addRow">
      {{ addLabel }}
    </button>
  </div>
</template>

<script setup>
/**
 * 双列键值表编辑器：flowSeed、flowOutputs 等场景共用。
 * columns 定义列 field / label / placeholder；rows 为行对象数组。
 */
import { ref, watch } from 'vue';

const props = defineProps({
  columns: {
    type: Array,
    required: true,
  },
  rows: {
    type: Array,
    default: () => [],
  },
  addLabel: {
    type: String,
    default: '＋ 添加行',
  },
  minRows: {
    type: Number,
    default: 1,
  },
});

const emit = defineEmits(['update:rows']);

function emptyRow() {
  return Object.fromEntries(props.columns.map((col) => [col.field, '']));
}

function normalizeRows(rows) {
  if (!Array.isArray(rows) || !rows.length) {
    return [emptyRow()];
  }
  return rows.map((row) => {
    const next = emptyRow();
    props.columns.forEach((col) => {
      next[col.field] = row?.[col.field] == null ? '' : String(row[col.field]);
    });
    return next;
  });
}

const localRows = ref(normalizeRows(props.rows));

watch(
  () => props.rows,
  (val) => {
    localRows.value = normalizeRows(val);
  },
  { deep: true },
);

function emitRows() {
  emit('update:rows', localRows.value.map((row) => ({ ...row })));
}

function addRow() {
  localRows.value.push(emptyRow());
}

function removeRow(idx) {
  if (localRows.value.length <= props.minRows) return;
  localRows.value.splice(idx, 1);
  emitRows();
}
</script>

<style scoped lang="scss">
@use '../styles/propPanel.scss' as prop;
@use '../styles/flowCanvasTokens.scss' as flow;

.flow-kv-editor {
  @include prop.flow-kv-editor-root;
  @include flow.flow-pd-buttons-nested(26px, 8px);
}
</style>

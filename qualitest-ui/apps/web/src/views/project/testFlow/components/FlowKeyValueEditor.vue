<template>
  <div class="flow-kv-editor">
    <div class="flow-kv-editor__head">
      <span aria-hidden="true" class="flow-kv-editor__req flow-kv-editor__req--spacer" />
      <span v-for="col in columns" :key="col.field" class="flow-kv-editor__col">{{ col.label }}</span>
      <span aria-hidden="true" class="flow-kv-editor__col flow-kv-editor__col--action" />
    </div>
    <div v-for="(row, idx) in localRows" :key="idx" class="flow-kv-editor__row">
      <span
          v-if="isRequiredRow(row)"
          class="flow-kv-editor__req"
          title="入口标为建议必填；缺值不阻止开跑"
      >*</span>
      <span v-else class="flow-kv-editor__req flow-kv-editor__req--spacer" aria-hidden="true" />
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
 * 双列键值表编辑器（场景 inSeed 等）。
 * columns 定义列 field / label / placeholder；rows 为行对象数组。
 * requiredKeys：首列键名落在集合内时行首显示 *，仅作提示，不校验能否提交。
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
  /** 须标 * 的键名列表（通常来自场景入口 inSeedKeys.required） */
  requiredKeys: {
    type: Array,
    default: () => [],
  },
});

const emit = defineEmits(['update:rows']);

function emptyRow() {
  return Object.fromEntries(props.columns.map((col) => [col.field, '']));
}

/** 该行首列键名是否在 requiredKeys 中 */
function isRequiredRow(row) {
  const keyField = props.columns[0]?.field || 'key';
  const name = String(row?.[keyField] ?? '').trim();
  if (!name || !Array.isArray(props.requiredKeys)) return false;
  return props.requiredKeys.includes(name);
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

  .flow-kv-editor__head,
  .flow-kv-editor__row {
    grid-template-columns: 10px 1fr 1fr 28px;
  }
}

.flow-kv-editor__req {
  width: 10px;
  text-align: center;
  color: var(--el-color-danger, #f56c6c);
  font-weight: 600;
  line-height: 32px;
}

.flow-kv-editor__req--spacer {
  visibility: hidden;
}
</style>

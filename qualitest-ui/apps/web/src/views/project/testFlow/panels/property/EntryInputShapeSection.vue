<template>
  <div class="entry-input-shape">
    <div class="field">
      <label>入参形状 inputShape（无值声明）</label>
      <div class="field__hint">Body 可嵌套；Query / Headers 扁平。开放 HTTP 不符时平台 400</div>
    </div>

    <div class="entry-input-shape__block">
      <div class="entry-input-shape__title">Body（字段树）</div>
      <ShapeFieldRows
          :rows="bodyRows"
          :depth="0"
          nestable
          @change="onBodyChange"
      />
      <button type="button" class="btn btn--ghost" @click="addBodyRoot">+ Body 字段</button>
    </div>

    <div v-for="sec in flatSections" :key="sec.key" class="entry-input-shape__block">
      <div class="entry-input-shape__title">{{ sec.label }}</div>
      <ShapeFieldRows
          :rows="rows(sec.key)"
          :depth="0"
          @change="(next) => onFlatChange(sec.key, next)"
      />
      <button type="button" class="btn btn--ghost" @click="addFlat(sec.key)">+ {{ sec.label }} 字段</button>
    </div>
  </div>
</template>

<script setup>
/**
 * HTTP 入口属性：编辑 data.inputShape。
 * - body：可嵌套字段树（children），开放入站按树校验请求体。
 * - query / headers：扁平字段表，不嵌套。
 * 形状只声明键与约束，不存测值；测值来自入站请求。
 */
import { computed } from 'vue'

import ShapeFieldRows from './ShapeFieldRows.vue'

const props = defineProps({
  /** inputShape 对象：{ body, query, headers }，各为字段数组 */
  modelValue: { type: Object, default: null },
})

const emit = defineEmits(['update:modelValue'])

/** query、headers 两节标题与落盘键 */
const flatSections = [
  { key: 'query', label: 'Query' },
  { key: 'headers', label: 'Headers' },
]

/** 归一后的三节形状；缺省补空数组，避免模板空引用 */
const shape = computed(() => {
  const v = props.modelValue
  if (v && typeof v === 'object') return v
  return { body: [], query: [], headers: [] }
})

/** body 字段树当前行 */
const bodyRows = computed(() => (Array.isArray(shape.value.body) ? shape.value.body : []))

/** 按节名取扁平字段列表 */
function rows(key) {
  const list = shape.value[key]
  return Array.isArray(list) ? list : []
}

function emitShape(next) {
  emit('update:modelValue', next)
}

/**
 * 深拷贝三节后交给 mutator 改，再整包回写，避免直接改 props。
 */
function patchShape(mutator) {
  const cur = shape.value
  const base = {
    body: Array.isArray(cur.body) ? structuredCloneSafe(cur.body) : [],
    query: Array.isArray(cur.query) ? structuredCloneSafe(cur.query) : [],
    headers: Array.isArray(cur.headers) ? structuredCloneSafe(cur.headers) : [],
  }
  mutator(base)
  emitShape(base)
}

/** 深拷贝；structuredClone 失败时退回 JSON 序列化 */
function structuredCloneSafe(v) {
  try {
    return structuredClone(v)
  } catch {
    return JSON.parse(JSON.stringify(v))
  }
}

/** Body 树变更 */
function onBodyChange(nextRows) {
  patchShape((s) => {
    s.body = nextRows
  })
}

/** Query / Headers 扁平表变更 */
function onFlatChange(key, nextRows) {
  patchShape((s) => {
    s[key] = nextRows
  })
}

/** 在 body 根级追加空字段 */
function addBodyRoot() {
  patchShape((s) => {
    s.body = [...s.body, { name: '', type: 'text', required: false }]
  })
}

/** 在 query 或 headers 追加空字段 */
function addFlat(key) {
  patchShape((s) => {
    s[key] = [...s[key], { name: '', type: 'text', required: false }]
  })
}
</script>

<style scoped lang="scss">
@use '../../styles/propPanel.scss' as prop;
@include prop.flow-prop-panel-root;

.entry-input-shape__block {
  margin-bottom: 12px;
}

.entry-input-shape__title {
  font-size: 12px;
  font-weight: 600;
  margin-bottom: 6px;
}
</style>

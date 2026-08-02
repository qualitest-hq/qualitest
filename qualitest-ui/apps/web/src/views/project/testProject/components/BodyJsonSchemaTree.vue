<template>
  <div
      :class="[
        'body-json-schema-tree',
        {
          'body-json-schema-tree--compact': compact,
          'body-json-schema-tree--expand-scroll': expandWithParentScroll,
          'is-schema-pending': schemaApplyPending
        }
      ]"
  >
    <div v-if="!compact" class="schema-tree-toolbar">
      <span class="schema-tree-title">{{ panelTitle }}</span>
      <span class="schema-tree-ct">{{ contentTypeHint }}</span>
    </div>
    <div class="body-schema-kv-root" :class="{ 'body-schema-kv-root--virtual': useVirtualFlat }">
      <template v-if="useVirtualFlat">
        <div class="body-schema-kv-x">
          <div class="debug-kv-head body-schema-kv-grid" :style="gridStyle">
            <div class="debug-kv-cell debug-kv-cell--muted debug-kv-cell--chk"/>
            <div class="debug-kv-cell debug-kv-cell--muted">参数名</div>
            <div class="debug-kv-cell debug-kv-cell--muted">参数值</div>
            <div v-if="showExampleColumn" class="debug-kv-cell debug-kv-cell--muted">示例值</div>
            <div class="debug-kv-cell debug-kv-cell--muted">类型</div>
            <div class="debug-kv-cell debug-kv-cell--muted">说明</div>
            <div class="debug-kv-cell debug-kv-cell--muted debug-kv-cell--action"/>
          </div>
          <div
              ref="virtScrollRef"
              class="body-schema-kv-virt-scroll"
              @scroll.passive="scheduleVirtSlice"
          >
            <div :style="{ height: topVirtPad + 'px' }" aria-hidden="true" class="body-schema-kv-virt-pad"/>
            <div
                v-for="idx in virtVisibleIndices"
                :key="flatRows[idx].node.id"
                :style="[gridStyle, { minHeight: `${VIRTUAL_ROW_HEIGHT}px` }]"
                class="debug-kv-row body-schema-kv-grid body-schema-kv-row--virtual"
            >
              <div class="debug-kv-cell debug-kv-cell--chk"/>
              <div class="debug-kv-cell">
                <div :style="{ paddingLeft: `${flatRows[idx].depth * 14}px` }" class="body-schema-name-wrap">
                  <span v-if="flatRows[idx].node === rootNode" class="schema-root-tag">{{ rootLabel }}</span>
                  <el-input
                      v-else
                      v-model="flatRows[idx].node.key"
                      class="debug-kv-field"
                      placeholder="名称"
                      @change="emitSchema"
                  />
                </div>
              </div>
              <div class="debug-kv-cell">
                <el-input
                    v-if="!isStructureType(flatRows[idx].node.type)"
                    v-model="flatRows[idx].node.mock"
                    class="debug-kv-field"
                    placeholder="值"
                    @change="emitSchema"
                />
                <span v-else class="body-schema-na">—</span>
              </div>
              <div v-if="showExampleColumn" class="debug-kv-cell">
                <el-input
                    v-if="!isStructureType(flatRows[idx].node.type)"
                    v-model="flatRows[idx].node.example"
                    class="debug-kv-field"
                    placeholder="示例"
                    @change="emitSchema"
                />
                <span v-else class="body-schema-na">—</span>
              </div>
              <div class="debug-kv-cell debug-kv-cell--type">
                <DebugParamTypeCell
                    :allow-type-create="false"
                    :row="flatRows[idx].node"
                    :show-schema-gear="showSchemaGearForType(flatRows[idx].node.type)"
                    :type-class-fn="jsonBodyTypeSelectClass"
                    :type-options="SCHEMA_JSON_BODY_TYPES"
                    @open-schema="onOpenJsonTypeSchema(flatRows[idx].node)"
                    @toggle-required="onToggleJsonTypeRequired(flatRows[idx].node)"
                    @type-change="onTypeChange(flatRows[idx].node)"
                />
              </div>
              <div class="debug-kv-cell">
                <el-input
                    v-model="flatRows[idx].node.description"
                    class="debug-kv-field"
                    placeholder="说明"
                    @change="emitSchema"
                />
              </div>
              <div class="debug-kv-cell debug-kv-cell--action">
                <el-tooltip v-if="flatRows[idx].node.type === 'object'" content="添加子字段" placement="top">
                  <button
                      class="body-schema-icon-btn"
                      type="button"
                      @click="addChildProperty(flatRows[idx].node)"
                  >
                    <el-icon><CirclePlus/></el-icon>
                  </button>
                </el-tooltip>
                <el-tooltip
                    v-if="flatRows[idx].node.type === 'array' && !flatRows[idx].node.items"
                    content="元素类型"
                    placement="top"
                >
                  <button
                      class="body-schema-icon-btn"
                      type="button"
                      @click="ensureArrayItems(flatRows[idx].node)"
                  >
                    <el-icon><CirclePlus/></el-icon>
                  </button>
                </el-tooltip>
                <el-tooltip v-if="flatRows[idx].node !== rootNode" content="删除" placement="top">
                  <button class="debug-kv-remove" type="button" @click="removeNode(flatRows[idx].node)">
                    <el-icon><Delete/></el-icon>
                  </button>
                </el-tooltip>
              </div>
            </div>
            <div :style="{ height: bottomVirtPad + 'px' }" aria-hidden="true" class="body-schema-kv-virt-pad"/>
          </div>
        </div>
      </template>
      <template v-else>
        <div class="body-schema-kv-scroll">
          <div class="debug-kv-head body-schema-kv-grid" :style="gridStyle">
            <div class="debug-kv-cell debug-kv-cell--muted debug-kv-cell--chk"/>
            <div class="debug-kv-cell debug-kv-cell--muted">参数名</div>
            <div class="debug-kv-cell debug-kv-cell--muted">参数值</div>
            <div v-if="showExampleColumn" class="debug-kv-cell debug-kv-cell--muted">示例值</div>
            <div class="debug-kv-cell debug-kv-cell--muted">类型</div>
            <div class="debug-kv-cell debug-kv-cell--muted">说明</div>
            <div class="debug-kv-cell debug-kv-cell--muted debug-kv-cell--action"/>
          </div>
          <div
              v-for="item in flatRows"
              :key="item.node.id"
              class="debug-kv-row body-schema-kv-grid"
              :style="gridStyle"
          >
            <div class="debug-kv-cell debug-kv-cell--chk"/>
            <div class="debug-kv-cell">
              <div :style="{ paddingLeft: `${item.depth * 14}px` }" class="body-schema-name-wrap">
                <span v-if="item.node === rootNode" class="schema-root-tag">{{ rootLabel }}</span>
                <el-input
                    v-else
                    v-model="item.node.key"
                    class="debug-kv-field"
                    placeholder="名称"
                    @change="emitSchema"
                />
              </div>
            </div>
            <div class="debug-kv-cell">
              <el-input
                  v-if="!isStructureType(item.node.type)"
                  v-model="item.node.mock"
                  class="debug-kv-field"
                  placeholder="值"
                  @change="emitSchema"
              />
              <span v-else class="body-schema-na">—</span>
            </div>
            <div v-if="showExampleColumn" class="debug-kv-cell">
              <el-input
                  v-if="!isStructureType(item.node.type)"
                  v-model="item.node.example"
                  class="debug-kv-field"
                  placeholder="示例"
                  @change="emitSchema"
              />
              <span v-else class="body-schema-na">—</span>
            </div>
            <div class="debug-kv-cell debug-kv-cell--type">
              <DebugParamTypeCell
                  :allow-type-create="false"
                  :row="item.node"
                  :show-schema-gear="showSchemaGearForType(item.node.type)"
                  :type-class-fn="jsonBodyTypeSelectClass"
                  :type-options="SCHEMA_JSON_BODY_TYPES"
                  @open-schema="onOpenJsonTypeSchema(item.node)"
                  @toggle-required="onToggleJsonTypeRequired(item.node)"
                  @type-change="onTypeChange(item.node)"
              />
            </div>
            <div class="debug-kv-cell">
              <el-input
                  v-model="item.node.description"
                  class="debug-kv-field"
                  placeholder="说明"
                  @change="emitSchema"
              />
            </div>
            <div class="debug-kv-cell debug-kv-cell--action">
              <el-tooltip v-if="item.node.type === 'object'" content="添加子字段" placement="top">
                <button
                    class="body-schema-icon-btn"
                    type="button"
                    @click="addChildProperty(item.node)"
                >
                  <el-icon><CirclePlus/></el-icon>
                </button>
              </el-tooltip>
              <el-tooltip
                  v-if="item.node.type === 'array' && !item.node.items"
                  content="元素类型"
                  placement="top"
              >
                <button
                    class="body-schema-icon-btn"
                    type="button"
                    @click="ensureArrayItems(item.node)"
                >
                  <el-icon><CirclePlus/></el-icon>
                </button>
              </el-tooltip>
              <el-tooltip v-if="item.node !== rootNode" content="删除" placement="top">
                <button class="debug-kv-remove" type="button" @click="removeNode(item.node)">
                  <el-icon><Delete/></el-icon>
                </button>
              </el-tooltip>
            </div>
          </div>
        </div>
      </template>
    </div>
  </div>
</template>

<script setup>
import {CirclePlus, Delete} from '@element-plus/icons-vue'
import {useDebounceFn} from '@vueuse/core'
import {computed, nextTick, onBeforeUnmount, onMounted, ref, watch} from 'vue'
import DebugParamTypeCell from './DebugParamTypeCell.vue'
import {pruneConstraintsForType} from '@/views/project/testProject/utils/fieldTypeConstraints'
import {
  SCHEMA_JSON_BODY_TYPES,
  buildExampleFromSchemaDefaults,
  createDefaultChildProperty,
  createEmptyRootSchema,
  createRootUiNode,
  ensureObjectTrailingEmptyRowsDeep,
  flattenSchemaUiRows,
  isEmptyObjectSchema,
  schemaJsonToUiRoot,
  uiRootToSchemaJson
} from '@/views/project/testProject/utils/jsonSchemaTree'

const props = defineProps({
  modelValue: {
    type: Object,
    default: null
  },
  /** 为 true 时多「示例值」列，并序列化到 JSON Schema `example`（响应用） */
  showExampleColumn: {
    type: Boolean,
    default: false
  },
  panelTitle: {
    type: String,
    default: '数据结构'
  },
  contentTypeHint: {
    type: String,
    default: 'application/json'
  },
  /** body：请求体树；response：响应树（隐藏最外层 object 壳等） */
  treeMode: {
    type: String,
    default: 'body',
    validator: (v) => v === 'body' || v === 'response'
  },
  /** 调试页 Body→JSON：紧凑布局，隐藏内层标题条与外框 */
  compact: {
    type: Boolean,
    default: false
  },
  /** flatten 行数 ≥ 该值时虚拟渲染（0 关闭）。调试/设计内嵌请求体、响应配置等处可传更低阈值以提前启用。 */
  virtualMinFlatRows: {
    type: Number,
    default: 44
  },
  /** 为 true 时不限制内部列表高度，由外层页面滚动（如设计 Tab 响应结构） */
  expandWithParentScroll: {
    type: Boolean,
    default: false
  }
})

const emit = defineEmits(['update:modelValue', 'open-schema'])

/** 供 DebugParamTypeCell 使用的类型选择器样式类名 */
function jsonBodyTypeSelectClass(type) {
  const raw = type == null || type === '' ? 'string' : String(type)
  const safe = raw.toLowerCase().replace(/[^a-z0-9]/g, '') || 'custom'
  return ['param-type-select', `is-type-${safe}`]
}

function onToggleJsonTypeRequired(node) {
  if (!node || typeof node !== 'object') return
  node.required = node.required === true ? false : true
  emitSchema()
}

function onOpenJsonTypeSchema(node) {
  emit('open-schema', node)
}

const rootNode = ref(createRootUiNode({treeMode: props.treeMode}))
let syncingFromParent = false

/** 大体量 Schema 在 requestIdleCallback 中构建，避免切换 API 页签时长时间占用主线程 */
const schemaApplyPending = ref(false)
let applyGen = 0
let idleApplyId = null

function cancelScheduledSchemaApply() {
  if (idleApplyId != null && typeof cancelIdleCallback !== 'undefined') {
    cancelIdleCallback(idleApplyId)
  }
  idleApplyId = null
}

/** 不 stringify 全量 JSON，仅估算节点规模；用于判断是否延后构建 */
function roughSchemaPayloadWeight(v) {
  if (v == null || typeof v !== 'object') return 0
  function walk(o, depth) {
    if (depth > 28) return 500
    if (o == null || typeof o !== 'object') return 1
    let n = 2
    if (Array.isArray(o)) {
      for (const x of o) n += walk(x, depth + 1)
      return n
    }
    const keys = Object.keys(o)
    for (const k of keys) {
      n += 1 + walk(o[k], depth + 1)
    }
    return n
  }
  return walk(v, 0)
}

const HEAVY_SCHEMA_WEIGHT = 520

const rootLabel = computed(() => (props.treeMode === 'response' ? '结构根' : '根节点'))

const flatRows = computed(() =>
    flattenSchemaUiRows(rootNode.value, {
      hideRootObjectShell:
          props.treeMode === 'response' &&
          String(rootNode.value?.type || '').toLowerCase() === 'object',
      unwrapOuterDataWrapper: props.treeMode === 'response'
    })
)

const gridStyle = computed(() => {
  const cols = ['44px', 'minmax(108px, 1fr)', 'minmax(108px, 1.15fr)']
  if (props.showExampleColumn) cols.push('minmax(96px, 1fr)')
  /* 操作列略宽；类型列固定 168px 与调试区 KV 表对齐 */
  cols.push('168px', 'minmax(88px, 1fr)', '80px')
  return {gridTemplateColumns: cols.join(' ')}
})

const VIRTUAL_ROW_HEIGHT = 56
const VIRTUAL_BUFFER_ROWS = 12

const virtScrollRef = ref(null)
const virtSliceStart = ref(0)
const virtSliceEnd = ref(0)

const useVirtualFlat = computed(() =>
    props.virtualMinFlatRows > 0 && flatRows.value.length >= props.virtualMinFlatRows
)

const virtVisibleIndices = computed(() => {
  const n = flatRows.value.length
  const a = Math.min(virtSliceStart.value, n)
  const b = Math.min(virtSliceEnd.value, n)
  const out = []
  for (let i = a; i < b; i++) out.push(i)
  return out
})

const topVirtPad = computed(() => Math.min(virtSliceStart.value, flatRows.value.length) * VIRTUAL_ROW_HEIGHT)

const bottomVirtPad = computed(() =>
    Math.max(0, flatRows.value.length - virtSliceEnd.value) * VIRTUAL_ROW_HEIGHT
)

function syncVirtSlice() {
  if (!useVirtualFlat.value) return
  const el = virtScrollRef.value
  const n = flatRows.value.length
  if (n === 0) {
    virtSliceStart.value = 0
    virtSliceEnd.value = 0
    return
  }
  const st = el ? el.scrollTop : 0
  const ch = el && el.clientHeight > 0 ? el.clientHeight : 480
  const first = Math.max(0, Math.floor(st / VIRTUAL_ROW_HEIGHT))
  const visibleCount = Math.max(1, Math.ceil(ch / VIRTUAL_ROW_HEIGHT)) + VIRTUAL_BUFFER_ROWS * 2
  virtSliceStart.value = Math.max(0, first - VIRTUAL_BUFFER_ROWS)
  virtSliceEnd.value = Math.min(n, first + visibleCount)
}

let virtScrollRafId = 0
function scheduleVirtSlice() {
  if (virtScrollRafId) return
  virtScrollRafId = requestAnimationFrame(() => {
    virtScrollRafId = 0
    syncVirtSlice()
  })
}

watch(useVirtualFlat, (v) => {
  if (v) {
    nextTick(() => {
      if (virtScrollRef.value) virtScrollRef.value.scrollTop = 0
      syncVirtSlice()
    })
  }
})

watch(() => flatRows.value.length, () => {
  nextTick(syncVirtSlice)
})

onMounted(() => {
  if (useVirtualFlat.value) nextTick(syncVirtSlice)
})

function syncApplyIncomingModelValue(v) {
  const incoming =
      v && typeof v === 'object' ? JSON.stringify(v) : ''
  const built = uiRootToSchemaJson(rootNode.value, {includeExample: props.showExampleColumn})
  const current = JSON.stringify(built)
  if (incoming === current) return
  if (v == null || v === undefined) {
    const emptyObj = JSON.stringify(
        uiRootToSchemaJson(createRootUiNode({treeMode: props.treeMode}), {
          includeExample: props.showExampleColumn
        })
    )
    if (current === emptyObj) return
  }
  syncingFromParent = true
  rootNode.value = schemaJsonToUiRoot(v || createEmptyRootSchema(), {
    mergeExampleIntoMock: !props.showExampleColumn,
    treeMode: props.treeMode
  })
  nextTick(() => {
    syncingFromParent = false
  })
}

watch(
    () => props.modelValue,
    (v) => {
      const gen = ++applyGen
      cancelScheduledSchemaApply()

      const heavy =
          roughSchemaPayloadWeight(v) > HEAVY_SCHEMA_WEIGHT &&
          typeof requestIdleCallback !== 'undefined'

      if (heavy) {
        schemaApplyPending.value = true
        syncingFromParent = true
        rootNode.value = schemaJsonToUiRoot(createEmptyRootSchema(), {
          mergeExampleIntoMock: !props.showExampleColumn,
          treeMode: props.treeMode
        })
        nextTick(() => {
          syncingFromParent = false
        })
        idleApplyId = requestIdleCallback(
            () => {
              idleApplyId = null
              if (gen !== applyGen) return
              try {
                syncApplyIncomingModelValue(v)
              } finally {
                schemaApplyPending.value = false
              }
            },
            {timeout: 500}
        )
      } else {
        schemaApplyPending.value = false
        syncApplyIncomingModelValue(v)
      }
    },
    {immediate: true}
)

onBeforeUnmount(() => {
  if (virtScrollRafId) {
    cancelAnimationFrame(virtScrollRafId)
    virtScrollRafId = 0
  }
  cancelScheduledSchemaApply()
})

function isStructureType(t) {
  const x = String(t || '').toLowerCase()
  return x === 'object' || x === 'array'
}

function showSchemaGearForType(t) {
  return String(t || '').toLowerCase() !== 'object'
}

function runEmitSchema() {
  if (syncingFromParent) return
  ensureObjectTrailingEmptyRowsDeep(rootNode.value, props.treeMode)
  const out = uiRootToSchemaJson(rootNode.value, {includeExample: props.showExampleColumn})
  if (props.modelValue == null && isEmptyObjectSchema(out)) {
    return
  }
  emit('update:modelValue', out)
}

const debouncedEmitSchema = useDebounceFn(runEmitSchema, 48)

function emitSchema() {
  debouncedEmitSchema()
}

/** 弹窗关闭等场景需立即把当前树写回父级，避免 debounce 丢最后一次更新 */
function flushEmitSchema() {
  debouncedEmitSchema.cancel()
  runEmitSchema()
}

function onTypeChange(node) {
  const t = String(node.type || '').toLowerCase()
  if (t === 'object') {
    node.items = null
    node.children = []
  } else if (t === 'array') {
    node.children = []
    if (!node.items) {
      const leaf = createDefaultChildProperty()
      leaf.type = 'string'
      node.items = leaf
    }
  } else {
    node.children = []
    node.items = null
  }
  delete node.enum
  delete node.const
  pruneConstraintsForType(node, node.type, { flatParam: false })
  emitSchema()
}

function addChildProperty(node) {
  if (node.type !== 'object') return
  if (!Array.isArray(node.children)) node.children = []
  node.children.push(createDefaultChildProperty())
  emitSchema()
}

function ensureArrayItems(node) {
  if (node.type !== 'array') return
  const leaf = createDefaultChildProperty()
  leaf.type = 'string'
  node.items = leaf
  emitSchema()
}

function removeNode(target) {
  if (!removeNodeById(rootNode.value, target.id)) return
  emitSchema()
}

function removeNodeById(root, id) {
  if (!root || !id) return false
  if (root.id === id) return false
  if (root.type === 'object' && Array.isArray(root.children)) {
    const i = root.children.findIndex((c) => c.id === id)
    if (i >= 0) {
      root.children.splice(i, 1)
      return true
    }
    for (const c of root.children) {
      if (removeNodeById(c, id)) return true
    }
  }
  if (root.type === 'array' && root.items) {
    if (root.items.id === id) {
      root.items = null
      return true
    }
    if (removeNodeById(root.items, id)) return true
  }
  return false
}

/**
 * 调试发送用：直接从当前树（含未 debounce 的参数值）生成 example 片段，
 * 不依赖父级 v-model 是否已写回。
 */
function buildDebugExampleFromTree() {
  if (!rootNode.value) return undefined
  const schema = uiRootToSchemaJson(rootNode.value, {includeExample: props.showExampleColumn})
  return buildExampleFromSchemaDefaults(schema, {onlyExplicitDefaults: true})
}

defineExpose({emitSchema: flushEmitSchema, buildDebugExampleFromTree})
</script>

<style lang="scss" scoped>
.body-json-schema-tree {
  display: flex;
  flex-direction: column;
  min-height: 200px;
  background: var(--pd-surface-elevated, #fff);
  border: 1px solid var(--pd-divider, #dbe8f4);
  border-radius: 0;

  &.is-schema-pending .body-schema-kv-scroll,
  &.is-schema-pending .body-schema-kv-virt-scroll {
    opacity: 0.55;
    pointer-events: none;
    transition: opacity 0.15s ease;
  }

  &--compact {
    min-height: 0;
    border: none;
    background: transparent;
  }

  /** 取消 min(56vh,520px) 内层滚动，整表随内容增高，由设计页 .design-scroll 滚动 */
  &--expand-scroll {
    .body-schema-kv-scroll {
      max-height: none;
      overflow-y: visible;
      overflow-x: auto;
      scrollbar-gutter: auto;
      content-visibility: visible;
      contain-intrinsic-size: unset;
    }

    .body-schema-kv-root--virtual .body-schema-kv-virt-scroll {
      max-height: none;
      overflow-y: visible;
      overflow-x: hidden;
      scrollbar-gutter: auto;
    }
  }
}

.schema-tree-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 10px;
  background: var(--pd-bg-sunken, #e9f2fc);
  border-bottom: 1px solid var(--pd-divider, #dbe8f4);
  font-size: 13px;
  font-weight: 600;
  color: var(--pd-text, #0f172a);
}

.schema-tree-ct {
  font-size: 12px;
  font-weight: 500;
  color: var(--pd-text-muted, #5a6b86);
}

.body-schema-kv-root {
  display: flex;
  flex-direction: column;
  min-height: 0;
}

.body-schema-kv-root--virtual {
  .body-schema-kv-x {
    overflow-x: auto;
    min-width: 0;
  }

  .body-schema-kv-virt-scroll {
    max-height: min(56vh, 520px);
    overflow-x: hidden;
    overflow-y: auto;
    scrollbar-gutter: stable;
    -webkit-overflow-scrolling: touch;
    overscroll-behavior: contain;
    transform: translateZ(0);
  }

  .body-schema-kv-virt-pad {
    width: 100%;
    flex-shrink: 0;
  }
}

.body-schema-kv-scroll {
  overflow-x: auto;
  overflow-y: auto;
  scrollbar-gutter: stable;
  max-height: min(56vh, 520px);
  content-visibility: auto;
  contain-intrinsic-size: 1px 320px;
}

.body-schema-kv-grid,
.debug-kv-head,
.debug-kv-row {
  display: grid;
  align-items: stretch;
  min-width: 640px;
}

.debug-kv-head {
  position: sticky;
  top: 0;
  z-index: 1;
  background: var(--pd-bg-sunken, rgba(233, 242, 252, 0.65));
  border-bottom: 1px solid var(--pd-divider, var(--pd-border-muted));
  font-size: 12px;
  font-weight: 600;
  letter-spacing: 0.02em;
  color: var(--pd-text-muted);
}

.debug-kv-row {
  border-bottom: 1px solid var(--pd-divider, var(--pd-border-muted));
  transition: background 0.12s ease;

  &.body-schema-kv-row--virtual {
    align-items: center;
  }

  &:hover {
    background: rgba(11, 110, 220, 0.06);
  }
}

.debug-kv-cell {
  padding: 8px 8px;
  display: flex;
  align-items: center;
  min-width: 0;

  &--chk {
    justify-content: center;
    padding-left: 2px;
    padding-right: 4px;
  }

  &--type {
    align-items: stretch;
  }

  &--action {
    justify-content: center;
    align-items: center;
    gap: 4px;
    flex-wrap: nowrap;
    padding-left: 4px;
    padding-right: 4px;

    :deep(.el-tooltip__trigger) {
      display: inline-flex;
      align-items: center;
      flex-shrink: 0;
    }
  }

  &--muted {
    padding-top: 10px;
    padding-bottom: 10px;
  }
}

.body-schema-name-wrap {
  display: flex;
  align-items: center;
  width: 100%;
  min-height: 30px;
}

.schema-root-tag {
  display: inline-block;
  padding: 2px 8px;
  border-radius: 4px;
  background: rgba(124, 58, 237, 0.12);
  color: #6d28d9;
  font-weight: 600;
  font-size: 12px;
}

.body-schema-na {
  font-size: 12px;
  color: var(--pd-text-muted);
}

.debug-kv-field {
  width: 100%;

  :deep(.el-input__wrapper),
  :deep(.el-textarea__inner) {
    box-shadow: none;
    background: transparent;
  }

  :deep(.el-input__wrapper) {
    border-radius: 6px;
    border: 1px solid transparent;
    transition: border-color 0.15s ease, background 0.15s ease;

    &:hover {
      background: rgba(15, 23, 42, 0.03);
    }

    &.is-focus {
      background: var(--pd-surface-elevated);
      box-shadow: 0 0 0 1px var(--pd-primary) inset;
    }
  }
}

.body-schema-icon-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  width: 32px;
  height: 32px;
  margin: 0;
  padding: 0;
  border: none;
  border-radius: 6px;
  background: transparent;
  color: var(--pd-primary, #0b6edc);
  cursor: pointer;
  transition: color 0.15s ease, background 0.15s ease;

  &:hover {
    background: rgba(11, 110, 220, 0.1);
  }

  :deep(.el-icon) {
    font-size: 16px;
  }
}

.debug-kv-remove {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  width: 32px;
  height: 32px;
  margin: 0;
  padding: 0;
  border: none;
  border-radius: 6px;
  background: transparent;
  color: var(--pd-text-muted);
  cursor: pointer;
  transition: color 0.15s ease, background 0.15s ease;

  &:hover {
    color: #dc2626;
    background: rgba(220, 38, 38, 0.08);
  }

  :deep(.el-icon) {
    font-size: 16px;
  }
}
</style>

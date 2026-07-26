<template>
  <div ref="wrapRef" class="doc-preview-data-table">
    <el-table-v2
        v-if="useV2"
        :key="'v2-' + variant"
        :cache="10"
        :columns="v2Columns"
        :data="v2Rows"
        :header-height="38"
        :height="v2Height"
        :row-height="42"
        :width="v2Width"
        class="doc-table-v2"
        row-key="_rk"
    />
    <el-table
        v-else-if="variant === 'kv4'"
        :key="'v1-kv4'"
        :class="tableClass"
        :data="displayedRows"
        border
        size="small"
        stripe
    >
      <el-table-column label="名称" min-width="120" prop="name" show-overflow-tooltip/>
      <el-table-column align="center" label="必填" width="72">
        <template #default="scope">
          <span :class="['doc-required-tag', scope.row.required ? 'is-yes' : 'is-no']">
            {{ scope.row.required ? '是' : '否' }}
          </span>
        </template>
      </el-table-column>
      <el-table-column label="说明" min-width="100" prop="description" show-overflow-tooltip/>
      <el-table-column label="示例值" min-width="120" prop="value" show-overflow-tooltip/>
    </el-table>
    <el-table
        v-else-if="variant === 'kv5'"
        :key="'v1-kv5'"
        :class="tableClass"
        :data="displayedRows"
        border
        size="small"
        stripe
    >
      <el-table-column label="名称" min-width="120" prop="name" show-overflow-tooltip/>
      <el-table-column label="类型" prop="type" width="100"/>
      <el-table-column align="center" label="必填" width="72">
        <template #default="scope">
          <span :class="['doc-required-tag', scope.row.required ? 'is-yes' : 'is-no']">
            {{ scope.row.required ? '是' : '否' }}
          </span>
        </template>
      </el-table-column>
      <el-table-column label="说明" min-width="100" prop="description" show-overflow-tooltip/>
      <el-table-column label="示例值" min-width="120" prop="value" show-overflow-tooltip/>
    </el-table>
    <template v-else>
      <el-table
          key="v1-schema"
          :class="tableClass"
          :data="displayedRows"
          border
          size="small"
          stripe
      >
        <el-table-column label="字段" min-width="160">
          <template #default="scope">
            <span
                :style="{ paddingLeft: scope.row.depth * 14 + 'px' }"
                class="doc-schema-field"
            >{{ scope.row.fieldLabel }}</span>
          </template>
        </el-table-column>
        <el-table-column label="类型" prop="type" width="100"/>
        <el-table-column align="center" label="必填" width="72">
          <template #default="scope">
            <span :class="['doc-required-tag', scope.row.required ? 'is-yes' : 'is-no']">
              {{ scope.row.required ? '是' : '否' }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="说明" min-width="160" prop="description" show-overflow-tooltip/>
      </el-table>
      <div
          v-if="expandFlow && hasMoreRows"
          ref="loadMoreSentinel"
          class="doc-table-load-sentinel"
          aria-hidden="true"
      >
        <span class="doc-table-load-hint">正在加载更多字段…（{{ displayedRows.length }} / {{ totalRows }}）</span>
      </div>
    </template>
  </div>
</template>

<script setup>
import {computed, h, nextTick, onBeforeUnmount, onMounted, ref, watch} from 'vue'

const props = defineProps({
  /** 只读行数据（与原先 el-table :data 一致） */
  rows: {type: Array, required: true},
  /** kv4：名称/必填/说明/示例值；kv5 多类型列；schema：字段/类型/必填/说明 */
  variant: {
    type: String,
    required: true,
    validator: (v) => ['kv4', 'kv5', 'schema'].includes(v)
  },
  /**
   * 文档「数据结构」等：随页面流展开，不固定视口高度；
   * 超出 lazyChunkSize 的行在滚入视口时分批渲染。
   */
  expandFlow: {type: Boolean, default: false},
  /** expandFlow 时每批追加渲染的行数 */
  lazyChunkSize: {type: Number, default: 40},
  /** 行数达到该值时使用 Table V2 虚拟表（固定视口、内层滚动）；expandFlow 时无效 */
  virtualThreshold: {type: Number, default: 48},
  /** 小表模式下的 class（如 doc-kv-table、doc-schema-table） */
  tableClass: {type: String, default: 'doc-kv-table'}
})

const wrapRef = ref(null)
const loadMoreSentinel = ref(null)
const v2Width = ref(880)
const visibleCount = ref(0)

const totalRows = computed(() => props.rows?.length || 0)

const useV2 = computed(
    () =>
        !props.expandFlow &&
        totalRows.value >= props.virtualThreshold &&
        props.virtualThreshold > 0
)

const v2Height = 420

const displayedRows = computed(() => {
  const rows = props.rows || []
  if (!props.expandFlow) return rows
  return rows.slice(0, visibleCount.value)
})

const hasMoreRows = computed(() => props.expandFlow && visibleCount.value < totalRows.value)

const v2Rows = computed(() =>
    (props.rows || []).map((row, i) => ({
      ...row,
      _rk: `${props.variant}-${i}`
    }))
)

function resetVisibleCount() {
  const total = totalRows.value
  if (!props.expandFlow || total === 0) {
    visibleCount.value = total
    return
  }
  visibleCount.value = Math.min(props.lazyChunkSize, total)
}

function loadMoreRows() {
  if (!hasMoreRows.value) return
  visibleCount.value = Math.min(visibleCount.value + props.lazyChunkSize, totalRows.value)
}

function cellEllipsis(text) {
  const s = text != null ? String(text) : ''
  return h('span', {class: 'doc-v2-cell', title: s}, s)
}

function requiredCell(rowData) {
  return h(
      'span',
      {class: ['doc-required-tag', rowData.required ? 'is-yes' : 'is-no']},
      rowData.required ? '是' : '否'
  )
}

const v2Columns = computed(() => {
  const v = props.variant
  if (v === 'kv4') {
    return [
      {
        key: 'name',
        dataKey: 'name',
        title: '名称',
        width: 168,
        cellRenderer: ({rowData}) => cellEllipsis(rowData.name)
      },
      {
        key: 'required',
        dataKey: 'required',
        title: '必填',
        width: 72,
        align: 'center',
        cellRenderer: ({rowData}) => requiredCell(rowData)
      },
      {
        key: 'description',
        dataKey: 'description',
        title: '说明',
        width: 240,
        cellRenderer: ({rowData}) => cellEllipsis(rowData.description)
      },
      {
        key: 'value',
        dataKey: 'value',
        title: '示例值',
        width: 200,
        cellRenderer: ({rowData}) => cellEllipsis(rowData.value)
      }
    ]
  }
  if (v === 'kv5') {
    return [
      {
        key: 'name',
        dataKey: 'name',
        title: '名称',
        width: 168,
        cellRenderer: ({rowData}) => cellEllipsis(rowData.name)
      },
      {
        key: 'type',
        dataKey: 'type',
        title: '类型',
        width: 100,
        cellRenderer: ({rowData}) => cellEllipsis(rowData.type)
      },
      {
        key: 'required',
        dataKey: 'required',
        title: '必填',
        width: 72,
        align: 'center',
        cellRenderer: ({rowData}) => requiredCell(rowData)
      },
      {
        key: 'description',
        dataKey: 'description',
        title: '说明',
        width: 220,
        cellRenderer: ({rowData}) => cellEllipsis(rowData.description)
      },
      {
        key: 'value',
        dataKey: 'value',
        title: '示例值',
        width: 200,
        cellRenderer: ({rowData}) => cellEllipsis(rowData.value)
      }
    ]
  }
  return [
    {
      key: 'field',
      dataKey: 'fieldLabel',
      title: '字段',
      width: 280,
      cellRenderer: ({rowData}) =>
          h(
              'span',
              {
                class: 'doc-schema-field doc-v2-cell',
                style: {paddingLeft: `${(rowData.depth || 0) * 14}px`},
                title: String(rowData.fieldLabel ?? '')
              },
              String(rowData.fieldLabel ?? '')
          )
    },
    {
      key: 'type',
      dataKey: 'type',
      title: '类型',
      width: 100,
      cellRenderer: ({rowData}) => cellEllipsis(rowData.type)
    },
    {
      key: 'required',
      dataKey: 'required',
      title: '必填',
      width: 72,
      align: 'center',
      cellRenderer: ({rowData}) => requiredCell(rowData)
    },
    {
      key: 'description',
      dataKey: 'description',
      title: '说明',
      width: 320,
      cellRenderer: ({rowData}) => cellEllipsis(rowData.description)
    }
  ]
})

let resizeObserver = null
let lazyLoadObserver = null

function measureWrap() {
  const el = wrapRef.value
  if (!el) return
  const w = el.clientWidth
  if (w > 240) v2Width.value = Math.floor(w)
}

function disconnectLazyObserver() {
  lazyLoadObserver?.disconnect()
  lazyLoadObserver = null
}

function scheduleBackgroundChunks() {
  if (!props.expandFlow || !hasMoreRows.value) return
  const run = () => {
    if (!hasMoreRows.value) return
    loadMoreRows()
    if (hasMoreRows.value) {
      if (typeof requestIdleCallback === 'function') {
        requestIdleCallback(run, {timeout: 150})
      } else {
        setTimeout(run, 0)
      }
    }
  }
  run()
}

function bindLazyObserver() {
  disconnectLazyObserver()
  if (!props.expandFlow) return

  if (typeof IntersectionObserver === 'undefined') {
    scheduleBackgroundChunks()
    return
  }

  lazyLoadObserver = new IntersectionObserver(
      (entries) => {
        if (entries.some((e) => e.isIntersecting)) loadMoreRows()
      },
      {root: null, rootMargin: '240px 0px', threshold: 0}
  )

  const el = loadMoreSentinel.value
  if (el) lazyLoadObserver.observe(el)
}

watch(
    () => props.rows,
    () => {
      resetVisibleCount()
      nextTick(bindLazyObserver)
    },
    {deep: true}
)

watch(
    () => props.expandFlow,
    () => {
      resetVisibleCount()
      nextTick(bindLazyObserver)
    }
)

watch(hasMoreRows, () => nextTick(bindLazyObserver))

watch(loadMoreSentinel, () => nextTick(bindLazyObserver))

onMounted(() => {
  resetVisibleCount()
  measureWrap()
  resizeObserver = typeof ResizeObserver !== 'undefined' ? new ResizeObserver(measureWrap) : null
  if (resizeObserver && wrapRef.value) resizeObserver.observe(wrapRef.value)
  nextTick(bindLazyObserver)
})

watch(useV2, (v) => {
  if (v) nextTick(measureWrap)
})

onBeforeUnmount(() => {
  resizeObserver?.disconnect()
  resizeObserver = null
  disconnectLazyObserver()
})
</script>

<style lang="scss" scoped>
.doc-preview-data-table {
  width: 100%;
  min-width: 0;
}

.doc-kv-table,
.doc-schema-table {
  width: 100%;
}

.doc-v2-cell {
  display: inline-block;
  max-width: 100%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  vertical-align: middle;
  font-size: 12px;
}

.doc-schema-field {
  font-family: ui-monospace, monospace;
  font-size: 12px;
}

.doc-table-v2 {
  border: 1px solid var(--pd-border-subtle, var(--pd-divider, var(--el-border-color-lighter)));
  border-radius: var(--pd-radius-sm, 8px);
  overflow: hidden;
  box-shadow: 0 1px 2px rgba(20, 60, 120, 0.04);
}

.doc-table-load-sentinel {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 36px;
  padding: 8px 12px;
  border: 1px solid var(--pd-border-muted, #d6e6f5);
  border-top: none;
  border-radius: 0 0 var(--pd-radius-sm, 8px) var(--pd-radius-sm, 8px);
  background: var(--pd-bg-sunken, #e9f2fc);
}

.doc-table-load-hint {
  font-size: 12px;
  color: var(--pd-text-muted, #5a6b86);
}

.doc-required-tag {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 28px;
  padding: 1px 6px;
  border-radius: 4px;
  font-size: 11px;
  font-weight: 600;
  line-height: 1.4;

  &.is-yes {
    color: #047857;
    background: #ecfdf5;
    border: 1px solid #a7f3d0;
  }

  &.is-no {
    color: #64748b;
    background: #f8fafc;
    border: 1px solid #e2e8f0;
  }
}
</style>

<template>
  <div
      class="debug-kv-sheet"
      :class="{
        'debug-kv-sheet--virtual': useVirtual,
        'debug-kv-sheet--boxed-edit': borderedEditableFields
      }"
  >
    <!-- 大行数：表头固定，仅视口内渲染行，数据仍在 props.rows 中完整保留 -->
    <template v-if="useVirtual">
      <div class="debug-kv-outer-x">
        <div class="debug-kv-head" :style="gridStyle">
          <div class="debug-kv-cell debug-kv-cell--muted debug-kv-cell--chk"/>
          <div class="debug-kv-cell debug-kv-cell--muted">{{ nameLabel }}</div>
          <div v-if="showRemarkColumn" class="debug-kv-cell debug-kv-cell--muted">{{ remarkLabel }}</div>
          <div class="debug-kv-cell debug-kv-cell--muted">{{ valueLabel }}</div>
          <div v-if="showExampleColumn" class="debug-kv-cell debug-kv-cell--muted">{{ exampleLabel }}</div>
          <div v-if="showTypeColumn" class="debug-kv-cell debug-kv-cell--muted">类型</div>
          <div v-if="showDescriptionColumn" class="debug-kv-cell debug-kv-cell--muted">{{ descriptionLabel }}</div>
          <div class="debug-kv-cell debug-kv-cell--muted debug-kv-cell--action"/>
        </div>
        <div
            ref="virtualScrollRef"
            class="debug-kv-virtual-scroll"
            @scroll.passive="scheduleVirtualSlice"
        >
          <div :style="{ height: topPadPx + 'px' }" class="debug-kv-virtual-gutter" aria-hidden="true"/>
          <div
              v-for="index in visibleIndices"
              :key="'kv-' + index"
              class="debug-kv-row debug-kv-row--virtual"
              :style="{ ...gridStyle, minHeight: VIRTUAL_ROW_HEIGHT + 'px' }"
          >
            <div class="debug-kv-cell debug-kv-cell--chk">
              <el-checkbox v-model="rows[index]._enabled"/>
            </div>
            <div class="debug-kv-cell" :style="keyCellStyle(rows[index])">
              <el-input
                  v-if="!isKeyDisabled(rows[index])"
                  v-model="rows[index][keyColumnField]"
                  :placeholder="namePlaceholder"
                  class="debug-kv-field"
              />
            </div>
            <div v-if="showRemarkColumn" class="debug-kv-cell debug-kv-cell--remark">
              <div
                  v-if="isRemarkEditable(rows[index])"
                  :class="remarkExpandable ? 'debug-kv-remark-wrap' : undefined"
              >
                <el-input
                    v-model="rows[index].remark"
                    :placeholder="remarkPlaceholder"
                    :class="remarkExpandable ? 'debug-kv-field debug-kv-remark-inline' : 'debug-kv-field'"
                    :title="remarkExpandable && rows[index].remark ? String(rows[index].remark) : undefined"
                />
                <el-popover
                    v-if="remarkExpandable"
                    :visible="openRemarkRowIndex === index"
                    :width="400"
                    placement="bottom-end"
                    popper-class="debug-kv-remark-popper"
                    trigger="manual"
                    :teleported="true"
                    @update:visible="(v) => onRemarkPopoverVisible(index, v)"
                >
                  <div class="debug-kv-remark-popover">
                    <div class="debug-kv-remark-popover-title">{{ remarkLabel }}</div>
                    <el-input
                        v-model="rows[index].remark"
                        :autosize="{ minRows: 4, maxRows: 14 }"
                        :placeholder="remarkPlaceholder"
                        class="debug-kv-field"
                        resize="vertical"
                        type="textarea"
                    />
                  </div>
                  <template #reference>
                    <button
                        aria-label="展开编辑备注"
                        class="debug-kv-remark-expand"
                        title="展开编辑备注"
                        type="button"
                        @click="toggleRemarkPopover(index)"
                    >
                      <el-icon><FullScreen/></el-icon>
                    </button>
                  </template>
                </el-popover>
              </div>
            </div>
            <div class="debug-kv-cell">
              <div v-if="shouldShowFileUpload(rows[index])" class="debug-kv-file-value">
                <input
                    :ref="el => setFileInputRef(index, el)"
                    class="debug-kv-file-native"
                    tabindex="-1"
                    type="file"
                    @change="onFileInputChange(rows[index], $event)"
                />
                <el-button
                    :loading="!!rows[index]._uploading"
                    class="debug-kv-file-btn"
                    size="small"
                    type="primary"
                    @click="triggerFilePick(index)"
                >
                  选择文件
                </el-button>
                <span :title="fileRowLabel(rows[index])" class="debug-kv-file-name">{{ fileRowLabel(rows[index]) }}</span>
                <el-button
                    v-if="rows[index]._file || (rows[index].value && String(rows[index].value).trim())"
                    :disabled="!!rows[index]._uploading"
                    class="debug-kv-file-clear"
                    link
                    size="small"
                    type="danger"
                    @click="clearFileRow(rows[index])"
                >
                  清除
                </el-button>
              </div>
              <el-input
                  v-else-if="!isValueDisabled(rows[index]) && !valueMultiline"
                  v-model="rows[index].value"
                  :placeholder="valuePlaceholder"
                  class="debug-kv-field"
              />
              <el-input
                  v-else-if="!isValueDisabled(rows[index])"
                  v-model="rows[index].value"
                  :autosize="{ minRows: 2, maxRows: 8 }"
                  :placeholder="valuePlaceholder"
                  class="debug-kv-field"
                  type="textarea"
              />
            </div>
            <div v-if="showExampleColumn" class="debug-kv-cell">
              <el-input
                  v-model="rows[index].exampleValue"
                  :placeholder="examplePlaceholder"
                  class="debug-kv-field"
              />
            </div>
            <div v-if="showTypeColumn" class="debug-kv-cell debug-kv-cell--type">
              <slot :index="index" :row="rows[index]" name="type"/>
            </div>
            <div v-if="showDescriptionColumn" class="debug-kv-cell">
              <el-input
                  v-model="rows[index].description"
                  :placeholder="descriptionPlaceholder"
                  class="debug-kv-field"
              />
            </div>
            <div class="debug-kv-cell debug-kv-cell--action debug-kv-cell--actions">
              <el-tooltip
                  v-if="canAddChild(rows[index])"
                  content="添加子项"
                  placement="top"
              >
                <button
                    class="debug-kv-add-child"
                    type="button"
                    @click="$emit('add-child', index)"
                >
                  <el-icon><CirclePlus/></el-icon>
                </button>
              </el-tooltip>
              <el-tooltip content="删除" placement="top">
                <button
                    class="debug-kv-remove"
                    type="button"
                    @click="$emit('remove', index)"
                >
                  <el-icon><Delete/></el-icon>
                </button>
              </el-tooltip>
            </div>
          </div>
          <div :style="{ height: bottomPadPx + 'px' }" class="debug-kv-virtual-gutter" aria-hidden="true"/>
        </div>
      </div>
    </template>

    <template v-else>
      <div class="debug-kv-scroll">
        <div class="debug-kv-head" :style="gridStyle">
          <div class="debug-kv-cell debug-kv-cell--muted debug-kv-cell--chk"/>
          <div class="debug-kv-cell debug-kv-cell--muted">{{ nameLabel }}</div>
          <div v-if="showRemarkColumn" class="debug-kv-cell debug-kv-cell--muted">{{ remarkLabel }}</div>
          <div class="debug-kv-cell debug-kv-cell--muted">{{ valueLabel }}</div>
          <div v-if="showExampleColumn" class="debug-kv-cell debug-kv-cell--muted">{{ exampleLabel }}</div>
          <div v-if="showTypeColumn" class="debug-kv-cell debug-kv-cell--muted">类型</div>
          <div v-if="showDescriptionColumn" class="debug-kv-cell debug-kv-cell--muted">{{ descriptionLabel }}</div>
          <div class="debug-kv-cell debug-kv-cell--muted debug-kv-cell--action"/>
        </div>
        <div
            v-for="(row, index) in rows"
            :key="index"
            class="debug-kv-row"
            :style="gridStyle"
        >
          <div class="debug-kv-cell debug-kv-cell--chk">
            <el-checkbox v-model="row._enabled"/>
          </div>
          <div class="debug-kv-cell" :style="keyCellStyle(row)">
            <el-input
                v-if="!isKeyDisabled(row)"
                v-model="row[keyColumnField]"
                :placeholder="namePlaceholder"
                class="debug-kv-field"
            />
          </div>
          <div v-if="showRemarkColumn" class="debug-kv-cell debug-kv-cell--remark">
            <div
                v-if="isRemarkEditable(row)"
                :class="remarkExpandable ? 'debug-kv-remark-wrap' : undefined"
            >
              <el-input
                  v-model="row.remark"
                  :placeholder="remarkPlaceholder"
                  :class="remarkExpandable ? 'debug-kv-field debug-kv-remark-inline' : 'debug-kv-field'"
                  :title="remarkExpandable && row.remark ? String(row.remark) : undefined"
              />
              <el-popover
                  v-if="remarkExpandable"
                  :visible="openRemarkRowIndex === index"
                  :width="400"
                  placement="bottom-end"
                  popper-class="debug-kv-remark-popper"
                  trigger="manual"
                  :teleported="true"
                  @update:visible="(v) => onRemarkPopoverVisible(index, v)"
              >
                <div class="debug-kv-remark-popover">
                  <div class="debug-kv-remark-popover-title">{{ remarkLabel }}</div>
                  <el-input
                      v-model="row.remark"
                      :autosize="{ minRows: 4, maxRows: 14 }"
                      :placeholder="remarkPlaceholder"
                      class="debug-kv-field"
                      resize="vertical"
                      type="textarea"
                  />
                </div>
                <template #reference>
                  <button
                      aria-label="展开编辑备注"
                      class="debug-kv-remark-expand"
                      title="展开编辑备注"
                      type="button"
                      @click="toggleRemarkPopover(index)"
                  >
                    <el-icon><FullScreen/></el-icon>
                  </button>
                </template>
              </el-popover>
            </div>
          </div>
          <div class="debug-kv-cell">
            <div v-if="shouldShowFileUpload(row)" class="debug-kv-file-value">
              <input
                  :ref="el => setFileInputRef(index, el)"
                  class="debug-kv-file-native"
                  tabindex="-1"
                  type="file"
                  @change="onFileInputChange(row, $event)"
              />
              <el-button
                  :loading="!!row._uploading"
                  class="debug-kv-file-btn"
                  size="small"
                  type="primary"
                  @click="triggerFilePick(index)"
              >
                选择文件
              </el-button>
              <span :title="fileRowLabel(row)" class="debug-kv-file-name">{{ fileRowLabel(row) }}</span>
              <el-button
                  v-if="row._file || (row.value && String(row.value).trim())"
                  :disabled="!!row._uploading"
                  class="debug-kv-file-clear"
                  link
                  size="small"
                  type="danger"
                  @click="clearFileRow(row)"
              >
                清除
              </el-button>
            </div>
            <el-input
                v-else-if="!isValueDisabled(row) && !valueMultiline"
                v-model="row.value"
                :placeholder="valuePlaceholder"
                class="debug-kv-field"
            />
            <el-input
                v-else-if="!isValueDisabled(row)"
                v-model="row.value"
                :autosize="{ minRows: 2, maxRows: 8 }"
                :placeholder="valuePlaceholder"
                class="debug-kv-field"
                type="textarea"
            />
          </div>
          <div v-if="showExampleColumn" class="debug-kv-cell">
            <el-input
                v-model="row.exampleValue"
                :placeholder="examplePlaceholder"
                class="debug-kv-field"
            />
          </div>
          <div v-if="showTypeColumn" class="debug-kv-cell debug-kv-cell--type">
            <slot :index="index" :row="row" name="type"/>
          </div>
          <div v-if="showDescriptionColumn" class="debug-kv-cell">
            <el-input
                v-model="row.description"
                :placeholder="descriptionPlaceholder"
                class="debug-kv-field"
            />
          </div>
          <div class="debug-kv-cell debug-kv-cell--action debug-kv-cell--actions">
            <el-tooltip
                v-if="canAddChild(row)"
                content="添加子项"
                placement="top"
            >
              <button
                  class="debug-kv-add-child"
                  type="button"
                  @click="$emit('add-child', index)"
              >
                <el-icon><CirclePlus/></el-icon>
              </button>
            </el-tooltip>
            <el-tooltip content="删除" placement="top">
              <button
                  class="debug-kv-remove"
                  type="button"
                  @click="$emit('remove', index)"
              >
                <el-icon><Delete/></el-icon>
              </button>
            </el-tooltip>
          </div>
        </div>
      </div>
    </template>
  </div>
</template>

<script setup>
import {CirclePlus, Delete, FullScreen} from '@element-plus/icons-vue'
import {ElMessage} from 'element-plus'
import {useDebounceFn} from '@vueuse/core'
import {uploadCommonFile} from '@/api/common/upload'

/** 与 .debug-kv-row 单行高度对齐，用于占位计算（略大于实际单行，避免裁切） */
const VIRTUAL_ROW_HEIGHT = 56
/** 视口外多渲染的行数，略大可减少快速滚动时空白 */
const VIRTUAL_BUFFER_ROWS = 12

const props = defineProps({
  rows: {
    type: Array,
    required: true
  },
  showTypeColumn: {
    type: Boolean,
    default: false
  },
  showDescriptionColumn: {
    type: Boolean,
    default: false
  },
  /** 在「参数值」后显示「示例值」列 */
  showExampleColumn: {
    type: Boolean,
    default: false
  },
  exampleLabel: {
    type: String,
    default: '示例值'
  },
  examplePlaceholder: {
    type: String,
    default: '示例'
  },
  valueMultiline: {
    type: Boolean,
    default: false
  },
  nameLabel: {
    type: String,
    default: '名称'
  },
  valueLabel: {
    type: String,
    default: '值'
  },
  descriptionLabel: {
    type: String,
    default: '说明'
  },
  namePlaceholder: {
    type: String,
    default: ''
  },
  valuePlaceholder: {
    type: String,
    default: ''
  },
  descriptionPlaceholder: {
    type: String,
    default: ''
  },
  /** 类型为 file 时，值列展示「选择文件」而非普通文本框 */
  enableFileUploadForFileType: {
    type: Boolean,
    default: false
  },
  /**
   * 选文件后的处理方式：
   * - true：上传到服务端，value 写成可落库的存储路径（如 /profile/upload/...）
   * - false：只把浏览器 File 挂到行上的 _file，供调试发送，不持久化
   */
  persistFileUpload: {
    type: Boolean,
    default: false
  },
  /**
   * 行数达到该阈值时启用虚拟列表（设为 0 表示关闭）。
   * form-data 含文件上传列时强制走非虚拟路径，避免行高不一致。
   */
  virtualMinRows: {
    type: Number,
    default: 48
  },
  /** 名称列按 row._depth 缩进（每层 depthIndentPx） */
  depthIndentPx: {
    type: Number,
    default: 20
  },
  /** object/array 行禁用参数值列（由子行承载数据） */
  disableValueForCompositeTypes: {
    type: Boolean,
    default: false
  },
  /** object/array 行显示 + 按钮以新增子项 */
  showAddChildForComposite: {
    type: Boolean,
    default: false
  },
  /** 首列绑定字段名，素材库为 key，调试参数为 name */
  keyColumnField: {
    type: String,
    default: 'name'
  },
  /** 备注列（通常仅顶级素材行可编辑） */
  showRemarkColumn: {
    type: Boolean,
    default: false
  },
  remarkLabel: {
    type: String,
    default: '备注'
  },
  remarkPlaceholder: {
    type: String,
    default: '备注'
  },
  /** 备注列显示展开按钮，在 Popover 中以多行文本域编辑 */
  remarkExpandable: {
    type: Boolean,
    default: false
  },
  /** 可编辑输入框显示边框（素材库等场景） */
  borderedEditableFields: {
    type: Boolean,
    default: false
  }
})

defineEmits(['remove', 'add-child'])

const fileInputRefs = ref({})
const virtualScrollRef = ref(null)
const vSliceStart = ref(0)
const vSliceEnd = ref(0)
/** 当前展开备注 Popover 的行索引（手动触发，避免 click 被拦截） */
const openRemarkRowIndex = ref(null)

function toggleRemarkPopover(index) {
  openRemarkRowIndex.value = openRemarkRowIndex.value === index ? null : index
}

function onRemarkPopoverVisible(index, visible) {
  if (visible) {
    openRemarkRowIndex.value = index
  } else if (openRemarkRowIndex.value === index) {
    openRemarkRowIndex.value = null
  }
}

const useVirtual = computed(() => {
  if (props.virtualMinRows <= 0) return false
  if (props.enableFileUploadForFileType) return false
  if (props.valueMultiline) return false
  const n = props.rows?.length ?? 0
  return n >= props.virtualMinRows
})

const visibleIndices = computed(() => {
  const out = []
  const n = props.rows?.length ?? 0
  const a = Math.min(vSliceStart.value, n)
  const b = Math.min(vSliceEnd.value, n)
  for (let i = a; i < b; i++) out.push(i)
  return out
})

const topPadPx = computed(() => Math.min(vSliceStart.value, props.rows?.length ?? 0) * VIRTUAL_ROW_HEIGHT)

const bottomPadPx = computed(() => {
  const n = props.rows?.length ?? 0
  return Math.max(0, n - vSliceEnd.value) * VIRTUAL_ROW_HEIGHT
})

function setFileInputRef(index, el) {
  if (el) {
    fileInputRefs.value[index] = el
  } else {
    delete fileInputRefs.value[index]
  }
}

function keyCellStyle(row) {
  const depth = row?._depth ?? 0
  if (!depth) return undefined
  const extra = depth * props.depthIndentPx
  return {paddingLeft: `${8 + extra}px`}
}

function isKeyDisabled(row) {
  return Boolean(row?._hideKey)
}

function isRemarkEditable(row) {
  if (!props.showRemarkColumn) return false
  return row?._rowKind === 'entry' || row?._rowKind === 'asset' || (row?._depth ?? 0) === 0
}

function isValueDisabled(row) {
  if (!props.disableValueForCompositeTypes) return false
  const t = String(row?.type || '').toLowerCase()
  return t === 'object' || t === 'array'
}

function canAddChild(row) {
  if (!props.showAddChildForComposite) return false
  const t = String(row?.type || '').toLowerCase()
  return t === 'object' || t === 'array'
}

/** 当前行是否应显示文件选择控件 */
function shouldShowFileUpload(row) {
  if (isValueDisabled(row)) return false
  return props.enableFileUploadForFileType && String(row?.type || '').toLowerCase() === 'file'
}

/** 文件列展示文案：上传中 / 原始文件名 / 路径 / 未选择 */
function fileRowLabel(row) {
  if (row._uploading) return '上传中…'
  if (row._file instanceof File) return row._file.name
  const original = row._uploadedOriginalName != null ? String(row._uploadedOriginalName).trim() : ''
  if (original) return original
  const v = row.value != null ? String(row.value).trim() : ''
  return v || '未选择文件'
}

/** 打开系统文件选择框（上传进行中时忽略） */
function triggerFilePick(index) {
  if (props.rows[index]?._uploading) return
  fileInputRefs.value[index]?.click()
}

/**
 * 用户选中文件后的回调。
 * persistFileUpload 开启时上传并写 value=存储路径；否则只保留内存中的 File 供当次发送。
 */
async function onFileInputChange(row, e) {
  const input = e.target
  const f = input?.files?.[0]
  if (!f) return
  input.value = ''
  if (props.persistFileUpload) {
    row._uploading = true
    row._file = null
    try {
      const res = await uploadCommonFile(f)
      // 接口返回的 fileName 即服务端存储路径
      const storagePath = res?.fileName != null ? String(res.fileName).trim() : ''
      if (!storagePath) {
        throw new Error('上传成功但未返回文件路径')
      }
      row.value = storagePath
      // 保留用户看到的原始文件名，保存素材时写入 fileName 字段
      row._uploadedOriginalName = res?.originalFilename || f.name
    } catch (err) {
      ElMessage.error(err?.message || '文件上传失败')
    } finally {
      row._uploading = false
    }
    return
  }
  row._file = f
  row.value = f.name
  delete row._uploadedOriginalName
}

/** 清除本行已选文件、路径与上传状态 */
function clearFileRow(row) {
  row._file = null
  row.value = ''
  delete row._uploadedOriginalName
  row._uploading = false
}

const clearFileOnNonFileTypeRows = useDebounceFn(() => {
  for (const r of props.rows || []) {
    if (String(r?.type || '').toLowerCase() !== 'file' && r && r._file) {
      r._file = null
    }
  }
}, 24)

const clearValueOnCompositeTypeRows = useDebounceFn(() => {
  if (!props.disableValueForCompositeTypes) return
  for (const r of props.rows || []) {
    if (isValueDisabled(r) && r && (r.value != null && String(r.value).trim() !== '')) {
      r.value = ''
    }
  }
}, 24)

const clearKeyOnHiddenKeyRows = useDebounceFn(() => {
  const field = props.keyColumnField || 'name'
  for (const r of props.rows || []) {
    if (isKeyDisabled(r) && r && (r[field] != null && String(r[field]).trim() !== '')) {
      r[field] = ''
    }
  }
}, 24)

watch(() => props.rows, () => {
  clearFileOnNonFileTypeRows()
  clearValueOnCompositeTypeRows()
  clearKeyOnHiddenKeyRows()
}, {deep: true})

const gridStyle = computed(() => {
  const cols = ['44px', 'minmax(108px, 1fr)']
  if (props.showRemarkColumn) cols.push('minmax(96px, 0.85fr)')
  cols.push('minmax(108px, 1.15fr)')
  if (props.showExampleColumn) cols.push('minmax(96px, 1fr)')
  if (props.showTypeColumn) cols.push('168px')
  if (props.showDescriptionColumn) cols.push('minmax(88px, 1fr)')
  cols.push(props.showAddChildForComposite ? '72px' : '48px')
  return {gridTemplateColumns: cols.join(' ')}
})

function syncVirtualSlice() {
  if (!useVirtual.value) return
  const el = virtualScrollRef.value
  const n = props.rows?.length ?? 0
  if (n === 0) {
    vSliceStart.value = 0
    vSliceEnd.value = 0
    return
  }
  const st = el ? el.scrollTop : 0
  const ch = el && el.clientHeight > 0 ? el.clientHeight : 480
  const first = Math.max(0, Math.floor(st / VIRTUAL_ROW_HEIGHT))
  const visibleCount = Math.max(1, Math.ceil(ch / VIRTUAL_ROW_HEIGHT)) + VIRTUAL_BUFFER_ROWS * 2
  vSliceStart.value = Math.max(0, first - VIRTUAL_BUFFER_ROWS)
  vSliceEnd.value = Math.min(n, first + visibleCount)
}

/** 滚动用 rAF 合并到每帧一次，比固定 ms 节流更跟手、更贴显示器刷新 */
let virtualScrollRafId = 0
function scheduleVirtualSlice() {
  if (virtualScrollRafId) return
  virtualScrollRafId = requestAnimationFrame(() => {
    virtualScrollRafId = 0
    syncVirtualSlice()
  })
}

onBeforeUnmount(() => {
  if (virtualScrollRafId) {
    cancelAnimationFrame(virtualScrollRafId)
    virtualScrollRafId = 0
  }
})

watch(useVirtual, (v) => {
  if (v) {
    nextTick(() => {
      if (virtualScrollRef.value) virtualScrollRef.value.scrollTop = 0
      syncVirtualSlice()
    })
  }
})

watch(() => props.rows?.length, () => {
  nextTick(() => {
    syncVirtualSlice()
  })
})

onMounted(() => {
  if (useVirtual.value) nextTick(syncVirtualSlice)
})
</script>

<style lang="scss" scoped>
.debug-kv-sheet {
  border: none;
  border-radius: 0;
  background: transparent;
  overflow: hidden;
}

.debug-kv-scroll {
  overflow-x: auto;
  overflow-y: visible;
}

.debug-kv-sheet--virtual {
  .debug-kv-outer-x {
    overflow-x: auto;
    min-width: 0;
  }

  .debug-kv-virtual-scroll {
    max-height: min(52vh, 560px);
    overflow-x: hidden;
    overflow-y: auto;
    scrollbar-gutter: stable;
    -webkit-overflow-scrolling: touch;
    overscroll-behavior: contain;
    /* 提升滚动手持合成，减轻主线程滚动时布局抖动 */
    transform: translateZ(0);
  }

  .debug-kv-virtual-gutter {
    width: 100%;
    flex-shrink: 0;
  }
}

.debug-kv-head,
.debug-kv-row {
  display: grid;
  align-items: stretch;
  min-width: 640px;
}

.debug-kv-row--virtual {
  align-items: center;
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
    padding-left: 4px;
    padding-right: 4px;
  }

  &--actions {
    gap: 0;
    justify-content: center;
  }

  &--muted {
    padding-top: 10px;
    padding-bottom: 10px;
  }
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

  :deep(.el-textarea__inner) {
    border-radius: 6px;
    border: 1px solid transparent;
    resize: vertical;

    &:hover {
      background: rgba(15, 23, 42, 0.03);
    }

    &:focus {
      background: var(--pd-surface-elevated);
      box-shadow: 0 0 0 1px var(--pd-primary) inset;
    }
  }
}

.debug-kv-sheet--boxed-edit {
  .debug-kv-field {
    :deep(.el-input__wrapper),
    :deep(.el-textarea__inner) {
      background: var(--pd-surface-elevated, #fff);
      border: 1px solid var(--pd-border-subtle, #c5d8ec);
      box-shadow: none;
    }

    :deep(.el-input__wrapper) {
      &:hover {
        border-color: color-mix(in srgb, var(--pd-primary, #0b6edc) 35%, #c5d8ec);
        background: var(--pd-surface-elevated, #fff);
      }

      &.is-focus {
        border-color: var(--pd-primary, #0b6edc);
        box-shadow: 0 0 0 1px var(--pd-primary, #0b6edc) inset;
      }
    }

    :deep(.el-textarea__inner) {
      &:hover {
        border-color: color-mix(in srgb, var(--pd-primary, #0b6edc) 35%, #c5d8ec);
      }

      &:focus {
        border-color: var(--pd-primary, #0b6edc);
        box-shadow: 0 0 0 1px var(--pd-primary, #0b6edc) inset;
      }
    }
  }

  .debug-kv-cell--type :deep(.el-select__wrapper) {
    background: var(--pd-surface-elevated, #fff);
    border: 1px solid var(--pd-border-subtle, #c5d8ec);
    box-shadow: none;

    &:hover {
      border-color: color-mix(in srgb, var(--pd-primary, #0b6edc) 35%, #c5d8ec);
    }

    &.is-focused {
      border-color: var(--pd-primary, #0b6edc);
      box-shadow: 0 0 0 1px var(--pd-primary, #0b6edc) inset;
    }
  }
}

.debug-kv-file-value {
  position: relative;
  display: flex;
  align-items: center;
  gap: 8px;
  width: 100%;
  min-width: 0;
}

.debug-kv-file-native {
  position: absolute;
  width: 1px;
  height: 1px;
  margin: -1px;
  padding: 0;
  overflow: hidden;
  clip: rect(0 0 0 0);
  clip-path: inset(50%);
  white-space: nowrap;
  border: 0;
}

.debug-kv-file-btn {
  flex-shrink: 0;
}

.debug-kv-file-name {
  flex: 1;
  min-width: 0;
  font-size: 12px;
  color: var(--pd-text, #303133);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.debug-kv-file-clear {
  flex-shrink: 0;
}

.debug-kv-remark-wrap {
  display: flex;
  align-items: center;
  gap: 4px;
  width: 100%;
  min-width: 0;
}

.debug-kv-remark-inline {
  flex: 1;
  min-width: 0;

  :deep(.el-input__inner) {
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
}

.debug-kv-remark-expand {
  display: inline-flex;
  flex-shrink: 0;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  margin: 0;
  padding: 0;
  border: none;
  border-radius: 6px;
  background: transparent;
  color: var(--pd-text-muted, #64748b);
  cursor: pointer;
  transition: color 0.15s ease, background 0.15s ease;

  &:hover {
    color: var(--pd-primary, #0b6edc);
    background: rgba(11, 110, 220, 0.1);
  }

  :deep(.el-icon) {
    font-size: 14px;
  }
}

.debug-kv-remark-popover-title {
  margin-bottom: 8px;
  font-size: 12px;
  font-weight: 600;
  color: var(--pd-text, #303133);
}

.debug-kv-add-child {
  display: inline-flex;
  align-items: center;
  justify-content: center;
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

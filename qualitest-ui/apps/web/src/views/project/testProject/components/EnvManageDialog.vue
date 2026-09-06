<template>
  <el-dialog
      v-model="visible"
      class="env-manage-dialog"
      title="环境管理"
      width="1280px"
      top="4vh"
      append-to-body
      :z-index="10000"
      destroy-on-close
      :close-on-click-modal="false"
      @closed="onDialogClosed"
      @opened="onDialogOpened"
  >
    <div class="env-manage-shell">
      <div class="env-manage-columns">
      <aside class="env-sidebar">
        <div class="env-sidebar-head">
          <span class="env-sidebar-title">环境</span>
          <div class="env-sidebar-actions">
            <el-tooltip content="新建环境" placement="bottom">
              <el-button class="env-sidebar-tool" link type="primary" @click="startNewEnv">
                <el-icon>
                  <Plus/>
                </el-icon>
              </el-button>
            </el-tooltip>
            <el-tooltip content="敬请期待" placement="bottom">
              <el-button class="env-sidebar-tool" link type="info">
                <el-icon>
                  <MoreFilled/>
                </el-icon>
              </el-button>
            </el-tooltip>
          </div>
        </div>
        <div v-loading="listLoading || orderSaving" class="env-sidebar-list">
          <draggable
              v-model="envList"
              item-key="testProjectEnvId"
              handle=".env-drag-hint"
              :animation="200"
              :disabled="listLoading || orderSaving"
              class="env-sidebar-draggable"
              ghost-class="env-sidebar-item--ghost"
              @end="onEnvDragEnd"
          >
            <template #item="{ element: env }">
              <div
                  :class="{ active: isRowActive(env) }"
                  class="env-sidebar-item"
                  @click="selectExistingEnv(env)"
              >
                <span
                    :class="{ visible: isRowActive(env) }"
                    class="env-drag-hint"
                    aria-hidden="true"
                >
                  <el-icon>
                    <Rank/>
                  </el-icon>
                </span>
                <span
                    class="env-sidebar-name-pill"
                    :style="sidebarPillStyle(env)"
                >
                  {{ env.envName }}
                </span>
                <el-dropdown trigger="click" @click.stop>
                  <span class="env-item-more">
                    <el-icon>
                      <MoreFilled/>
                    </el-icon>
                  </span>
                  <template #dropdown>
                    <el-dropdown-menu>
                      <el-dropdown-item @click="confirmDeleteEnv(env)">删除</el-dropdown-item>
                    </el-dropdown-menu>
                  </template>
                </el-dropdown>
              </div>
            </template>
          </draggable>
        </div>
      </aside>

      <main class="env-main">
        <template v-if="detailLoading">
          <div class="env-main-loading">
            <el-icon class="is-loading">
              <Loading/>
            </el-icon>
            <span>加载中…</span>
          </div>
        </template>
        <template v-else>
          <div class="env-main-header">
            <div class="env-config-title-row">
              <el-popover
                  :width="236"
                  placement="bottom-start"
                  popper-class="env-color-popover"
                  trigger="click"
              >
                <template #reference>
                  <button
                      class="env-color-trigger"
                      type="button"
                      aria-label="环境颜色"
                  >
                    <span
                        class="env-color-trigger-swatch"
                        :style="envColorTriggerStyle"
                    />
                  </button>
                </template>
                <div class="env-color-panel">
                  <div class="env-color-swatches">
                    <button
                        v-for="c in ENV_COLOR_PRESETS"
                        :key="c || 'default'"
                        class="env-color-swatch"
                        :class="{ active: isPresetActive(c), 'is-none': !c }"
                        :style="presetSwatchSurfaceStyle(c)"
                        type="button"
                        @click="pickPresetColor(c)"
                    >
                      <el-icon v-if="isPresetActive(c)" class="env-color-check">
                        <Check/>
                      </el-icon>
                    </button>
                    <div
                        class="env-color-custom-cell"
                        title="自定义颜色"
                        :class="{ active: isCustomEnvColorActive }"
                        :style="customColorCellSurfaceStyle"
                    >
                      <el-color-picker
                          :model-value="customColorPickerValue"
                          :predefine="ENV_COLOR_PRESETS.filter(Boolean)"
                          popper-class="env-manage-color-picker-popper"
                          show-alpha="false"
                          size="small"
                          @update:model-value="onCustomEnvColorChange"
                      />
                    </div>
                  </div>
                </div>
              </el-popover>
              <div class="env-title-text">
                <input
                    v-model="form.envName"
                    class="env-name-input"
                    maxlength="64"
                    placeholder="环境名称"
                    type="text"
                />
              </div>
            </div>
            <span class="env-privacy-badge" title="环境均为私有，暂不支持共享">
              <el-icon class="env-privacy-badge-icon">
                <Lock/>
              </el-icon>
              私有
            </span>
          </div>

          <div class="env-config-body">
            <section class="env-section">
              <h3 class="env-section-title">前置 URL</h3>
              <el-input
                  v-model="baseUrl"
                  class="env-url-input"
                  placeholder="https://api.example.com、localhost:8800 等"
                  @blur="trimBaseUrl"
              />
            </section>

            <section class="env-section env-section--reset">
              <h3 class="env-section-title">被测数据还原</h3>
              <div v-if="isProductionEnv" class="env-prod-warn">
                生产环境禁止开启数据还原（allowDestructiveReset 已锁定为关闭）
              </div>
              <div class="env-reset-row">
                <el-switch
                    v-model="allowDestructiveResetOn"
                    :disabled="isProductionEnv"
                    active-text="允许还原被测数据"
                    inactive-text="禁止还原"
                />
              </div>
              <p class="env-reset-hint">
                开启后，测试流可在节点执行前调用被测方 <code>/test-support/snapshot</code> 打 checkpoint，失败时可还原重试。
                test-support 根路径由前置 URL 派生，无需单独配置。
                <strong>开启数据还原时，同一环境请串行跑</strong>（还原会覆盖被测数据，并行 Run 会互相踩库）。
              </p>
            </section>

            <section class="env-section">
              <div class="env-section-head">
                <h3 class="env-section-title">环境变量</h3>
                <el-button link type="primary" @click="addEntryRow">
                  <el-icon>
                    <Plus/>
                  </el-icon>
                  添加变量
                </el-button>
              </div>
              <VariableEntrySheetSection
                  :rows="sheetRows"
                  wrap-class="env-variable-sheet"
                  name-placeholder="key"
                  type-popper-class="env-variable-type-select-popper"
                  :show-empty="!sheetRows.length"
                  empty-text="暂无环境变量，点击「添加变量」"
                  @add-child="onEnvAddChildRow"
                  @remove="onEnvRemoveRow"
                  @type-change="onRowTypeChange"
              />
            </section>
          </div>
        </template>
      </main>
      </div>

      <footer v-if="!detailLoading" class="env-config-footer">
        <el-button @click="visible = false">取 消</el-button>
        <el-button :loading="saveLoading" @click="handleSave(true)">
          保存并关闭
        </el-button>
        <el-button :loading="saveLoading" type="primary" @click="handleSave(false)">
          保 存
        </el-button>
      </footer>
    </div>
  </el-dialog>
</template>

<script setup>
import {
  Check,
  Lock,
  Loading,
  MoreFilled,
  Plus,
  Rank,
} from '@element-plus/icons-vue'
import {
  DEFAULT_ENV_MODULE_NAME,
  resolveEnvBaseUrlForRequest,
  resolveEnvSwatchBackground,
  pickContrastForegroundForBg,
  softenEnvUiSurface,
  serializeEnvUrlRows
} from '@/views/project/testProject/utils/envConfigUtils'
import VariableEntrySheetSection from './VariableEntrySheetSection.vue'
import {useVariableEntrySheet} from '@/views/project/testProject/composables/useVariableEntrySheet'
import draggable from 'vuedraggable'
import {
  addTestProjectEnv,
  delTestProjectEnv,
  getTestProjectEnv,
  listTestProjectEnv,
  reorderTestProjectEnv,
  updateTestProjectEnv
} from '@/api/project/testProjectEnv'
import {editTestProjectUserSetting} from '@/api/project/testProjectUserSetting'
import {isProductionEnvName} from '@/views/project/testProject/utils/envProductionUtils'

const visible = defineModel('visible', {type: Boolean, default: false})

const props = defineProps({
  testProjectId: {
    type: [String, Number],
    required: true
  },
  /** 工具栏当前选中的环境 ID；删除该环境时需同步清空用户设置 */
  toolbarEnvId: {
    type: [String, Number],
    default: null
  }
})

const emit = defineEmits(['saved'])

const {proxy} = getCurrentInstance()

/** 首项空白 = 默认配色（按环境名称哈希）；其余为可选纯色 */
const ENV_COLOR_PRESETS = [
  '',
  '#f56c6c',
  '#67c23a',
  '#409eff',
  '#e6a23c',
  '#f472b6',
  '#06b6d4',
  '#9333ea',
  '#14b8a6',
  '#6366f1',
  '#64748b'
]

/** 触发器与色盘一致：淡色铺底 + 保留原色变量描边 */
const envColorTriggerStyle = computed(() => {
  const raw = resolveEnvSwatchBackground({
    envColor: form.envColor,
    envName: form.envName
  })
  return {
    '--env-trigger-surface': softenEnvUiSurface(raw),
    '--env-trigger-raw': raw
  }
})

/** Popover 预设：每格淡色一致；「默认」仍为白底虚线 */
function presetSwatchSurfaceStyle(c) {
  if ((c || '').trim() === '') return {}
  const raw = String(c).trim()
  return {
    '--env-swatch-surface': softenEnvUiSurface(raw),
    '--env-swatch-raw': raw
  }
}

const customColorPickerValue = computed(() => {
  const c = (form.envColor || '').trim()
  return c || null
})

/** 当前色值不在预设列表内时，视为「自定义」选中（第 12 格） */
const isCustomEnvColorActive = computed(() => {
  const cur = (form.envColor || '').trim()
  if (!cur) return false
  const lowered = cur.toLowerCase()
  return !ENV_COLOR_PRESETS.filter(Boolean).some((p) => lowered === String(p).toLowerCase())
})

/** 自定义格内取色器触发块与预设格同款淡色（不改变提交值） */
const customColorCellSurfaceStyle = computed(() => {
  const raw = (form.envColor || '').trim()
  if (!raw) return {}
  return {
    '--env-custom-surface': softenEnvUiSurface(raw),
    '--env-custom-raw': raw
  }
})

const envList = ref([])
const listLoading = ref(false)
const orderSaving = ref(false)
const detailLoading = ref(false)
const saveLoading = ref(false)

/** 新建草稿时为 true */
const isDraftNew = ref(false)
/** 当前选中的已有环境 ID；新建模式下为 null */
const selectedEnvId = ref(null)

const form = reactive({
  testProjectEnvId: null,
  envName: '',
  envColor: '',
  /** 仅编辑时提交；新建由服务端按 max(sort_num)+1 赋值 */
  sortNum: 0,
  allowDestructiveReset: 0
})

const isProductionEnv = computed(() => isProductionEnvName(form.envName))

const allowDestructiveResetOn = computed({
  get() {
    if (isProductionEnv.value) return false
    return Number(form.allowDestructiveReset) === 1
  },
  set(val) {
    if (isProductionEnv.value) {
      form.allowDestructiveReset = 0
      return
    }
    form.allowDestructiveReset = val ? 1 : 0
  }
})

watch(() => form.envName, () => {
  if (isProductionEnv.value) {
    form.allowDestructiveReset = 0
  }
})

/** 环境前置 Base URL（写入 envUrl，供调试请求拼接） */
const baseUrl = ref('')

const {
  sheetRows,
  loadFromEntriesJson,
  addEntryRow,
  onRemoveRowAt,
  onAddChildRowAt,
  onRowTypeChange,
  validateBeforeSave,
  buildEntriesForSave,
  resetSheet
} = useVariableEntrySheet()

function sidebarPillStyle(env) {
  const raw = resolveEnvSwatchBackground(env)
  const surface = softenEnvUiSurface(raw)
  return {
    '--env-sidebar-surface': surface,
    '--env-sidebar-raw': raw,
    color: pickContrastForegroundForBg(surface)
  }
}

function pickPresetColor(c) {
  form.envColor = c ? c : ''
}

function isPresetActive(c) {
  const cur = (form.envColor || '').trim()
  if (!c) return !cur
  return cur.toLowerCase() === String(c).toLowerCase()
}

function onCustomEnvColorChange(val) {
  form.envColor = val || ''
}

function isRowActive(env) {
  return !isDraftNew.value && selectedEnvId.value === env.testProjectEnvId
}

function trimBaseUrl() {
  baseUrl.value = (baseUrl.value || '').trim()
}

function onEnvRemoveRow(visibleIndex) {
  onRemoveRowAt(visibleIndex)
}

function onEnvAddChildRow(visibleIndex) {
  onAddChildRowAt(visibleIndex)
}

function loadFromPayload(payload) {
  form.testProjectEnvId = payload?.testProjectEnvId ?? null
  form.envName = payload?.envName ?? ''
  form.envColor = payload?.envColor ?? ''
  form.sortNum =
      payload?.sortNum != null ? Number(payload.sortNum) : 0
  form.allowDestructiveReset = Number(payload?.allowDestructiveReset ?? 0)
  if (isProductionEnvName(form.envName)) {
    form.allowDestructiveReset = 0
  }

  baseUrl.value = resolveEnvBaseUrlForRequest(payload?.envUrl) || ''
  loadFromEntriesJson(payload?.envVariables)
}

function resetDraftNewForm() {
  form.testProjectEnvId = null
  form.envName = ''
  form.envColor = ''
  form.sortNum = 0
  form.allowDestructiveReset = 0
  baseUrl.value = ''
  resetSheet()
}

function fetchEnvList() {
  listLoading.value = true
  return listTestProjectEnv({
    pageNum: 1,
    pageSize: 200,
    testProjectId: props.testProjectId
  })
      .then((res) => {
        envList.value = res.rows || []
        return envList.value
      })
      .catch(() => {
        envList.value = []
        return []
      })
      .finally(() => {
        listLoading.value = false
      })
}

/**
 * 侧栏拖拽排序：换位后调用专用 reorder 接口，一次请求批量写 sort_num。
 */
function onEnvDragEnd(evt) {
  if (!evt || evt.oldIndex === evt.newIndex) {
    return
  }
  const rows = envList.value
  orderSaving.value = true
  reorderTestProjectEnv({
    testProjectId: props.testProjectId,
    orderedEnvIds: rows.map((e) => e.testProjectEnvId)
  })
      .then((res) => {
        if (res.code !== 200) {
          return Promise.reject(new Error(res.msg || '排序保存失败'))
        }
        rows.forEach((env, idx) => {
          env.sortNum = idx
        })
        const curId = form.testProjectEnvId
        if (curId != null) {
          const row = rows.find((e) => e.testProjectEnvId === curId)
          if (row) {
            form.sortNum = row.sortNum
          }
        }
        emit('saved', {})
      })
      .catch(() => {
        proxy.$modal.msgError('排序保存失败')
        return fetchEnvList()
      })
      .finally(() => {
        orderSaving.value = false
      })
}

function onDialogOpened() {
  detailLoading.value = true
  fetchEnvList().then((rows) => {
    if (rows.length) {
      const tid = props.toolbarEnvId
      const tidStr = tid != null && tid !== '' ? String(tid) : ''
      const hasToolbarPreference =
          tidStr !== '' &&
          tidStr !== '0' &&
          rows.some((r) => String(r.testProjectEnvId) === tidStr)
      const preferred = hasToolbarPreference
          ? rows.find((r) => String(r.testProjectEnvId) === tidStr)
          : rows[0]
      isDraftNew.value = false
      selectedEnvId.value = preferred.testProjectEnvId
      loadEnvDetail(preferred.testProjectEnvId)
    } else {
      isDraftNew.value = true
      selectedEnvId.value = null
      detailLoading.value = false
      resetDraftNewForm()
    }
  })
}

function onDialogClosed() {
  isDraftNew.value = false
  selectedEnvId.value = null
}

function loadEnvDetail(testProjectEnvId) {
  detailLoading.value = true
  getTestProjectEnv(testProjectEnvId)
      .then((res) => {
        const data = res.data
        if (data) {
          loadFromPayload(data)
        }
      })
      .catch(() => {
        const row = envList.value.find((e) => e.testProjectEnvId === testProjectEnvId)
        if (row) loadFromPayload(row)
      })
      .finally(() => {
        detailLoading.value = false
      })
}

function selectExistingEnv(env) {
  if (!env?.testProjectEnvId) return
  isDraftNew.value = false
  selectedEnvId.value = env.testProjectEnvId
  loadEnvDetail(env.testProjectEnvId)
}

function startNewEnv() {
  isDraftNew.value = true
  selectedEnvId.value = null
  detailLoading.value = false
  resetDraftNewForm()
}

function validateForm() {
  const name = (form.envName || '').trim()
  if (!name) {
    proxy.$modal.msgError('请填写环境名称')
    return false
  }
  const check = validateBeforeSave({ keyLabel: '环境变量' })
  if (!check.ok) {
    proxy.$modal.msgError(check.message)
    return false
  }
  return true
}

/**
 * @param {boolean} closeAfter 为 true 时保存成功后关闭弹框（保存并关闭）；为 false 时保存后保持弹框打开（保 存）
 */
function handleSave(closeAfter) {
  if (!validateForm()) return

  const envUrl = serializeEnvUrlRows([
    {moduleName: DEFAULT_ENV_MODULE_NAME, url: (baseUrl.value || '').trim()}
  ])
  const entries = buildEntriesForSave()
  const envVariables = entries.length ? JSON.stringify(entries) : '[]'

  const body = {
    testProjectId: props.testProjectId,
    envName: (form.envName || '').trim(),
    envUrl,
    envVariables,
    shareStatus: 'private',
    envColor: (form.envColor || '').trim(),
    allowDestructiveReset: isProductionEnv.value ? 0 : (Number(form.allowDestructiveReset) === 1 ? 1 : 0)
  }
  if (form.testProjectEnvId != null) {
    body.testProjectEnvId = form.testProjectEnvId
    body.sortNum = Number(form.sortNum) || 0
  }

  saveLoading.value = true
  const req =
      form.testProjectEnvId != null
          ? updateTestProjectEnv(body)
          : addTestProjectEnv(body)

  req
      .then((response) => {
        if (response.code === 200) {
          proxy.$modal.msgSuccess('保存成功')
          emit('saved', {})
          return fetchEnvList().then(() => {
            if (form.testProjectEnvId != null) {
              selectedEnvId.value = form.testProjectEnvId
              isDraftNew.value = false
            } else {
              const rows = envList.value
              const created = rows[0]
              if (created?.testProjectEnvId) {
                isDraftNew.value = false
                selectedEnvId.value = created.testProjectEnvId
                form.testProjectEnvId = created.testProjectEnvId
                loadEnvDetail(created.testProjectEnvId)
              }
            }
          }).then(() => {
            if (closeAfter) {
              visible.value = false
            }
          })
        } else {
          proxy.$modal.msgError(response.msg || '保存失败')
        }
      })
      .catch(() => {
        proxy.$modal.msgError('保存失败')
      })
      .finally(() => {
        saveLoading.value = false
      })
}

function confirmDeleteEnv(env) {
  if (!env?.testProjectEnvId) return
  proxy.$modal.confirm(`是否确认删除环境「${env.envName}」？`).then(() => {
    return delTestProjectEnv(env.testProjectEnvId)
  }).then((response) => {
    if (response.code === 200) {
      proxy.$modal.msgSuccess('删除成功')
      const deletedId = env.testProjectEnvId
      const tidStr =
          props.toolbarEnvId != null && props.toolbarEnvId !== ''
              ? String(props.toolbarEnvId)
              : ''
      const clearToolbar =
          tidStr !== '' &&
          tidStr !== '0' &&
          tidStr === String(deletedId)
      if (clearToolbar) {
        editTestProjectUserSetting({
          testProjectId: props.testProjectId,
          testProjectEnvId: 0
        }).catch(() => {})
      }
      emit('saved', {deletedId, clearedToolbar: clearToolbar})
      return fetchEnvList().then((rows) => {
        if (rows.length) {
          const next = rows[0]
          isDraftNew.value = false
          selectedEnvId.value = next.testProjectEnvId
          loadEnvDetail(next.testProjectEnvId)
        } else {
          startNewEnv()
        }
      })
    } else {
      proxy.$modal.msgError(response.msg || '删除失败')
    }
  }).catch(() => {})
}

</script>

<style lang="scss" scoped>
.env-manage-shell {
  --env-border: #e8edf3;
  --env-sidebar-bg: #f6f8fb;
  --pd-border-subtle: var(--env-border);
  --pd-radius: 8px;
  --pd-shadow-card: none;
  --pd-surface-elevated: #ffffff;

  display: flex;
  flex-direction: column;
  min-height: 520px;
  max-height: min(80vh, 800px);
  margin: -8px -4px 0;
  overflow: hidden;
}

.env-manage-columns {
  display: flex;
  flex: 1;
  min-height: 0;
}

.env-sidebar {
  display: flex;
  flex-direction: column;
  width: 280px;
  flex-shrink: 0;
  border-right: 1px solid var(--env-border);
  background: var(--env-sidebar-bg);
}

.env-sidebar-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 16px 10px;
}

.env-sidebar-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.env-sidebar-actions {
  display: inline-flex;
  align-items: center;
  gap: 2px;
}

.env-sidebar-tool {
  padding: 4px;
  min-height: auto;
}

.env-sidebar-list {
  flex: 1;
  overflow: auto;
  padding: 0 10px 12px;
}

.env-sidebar-draggable {
  display: flex;
  flex-direction: column;
  width: 100%;
}

.env-sidebar-item--ghost {
  opacity: 0.45;
  background: var(--el-fill-color-dark);
  border-radius: 8px;
}

.env-sidebar-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 10px;
  margin-bottom: 4px;
  border-radius: 8px;
  cursor: pointer;
  font-size: 13px;
  color: var(--el-text-color-primary);
  transition: background 0.15s ease;

  &:hover {
    background: rgba(255, 255, 255, 0.65);
  }

  &.active {
    background: #ffffff;

    .env-drag-hint {
      opacity: 0.55;
    }
  }
}

.env-drag-hint {
  flex-shrink: 0;
  width: 14px;
  opacity: 0;
  color: var(--el-text-color-placeholder);
  cursor: grab;

  &.visible {
    opacity: 0.45;
  }
}

.env-sidebar-name-pill {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  padding: 5px 10px;
  border-radius: 8px;
  font-size: 13px;
  font-weight: 600;
  background: var(--env-sidebar-surface);
  box-shadow: inset 0 0 0 1px
    color-mix(in srgb, var(--env-sidebar-raw) 26%, rgba(15, 23, 42, 0.07));
}

.env-item-more {
  flex-shrink: 0;
  padding: 2px;
  color: var(--el-text-color-secondary);
  cursor: pointer;

  &:hover {
    color: var(--el-color-primary);
  }
}

.env-main {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  background: #ffffff;
}

.env-main-loading {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  color: var(--el-text-color-secondary);
  font-size: 14px;
}

.env-main-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 16px 20px;
  border-bottom: 1px solid var(--env-border);
}

.env-config-title-row {
  display: flex;
  align-items: center;
  gap: 12px;
  min-width: 0;
  flex: 1;
}

.env-color-trigger {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  margin: 0;
  padding: 0;
  border: none;
  border-radius: 8px;
  background: transparent;
  cursor: pointer;

  &:focus-visible {
    outline: 2px solid var(--el-color-primary);
    outline-offset: 2px;
  }
}

.env-color-trigger-swatch {
  width: 22px;
  height: 22px;
  border-radius: 6px;
  display: block;
  box-sizing: border-box;
  background: var(--env-trigger-surface);
  box-shadow: inset 0 0 0 1px
    color-mix(in srgb, var(--env-trigger-raw) 28%, rgba(15, 23, 42, 0.08));
}

.env-color-panel {
  padding: 4px 0 0;
}

.env-color-swatches {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.env-color-swatch {
  position: relative;
  width: 26px;
  height: 26px;
  padding: 0;
  border: 2px solid transparent;
  border-radius: 6px;
  cursor: pointer;
  box-sizing: border-box;

  &:not(.is-none) {
    background: var(--env-swatch-surface);
    box-shadow: inset 0 0 0 1px
      color-mix(in srgb, var(--env-swatch-raw) 26%, rgba(15, 23, 42, 0.07));
  }

  &.is-none {
    border: 2px dashed var(--el-border-color);
    background: #ffffff;
    box-shadow: 0 0 0 1px var(--el-border-color-lighter) inset;
  }

  &.active:not(.is-none) {
    border-color: var(--el-color-primary);
    box-shadow:
      inset 0 0 0 1px color-mix(in srgb, var(--env-swatch-raw) 26%, rgba(15, 23, 42, 0.07)),
      0 0 0 1px var(--el-color-primary-light-7);
  }

  &.is-none.active {
    border-color: var(--el-color-primary);
    box-shadow:
      0 0 0 1px var(--el-border-color-lighter) inset,
      0 0 0 1px var(--el-color-primary-light-7);
  }
}

.env-color-check {
  position: absolute;
  left: 50%;
  top: 50%;
  transform: translate(-50%, -50%);
  color: var(--el-text-color-primary);
  font-size: 14px;
  filter: drop-shadow(0 0 1px rgba(255, 255, 255, 0.95));
}

.env-color-custom-cell {
  position: relative;
  width: 26px;
  height: 26px;
  flex-shrink: 0;
  border-radius: 6px;
  border: 2px solid transparent;
  box-sizing: border-box;

  &.active {
    border-color: var(--el-color-primary);
    box-shadow: 0 0 0 1px var(--el-color-primary-light-7);
  }

  :deep(.el-color-picker) {
    display: block;
    width: 100%;
    height: 100%;
  }

  :deep(.el-color-picker__trigger) {
    width: 100%;
    height: 100%;
    padding: 0;
    border: none;
    border-radius: 4px;
    overflow: hidden;
    box-shadow: inset 0 0 0 1px
      color-mix(in srgb, var(--env-custom-raw) 26%, rgba(15, 23, 42, 0.07));
  }

  :deep(.el-color-picker__color),
  :deep(.el-color-picker__color-inner) {
    background-color: var(--env-custom-surface) !important;
  }
}

.env-title-text {
  display: flex;
  align-items: center;
  min-width: 0;
  flex: 1;
  min-height: 32px;
}

.env-name-input {
  width: 100%;
  border: none;
  outline: none;
  font-size: 16px;
  font-weight: 600;
  line-height: 1.5;
  color: var(--el-text-color-primary);
  background: transparent;

  &::placeholder {
    color: var(--el-text-color-placeholder);
    font-weight: 500;
  }
}

.env-privacy-badge {
  flex-shrink: 0;
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
  padding: 4px 10px;
  border-radius: 6px;
  border: 1px solid var(--env-border);
  background: var(--env-sidebar-bg);
  user-select: none;
}

.env-privacy-badge-icon {
  font-size: 16px;
}

.env-config-body {
  flex: 1;
  overflow: auto;
  padding: 16px 20px 20px;
}

.env-section {
  margin-bottom: 20px;

  &:last-child {
    margin-bottom: 0;
  }
}

.env-section-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 8px;

  .env-section-title {
    margin-bottom: 0;
  }
}

.env-section-title {
  margin: 0 0 8px;
  font-size: 13px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.env-url-input {
  width: 100%;

  :deep(.el-input__wrapper) {
    border-radius: 8px;
    box-shadow: 0 0 0 1px var(--env-border) inset;
  }
}

:deep(.env-variable-sheet.variable-entry-sheet-wrap) {
  border: 1px solid var(--env-border);
  border-radius: 8px;
  box-shadow: none;
  background: #ffffff;
}

.env-prod-warn {
  margin-bottom: 10px;
  padding: 8px 10px;
  border-radius: 6px;
  font-size: 12px;
  font-weight: 600;
  color: #b91c1c;
  background: #fee2e2;
  border: 1px solid #fecaca;
}

.env-reset-row {
  margin-bottom: 8px;
}

.env-reset-hint {
  margin: 0;
  font-size: 12px;
  line-height: 1.5;
  color: var(--env-text-muted, #64748b);

  code {
    font-size: 11px;
    padding: 1px 4px;
    border-radius: 4px;
    background: #f1f5f9;
  }
}

.env-config-footer {
  display: flex;
  justify-content: flex-end;
  align-items: center;
  gap: 10px;
  flex-shrink: 0;
  padding: 12px 20px;
  border-top: 1px solid var(--env-border);
  background: #ffffff;
}
</style>

<style lang="scss">
.env-manage-dialog.el-dialog .el-dialog__body {
  padding: 0 16px 16px;
}

.env-manage-dialog.el-dialog {
  max-width: min(92vw, 1280px);
}

/* 全屏详情 9999 < 弹框 10000 < 下拉/气泡 10010 < Message/MessageBox 10100 */
.env-color-popover.el-popper,
.env-manage-color-picker-popper,
.env-variable-type-select-popper.el-popper,
.debug-kv-remark-popper.el-popper {
  z-index: 10010 !important;
}

.el-message {
  z-index: 10100 !important;
}

.el-overlay.is-message-box {
  z-index: 10100 !important;
}
</style>

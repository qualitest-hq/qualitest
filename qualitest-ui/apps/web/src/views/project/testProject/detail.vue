<template>
  <div :class="{ 'fullscreen-mode': isFullscreen }" class="project-detail-container">
    <div class="detail-content">
      <ApiGroupTreePanel
          ref="apiTreePanelRef"
          :test-project-id="testProjectId"
          @refresh-start="onTreeRefreshStart"
          @tree-refreshed="getProjectInfo"
          @select-api="onSelectApiFromTree"
          @clear-detail="onTreeClearDetail"
      />

      <div class="detail-main">
        <div class="detail-main-inner">
          <!-- AI API 助手侧栏挂载点：铺满详情主区域，供 Teleport 全高贴右展示 -->
          <div ref="apiAiDockHost" class="api-ai-dock-host" />
          <ProjectDetailDocTabs
              :active-key="activeDocumentTabKey"
              :tabs="documentTabsForDocBar"
              @close="closeDocumentTab"
              @menu-command="onDocumentTabContextCommand"
              @select="activateDocumentTab"
          >
            <template #actions>
              <button
                  type="button"
                  class="detail-doc-tab-flow-btn"
                  title="测试流"
                  aria-label="测试流"
                  @click="openTestFlowList"
              >
                <svg-icon class="detail-doc-tab-flow-icon" icon-class="test-flow"/>
              </button>
            </template>
          </ProjectDetailDocTabs>
          <div class="detail-main-tabs-wrap">
            <div v-if="showDetailToolbar" class="detail-toolbar-right">
              <div
                  class="tabs-env-field"
                  :class="{ 'tabs-env-field--themed': !!selectedToolbarEnv }"
                  :style="toolbarEnvSelectSurfaceStyle"
              >
                <el-select
                    v-model="settingForm.testProjectEnvId"
                    :loading="envSaving"
                    class="tabs-env-select"
                    placeholder="请选择环境"
                    popper-class="tabs-env-select-dropdown"
                    @change="handleEnvChange"
                >
                  <template #prefix>
                    <span
                        v-if="!selectedToolbarEnv"
                        class="tabs-env-swatch tabs-env-swatch--empty"
                        aria-hidden="true"
                    />
                  </template>
                  <el-option
                      class="tabs-env-dropdown-option-none"
                      :value="TOOLBAR_NO_ENV_ID"
                      label="无环境"
                  />
                  <el-option
                      v-for="env in envList"
                      :key="env.testProjectEnvId"
                      :label="env.envName"
                      :value="String(env.testProjectEnvId)"
                  >
                    <span
                        class="tabs-env-option-row"
                        :style="envOptionRowStyle(env)"
                    >
                      <span class="tabs-env-option-label">{{ env.envName }}</span>
                    </span>
                  </el-option>
                </el-select>
                <button
                    type="button"
                    class="tabs-env-manage-btn"
                    title="环境管理"
                    aria-label="环境管理"
                    @click="openEnvManageDialog"
                >
                  <svg
                      class="tabs-env-manage-lines"
                      viewBox="0 0 24 24"
                      aria-hidden="true"
                      xmlns="http://www.w3.org/2000/svg"
                  >
                    <path
                        fill="none"
                        stroke="currentColor"
                        stroke-linecap="round"
                        stroke-width="2"
                        d="M5 7h14M5 12h14M5 17h14"
                    />
                  </svg>
                </button>
              </div>
              <button
                  type="button"
                  class="detail-toolbar-asset-btn"
                  :class="{ 'is-active': apiAiPanelOpen }"
                  title="AI 助手"
                  aria-label="AI 助手"
                  :disabled="!apiDetail?.testProjectApiId"
                  @click="apiAiPanelOpen = !apiAiPanelOpen"
              >
                <svg
                    class="detail-toolbar-asset-icon"
                    viewBox="0 0 24 24"
                    aria-hidden="true"
                    xmlns="http://www.w3.org/2000/svg"
                >
                  <path
                      fill="none"
                      stroke="currentColor"
                      stroke-linecap="round"
                      stroke-linejoin="round"
                      stroke-width="2"
                      d="M12 3v2M12 19v2M4.9 4.9l1.4 1.4M17.7 17.7l1.4 1.4M3 12h2M19 12h2M4.9 19.1l1.4-1.4M17.7 6.3l1.4-1.4M8 12a4 4 0 1 0 8 0 4 4 0 0 0-8 0z"
                  />
                </svg>
              </button>
              <button
                  type="button"
                  class="detail-toolbar-asset-btn"
                  title="素材库"
                  aria-label="素材库"
                  @click="openAssetManageDialog"
              >
                <svg
                    class="detail-toolbar-asset-icon"
                    viewBox="0 0 24 24"
                    aria-hidden="true"
                    xmlns="http://www.w3.org/2000/svg"
                >
                  <path
                      fill="none"
                      stroke="currentColor"
                      stroke-linecap="round"
                      stroke-linejoin="round"
                      stroke-width="2"
                      d="M3 7a2 2 0 0 1 2-2h4l2 2h8a2 2 0 0 1 2 2v9a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V7z"
                  />
                  <path
                      fill="none"
                      stroke="currentColor"
                      stroke-linecap="round"
                      stroke-width="2"
                      d="M8 12h8M8 15h5"
                  />
                </svg>
              </button>
              <button
                  type="button"
                  class="detail-toolbar-settings-btn"
                  title="项目设置"
                  aria-label="项目设置"
                  @click="openProjectSettingDrawer"
              >
                <svg
                    class="detail-toolbar-settings-icon"
                    viewBox="0 0 24 24"
                    aria-hidden="true"
                    xmlns="http://www.w3.org/2000/svg"
                >
                  <path
                      fill="none"
                      stroke="currentColor"
                      stroke-linecap="round"
                      stroke-linejoin="round"
                      stroke-width="2"
                      d="M12 15a3 3 0 1 0 0-6 3 3 0 0 0 0 6Z M19.4 15a1.65 1.65 0 0 0 .33 1.82l.06.06a2 2 0 0 1 0 2.83 2 2 0 0 1-2.83 0l-.06-.06a1.65 1.65 0 0 0-1.82-.33 1.65 1.65 0 0 0-1 1.51V21a2 2 0 0 1-4 0v-.09a1.65 1.65 0 0 0-1-1.54 1.65 1.65 0 0 0-1.82.33l-.06.06a2 2 0 1 1-2.83-2.83l.06-.06a1.65 1.65 0 0 0 .33-1.82 1.65 1.65 0 0 0-1.51-1H3a2 2 0 0 1 0-4h.09a1.65 1.65 0 0 0 1.51-1 1.65 1.65 0 0 0-.33-1.82l-.06-.06a2 2 0 1 1 2.83-2.83l.06.06a1.65 1.65 0 0 0 1.82.33H9a1.65 1.65 0 0 0 1-1.51V3a2 2 0 0 1 4 0v.09a1.65 1.65 0 0 0 1 1.54 1.65 1.65 0 0 0 1.82-.33l.06-.06a2 2 0 1 1 2.83 2.83l-.06.06a1.65 1.65 0 0 0-.33 1.82V9c.26.604.852.997 1.51 1H21a2 2 0 0 1 0 4h-.09a1.65 1.65 0 0 0-1.51 1z"
                  />
                </svg>
              </button>
            </div>
            <div v-loading="apiDetailLoading" class="api-detail-panel">
              <div
                  v-if="!apiDetailLoading && !apiDetail"
                  class="detail-empty-state"
              >
                <div class="detail-empty-illus" aria-hidden="true">
                  <svg fill="none" viewBox="0 0 120 88" xmlns="http://www.w3.org/2000/svg">
                    <rect
                        class="detail-empty-card"
                        height="52"
                        rx="8"
                        width="88"
                        x="16"
                        y="8"
                    />
                    <path
                        class="detail-empty-line"
                        d="M28 26h64M28 38h44M28 50h56"
                        stroke-linecap="round"
                        stroke-width="3"
                    />
                    <circle class="detail-empty-dot" cx="56" cy="68" r="6"/>
                  </svg>
                </div>
                <p class="detail-empty-title">尚未选择 API</p>
                <p class="detail-empty-desc">在左侧列表中点击一条接口，在此查看与调试</p>
              </div>
              <!-- 加载中且尚未有详情时必须有真实子节点，否则 v-loading 内部 ElOnlyChild 会在空容器上反复报错 -->
              <div
                  v-else-if="apiDetailLoading && !apiDetail"
                  aria-busy="true"
                  aria-label="加载接口详情"
                  class="detail-loading-placeholder"
              />
              <template v-else-if="apiDetail">
                <div class="detail-top-tabs">
                  <nav aria-label="API 详情" class="detail-top-tablist" role="tablist">
                    <button
                        v-for="t in mainDetailTabs"
                        :key="t.name"
                        :aria-selected="apiDetailSubTab === t.name"
                        :class="{ active: apiDetailSubTab === t.name }"
                        class="detail-top-tab"
                        role="tab"
                        type="button"
                        @click="setApiDetailSubTab(t.name)"
                    >
                      {{ t.label }}
                    </button>
                  </nav>
                  <div class="detail-tab-panels detail-tab-panels--stack">
                    <div
                        class="detail-tab-panel"
                        :class="{ 'is-active': apiDetailSubTab === 'doc' }"
                        role="tabpanel"
                        :aria-hidden="apiDetailSubTab !== 'doc'"
                    >
                      <keep-alive :max="12">
                        <ApiDocTab
                            v-if="apiDetailSubTab === 'doc'"
                            :key="'api-doc-' + (apiDetail.testProjectApiId ?? '')"
                            :api-detail="apiDetail"
                        />
                      </keep-alive>
                    </div>
                    <div
                        class="detail-tab-panel"
                        :class="{ 'is-active': apiDetailSubTab === 'debug' }"
                        role="tabpanel"
                        :aria-hidden="apiDetailSubTab !== 'debug'"
                    >
                      <keep-alive :max="12">
                        <ApiDebugTab
                            v-if="apiDetailSubTab === 'debug'"
                            :key="'api-dbg-' + (apiDetail.testProjectApiId ?? '')"
                            :api-detail="apiDetail"
                            :env-list="envList"
                            :http-transport-mode="projectHttpTransport"
                            :test-project-env-id="settingForm.testProjectEnvId"
                            @saved="onDebugSaved"
                        />
                      </keep-alive>
                    </div>
                    <div
                        class="detail-tab-panel"
                        :class="{ 'is-active': apiDetailSubTab === 'design' }"
                        role="tabpanel"
                        :aria-hidden="apiDetailSubTab !== 'design'"
                    >
                      <keep-alive :max="12">
                        <ApiDesignTab
                            v-if="apiDetailSubTab === 'design'"
                            :key="'api-dsg-' + (apiDetail.testProjectApiId ?? '')"
                            :api-detail="apiDetail"
                            @saved="onDebugSaved"
                        />
                      </keep-alive>
                    </div>
                    <div
                        class="detail-tab-panel"
                        :class="{ 'is-active': apiDetailSubTab === 'refs' }"
                        role="tabpanel"
                        :aria-hidden="apiDetailSubTab !== 'refs'"
                    >
                      <keep-alive :max="12">
                        <ApiFlowReferencesTab
                            v-if="apiDetailSubTab === 'refs'"
                            :key="'api-refs-' + (apiDetail.testProjectApiId ?? '')"
                            :api-detail="apiDetail"
                            :test-project-id="testProjectId"
                        />
                      </keep-alive>
                    </div>
                  </div>
                </div>
              </template>
            </div>

            <EnvManageDialog
                v-model:visible="envManageDialogVisible"
                :test-project-id="testProjectId"
                :toolbar-env-id="settingForm.testProjectEnvId"
                @saved="onEnvManageSaved"
            />

            <AssetManageDialog
                v-model:visible="assetManageDialogVisible"
                :test-project-id="testProjectId"
            />

            <ProjectSettingDrawer
                v-model:visible="projectSettingDrawerVisible"
                v-model:http-transport="projectHttpTransport"
                :forward-available="forwardAvail.available"
                :setting-loading="settingLoading"
                :setting-form="settingForm"
                :setting-context="settingContext"
                :show-http-transport-setting="showHttpTransportSetting"
                :test-project-id="testProjectId"
                @opened="getSetting"
                @refresh-token="handleRefreshToken"
            />

            <!-- AI API 助手侧栏：流式对话与 patch 应用到当前 apiDetail -->
            <ApiAiChatPanel
                v-if="apiDetail?.testProjectApiId"
                v-model:open="apiAiPanelOpen"
                :post-request-script="apiDetail?.postRequestScript ?? ''"
                :pre-request-script="apiDetail?.preRequestScript ?? ''"
                :test-project-api-id="String(apiDetail?.testProjectApiId ?? '')"
                :test-project-id="String(apiDetail?.testProjectId ?? testProjectId ?? '')"
                @apply="onApiAiApply"
            />
          </div>
          <ProjectDetailFooter
              :is-fullscreen="isFullscreen"
              :last-api-sync-time="footerLastApiSyncTime"
              @refresh-detail="handleFooterRefreshDetail"
              @toggle-fullscreen="toggleFullscreen"
          />
        </div>
      </div>
    </div>
  </div>
</template>

<script name="TestProjectDetail" setup>
import {getTestProject, myProjectContext} from '@/api/project/testProject'
import {getTestProjectApi} from '@/api/project/testProjectApi'
import {listTestProjectEnv} from '@/api/project/testProjectEnv'
import {
  editTestProjectUserSetting,
  refreshProjectTokenByProjectId
} from '@/api/project/testProjectUserSetting'
import useAppStore from '@/store/modules/app'
import useTagsViewStore from '@/store/modules/tagsView'
import { buildProjectTabTitle } from './utils/projectTabTitle'
import ApiDebugTab from './components/ApiDebugTab.vue'
import ApiDesignTab from './components/ApiDesignTab.vue'
import ApiDocTab from './components/ApiDocTab.vue'
import ApiFlowReferencesTab from './components/ApiFlowReferencesTab.vue'
import ApiGroupTreePanel from './components/ApiGroupTreePanel.vue'
import EnvManageDialog from './components/EnvManageDialog.vue'
import AssetManageDialog from './components/AssetManageDialog.vue'
import ProjectDetailFooter from './components/ProjectDetailFooter.vue'
import ProjectDetailDocTabs from './components/ProjectDetailDocTabs.vue'
import ProjectSettingDrawer from './components/ProjectSettingDrawer.vue'
import {pickContrastForegroundForBg, resolveEnvSwatchBackground, softenEnvUiSurface} from '@/views/project/testProject/utils/envConfigUtils'
import {
  readDebugHttpTransportPref,
  writeDebugHttpTransportPref
} from '@/views/project/testProject/utils/apiDebugUiPrefs'
import {getHttpForwardAvailability, isWebDebugTransportSwitchable} from '@/transport/runtime'
import {API_AI_DOCK_KEY} from './constants/apiAiDock'
import ApiAiChatPanel from './panels/ApiAiChatPanel.vue'
import {applyApiDesignChangesToDetail} from './utils/applyApiDesignPatch'

// --- 常量：文档页签 / 详情子 Tab 的 localStorage 版本前缀 ---
const DOCUMENT_TABS_STORAGE_VER = 1
/** 按 API 记录上次打开的详情子 Tab（文档、调试、设计等），键为项目 ID */
const API_DETAIL_SUBTAB_STORAGE_VER = 1

// --- 路由与 Store ---
const route = useRoute()
const router = useRouter()
const {proxy} = getCurrentInstance()
const appStore = useAppStore()
const tagsViewStore = useTagsViewStore()

// --- 项目与权限上下文 ---
const testProjectId = ref(route.params.testProjectId)
/** `myProjectContext` 返回的 setting；非 null 时抽屉展示 Token 区（Token 可能仍为空字符串） */
const settingContext = ref(null)
const projectInfo = ref({})

// --- 布局：全屏、抽屉、左侧树 ---
const isFullscreen = ref(false)
const envManageDialogVisible = ref(false)
const assetManageDialogVisible = ref(false)
const projectSettingDrawerVisible = ref(false)
const apiTreePanelRef = ref(null)
/** AI API 助手侧栏 Teleport 目标节点，子组件通过 inject 获取 */
const apiAiDockHost = ref(null)
provide(API_AI_DOCK_KEY, apiAiDockHost)
/** 详情页 AI API 助手侧栏开关 */
const apiAiPanelOpen = ref(false)

function openEnvManageDialog() {
  envManageDialogVisible.value = true
}

function openAssetManageDialog() {
  assetManageDialogVisible.value = true
}

function openTestFlowList() {
  router.push(`/project/testProject/flows/${testProjectId.value}`)
}

function openProjectSettingDrawer() {
  projectSettingDrawerVisible.value = true
}

/** 树开始刷新：占位，用于日后扩展；当前不重置右侧以免打断正在查看的接口 */
function onTreeRefreshStart() {
}

/** 点击分组等非接口节点：占位；当前不关闭已打开的文档页签 */
function onTreeClearDetail() {
}

// --- 文档页签（多接口并行，详情缓存在 tab 对象上）---
const openDocumentTabs = ref([])
const activeDocumentTabKey = ref(null)
let persistDocTabsTimer = null

/** 页签条展示用：固定在前 + 未固定在后，不含 detail，减轻子组件依赖与 diff 成本 */
function mapTabsForDocBar(list) {
  const pinned = list.filter(t => t.pinned)
  const unpinned = list.filter(t => !t.pinned)
  return [...pinned, ...unpinned].map(t => ({
    key: t.key,
    testProjectApiId: t.testProjectApiId,
    httpMethod: t.httpMethod,
    apiName: t.apiName,
    pinned: !!t.pinned
  }))
}

const documentTabsForDocBar = computed(() => mapTabsForDocBar(openDocumentTabs.value))

/**
 * 仅含会写入 localStorage 的字段。若对整个 openDocumentTabs 做 deep watch，
 * tab.detail（完整接口对象）每次赋值或深层变更都会触发监听，造成明显卡顿。
 */
const openDocumentTabsPersistSnapshot = computed(() => ({
  activeKey: activeDocumentTabKey.value,
  tabs: openDocumentTabs.value.map(t => ({
    key: t.key,
    testProjectApiId: t.testProjectApiId,
    httpMethod: t.httpMethod,
    apiName: t.apiName,
    pinned: !!t.pinned
  }))
}))

function getDocumentTabsStorageKey(projectId) {
  return `qualitest.projectDetail.openDocTabs.v${DOCUMENT_TABS_STORAGE_VER}.${projectId}`
}

function genDocumentTabKey() {
  return `dt-${Date.now()}-${Math.random().toString(36).slice(2, 9)}`
}

function persistOpenDocumentTabs() {
  try {
    const payload = {
      tabs: openDocumentTabs.value.map(t => ({
        key: t.key,
        testProjectApiId: t.testProjectApiId,
        httpMethod: t.httpMethod,
        apiName: t.apiName,
        pinned: !!t.pinned
      })),
      activeKey: activeDocumentTabKey.value
    }
    localStorage.setItem(getDocumentTabsStorageKey(testProjectId.value), JSON.stringify(payload))
  } catch {
    /* 写入失败时静默（如无存储配额） */
  }
}

function schedulePersistOpenDocumentTabs() {
  if (persistDocTabsTimer != null) clearTimeout(persistDocTabsTimer)
  persistDocTabsTimer = setTimeout(() => {
    persistDocTabsTimer = null
    persistOpenDocumentTabs()
  }, 80)
}

function restoreOpenDocumentTabsFromStorage() {
  try {
    const raw = localStorage.getItem(getDocumentTabsStorageKey(testProjectId.value))
    if (!raw) return
    const data = JSON.parse(raw)
    if (!data?.tabs?.length) return
    const tabs = data.tabs.map(t => ({
      key: t.key && String(t.key).length ? t.key : genDocumentTabKey(),
      testProjectApiId: t.testProjectApiId,
      httpMethod: t.httpMethod || '',
      apiName: t.apiName || '',
      pinned: !!t.pinned,
      detail: null
    }))
    openDocumentTabs.value = tabs
    const valid = data.activeKey && tabs.some(x => x.key === data.activeKey)
    activeDocumentTabKey.value = valid ? data.activeKey : tabs[0].key
    nextTick(() => {
      activateDocumentTab(activeDocumentTabKey.value)
    })
  } catch {
    /* 解析失败则忽略 */
  }
}

function activateDocumentTab(key) {
  if (key == null) return
  activeDocumentTabKey.value = key
  const tab = openDocumentTabs.value.find(t => t.key === key)
  if (!tab) return
  if (tab.testProjectApiId == null) {
    apiDetail.value = null
    apiDetailLoading.value = false
    return
  }
  if (tab.detail) {
    apiDetail.value = tab.detail
    apiDetailLoading.value = false
    return
  }
  fetchApiDetailForTab(tab)
}

function onSelectApiFromTree(payload) {
  const id = payload != null && typeof payload === 'object'
      ? payload.testProjectApiId
      : payload
  if (id == null) return

  const httpMethod = payload != null && typeof payload === 'object'
      ? (payload.httpMethod || '')
      : ''
  const apiName = payload != null && typeof payload === 'object'
      ? (payload.apiName || '')
      : ''

  const existing = openDocumentTabs.value.find(t => t.testProjectApiId === id)
  if (existing) {
    activateDocumentTab(existing.key)
    return
  }

  const active = openDocumentTabs.value.find(t => t.key === activeDocumentTabKey.value)
  if (active && active.testProjectApiId == null) {
    active.testProjectApiId = id
    active.httpMethod = httpMethod
    active.apiName = apiName
    active.detail = null
    fetchApiDetailForTab(active)
    return
  }

  const key = genDocumentTabKey()
  openDocumentTabs.value.push({
    key,
    testProjectApiId: id,
    httpMethod,
    apiName,
    pinned: false,
    detail: null
  })
  activeDocumentTabKey.value = key
  fetchApiDetailForTab(openDocumentTabs.value[openDocumentTabs.value.length - 1])
}

function closeDocumentTab(key) {
  const idx = openDocumentTabs.value.findIndex(t => t.key === key)
  if (idx === -1) return
  const wasActive = activeDocumentTabKey.value === key
  openDocumentTabs.value.splice(idx, 1)
  if (!wasActive) return
  if (openDocumentTabs.value.length === 0) {
    activeDocumentTabKey.value = null
    apiDetail.value = null
    apiDetailLoading.value = false
    return
  }
  const next = openDocumentTabs.value[Math.min(idx, openDocumentTabs.value.length - 1)]
  activeDocumentTabKey.value = next.key
  activateDocumentTab(next.key)
}

function toggleDocumentTabPinned(key) {
  const tab = openDocumentTabs.value.find(t => t.key === key)
  if (tab) tab.pinned = !tab.pinned
}

function closeOtherDocumentTabs(keepKey) {
  openDocumentTabs.value = openDocumentTabs.value.filter(t => t.key === keepKey)
  activeDocumentTabKey.value = keepKey
  activateDocumentTab(keepKey)
}

function closeAllDocumentTabs() {
  openDocumentTabs.value = []
  activeDocumentTabKey.value = null
  apiDetail.value = null
  apiDetailLoading.value = false
}

function onDocumentTabContextCommand(command, key) {
  if (command === 'pin') {
    toggleDocumentTabPinned(key)
    return
  }
  if (command === 'closeCurrent') {
    closeDocumentTab(key)
    return
  }
  if (command === 'closeOthers') {
    closeOtherDocumentTabs(key)
    return
  }
  if (command === 'closeAll') {
    closeAllDocumentTabs()
  }
}

watch(openDocumentTabsPersistSnapshot, () => {
  schedulePersistOpenDocumentTabs()
}, {deep: true})

// --- 主面板：当前 API 详情与顶部「文档 / 调试 / 设计」等 ---
const apiDetail = ref(null)
const apiDetailLoading = ref(false)

watch(
  () => apiDetail.value?.testProjectApiId,
  (id) => {
    /** 未选中接口时关闭 AI 助手侧栏 */
    if (!id) apiAiPanelOpen.value = false
  },
)
/** 已打开具体 API（含加载中）时展示右侧工具栏：环境选择与项目设置 */
const showDetailToolbar = computed(() => !!apiDetail.value || apiDetailLoading.value)
/** 并发/快速切换标签时，避免旧请求的 finally 误关 loading，引发面板分支错乱与 v-loading ElOnlyChild 告警 */
let apiDetailFetchSeq = 0

const mainDetailTabs = [
  {name: 'doc', label: '文档'},
  {name: 'debug', label: '调试'},
  {name: 'design', label: '设计'},
  // 反向引用：哪些测试流 HTTP 节点绑定了当前 API
  {name: 'refs', label: '引用'}
]

function parseRequestConfigForMethod(detail) {
  if (!detail?.requestConfig) return null
  const rc = detail.requestConfig
  if (typeof rc === 'object' && rc !== null) return rc
  if (typeof rc === 'string') {
    try {
      return JSON.parse(rc)
    } catch {
      return null
    }
  }
  return null
}

/** 从 API 详情的 requestConfig 读取 method 字段，供文档 Tab 标题展示 */
function extractMethodFromDetail(detail) {
  const rc = parseRequestConfigForMethod(detail)
  if (rc?.method) {
    return String(rc.method).toUpperCase().trim()
  }
  return ''
}

function fetchApiDetailForTab(tab) {
  if (tab.testProjectApiId == null) return
  const seq = ++apiDetailFetchSeq
  if (activeDocumentTabKey.value === tab.key) {
    apiDetail.value = null
  }
  apiDetailLoading.value = true
  getTestProjectApi(tab.testProjectApiId)
      .then(response => {
        const data = response.data || null
        tab.detail = data
        if (data) {
          tab.apiName = data.apiName || tab.apiName
          tab.httpMethod = extractMethodFromDetail(data) || tab.httpMethod
        }
        if (activeDocumentTabKey.value === tab.key) {
          apiDetail.value = data
        }
      })
      .catch(() => {
        tab.detail = null
        if (activeDocumentTabKey.value === tab.key) {
          apiDetail.value = null
        }
      })
      .finally(() => {
        if (seq !== apiDetailFetchSeq) return
        apiDetailLoading.value = false
      })
}

/** 底部栏「刷新详情」：清空当前 tab 缓存并重新请求 */
function handleFooterRefreshDetail() {
  const tab = openDocumentTabs.value.find(t => t.key === activeDocumentTabKey.value)
  if (!tab?.testProjectApiId) {
    proxy.$modal.msgWarning('请先选择要查看的 API')
    return
  }
  tab.detail = null
  fetchApiDetailForTab(tab)
}

/** 有当前接口详情时用该接口的 lastSyncTime；否则用项目维度的 lastApiSyncTime */
const footerLastApiSyncTime = computed(() => {
  const detail = apiDetail.value
  if (detail != null) {
    return detail.lastSyncTime ?? null
  }
  return projectInfo.value?.lastApiSyncTime ?? null
})

const apiDetailSubTab = ref('doc')

/** 双 rAF：先完成顶部 Tab 条样式与绘制，再切换重型子面板，减轻「点了没反应→突然卡住」的体感 */
function setApiDetailSubTab(name) {
  if (apiDetailSubTab.value === name) return
  requestAnimationFrame(() => {
    requestAnimationFrame(() => {
      apiDetailSubTab.value = name
    })
  })
}

function getApiDetailSubTabStorageKey(projectId) {
  return `qualitest.projectDetail.apiDetailSubTab.v${API_DETAIL_SUBTAB_STORAGE_VER}.${projectId}`
}

function loadApiDetailSubTabMap(projectId) {
  try {
    const raw = localStorage.getItem(getApiDetailSubTabStorageKey(projectId))
    if (!raw) return {}
    const data = JSON.parse(raw)
    return data && typeof data === 'object' && !Array.isArray(data) ? data : {}
  } catch {
    return {}
  }
}

function persistApiDetailSubTabMap(projectId, map) {
  try {
    localStorage.setItem(getApiDetailSubTabStorageKey(projectId), JSON.stringify(map))
  } catch {
    /* 写入失败时静默 */
  }
}

function persistCurrentApiDetailSubTab() {
  const pid = testProjectId.value
  const apiId = apiDetail.value?.testProjectApiId
  if (pid == null || apiId == null) return
  const map = loadApiDetailSubTabMap(pid)
  map[String(apiId)] = apiDetailSubTab.value
  persistApiDetailSubTabMap(pid, map)
}

function applySavedApiDetailSubTab(apiId) {
  if (apiId == null) return
  const map = loadApiDetailSubTabMap(testProjectId.value)
  const saved = map[String(apiId)]
  if (saved && mainDetailTabs.some((t) => t.name === saved)) {
    apiDetailSubTab.value = saved
  } else {
    apiDetailSubTab.value = 'doc'
  }
}

function onDebugSaved(merged) {
  apiDetail.value = merged
  const tab = openDocumentTabs.value.find(t => t.key === activeDocumentTabKey.value)
  if (tab && merged) {
    tab.detail = merged
    tab.apiName = merged.apiName || tab.apiName
    tab.httpMethod = extractMethodFromDetail(merged) || tab.httpMethod
  }
  apiTreePanelRef.value?.loadTree()
}

/** AI 助手建议：合并进 apiDetail 草稿，不自动落库 */
function onApiAiApply(payload) {
  const current = apiDetail.value
  if (!current || !payload?.changes?.length) return
  const next = applyApiDesignChangesToDetail(current, payload.changes)
  apiDetail.value = next
  const tab = openDocumentTabs.value.find(t => t.key === activeDocumentTabKey.value)
  if (tab) {
    tab.detail = next
  }
}

watch(
    () => apiDetail.value?.testProjectApiId,
    (id) => {
      if (id != null) applySavedApiDetailSubTab(id)
    }
)

let persistApiDetailSubTabTimer = null
function schedulePersistApiDetailSubTab() {
  if (persistApiDetailSubTabTimer != null) clearTimeout(persistApiDetailSubTabTimer)
  persistApiDetailSubTabTimer = setTimeout(() => {
    persistApiDetailSubTabTimer = null
    persistCurrentApiDetailSubTab()
  }, 150)
}

watch(apiDetailSubTab, () => {
  schedulePersistApiDetailSubTab()
})

// --- 环境与用户设置 ---
/**
 * 工具栏「无环境」占位值（字符串 '0'）。
 * 后端 Long 多为 JSON 字符串下发（避免雪花 ID 超出 JS 安全整数）；若对 ID 做 Number() 会丢精度，
 * el-select 无法匹配选项 value，选中态会退回展示原始 ID。
 */
const TOOLBAR_NO_ENV_ID = '0'

function coerceToolbarEnvId(id) {
  if (id == null || id === '') return TOOLBAR_NO_ENV_ID
  if (typeof id === 'number') {
    if (!Number.isFinite(id) || id === 0) return TOOLBAR_NO_ENV_ID
    return String(Math.trunc(id))
  }
  const s = String(id).trim()
  if (s === '' || s === '0') return TOOLBAR_NO_ENV_ID
  if (/^\d+$/.test(s)) return s
  return TOOLBAR_NO_ENV_ID
}

const envList = ref([])
const envLoading = ref(false)
const envTotal = ref(0)
const envQueryParams = reactive({
  pageNum: 1,
  pageSize: 10,
  testProjectId: testProjectId.value
})

const settingData = ref(null)
const settingLoading = ref(false)
/** 最近一次保存成功的环境 ID；请求失败时用来恢复下拉框 */
const lastSyncedEnvId = ref(null)
const envSaving = ref(false)
const settingForm = reactive({
  testProjectUserSettingId: null,
  testProjectId: testProjectId.value,
  testProjectEnvId: TOOLBAR_NO_ENV_ID,
  projectToken: ''
})

const forwardAvail = computed(() => getHttpForwardAvailability())
const showHttpTransportSetting = computed(() => isWebDebugTransportSwitchable())
const projectHttpTransport = ref('browser')
let httpTransportPrefWarned = false

function loadProjectHttpTransportPref() {
  const pid = testProjectId.value
  if (pid == null || pid === '') {
    projectHttpTransport.value = 'browser'
    return
  }
  let mode = readDebugHttpTransportPref(pid)
  if (mode === 'browser-java-forward' && !forwardAvail.value.available) {
    mode = 'browser'
    if (!httpTransportPrefWarned) {
      httpTransportPrefWarned = true
      proxy?.$modal?.msgWarning?.('服务端代理未配置，已切换为浏览器直连')
    }
  }
  projectHttpTransport.value = mode
}

watch(projectHttpTransport, (mode) => {
  const pid = testProjectId.value
  if (pid != null && pid !== '' && (mode === 'browser' || mode === 'browser-java-forward')) {
    writeDebugHttpTransportPref(pid, mode)
  }
})

watch(testProjectId, () => {
  loadProjectHttpTransportPref()
}, {immediate: true})

const selectedToolbarEnv = computed(() => {
  const id = settingForm.testProjectEnvId
  if (
      id == null ||
      id === '' ||
      id === TOOLBAR_NO_ENV_ID ||
      id === 0
  ) {
    return null
  }
  return envList.value.find((e) => String(e.testProjectEnvId) === String(id)) ?? null
})

/** 工具栏与下拉项：统一淡色铺底（入库仍为原始 envColor） */
const toolbarEnvSelectSurfaceStyle = computed(() => {
  const env = selectedToolbarEnv.value
  if (!env) return {}
  const raw = resolveEnvSwatchBackground(env)
  const surface = softenEnvUiSurface(raw)
  return {
    '--tabs-env-select-accent': surface,
    '--tabs-env-select-raw': raw,
    '--tabs-env-select-fg': pickContrastForegroundForBg(surface)
  }
})

function envOptionRowStyle(env) {
  const raw = resolveEnvSwatchBackground(env)
  const surface = softenEnvUiSurface(raw)
  return {
    '--tabs-env-select-accent': surface,
    '--tabs-env-select-raw': raw,
    '--tabs-env-option-fg': pickContrastForegroundForBg(surface)
  }
}

function getEnvList() {
  envLoading.value = true
  listTestProjectEnv(envQueryParams).then(response => {
    envList.value = response.rows || []
    envTotal.value = response.total || 0
    envLoading.value = false
  }).catch(() => {
    envLoading.value = false
  })
}

/** 将 myProjectContext 返回的 setting 同步到抽屉表单与本地缓存 */
function applySettingFromContext(setting) {
  if (setting) {
    settingData.value = setting
    settingForm.testProjectUserSettingId = setting.testProjectUserSettingId
    settingForm.testProjectId = setting.testProjectId
    settingForm.testProjectEnvId = coerceToolbarEnvId(setting.testProjectEnvId)
    settingForm.projectToken = setting.projectToken || ''
    lastSyncedEnvId.value = settingForm.testProjectEnvId
  } else {
    settingData.value = null
    settingForm.testProjectUserSettingId = null
    settingForm.testProjectId = testProjectId.value
    settingForm.testProjectEnvId = TOOLBAR_NO_ENV_ID
    settingForm.projectToken = ''
    lastSyncedEnvId.value = TOOLBAR_NO_ENV_ID
  }
}

/**
 * 拉取「我的项目上下文」（含成员角色与用户设置），并写入 settingContext / 表单。
 * @param {{ drawerLoading?: boolean }} opts drawerLoading 为 true 时用于抽屉打开的 loading 态
 */
function fetchProjectContext(opts = {}) {
  const drawerLoading = opts.drawerLoading === true
  if (drawerLoading) {
    settingLoading.value = true
  }
  return myProjectContext(testProjectId.value)
      .then((res) => {
        const setting = res.data?.setting ?? null
        settingContext.value = setting
        applySettingFromContext(setting)
      })
      .catch(() => {
        settingContext.value = null
        applySettingFromContext(null)
      })
      .finally(() => {
        if (drawerLoading) {
          settingLoading.value = false
        }
      })
}

function loadMyProjectContext() {
  return fetchProjectContext()
}

function getSetting() {
  return fetchProjectContext({ drawerLoading: true })
}

function onEnvManageSaved(payload) {
  getEnvList()
  if (payload?.clearedToolbar) {
    settingForm.testProjectEnvId = TOOLBAR_NO_ENV_ID
    lastSyncedEnvId.value = TOOLBAR_NO_ENV_ID
    getSetting()
  }
}

function handleRefreshToken() {
  proxy.$modal.confirm('刷新Token后，旧的Token将失效，是否确认刷新？').then(() => {
    refreshProjectTokenByProjectId(testProjectId.value).then(response => {
      if (response.code === 200) {
        proxy.$modal.msgSuccess('Token刷新成功，新Token已生成')
        settingForm.projectToken = response.data
        if (settingContext.value !== null) {
          settingContext.value = {
            ...settingContext.value,
            projectToken: response.data
          }
        }
        if (settingData.value) {
          settingData.value.projectToken = response.data
        }
      } else {
        proxy.$modal.msgError(response.msg || 'Token刷新失败')
      }
    }).catch(() => {
      proxy.$modal.msgError('Token刷新失败')
    })
  }).catch(() => {
  })
}

function handleEnvChange(val) {
  const previous = lastSyncedEnvId.value
  envSaving.value = true
  const resolved = coerceToolbarEnvId(val)
  settingForm.testProjectEnvId = resolved
  const saveData = {
    testProjectId: settingForm.testProjectId,
    testProjectEnvId: resolved
  }
  editTestProjectUserSetting(saveData)
      .then((response) => {
        if (response.code === 200) {
          proxy.$modal.msgSuccess('测试环境已更新')
          lastSyncedEnvId.value = saveData.testProjectEnvId
          getSetting()
        } else {
          settingForm.testProjectEnvId = coerceToolbarEnvId(previous)
          proxy.$modal.msgError(response.msg || '更新失败')
        }
        envSaving.value = false
      })
      .catch(() => {
        settingForm.testProjectEnvId = coerceToolbarEnvId(previous)
        envSaving.value = false
        proxy.$modal.msgError('更新失败')
      })
}

// --- 项目信息（名称同步到 TagsView）---
function updateDetailTabTitle() {
  const name = projectInfo.value?.projectName
  if (!name) return
  tagsViewStore.updateVisitedView(
      Object.assign({}, route, { title: buildProjectTabTitle(name, 'API') })
  )
}

function getProjectInfo() {
  getTestProject(testProjectId.value).then(response => {
    projectInfo.value = response.data || {}
    updateDetailTabTitle()
  })
}

// --- 全屏：隐藏侧栏并给 body 加类名以便全局隐藏顶栏 ---
function toggleFullscreen() {
  isFullscreen.value = !isFullscreen.value
  appStore.toggleSideBarHide(isFullscreen.value)

  if (isFullscreen.value) {
    document.body.classList.add('fullscreen-detail-mode')
  } else {
    document.body.classList.remove('fullscreen-detail-mode')
  }
}

// --- 路由切换项目时重置并重新拉数 ---
watch(
    () => route.params.testProjectId,
    (newId, oldId) => {
      if (newId == null || newId === oldId) return
      testProjectId.value = newId
      settingForm.testProjectId = newId
      envQueryParams.testProjectId = newId
      openDocumentTabs.value = []
      activeDocumentTabKey.value = null
      apiDetail.value = null
      apiDetailLoading.value = false
      restoreOpenDocumentTabsFromStorage()
      getProjectInfo()
      loadMyProjectContext()
      nextTick(() => {
        apiTreePanelRef.value?.loadTree()
      })
      getEnvList()
    }
)

onMounted(() => {
  restoreOpenDocumentTabsFromStorage()
  loadMyProjectContext()
  getProjectInfo()
  nextTick(() => {
    apiTreePanelRef.value?.loadTree()
  })
  getEnvList()
})

onBeforeUnmount(() => {
  if (persistApiDetailSubTabTimer != null) {
    clearTimeout(persistApiDetailSubTabTimer)
    persistApiDetailSubTabTimer = null
    persistCurrentApiDetailSubTab()
  }
  if (isFullscreen.value) {
    appStore.toggleSideBarHide(false)
    document.body.classList.remove('fullscreen-detail-mode')
  }
})
</script>

<style lang="scss" scoped>
.project-detail-container {
  /* 页面设计变量，子组件通过继承使用 */
  --pd-bg-page: #e6f0fb;
  --pd-bg-sidebar: #dfeaf8;
  --pd-bg-sunken: #e9f2fc;
  --pd-bg-toolbar: #d0e2f4;
  --pd-surface: #fbfdff;
  --pd-surface-elevated: #ffffff;
  --pd-border-subtle: #c5d8ec;
  --pd-border-muted: #d6e6f5;
  --pd-divider: #dbe8f4;
  --pd-text: #0f172a;
  --pd-text-muted: #5a6b86;
  --pd-text-tab: #334c6e;
  --pd-primary: #0b6edc;
  --pd-primary-glow: rgba(11, 110, 220, 0.25);
  --pd-primary-soft: rgba(11, 110, 220, 0.12);
  --pd-radius: 10px;
  --pd-radius-sm: 8px;
  --pd-shadow-card: 0 1px 2px rgba(20, 60, 120, 0.05), 0 4px 14px rgba(30, 80, 160, 0.07);
  --pd-gradient-tabstrip: linear-gradient(180deg, #fafcff 0%, #f0f6fc 100%);
  --pd-gradient-panel-head: linear-gradient(180deg, #fafcff 0%, #ffffff 100%);
  --pd-font-tab: 14px;
  --pd-font-body: 13px;
  --pd-tab-h: 40px;
  --pd-doc-strip-h: 53px;

  height: 100%;
  display: flex;
  flex-direction: column;
  background: var(--pd-bg-page);

  &.fullscreen-mode {
    position: fixed;
    top: 0;
    left: 0;
    right: 0;
    bottom: 0;
    z-index: 9999;
  }

  .detail-content {
    flex: 1;
    overflow: hidden;
    min-height: 0;
    display: flex;
    flex-direction: row;
    align-items: stretch;
    gap: 0;

    .detail-main {
      flex: 1;
      min-width: 0;
      padding: 0;
      background: var(--pd-bg-page);
      overflow: hidden;
      display: flex;
      flex-direction: column;
      min-height: 0;

      .detail-main-inner {
        position: relative;
        flex: 1;
        display: flex;
        flex-direction: column;
        min-height: 0;
        overflow: hidden;
      }

      .api-ai-dock-host {
        /* 侧栏 Teleport 容器：绝对定位铺满主区域，不拦截点击，子面板自行开启 pointer-events */
        position: absolute;
        inset: 0;
        z-index: 300;
        pointer-events: none;
        overflow: visible;
      }

      .detail-doc-tab-flow-btn {
        flex-shrink: 0;
        width: 34px;
        height: 34px;
        margin: 0;
        padding: 0;
        border: none;
        border-radius: var(--pd-radius-sm);
        background: transparent;
        cursor: pointer;
        display: inline-flex;
        align-items: center;
        justify-content: center;
        color: var(--pd-primary, #0b6edc);
        transition: background 0.15s ease, color 0.15s ease;

        &:hover {
          background: rgba(11, 110, 220, 0.08);
        }

        &:focus-visible {
          outline: 2px solid color-mix(in srgb, var(--pd-primary, #0b6edc) 45%, transparent);
          outline-offset: 2px;
        }
      }

      .detail-doc-tab-flow-icon {
        width: 20px;
        height: 20px;
        font-size: 20px;
      }

      .detail-main-tabs-wrap {
        position: relative;
        flex: 1;
        min-height: 0;
        display: flex;
        flex-direction: column;
      }

      .detail-toolbar-right {
        position: absolute;
        top: 0;
        right: 20px;
        z-index: 5;
        display: flex;
        align-items: center;
        gap: 8px;
        height: var(--pd-tab-h, 40px);
        pointer-events: auto;

        .detail-toolbar-asset-btn,
        .detail-toolbar-settings-btn {
          flex-shrink: 0;
          width: 34px;
          height: 34px;
          margin: 0;
          padding: 0;
          border: none;
          border-radius: var(--pd-radius-sm);
          background: transparent;
          cursor: pointer;
          display: inline-flex;
          align-items: center;
          justify-content: center;
          color: var(--pd-primary, #0b6edc);
          transition: background 0.15s ease, color 0.15s ease;

          &:hover {
            background: rgba(11, 110, 220, 0.08);
            color: var(--pd-primary, #0b6edc);
          }

          &.is-active {
            background: rgba(11, 110, 220, 0.14);
            color: var(--pd-primary, #0b6edc);
          }

          &:disabled {
            opacity: 0.45;
            cursor: not-allowed;
          }

          &:focus-visible {
            outline: 2px solid color-mix(in srgb, var(--pd-primary, #0b6edc) 45%, transparent);
            outline-offset: 2px;
          }

          .detail-toolbar-settings-icon {
            width: 20px;
            height: 20px;
            display: block;
            flex-shrink: 0;
          }

          .detail-toolbar-asset-icon {
            width: 22px;
            height: 22px;
            display: block;
            flex-shrink: 0;
          }
        }

        .tabs-env-field {
          display: inline-flex;
          align-items: stretch;
          min-width: 208px;
          max-width: min(312px, 40vw);
          border-radius: var(--pd-radius-sm);
          box-shadow: 0 0 0 1px var(--pd-border-subtle) inset;
          background: var(--pd-surface-elevated);
          overflow: hidden;

          &.tabs-env-field--themed {
            background: var(--tabs-env-select-accent);
            box-shadow: inset 0 0 0 1px
              color-mix(in srgb, var(--tabs-env-select-raw) 26%, rgba(15, 23, 42, 0.07));
          }

          .tabs-env-select {
            flex: 1;
            min-width: 0;
            width: auto;

            :deep(.el-select__wrapper) {
              min-height: 34px;
              padding: 5px 10px;
              border: none;
              border-radius: 0;
              box-shadow: none;
              background: transparent;
              font-size: var(--pd-font-tab);
              font-weight: 500;
              gap: 0;
            }

            :deep(.el-select__prefix) {
              margin-inline-end: 8px;
            }

            :deep(.el-select__selected-item),
            :deep(.el-select__placeholder) {
              font-weight: 500;
            }

            :deep(.el-select__placeholder) {
              font-weight: 400;
              color: var(--pd-text-muted);
            }

            :deep(.el-select__caret) {
              color: var(--pd-text-muted);
            }
          }

          &.tabs-env-field--themed .tabs-env-select :deep(.el-select__prefix) {
            margin-inline-end: 0;
            width: 0;
            min-width: 0;
            overflow: hidden;
          }

          &.tabs-env-field--themed .tabs-env-select :deep(.el-select__selected-item),
          &.tabs-env-field--themed .tabs-env-select :deep(.el-select__caret) {
            color: var(--tabs-env-select-fg);
          }

          &.tabs-env-field--themed .tabs-env-select :deep(.el-select__placeholder) {
            color: var(--tabs-env-select-fg);
            opacity: 0.72;
          }

          .tabs-env-manage-btn {
            flex-shrink: 0;
            width: 38px;
            margin: 0;
            padding: 0;
            border: none;
            border-left: 1px solid var(--pd-border-subtle);
            border-radius: 0;
            background: transparent;
            cursor: pointer;
            display: inline-flex;
            align-items: center;
            justify-content: center;
            color: var(--pd-text-muted, #5a6b86);
            transition: background 0.15s ease, color 0.15s ease;

            &:hover {
              background: rgba(255, 255, 255, 0.55);
              color: var(--pd-primary, #0b6edc);
            }

            .tabs-env-manage-lines {
              width: 18px;
              height: 18px;
              display: block;
              flex-shrink: 0;
            }
          }

          &.tabs-env-field--themed .tabs-env-manage-btn {
            border-left-color: color-mix(
              in srgb,
              var(--tabs-env-select-raw) 26%,
              rgba(15, 23, 42, 0.12)
            );
            color: var(--tabs-env-select-fg);

            &:hover {
              background: rgba(255, 255, 255, 0.22);
              color: var(--tabs-env-select-fg);
              opacity: 0.92;
            }
          }
        }

        .tabs-env-swatch {
          display: inline-block;
          width: 12px;
          height: 12px;
          border-radius: 4px;
          flex-shrink: 0;
          box-shadow: 0 0 0 1px rgba(15, 23, 42, 0.1) inset;
        }

        .tabs-env-swatch--empty {
          background: var(--pd-border-muted, #d6e6f5);
          box-shadow: 0 0 0 1px var(--pd-border-subtle) inset;
        }

      }

      .api-detail-panel {
        min-height: 200px;
        flex: 1;
        display: flex;
        flex-direction: column;
        min-height: 0;
        background: var(--pd-surface-elevated);
        padding: 0 0 4px 0;
      }

      .detail-empty-state {
        flex: 1;
        display: flex;
        flex-direction: column;
        align-items: center;
        justify-content: center;
        min-height: 280px;
        padding: 32px 24px;
        margin: 0;
        background: var(--pd-surface-elevated);
        border-radius: 0;
        box-shadow: none;
        border: none;
      }

      .detail-loading-placeholder {
        flex: 1;
        min-height: 280px;
        box-sizing: border-box;
      }

      .detail-empty-illus {
        margin-bottom: 16px;
        opacity: 0.85;

        .detail-empty-card {
          fill: var(--pd-surface);
          stroke: var(--pd-border-subtle);
          stroke-width: 1.5;
        }

        .detail-empty-line {
          stroke: var(--pd-border-subtle);
        }

        .detail-empty-dot {
          fill: var(--pd-primary);
          opacity: 0.35;
        }
      }

      .detail-empty-title {
        margin: 0 0 8px;
        font-size: 16px;
        font-weight: 600;
        color: var(--pd-text);
      }

      .detail-empty-desc {
        margin: 0;
        font-size: var(--pd-font-body);
        color: var(--pd-text-muted);
        max-width: 280px;
        text-align: center;
        line-height: 1.5;
      }

      .detail-top-tabs {
        height: 100%;
        min-height: 0;
        display: flex;
        flex-direction: column;
        gap: 0;
      }

      .detail-top-tablist {
        display: flex;
        flex-wrap: wrap;
        align-items: stretch;
        gap: 0;
        margin: 0;
        padding: 0 544px 0 8px;
        min-height: var(--pd-tab-h);
        flex-shrink: 0;
        border-bottom: 1px solid var(--pd-border-subtle);
        background: var(--pd-bg-page);
      }

      .detail-top-tab {
        position: relative;
        margin: 0;
        padding: 0 18px;
        height: auto;
        min-height: var(--pd-tab-h);
        border: 1px solid transparent;
        border-bottom: none;
        border-radius: 0;
        background: transparent;
        font-family: inherit;
        font-size: var(--pd-font-tab);
        font-weight: 500;
        color: var(--pd-text-muted);
        cursor: pointer;
        transition:
          color 0.15s ease,
          background 0.15s ease,
          border-color 0.15s ease;

        &:hover:not(.active) {
          color: var(--pd-text-tab);
          background: rgba(255, 255, 255, 0.55);
          border-color: var(--pd-border-muted);
        }

        &.active {
          color: var(--pd-primary);
          font-weight: 600;
          background: var(--pd-surface-elevated);
          border-color: var(--pd-border-subtle);
          border-bottom: none;
          margin-bottom: -1px;
          z-index: 1;
          box-shadow: none;

          &::after {
            content: '';
            position: absolute;
            left: 0;
            right: 0;
            bottom: -1px;
            height: 3px;
            background: var(--pd-primary);
            border-radius: 0;
            z-index: 2;
          }
        }

        &:focus-visible {
          outline: 2px solid color-mix(in srgb, var(--pd-primary) 45%, transparent);
          outline-offset: 1px;
        }
      }

      .detail-tab-panels {
        flex: 1;
        overflow: hidden;
        min-height: 0;
        display: flex;
        flex-direction: column;
        padding: 0;
      }

      .detail-tab-panels--stack {
        position: relative;
      }

      .detail-tab-panels--stack > .detail-tab-panel {
        position: absolute;
        inset: 0;
        display: none;
        flex-direction: column;
        overflow: hidden;
        background: var(--pd-surface-elevated);
        border: none;
        border-radius: 0;
        box-shadow: none;
      }

      .detail-tab-panels--stack > .detail-tab-panel.is-active {
        display: flex;
      }
    }
  }
}
</style>

<style lang="scss">
body.fullscreen-detail-mode {
  .main-container .fixed-header {
    display: none !important;
  }

  .app-wrapper .main-container {
    margin-top: 0 !important;
  }
}

.tabs-env-select-dropdown {
  .el-select-dropdown__item {
    height: auto;
    line-height: normal;
    padding: 5px 10px;
  }

  .el-select-dropdown__item.tabs-env-dropdown-option-none {
    margin: 2px 8px;
    padding: 8px 12px;
    border-radius: 8px;
    background: #f1f5f9;
    box-shadow: inset 0 0 0 1px rgba(15, 23, 42, 0.08);
    font-weight: 500;
    font-size: 13px;
    color: #64748b;
  }

  .tabs-env-option-row {
    display: flex;
    align-items: center;
    min-width: 0;
    padding: 8px 12px;
    border-radius: 8px;
    background: var(--tabs-env-select-accent);
    box-shadow: inset 0 0 0 1px
      color-mix(in srgb, var(--tabs-env-select-raw) 26%, rgba(15, 23, 42, 0.07));
  }

  .tabs-env-option-label {
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
    font-size: 13px;
    font-weight: 500;
    color: var(--tabs-env-option-fg);
  }
}
</style>

<template>
  <div class="flow-list-page">
    <FlowGroupTreePanel
        ref="groupTreeRef"
        :test-project-id="testProjectId"
        @changed="handleGroupChanged"
        @select="handleGroupSelect"
        @tree-loaded="handleTreeLoaded"
    />
    <div class="flow-list-main">
      <el-row :gutter="10" class="mb8 toolbar-row">
        <el-col :span="1.5">
          <el-button icon="ArrowLeft" plain @click="handleBack">返回项目</el-button>
        </el-col>
        <el-col :span="1.5">
          <el-button
              v-hasPermi="['project:testProject:add']"
              icon="Plus"
              plain
              type="primary"
              @click="handleAdd"
          >新建测试流
          </el-button>
        </el-col>
        <el-col :span="1.5">
          <el-button
              v-hasPermi="['project:testProject:edit']"
              :disabled="!selectedIds.length"
              icon="FolderOpened"
              plain
              @click="openMoveDialog"
          >移动到分组
          </el-button>
        </el-col>
        <el-col :span="1.5">
          <el-button
              v-hasPermi="['project:testProject:remove']"
              :disabled="!selectedIds.length"
              icon="Delete"
              plain
              type="danger"
              @click="handleDelete"
          >删除
          </el-button>
        </el-col>
        <el-col :span="1.5">
          <el-button
              v-hasPermi="['project:testProject:query']"
              icon="Setting"
              plain
              @click="openFlowProjectSetting"
          >项目设置
          </el-button>
        </el-col>
        <el-col :span="6">
          <el-input
              v-model="flowNameQuery"
              clearable
              placeholder="按名称搜索"
              prefix-icon="Search"
              @clear="getList"
              @keyup.enter="getList"
          />
        </el-col>
        <el-col :span="1.5">
          <el-button icon="Search" type="primary" @click="getList">搜索</el-button>
        </el-col>
      </el-row>

      <el-table
          v-loading="loading"
          :data="flowList"
          row-key="testFlowId"
          @row-click="handleRowClick"
          @selection-change="handleSelectionChange"
      >
        <el-table-column align="center" type="selection" width="55"/>
        <el-table-column align="center" label="测试流名称" min-width="180" prop="flowName"/>
        <el-table-column align="center" label="分组" min-width="120" prop="flowGroupName">
          <template #default="scope">
            {{ scope.row.flowGroupName || '未分组' }}
          </template>
        </el-table-column>
        <el-table-column
            align="center"
            label="说明"
            min-width="200"
            prop="flowDescription"
            show-overflow-tooltip
        />
        <el-table-column align="center" label="更新时间" prop="updateTime" width="180"/>
        <el-table-column align="center" label="操作" width="160">
          <template #default="scope">
            <el-button link type="primary" @click.stop="openCanvas(scope.row)">打开</el-button>
            <el-button
                v-hasPermi="['project:testProject:edit']"
                link
                type="primary"
                @click.stop="handleEdit(scope.row)"
            >修改
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <el-dialog
        v-model="metaDialogVisible"
        :title="metaDialogTitle"
        append-to-body
        width="480px"
        @closed="resetMetaForm"
    >
      <el-form ref="metaFormRef" :model="metaForm" :rules="metaRules" label-width="80px">
        <el-form-item label="名称" prop="flowName">
          <el-input v-model="metaForm.flowName" maxlength="100" placeholder="请输入测试流名称"/>
        </el-form-item>
        <el-form-item label="说明" prop="flowDescription">
          <el-input
              v-model="metaForm.flowDescription"
              :rows="3"
              maxlength="500"
              placeholder="可选，简要说明用途"
              show-word-limit
              type="textarea"
          />
        </el-form-item>
        <el-form-item label="分组">
          <el-tree-select
              v-model="metaForm.flowGroupId"
              :data="groupSelectData"
              :props="{ label: 'label', value: 'flowGroupId', children: 'children' }"
              check-strictly
              clearable
              default-expand-all
              placeholder="未分组"
              style="width: 100%"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="metaDialogVisible = false">取消</el-button>
        <el-button :loading="metaSubmitting" type="primary" @click="submitMetaForm">确定</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="moveDialogVisible" append-to-body title="移动到分组" width="420px">
      <el-form label-width="80px">
        <el-form-item label="目标分组">
          <el-tree-select
              v-model="moveTargetGroupId"
              :data="groupSelectData"
              :props="{ label: 'label', value: 'flowGroupId', children: 'children' }"
              check-strictly
              clearable
              default-expand-all
              placeholder="未分组"
              style="width: 100%"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="moveDialogVisible = false">取消</el-button>
        <el-button :loading="moveSubmitting" type="primary" @click="submitMove">确定</el-button>
      </template>
    </el-dialog>

    <ProjectSettingDrawer
        v-model:visible="projectSettingDrawerVisible"
        v-model:http-transport="projectHttpTransport"
        :drawer-title="drawerTitle"
        :forward-available="forwardAvail.available"
        :setting-context="settingContext"
        :setting-form="settingForm"
        :setting-loading="settingLoading"
        :show-http-transport-setting="showHttpTransportSetting"
        :test-project-id="activeTestProjectId || testProjectId"
        @opened="getSettingOnDrawerOpen"
        @refresh-token="handleRefreshToken"
    />
  </div>
</template>

<script setup>
/**
 * 项目测试流列表：左侧目录树 + 右侧表格。
 * 支持按目录/未分组过滤、名称搜索、新建与改元信息（含挂目录）、批量移动目录、删除、进画布。
 */
import { getCurrentInstance, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useRoute, useRouter } from 'vue-router'

import { addTestFlow, delTestFlow, listTestFlow, updateTestFlow } from '@/api/project/testFlow'
import { getTestFlowGroupTree } from '@/api/project/testFlowGroup'
import ProjectSettingDrawer from '@/views/project/testProject/components/ProjectSettingDrawer.vue'
import { useProjectSettingDrawer } from '@/views/project/testProject/composables/useProjectSettingDrawer'

import FlowGroupTreePanel from './components/FlowGroupTreePanel.vue'
import { createEmptyGraph } from './graphAdapter'
import { useProjectTabTitle } from './composables/useProjectTabTitle'

const { proxy } = getCurrentInstance()
const route = useRoute()
const router = useRouter()
const { projectName, loadProjectName } = useProjectTabTitle(route, () => '测试流')

const {
  projectSettingDrawerVisible,
  activeTestProjectId,
  settingContext,
  settingLoading,
  settingForm,
  projectHttpTransport,
  forwardAvail,
  showHttpTransportSetting,
  drawerTitle,
  openProjectSettingById,
  getSettingOnDrawerOpen,
  handleRefreshToken,
} = useProjectSettingDrawer(() => proxy)

const testProjectId = ref(String(route.params.testProjectId ?? ''))
const loading = ref(false)
const flowList = ref([])
const selectedIds = ref([])
const selectedRows = ref([])
const flowNameQuery = ref('')
/** 当前目录过滤：all | ungrouped | group+flowGroupId */
const groupFilter = ref({ mode: 'all' })
const groupTreeRef = ref()
/** 新建/修改/移动弹窗用的目录树数据 */
const groupSelectData = ref([])

const metaDialogVisible = ref(false)
const metaDialogTitle = ref('')
const metaSubmitting = ref(false)
const metaFormRef = ref()
const metaForm = reactive({
  testFlowId: null,
  flowName: '',
  flowDescription: '',
  flowGroupId: null,
})
const metaRules = {
  flowName: [{ required: true, message: '名称不能为空', trigger: 'blur' }],
}

const moveDialogVisible = ref(false)
const moveTargetGroupId = ref(null)
const moveSubmitting = ref(false)

function resetMetaForm() {
  metaForm.testFlowId = null
  metaForm.flowName = ''
  metaForm.flowDescription = ''
  metaForm.flowGroupId = null
  metaFormRef.value?.resetFields?.()
}

function handleBack() {
  router.push(`/project/testProject/detail/${testProjectId.value}`)
}

function openFlowProjectSetting() {
  openProjectSettingById(testProjectId.value, projectName.value)
}

function handleSelectionChange(selection) {
  selectedRows.value = selection
  selectedIds.value = selection.map((item) => item.testFlowId)
}

function openCanvas(row) {
  router.push(`/project/testProject/flow/${testProjectId.value}/${row.testFlowId}`)
}

function handleRowClick(row) {
  openCanvas(row)
}

/** 左侧点选目录后刷新表格 */
function handleGroupSelect(payload) {
  groupFilter.value = payload || { mode: 'all' }
  getList()
}

/** 树加载完成：缓存给分组下拉，避免再打接口 */
function handleTreeLoaded(tree) {
  groupSelectData.value = Array.isArray(tree) ? tree : []
}

/** 目录增删改后：同步下拉缓存并刷新表格（分组名可能变了） */
function handleGroupChanged() {
  syncGroupSelectFromTree()
  getList()
}

function syncGroupSelectFromTree() {
  const cached = groupTreeRef.value?.getGroupTree?.()
  if (Array.isArray(cached) && cached.length >= 0) {
    groupSelectData.value = cached
  }
}

/** 组装列表查询参数（项目 + 名称 + 目录过滤） */
function buildListParams() {
  const params = {
    testProjectId: testProjectId.value,
  }
  const name = String(flowNameQuery.value ?? '').trim()
  if (name) {
    params.flowName = name
  }
  const filter = groupFilter.value
  if (filter?.mode === 'ungrouped') {
    params.ungroupedOnly = true
  } else if (filter?.mode === 'group' && filter.flowGroupId != null) {
    params.flowGroupId = filter.flowGroupId
  }
  return params
}

async function getList() {
  loading.value = true
  try {
    const res = await listTestFlow(buildListParams())
    flowList.value = res.rows ?? []
  } finally {
    loading.value = false
  }
}

/** 优先用左侧树缓存；没有缓存再请求目录树 */
async function ensureGroupSelectData() {
  syncGroupSelectFromTree()
  if (groupSelectData.value.length > 0) {
    return
  }
  const res = await getTestFlowGroupTree(testProjectId.value)
  groupSelectData.value = Array.isArray(res?.data) ? res.data : []
}

/** 新建：默认挂到当前选中目录（若点的是真实目录） */
function handleAdd() {
  resetMetaForm()
  metaForm.flowName = '未命名测试流'
  if (groupFilter.value?.mode === 'group' && groupFilter.value.flowGroupId != null) {
    metaForm.flowGroupId = groupFilter.value.flowGroupId
  } else {
    metaForm.flowGroupId = null
  }
  metaDialogTitle.value = '新建测试流'
  ensureGroupSelectData()
  metaDialogVisible.value = true
}

/** 修改名称、说明与所属目录（不传画布 JSON） */
function handleEdit(row) {
  resetMetaForm()
  metaForm.testFlowId = row.testFlowId
  metaForm.flowName = row.flowName ?? ''
  metaForm.flowDescription = row.flowDescription ?? ''
  metaForm.flowGroupId = row.flowGroupId ?? null
  metaDialogTitle.value = '修改测试流'
  ensureGroupSelectData()
  metaDialogVisible.value = true
}

/**
 * 是否未选目录（应变为未分组）。
 */
function isUngroupedSelection(flowGroupId) {
  return flowGroupId == null || flowGroupId === ''
}

async function submitMetaForm() {
  const formEl = metaFormRef.value
  if (!formEl) return
  const valid = await formEl.validate().catch(() => false)
  if (!valid) return

  const flowName = String(metaForm.flowName ?? '').trim()
  if (!flowName) {
    ElMessage.warning('名称不能为空')
    return
  }
  const flowDescription = String(metaForm.flowDescription ?? '').trim()
  const ungrouped = isUngroupedSelection(metaForm.flowGroupId)

  metaSubmitting.value = true
  try {
    if (metaForm.testFlowId) {
      await updateTestFlow({
        testFlowId: metaForm.testFlowId,
        testProjectId: testProjectId.value,
        flowName,
        flowDescription,
        ...(ungrouped
            ? { clearFlowGroup: true }
            : { flowGroupId: metaForm.flowGroupId }),
      })
      ElMessage.success('修改成功')
      metaDialogVisible.value = false
      await getList()
      return
    }

    const graph = createEmptyGraph()
    const createBody = {
      testProjectId: testProjectId.value,
      flowName,
      flowDescription,
      graphJson: JSON.stringify(graph),
    }
    if (!ungrouped) {
      createBody.flowGroupId = metaForm.flowGroupId
    }
    await addTestFlow(createBody)
    ElMessage.success('创建成功')
    metaDialogVisible.value = false
    await getList()
    const created = flowList.value.find((item) => item.flowName === flowName)
    if (created) {
      openCanvas(created)
    }
  } finally {
    metaSubmitting.value = false
  }
}

async function openMoveDialog() {
  if (!selectedIds.value.length) return
  moveTargetGroupId.value = null
  await ensureGroupSelectData()
  moveDialogVisible.value = true
}

async function submitMove() {
  moveSubmitting.value = true
  try {
    const ungrouped = isUngroupedSelection(moveTargetGroupId.value)
    await Promise.all(
        selectedIds.value.map((id) =>
            ungrouped
                ? updateTestFlow({
                  testFlowId: id,
                  testProjectId: testProjectId.value,
                  clearFlowGroup: true,
                })
                : updateTestFlow({
                  testFlowId: id,
                  testProjectId: testProjectId.value,
                  flowGroupId: moveTargetGroupId.value,
                }),
        ),
    )
    ElMessage.success('移动成功')
    moveDialogVisible.value = false
    selectedIds.value = []
    selectedRows.value = []
    await getList()
  } finally {
    moveSubmitting.value = false
  }
}

async function handleDelete() {
  if (!selectedIds.value.length) return
  await ElMessageBox.confirm('确认删除选中的测试流吗？', '提示', { type: 'warning' })
  await delTestFlow(selectedIds.value.join(','))
  ElMessage.success('删除成功')
  selectedIds.value = []
  selectedRows.value = []
  await getList()
}

onMounted(async () => {
  await Promise.all([getList(), loadProjectName(testProjectId.value)])
})
</script>

<style lang="scss" scoped>
.flow-list-page {
  display: flex;
  height: calc(100vh - 84px);
  min-height: 480px;
  background: var(--pd-bg-page, #f0f5fb);
  overflow: hidden;
}

.flow-list-main {
  flex: 1;
  min-width: 0;
  padding: 16px 20px;
  overflow: auto;
  background: #fff;
}

.toolbar-row {
  align-items: center;
}
</style>

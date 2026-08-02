<template>
  <div class="app-container">
    <el-row :gutter="10" class="mb8">
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
    </el-row>

    <el-table
        v-loading="loading"
        :data="flowList"
        row-key="testFlowId"
        @selection-change="handleSelectionChange"
        @row-click="handleRowClick"
    >
      <el-table-column align="center" type="selection" width="55" />
      <el-table-column align="center" label="测试流名称" min-width="180" prop="flowName" />
      <el-table-column align="center" label="说明" min-width="220" prop="flowDescription" show-overflow-tooltip />
      <el-table-column align="center" label="更新时间" prop="updateTime" width="180" />
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

    <el-dialog v-model="metaDialogVisible" :title="metaDialogTitle" append-to-body width="480px" @closed="resetMetaForm">
      <el-form ref="metaFormRef" :model="metaForm" :rules="metaRules" label-width="80px">
        <el-form-item label="名称" prop="flowName">
          <el-input v-model="metaForm.flowName" maxlength="100" placeholder="请输入测试流名称" />
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
      </el-form>
      <template #footer>
        <el-button @click="metaDialogVisible = false">取消</el-button>
        <el-button :loading="metaSubmitting" type="primary" @click="submitMetaForm">确定</el-button>
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
 * 项目下测试流列表页：分页展示、新建/修改元信息、删除、跳转画布编辑。
 */
import { getCurrentInstance } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import { addTestFlow, delTestFlow, listTestFlow, updateTestFlow } from '@/api/project/testFlow'
import ProjectSettingDrawer from '@/views/project/testProject/components/ProjectSettingDrawer.vue'
import { useProjectSettingDrawer } from '@/views/project/testProject/composables/useProjectSettingDrawer'

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

const metaDialogVisible = ref(false)
const metaDialogTitle = ref('')
const metaSubmitting = ref(false)
const metaFormRef = ref()
const metaForm = reactive({
  testFlowId: null,
  flowName: '',
  flowDescription: '',
})
const metaRules = {
  flowName: [{ required: true, message: '名称不能为空', trigger: 'blur' }],
}

function resetMetaForm() {
  metaForm.testFlowId = null
  metaForm.flowName = ''
  metaForm.flowDescription = ''
  metaFormRef.value?.resetFields?.()
}

function handleBack() {
  router.push(`/project/testProject/detail/${testProjectId.value}`)
}

function openFlowProjectSetting() {
  openProjectSettingById(testProjectId.value, projectName.value)
}

function handleSelectionChange(selection) {
  selectedIds.value = selection.map((item) => item.testFlowId)
}

function openCanvas(row) {
  router.push(`/project/testProject/flow/${testProjectId.value}/${row.testFlowId}`)
}

function handleRowClick(row) {
  openCanvas(row)
}

async function getList() {
  loading.value = true
  try {
    const res = await listTestFlow({ testProjectId: testProjectId.value })
    flowList.value = res.rows ?? []
  } finally {
    loading.value = false
  }
}

/** 弹窗填写名称/说明后创建空 graph_json 记录并跳转画布 */
function handleAdd() {
  resetMetaForm()
  metaForm.flowName = '未命名测试流'
  metaDialogTitle.value = '新建测试流'
  metaDialogVisible.value = true
}

/** 仅改名称与说明，不传 graphJson */
function handleEdit(row) {
  resetMetaForm()
  metaForm.testFlowId = row.testFlowId
  metaForm.flowName = row.flowName ?? ''
  metaForm.flowDescription = row.flowDescription ?? ''
  metaDialogTitle.value = '修改测试流'
  metaDialogVisible.value = true
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

  metaSubmitting.value = true
  try {
    if (metaForm.testFlowId) {
      await updateTestFlow({
        testFlowId: metaForm.testFlowId,
        testProjectId: testProjectId.value,
        flowName,
        flowDescription,
      })
      ElMessage.success('修改成功')
      metaDialogVisible.value = false
      await getList()
      return
    }

    const graph = createEmptyGraph()
    await addTestFlow({
      testProjectId: testProjectId.value,
      flowName,
      flowDescription,
      graphJson: JSON.stringify(graph),
    })
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

async function handleDelete() {
  if (!selectedIds.value.length) return
  await ElMessageBox.confirm('确认删除选中的测试流吗？', '提示', { type: 'warning' })
  await delTestFlow(selectedIds.value.join(','))
  ElMessage.success('删除成功')
  selectedIds.value = []
  await getList()
}

onMounted(async () => {
  await Promise.all([getList(), loadProjectName(testProjectId.value)])
})
</script>

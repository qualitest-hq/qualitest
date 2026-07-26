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
      <el-table-column align="center" label="操作" width="120">
        <template #default="scope">
          <el-button link type="primary" @click.stop="openCanvas(scope.row)">打开</el-button>
        </template>
      </el-table-column>
    </el-table>

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
 * 项目下测试流列表页：分页展示、新建空图、删除、跳转画布编辑。
 */
import { getCurrentInstance } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import { addTestFlow, delTestFlow, listTestFlow } from '@/api/project/testFlow'
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

/** 弹窗输入名称后创建空 graph_json 记录并跳转画布 */
async function handleAdd() {
  const { value } = await ElMessageBox.prompt('请输入测试流名称', '新建测试流', {
    confirmButtonText: '创建',
    cancelButtonText: '取消',
    inputValue: '未命名测试流',
    inputPattern: /\S+/,
    inputErrorMessage: '名称不能为空',
  }).catch(() => ({ value: null }))
  if (!value) return

  const graph = createEmptyGraph()
  await addTestFlow({
    testProjectId: testProjectId.value,
    flowName: value,
    graphJson: JSON.stringify(graph),
  })
  ElMessage.success('创建成功')
  await getList()
  const created = flowList.value.find((item) => item.flowName === value)
  if (created) {
    openCanvas(created)
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

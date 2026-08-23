<template>
  <div class="app-container">
    <el-form v-show="showSearch" ref="queryRef" :inline="true" :model="queryParams" label-width="68px">
      <el-form-item label="项目名" prop="projectName">
        <el-input
            v-model="queryParams.projectName"
            clearable
            placeholder="请输入项目名"
            @keyup.enter="handleQuery"
        />
      </el-form-item>
      <el-form-item label="所有者" prop="ownerName">
        <el-input
            v-model="queryParams.ownerName"
            clearable
            placeholder="请输入所有者"
            @keyup.enter="handleQuery"
        />
      </el-form-item>
      <el-form-item>
        <el-button icon="Search" type="primary" @click="handleQuery">搜索</el-button>
        <el-button icon="Refresh" @click="resetQuery">重置</el-button>
      </el-form-item>
    </el-form>

    <el-row :gutter="10" class="mb8">
      <el-col :span="1.5">
        <el-button
            v-hasPermi="['project:testProject:add']"
            icon="Plus"
            plain
            type="primary"
            @click="handleAdd"
        >新增
        </el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button
            v-hasPermi="['project:testProject:edit']"
            :disabled="single"
            icon="Edit"
            plain
            type="success"
            @click="handleUpdate"
        >修改
        </el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button
            v-hasPermi="['project:testProject:remove']"
            :disabled="multiple"
            icon="Delete"
            plain
            type="danger"
            @click="handleDelete"
        >删除
        </el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button
            v-hasPermi="['project:testProject:export']"
            icon="Download"
            plain
            type="warning"
            @click="handleExport"
        >导出
        </el-button>
      </el-col>
      <right-toolbar v-model:showSearch="showSearch" :columns="columns" @queryTable="getList"></right-toolbar>
    </el-row>

    <el-table v-loading="loading" :data="testProjectList" row-key="testProjectId"
              @selection-change="handleSelectionChange">
      <el-table-column align="center" type="selection" width="55"/>
      <el-table-column v-if="columnVisible['testProjectId']" key="testProjectId" align="center" label="测试项目ID"
                       prop="testProjectId"/>
      <el-table-column v-if="columnVisible['projectName']" key="projectName" align="center" label="项目名"
                       prop="projectName"/>
      <el-table-column v-if="columnVisible['apiCount']" key="apiCount" align="center" label="API数量"
                       prop="apiCount"/>
      <el-table-column v-if="columnVisible['lastApiSyncTime']" key="lastApiSyncTime" align="center" label="最新API同步时间" prop="lastApiSyncTime"
                       width="180">
        <template #default="scope">
          <span>{{ parseTime(scope.row.lastApiSyncTime, '{y}-{m}-{d}') }}</span>
        </template>
      </el-table-column>
      <el-table-column v-if="columnVisible['ownerId']" key="ownerId" align="center" label="所有者ID" prop="ownerId"/>
      <el-table-column v-if="columnVisible['ownerName']" key="ownerName" align="center" label="所有者"
                       prop="ownerName"/>
      <el-table-column align="center" class-name="small-padding fixed-width" label="操作" width="300">
        <template #default="scope">
          <el-button class="test-project-op-btn" link type="primary" @click="handleOpenApi(scope.row)">
            <svg-icon icon-class="project-api"/>
            API
          </el-button>
          <el-button class="test-project-op-btn test-project-op-btn--flow" link type="primary" @click="handleOpenTestFlows(scope.row)">
            <svg-icon class="test-project-op-icon--flow" icon-class="test-flow"/>
            测试流
          </el-button>
          <el-button
              v-hasPermi="['project:testProject:query']"
              icon="Setting"
              link
              type="primary"
              @click="openProjectSetting(scope.row)"
          >设置
          </el-button>
          <el-button v-hasPermi="['project:testProjectMember:list']" icon="User" link type="primary"
                     @click="handleMember(scope.row)">成员
          </el-button>
          <el-button v-hasPermi="['project:testProject:edit']" icon="Edit" link type="primary"
                     @click="handleUpdate(scope.row)">修改
          </el-button>
          <el-button v-hasPermi="['project:testProject:remove']" icon="Delete" link type="primary"
                     @click="handleDelete(scope.row)">删除
          </el-button>
        </template>
      </el-table-column>
    </el-table>

    <pagination
        v-show="total>0"
        v-model:limit="queryParams.pageSize"
        v-model:page="queryParams.pageNum"
        :total="total"
        @pagination="getList"
    />

    <!-- 添加或修改测试项目对话框 -->
    <el-dialog v-model="open" :title="title" append-to-body width="560px">
      <el-form ref="testProjectRef" :model="form" :rules="rules" label-width="80px">
        <el-form-item label="项目名" prop="projectName">
          <el-input v-model="form.projectName" placeholder="请输入项目名"/>
        </el-form-item>
        <el-form-item v-if="!form.testProjectId" label="项目模板" prop="templateIds">
          <div class="test-project-template-field">
            <p class="test-project-template-tip">
              新建项目须至少勾选一套项目模板，用于登录口免登与凭证抽取。商城双端建议先勾「管理端 Bearer」，再勾「客户端 Bearer」。
            </p>
            <AuthTemplateCheckboxList
                v-model="form.templateIds"
                :loading="templateLoading"
                :templates="enabledTemplates"
            />
          </div>
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button type="primary" @click="submitForm">确 定</el-button>
          <el-button @click="cancel">取 消</el-button>
        </div>
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
        :test-project-id="activeTestProjectId"
        @opened="getSettingOnDrawerOpen"
        @refresh-token="handleRefreshToken"
    />
  </div>
</template>

<script name="TestProject" setup>
import {
  addTestProject,
  delTestProject,
  getTestProject,
  listTestProject,
  updateTestProject
} from "@/api/project/testProject";
import ProjectSettingDrawer from './components/ProjectSettingDrawer.vue'
import AuthTemplateCheckboxList from './components/AuthTemplateCheckboxList.vue'
import { useProjectSettingDrawer } from './composables/useProjectSettingDrawer'
import { toTemplateIds, useEnabledAuthTemplates } from './composables/useEnabledAuthTemplates'
import {useRouter} from 'vue-router'

const {proxy} = getCurrentInstance()
const router = useRouter()

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
  openProjectSetting,
  getSettingOnDrawerOpen,
  handleRefreshToken,
} = useProjectSettingDrawer(() => proxy)

const {
  templateLoading,
  enabledTemplates,
  loadEnabledTemplates,
} = useEnabledAuthTemplates()

const testProjectList = ref([])
const open = ref(false)
const loading = ref(true)
const showSearch = ref(true)
const ids = ref([])
const single = ref(true)
const multiple = ref(true)
const total = ref(0)
const title = ref("")

// 列显隐信息
const columns = ref([
  {key: 'testProjectId', label: `测试项目ID`, visible: false},
  {key: 'projectName', label: `项目名`, visible: true},
  {key: 'apiCount', label: `API数量`, visible: true},
  {key: 'lastApiSyncTime', label: `最新API同步时间`, visible: true},
  {key: 'ownerId', label: `所有者ID`, visible: false},
  {key: 'ownerName', label: `所有者`, visible: true},
])

// 列显隐状态对象（以字段名为key）
const columnVisible = computed(() => {
  const result = {};
  columns.value.forEach(col => {
    result[col.key] = col.visible;
  });
  return result;
})

const data = reactive({
  form: {},
  queryParams: {
    pageNum: 1,
    pageSize: 10,
    projectName: null,
    ownerName: null,
  },
  rules: {
    projectName: [
      {required: true, message: "项目名不能为空", trigger: "blur"}
    ],
    templateIds: [
      {
        validator: (_rule, value, callback) => {
          if (form.value.testProjectId != null) {
            callback()
            return
          }
          if (!Array.isArray(value) || value.length === 0) {
            callback(new Error('请至少勾选一套项目模板'))
            return
          }
          callback()
        },
        trigger: 'change',
      },
    ],
  }
})

const {queryParams, form, rules} = toRefs(data)

/** 查询测试项目列表 */
function getList() {
  loading.value = true
  listTestProject(queryParams.value).then(response => {
    testProjectList.value = response.rows
    total.value = response.total
    loading.value = false
  })
}

// 取消按钮
function cancel() {
  open.value = false
  reset()
}

// 表单重置
function reset() {
  form.value = {
    testProjectId: null,
    projectName: null,
    lastApiSyncTime: null,
    ownerId: null,
    templateIds: [],
  }
  proxy.resetForm("testProjectRef")
}

/** 搜索按钮操作 */
function handleQuery() {
  queryParams.value.pageNum = 1
  getList()
}

/** 重置按钮操作 */
function resetQuery() {
  proxy.resetForm("queryRef")
  handleQuery()
}

// 多选框选中数据
function handleSelectionChange(selection) {
  ids.value = selection.map(item => item.testProjectId)
  single.value = selection.length != 1
  multiple.value = !selection.length
}

/** 新增按钮操作 */
function handleAdd() {
  reset()
  loadEnabledTemplates()
  open.value = true
  title.value = "添加测试项目"
}

/** 修改按钮操作 */
function handleUpdate(row) {
  reset()
  const _testProjectId = row.testProjectId || ids.value
  getTestProject(_testProjectId).then(response => {
    form.value = response.data
    open.value = true
    title.value = "修改测试项目"
  })
}

/** 提交按钮 */
function submitForm() {
  proxy.$refs["testProjectRef"].validate(valid => {
    if (valid) {
      if (form.value.testProjectId != null) {
        updateTestProject(form.value).then(response => {
          proxy.$modal.msgSuccess("修改成功")
          open.value = false
          getList()
        })
      } else {
        const payload = {
          projectName: form.value.projectName,
          templateIds: toTemplateIds(form.value.templateIds),
        }
        addTestProject(payload).then(response => {
          proxy.$modal.msgSuccess("新增成功")
          open.value = false
          getList()
        })
      }
    }
  })
}

/** 删除按钮操作 */
function handleDelete(row) {
  const _testProjectIds = row.testProjectId || ids.value
  proxy.$modal.confirm('是否确认删除测试项目编号为"' + _testProjectIds + '"的数据项？').then(function () {
    return delTestProject(_testProjectIds)
  }).then(() => {
    getList()
    proxy.$modal.msgSuccess("删除成功")
  }).catch(() => {
  })
}

/** 导出按钮操作 */
function handleExport() {
  proxy.download('project/testProject/export', {
    ...queryParams.value
  }, `testProject_${new Date().getTime()}.xlsx`)
}

/** 打开项目 API 工作台（接口树、调试、文档） */
function handleOpenApi(row) {
  router.push(`/project/testProject/detail/${row.testProjectId}`)
}

/** 打开项目下测试流列表 */
function handleOpenTestFlows(row) {
  router.push(`/project/testProject/flows/${row.testProjectId}`)
}

/** 项目成员 */
function handleMember(row) {
  router.push({
    path: '/project/testProject/member',
    query: {
      testProjectId: row.testProjectId
    }
  })
}

getList()
</script>

<style scoped lang="scss">
.test-project-template-field {
  width: 100%;
}

.test-project-template-tip {
  margin: 0 0 10px;
  font-size: 12px;
  line-height: 1.5;
  color: var(--el-text-color-secondary);
}

.test-project-op-btn {
  :deep(.svg-icon) {
    margin-right: 6px;
    font-size: 14px;
    width: 1em;
    height: 1em;
    vertical-align: -0.15em;
  }

  &--flow :deep(.test-project-op-icon--flow) {
    font-size: 17px;
    width: 17px;
    height: 17px;
    vertical-align: -0.12em;
  }
}
</style>

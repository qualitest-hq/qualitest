<template>
  <div class="app-container">
    <el-form
      v-show="showSearch"
      ref="queryRef"
      :inline="true"
      :model="queryParams"
      label-width="80px"
    >
      <el-form-item label="模板名称" prop="templateName">
        <el-input
          v-model="queryParams.templateName"
          clearable
          placeholder="模糊搜索"
          style="width: 180px"
          @keyup.enter="handleQuery"
        />
      </el-form-item>
      <el-form-item label="启用状态" prop="enableStatus">
        <el-select
          v-model="queryParams.enableStatus"
          clearable
          placeholder="全部"
          style="width: 120px"
        >
          <el-option
            v-for="item in enableStatusOptions"
            :key="item.value"
            :label="item.label"
            :value="item.value"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="内置" prop="builtinStatus">
        <el-select
          v-model="queryParams.builtinStatus"
          clearable
          placeholder="全部"
          style="width: 120px"
        >
          <el-option label="内置" :value="1" />
          <el-option label="自定义" :value="0" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button icon="Search" type="primary" @click="handleQuery">搜索</el-button>
        <el-button icon="Refresh" @click="resetQuery">重置</el-button>
      </el-form-item>
    </el-form>

    <el-row :gutter="10" class="mb8">
      <el-col :span="1.5">
        <el-button
          v-hasPermi="['project:testProjectTemplate:add']"
          icon="Plus"
          plain
          type="primary"
          @click="handleAdd"
        >新增</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-tooltip
          :disabled="!single || !selectedBuiltin"
          content="内置模板只读，请克隆后修改"
        >
          <span class="tpl-op-tip-wrap">
            <el-button
              v-hasPermi="['project:testProjectTemplate:edit']"
              :disabled="single || selectedBuiltin"
              icon="Edit"
              plain
              type="success"
              @click="handleUpdate()"
            >修改</el-button>
          </span>
        </el-tooltip>
      </el-col>
      <el-col :span="1.5">
        <el-tooltip
          :disabled="!multiple || !selectedHasBuiltin"
          content="内置模板不可删除"
        >
          <span class="tpl-op-tip-wrap">
            <el-button
              v-hasPermi="['project:testProjectTemplate:remove']"
              :disabled="multiple || selectedHasBuiltin"
              icon="Delete"
              plain
              type="danger"
              @click="handleDelete()"
            >删除</el-button>
          </span>
        </el-tooltip>
      </el-col>
      <right-toolbar v-model:showSearch="showSearch" :columns="columns" @queryTable="getList" />
    </el-row>

    <el-table
      v-loading="loading"
      :data="templateList"
      row-key="testProjectTemplateId"
      @selection-change="handleSelectionChange"
    >
      <el-table-column align="center" type="selection" width="48" />
      <el-table-column
        v-if="columnVisible.templateName"
        key="templateName"
        label="模板名称"
        min-width="160"
        prop="templateName"
        show-overflow-tooltip
      >
        <template #default="scope">
          <span>{{ scope.row.templateName }}</span>
          <el-tag v-if="scope.row.builtinStatus === 1" class="tpl-builtin-tag" size="small" type="info">内置</el-tag>
        </template>
      </el-table-column>
      <el-table-column
        v-if="columnVisible.headerName"
        key="headerName"
        label="鉴权头"
        prop="headerName"
        width="120"
        show-overflow-tooltip
      />
      <el-table-column
        v-if="columnVisible.headerValueTemplate"
        key="headerValueTemplate"
        label="值模板"
        min-width="180"
        prop="headerValueTemplate"
        show-overflow-tooltip
      />
      <el-table-column
        v-if="columnVisible.matchConfig"
        key="matchConfig"
        label="pathPrefix"
        min-width="120"
        show-overflow-tooltip
      >
        <template #default="scope">
          {{ formatPathPrefixSummary(scope.row.matchConfig) }}
        </template>
      </el-table-column>
      <el-table-column
        v-if="columnVisible.enableStatus"
        align="center"
        key="enableStatus"
        label="启用"
        prop="enableStatus"
        width="80"
      >
        <template #default="scope">
          <dict-tag :options="enableStatusOptions" :value="scope.row.enableStatus" />
        </template>
      </el-table-column>
      <el-table-column
        v-if="columnVisible.sortNum"
        align="center"
        key="sortNum"
        label="排序"
        prop="sortNum"
        width="72"
      />
      <el-table-column
        v-if="columnVisible.remark"
        key="remark"
        label="备注"
        min-width="120"
        prop="remark"
        show-overflow-tooltip
      />
      <el-table-column
        v-if="columnVisible.updateTime"
        align="center"
        key="updateTime"
        label="更新时间"
        prop="updateTime"
        width="160"
      />
      <el-table-column align="center" class-name="small-padding fixed-width" label="操作" width="200">
        <template #default="scope">
          <el-button
            v-hasPermi="['project:testProjectTemplate:query']"
            icon="View"
            link
            type="primary"
            @click="handleView(scope.row)"
          >查看</el-button>
          <el-tooltip
            :disabled="scope.row.builtinStatus !== 1"
            content="内置模板只读，请克隆后修改"
          >
            <span class="tpl-op-tip-wrap">
              <el-button
                v-hasPermi="['project:testProjectTemplate:edit']"
                :disabled="scope.row.builtinStatus === 1"
                icon="Edit"
                link
                type="primary"
                @click="handleUpdate(scope.row)"
              >修改</el-button>
            </span>
          </el-tooltip>
          <el-button
            v-hasPermi="['project:testProjectTemplate:add']"
            icon="CopyDocument"
            link
            type="primary"
            @click="handleClone(scope.row)"
          >克隆</el-button>
          <el-button
            v-if="scope.row.builtinStatus !== 1"
            v-hasPermi="['project:testProjectTemplate:remove']"
            icon="Delete"
            link
            type="primary"
            @click="handleDelete(scope.row)"
          >删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <pagination
      v-show="total > 0"
      v-model:limit="queryParams.pageSize"
      v-model:page="queryParams.pageNum"
      :total="total"
      @pagination="getList"
    />

    <el-drawer
      v-model="open"
      :title="title"
      append-to-body
      class="test-project-template-drawer"
      destroy-on-close
      size="72vw"
    >
      <el-alert
        v-if="isReadonlyForm"
        :closable="false"
        class="tpl-readonly-alert"
        show-icon
        title="内置模板只读；可克隆为自定义模板后再修改。"
        type="info"
      />
      <el-form
        ref="templateRef"
        :model="form"
        :rules="rules"
        class="tpl-drawer-form"
        label-width="108px"
      >
        <div class="tpl-section-title">基本信息</div>
        <el-row v-if="isReadonlyForm" :gutter="16" class="tpl-readonly-meta">
          <el-col :span="12">
            <el-form-item label="模板名称">
              <span class="tpl-readonly-text">{{ form.templateName || '—' }}</span>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="鉴权头名称">
              <span class="tpl-readonly-text">{{ form.headerName || '—' }}</span>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="鉴权头值模板">
              <span class="tpl-readonly-text">{{ form.headerValueTemplate || '—' }}</span>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="pathPrefix">
              <span class="tpl-readonly-text">{{ form.pathPrefixText || '—' }}</span>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="启用状态">
              <span class="tpl-readonly-text">{{ enableStatusLabel(form.enableStatus) }}</span>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="排序">
              <span class="tpl-readonly-text">{{ form.sortNum ?? '—' }}</span>
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="备注">
              <span class="tpl-readonly-text">{{ form.remark || '—' }}</span>
            </el-form-item>
          </el-col>
        </el-row>
        <el-row v-else :gutter="16">
          <el-col :span="12">
            <el-form-item label="模板名称" prop="templateName">
              <el-input
                v-model="form.templateName"
                maxlength="100"
                placeholder="拷贝到项目后作为 Profile 名称"
                show-word-limit
              />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="鉴权头名称" prop="headerName">
              <el-input
                v-model="form.headerName"
                maxlength="64"
                placeholder="Authorization"
              />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="鉴权头值模板" prop="headerValueTemplate">
              <el-input
                v-model="form.headerValueTemplate"
                maxlength="512"
                placeholder="Bearer {{flow.token}}"
              />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="pathPrefix" prop="pathPrefixText">
              <el-input
                v-model="form.pathPrefixText"
                :rows="2"
                placeholder="每行一条，如 /api/；禁止单独 /"
                type="textarea"
              />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="启用状态" prop="enableStatus">
              <el-radio-group v-model="form.enableStatus">
                <el-radio
                  v-for="item in enableStatusOptions"
                  :key="item.value"
                  :value="item.value"
                >{{ item.label }}</el-radio>
              </el-radio-group>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="排序" prop="sortNum">
              <el-input-number
                v-model="form.sortNum"
                :min="0"
                controls-position="right"
              />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="备注" prop="remark">
              <el-input
                v-model="form.remark"
                :rows="2"
                maxlength="256"
                placeholder="可选"
                type="textarea"
              />
            </el-form-item>
          </el-col>
        </el-row>

        <el-form-item prop="apis" class="tpl-apis-form-item" label-width="0">
          <PrefabricatedApiPanel
            ref="apiPanelRef"
            v-model="form.apis"
            :read-only="isReadonlyForm"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="tpl-drawer-footer">
          <el-button
            v-if="isReadonlyForm"
            v-hasPermi="['project:testProjectTemplate:add']"
            type="primary"
            @click="handleCloneFromDialog"
          >克隆为自定义</el-button>
          <el-button v-if="!isReadonlyForm" type="primary" @click="submitForm">确 定</el-button>
          <el-button @click="cancel">{{ isReadonlyForm ? '关 闭' : '取 消' }}</el-button>
        </div>
      </template>
    </el-drawer>
  </div>
</template>

<script setup name="TestProjectTemplate">
import {
  addTestProjectTemplate,
  cloneTestProjectTemplate,
  delTestProjectTemplate,
  getTestProjectTemplate,
  listTestProjectTemplate,
  updateTestProjectTemplate,
} from '@/api/project/testProjectTemplate'
import PrefabricatedApiPanel from './components/PrefabricatedApiPanel.vue'
import {
  emptyTemplateForm,
  formatPathPrefixSummary,
  formToPayload,
  templateToForm,
  validateApis,
} from './utils/templateForm'

const { proxy } = getCurrentInstance()

const enableStatusOptions = [
  { label: '启用', value: 1, elTagType: 'success' },
  { label: '禁用', value: 0, elTagType: 'danger' },
]

const templateList = ref([])
const open = ref(false)
const loading = ref(true)
const showSearch = ref(true)
const ids = ref([])
const selectedRows = ref([])
const single = ref(true)
const multiple = ref(true)
const total = ref(0)
const title = ref('')
const dialogMode = ref('add')

const columns = ref([
  { key: 'templateName', label: '模板名称', visible: true },
  { key: 'headerName', label: '鉴权头', visible: true },
  { key: 'headerValueTemplate', label: '值模板', visible: true },
  { key: 'matchConfig', label: 'pathPrefix', visible: true },
  { key: 'enableStatus', label: '启用', visible: true },
  { key: 'sortNum', label: '排序', visible: true },
  { key: 'remark', label: '备注', visible: false },
  { key: 'updateTime', label: '更新时间', visible: true },
])

const columnVisible = computed(() => {
  const result = {}
  columns.value.forEach((col) => {
    result[col.key] = col.visible
  })
  return result
})

const selectedBuiltin = computed(() => {
  if (selectedRows.value.length !== 1) return false
  return selectedRows.value[0]?.builtinStatus === 1
})

const selectedHasBuiltin = computed(() => {
  return selectedRows.value.some((row) => row?.builtinStatus === 1)
})

const isReadonlyForm = computed(() => dialogMode.value === 'view')

function enableStatusLabel(status) {
  const found = enableStatusOptions.find((item) => item.value === status)
  return found?.label || '—'
}

const data = reactive({
  form: emptyTemplateForm(),
  queryParams: {
    pageNum: 1,
    pageSize: 10,
    templateName: undefined,
    enableStatus: undefined,
    builtinStatus: undefined,
  },
  rules: {
    templateName: [{ required: true, message: '模板名称不能为空', trigger: 'blur' }],
    headerName: [{ required: true, message: '鉴权头名称不能为空', trigger: 'blur' }],
    headerValueTemplate: [{ required: true, message: '鉴权头值模板不能为空', trigger: 'blur' }],
    apis: [{
      validator: (_rule, value, callback) => {
        try {
          validateApis(value)
          callback()
        } catch (e) {
          callback(new Error(String(e?.message || e)))
        }
      },
      trigger: 'change',
    }],
  },
})

const { queryParams, form, rules } = toRefs(data)
const apiPanelRef = ref(null)

function getList() {
  loading.value = true
  listTestProjectTemplate(queryParams.value).then((res) => {
    templateList.value = res.rows
    total.value = res.total
    loading.value = false
  })
}

function handleQuery() {
  queryParams.value.pageNum = 1
  getList()
}

function resetQuery() {
  proxy.resetForm('queryRef')
  handleQuery()
}

function handleSelectionChange(selection) {
  selectedRows.value = selection
  ids.value = selection.map((item) => item.testProjectTemplateId)
  single.value = selection.length !== 1
  multiple.value = !selection.length
}

function resetFormState() {
  form.value = emptyTemplateForm()
  proxy.resetForm('templateRef')
}

function cancel() {
  open.value = false
  resetFormState()
}

function openDialog(mode, row) {
  dialogMode.value = mode
  resetFormState()
  if (row) {
    form.value = templateToForm(row)
  }
  const titles = { add: '新增项目模板', edit: '修改项目模板', view: '查看项目模板' }
  title.value = titles[mode] || '项目模板'
  open.value = true
}

function handleAdd() {
  openDialog('add')
}

function handleView(row) {
  const id = row?.testProjectTemplateId ?? ids.value[0]
  getTestProjectTemplate(id).then((res) => {
    openDialog('view', res.data)
  })
}

function handleUpdate(row) {
  const target = row || selectedRows.value[0]
  if (!target || target.builtinStatus === 1) return
  const id = target.testProjectTemplateId
  getTestProjectTemplate(id).then((res) => {
    openDialog('edit', res.data)
  })
}

function cloneTemplateAndEdit(id) {
  if (!id) return
  cloneTestProjectTemplate(id).then((res) => {
    proxy.$modal.msgSuccess('克隆成功')
    getList()
    return getTestProjectTemplate(res.data)
  }).then((res) => {
    if (res?.data) {
      // 查看抽屉已打开时需先关再开，否则 destroy-on-close 不会重建且标题/只读态可能卡住
      open.value = false
      nextTick(() => openDialog('edit', res.data))
    }
  })
}

function handleClone(row) {
  cloneTemplateAndEdit(row?.testProjectTemplateId)
}

function handleCloneFromDialog() {
  cloneTemplateAndEdit(form.value.testProjectTemplateId)
}

function submitForm() {
  const panelResult = apiPanelRef.value?.flushAndValidate?.()
  if (panelResult && !panelResult.valid) {
    proxy.$modal.msgError(panelResult.message)
    return
  }
  proxy.$refs.templateRef.validate((valid) => {
    if (!valid) return
    let payload
    try {
      payload = formToPayload(form.value)
    } catch (e) {
      proxy.$modal.msgError(String(e?.message || e))
      return
    }
    const isEdit = dialogMode.value === 'edit' && payload.testProjectTemplateId
    const req = isEdit ? updateTestProjectTemplate(payload) : addTestProjectTemplate(payload)
    req.then(() => {
      proxy.$modal.msgSuccess(isEdit ? '修改成功' : '新增成功')
      open.value = false
      getList()
    })
  })
}

function handleDelete(row) {
  const idList = row?.testProjectTemplateId ? [row.testProjectTemplateId] : ids.value
  if (!idList.length) return
  if (row?.builtinStatus === 1 || idList.some((id) => {
    const found = templateList.value.find((t) => t.testProjectTemplateId === id)
    return found?.builtinStatus === 1
  })) {
    proxy.$modal.msgWarning('内置模板不可删除')
    return
  }
  proxy.$modal.confirm('是否确认删除所选项目模板？').then(() => {
    return delTestProjectTemplate(idList.join(','))
  }).then(() => {
    getList()
    proxy.$modal.msgSuccess('删除成功')
  }).catch(() => {})
}

getList()
</script>

<style scoped lang="scss">
.tpl-builtin-tag {
  margin-left: 6px;
}

.tpl-op-tip-wrap {
  display: inline-flex;
  vertical-align: middle;
}

.tpl-readonly-alert {
  margin-bottom: 16px;
}

.tpl-drawer-form {
  padding-right: 8px;
}

.tpl-section-title {
  margin: 0 0 12px;
  font-size: 14px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.tpl-readonly-text {
  display: inline-block;
  min-height: 22px;
  line-height: 22px;
  font-size: 14px;
  color: var(--el-text-color-primary);
  word-break: break-all;
  white-space: pre-wrap;
}

.tpl-readonly-meta {
  :deep(.el-form-item) {
    margin-bottom: 12px;
  }
}

.tpl-apis-form-item {
  :deep(.el-form-item__content) {
    display: block;
  }
}

.tpl-drawer-footer {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}
</style>

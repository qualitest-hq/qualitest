<template>
  <div class="app-container">
    <el-form
      v-show="showSearch"
      ref="queryRef"
      :inline="true"
      :model="queryParams"
      class="ai-prompt-template-query"
      label-width="80px"
    >
      <el-form-item label="模板范围" prop="templateScope">
        <el-select
          v-model="queryParams.templateScope"
          clearable
          placeholder="全部"
          style="width: 160px"
        >
          <el-option
            v-for="item in templateScopeOptions"
            :key="item.value"
            :label="item.label"
            :value="item.value"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="模板标题" prop="templateTitle">
        <el-input
          v-model="queryParams.templateTitle"
          clearable
          placeholder="模糊搜索标题"
          style="width: 180px"
          @keyup.enter="handleQuery"
        />
      </el-form-item>
      <el-form-item label="测试项目" prop="testProjectId">
        <el-select
          v-model="queryParams.testProjectId"
          clearable
          filterable
          placeholder="项目级筛选"
          style="width: 200px"
        >
          <el-option
            v-for="item in projectOptions"
            :key="item.testProjectId"
            :label="item.projectName"
            :value="item.testProjectId"
          />
        </el-select>
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
          v-hasPermi="['ai:aiPromptTemplate:add']"
          icon="Plus"
          plain
          type="primary"
          @click="handleAdd"
        >新增</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button
          v-hasPermi="['ai:aiPromptTemplate:edit']"
          :disabled="single"
          icon="Edit"
          plain
          type="success"
          @click="handleUpdate"
        >修改</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button
          v-hasPermi="['ai:aiPromptTemplate:remove']"
          :disabled="multiple"
          icon="Delete"
          plain
          type="danger"
          @click="handleDelete()"
        >删除</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button
          v-hasPermi="['ai:aiPromptTemplate:export']"
          icon="Download"
          plain
          type="warning"
          @click="handleExport"
        >导出</el-button>
      </el-col>
      <right-toolbar v-model:showSearch="showSearch" :columns="columns" @queryTable="getList" />
    </el-row>

    <el-table
      v-loading="loading"
      :data="aiPromptTemplateList"
      row-key="aiPromptTemplateId"
      @selection-change="handleSelectionChange"
    >
      <el-table-column align="center" type="selection" width="48" :selectable="rowSelectable" />
      <el-table-column
        v-if="columnVisible.aiPromptTemplateId"
        align="center"
        key="aiPromptTemplateId"
        label="ID"
        prop="aiPromptTemplateId"
        width="180"
        show-overflow-tooltip
      />
      <el-table-column
        v-if="columnVisible.templateTitle"
        key="templateTitle"
        label="模板标题"
        min-width="160"
        prop="templateTitle"
        show-overflow-tooltip
      >
        <template #default="scope">
          <span>{{ scope.row.templateTitle }}</span>
          <el-tag v-if="scope.row.builtinStatus === 1" class="builtin-tag" size="small" type="info">内置</el-tag>
        </template>
      </el-table-column>
      <el-table-column
        v-if="columnVisible.templateDescription"
        key="templateDescription"
        label="说明"
        min-width="160"
        prop="templateDescription"
        show-overflow-tooltip
      />
      <el-table-column
        v-if="columnVisible.templateScope"
        align="center"
        key="templateScope"
        label="范围"
        prop="templateScope"
        width="88"
      >
        <template #default="scope">
          <dict-tag :options="templateScopeOptions" :value="scope.row.templateScope" />
        </template>
      </el-table-column>
      <el-table-column
        v-if="columnVisible.templateContent"
        key="templateContent"
        label="正文预览"
        min-width="200"
        prop="templateContent"
        show-overflow-tooltip
      >
        <template #default="scope">
          <span class="content-preview">{{ contentPreview(scope.row.templateContent) }}</span>
        </template>
      </el-table-column>
      <el-table-column
        v-if="columnVisible.testProjectId"
        key="testProjectId"
        label="测试项目"
        min-width="140"
        show-overflow-tooltip
      >
        <template #default="scope">
          <span v-if="scope.row.templateScope === 'project'">{{ projectNameOf(scope.row.testProjectId) }}</span>
          <span v-else class="text-muted">—</span>
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
        v-if="columnVisible.sessionScene"
        align="center"
        key="sessionScene"
        label="场景"
        prop="sessionScene"
        width="130"
        show-overflow-tooltip
      />
      <el-table-column align="center" class-name="small-padding fixed-width" label="操作" width="140">
        <template #default="scope">
          <el-button
            v-hasPermi="['ai:aiPromptTemplate:edit']"
            icon="Edit"
            link
            type="primary"
            @click="handleUpdate(scope.row)"
          >修改</el-button>
          <el-button
            v-if="scope.row.builtinStatus !== 1"
            v-hasPermi="['ai:aiPromptTemplate:remove']"
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

    <el-dialog
      v-model="open"
      :title="title"
      append-to-body
      class="ai-prompt-template-dialog"
      width="720px"
    >
      <el-alert
        v-if="isBuiltinForm"
        :closable="false"
        class="builtin-alert"
        show-icon
        title="内置模板：范围与内置标记不可改；禁用后 AI 助手面板将不再展示。"
        type="info"
      />
      <el-form
        ref="aiPromptTemplateRef"
        :model="form"
        :rules="rules"
        class="ai-prompt-template-form"
        label-width="96px"
      >
        <el-divider content-position="left">归属</el-divider>
        <el-form-item label="模板范围" prop="templateScope">
          <el-radio-group v-model="form.templateScope" :disabled="isBuiltinForm">
            <el-radio
              v-for="item in templateScopeOptions"
              :key="item.value"
              :value="item.value"
            >{{ item.label }}</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item v-if="form.templateScope === 'project'" label="测试项目" prop="testProjectId">
          <el-select
            v-model="form.testProjectId"
            :disabled="isBuiltinForm"
            class="form-control-full"
            filterable
            placeholder="请选择测试项目"
          >
            <el-option
              v-for="item in projectOptions"
              :key="item.testProjectId"
              :label="item.projectName"
              :value="item.testProjectId"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="会话场景" prop="sessionScene">
          <el-select v-model="form.sessionScene" class="form-control-full" placeholder="请选择场景">
            <el-option
              v-for="item in sessionSceneOptions"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </el-select>
        </el-form-item>

        <el-divider content-position="left">模板内容</el-divider>
        <el-form-item label="模板标题" prop="templateTitle">
          <el-input
            v-model="form.templateTitle"
            maxlength="128"
            placeholder="面板中展示的标题"
            show-word-limit
          />
        </el-form-item>
        <el-form-item label="模板说明" prop="templateDescription">
          <el-input
            v-model="form.templateDescription"
            maxlength="255"
            placeholder="列表中的简短说明（可选）"
            show-word-limit
          />
        </el-form-item>
        <el-form-item label="模板正文" prop="templateContent">
          <el-input
            v-model="form.templateContent"
            :rows="10"
            maxlength="1024"
            placeholder="支持 {占位符}、@Api、@Node、@Run 等；发送前由用户替换占位符"
            show-word-limit
            type="textarea"
          />
        </el-form-item>
        <el-form-item label="备注" prop="remark">
          <el-input
            v-model="form.remark"
            :rows="3"
            maxlength="256"
            placeholder="可选，如管理端备忘"
            show-word-limit
            type="textarea"
          />
        </el-form-item>

        <el-divider content-position="left">展示设置</el-divider>
        <el-form-item label="启用状态" prop="enableStatus">
          <el-radio-group v-model="form.enableStatus">
            <el-radio
              v-for="item in enableStatusOptions"
              :key="item.value"
              :value="item.value"
            >{{ item.label }}</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="排序" prop="sortNum">
          <el-input-number v-model="form.sortNum" :min="0" class="form-control-sort" controls-position="right" />
          <span class="form-hint">同分类内越小越靠前</span>
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button type="primary" @click="submitForm">确 定</el-button>
          <el-button @click="cancel">取 消</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup name="AiPromptTemplate">
import {
  addAiPromptTemplate,
  delAiPromptTemplate,
  getAiPromptTemplate,
  listAiPromptTemplate,
  updateAiPromptTemplate,
} from '@/api/ai/aiPromptTemplate'
import { listTestProject } from '@/api/project/testProject'

const { proxy } = getCurrentInstance()

const DEFAULT_SESSION_SCENE = 'test_flow_design'

const templateScopeOptions = [
  { label: '平台', value: 'platform', elTagType: 'primary' },
  { label: '项目', value: 'project', elTagType: 'success' },
]

/** 提示词模板适用的 AI 助手会话场景（管理页筛选项与入库字段） */
const sessionSceneOptions = [
  { label: '测试流 AI 助手', value: DEFAULT_SESSION_SCENE },
  { label: 'AI API 助手', value: 'test_api_design' },
]

const enableStatusOptions = [
  { label: '启用', value: 1, elTagType: 'success' },
  { label: '禁用', value: 0, elTagType: 'danger' },
]

const aiPromptTemplateList = ref([])
const projectOptions = ref([])
const open = ref(false)
const loading = ref(true)
const showSearch = ref(true)
const ids = ref([])
const single = ref(true)
const multiple = ref(true)
const total = ref(0)
const title = ref('')
const isEdit = ref(false)

const columns = ref([
  { key: 'aiPromptTemplateId', label: 'ID', visible: false },
  { key: 'templateTitle', label: '模板标题', visible: true },
  { key: 'templateScope', label: '范围', visible: true },
  { key: 'templateDescription', label: '说明', visible: true },
  { key: 'templateContent', label: '正文预览', visible: true },
  { key: 'testProjectId', label: '测试项目', visible: true },
  { key: 'enableStatus', label: '启用', visible: true },
  { key: 'sortNum', label: '排序', visible: true },
  { key: 'sessionScene', label: '场景', visible: false },
  { key: 'builtinStatus', label: '内置', visible: false },
])

const columnVisible = computed(() => {
  const result = {}
  columns.value.forEach((col) => {
    result[col.key] = col.visible
  })
  return result
})

const projectNameMap = computed(() => {
  const map = new Map()
  projectOptions.value.forEach((item) => {
    map.set(String(item.testProjectId), item.projectName)
  })
  return map
})

const isBuiltinForm = computed(() => form.value.builtinStatus === 1)

const data = reactive({
  form: {},
  queryParams: {
    pageNum: 1,
    pageSize: 10,
    templateScope: null,
    testProjectId: null,
    sessionScene: null,
    templateTitle: null,
    builtinStatus: null,
    enableStatus: null,
  },
  rules: {
    templateScope: [{ required: true, message: '请选择模板范围', trigger: 'change' }],
    testProjectId: [{
      validator: (_rule, value, callback) => {
        if (form.value.templateScope === 'project' && (value == null || value === '')) {
          callback(new Error('项目级模板必须选择测试项目'))
          return
        }
        callback()
      },
      trigger: 'change',
    }],
    sessionScene: [{ required: true, message: '请选择会话场景', trigger: 'change' }],
    templateTitle: [{ required: true, message: '模板标题不能为空', trigger: 'blur' }],
    templateContent: [{ required: true, message: '模板正文不能为空', trigger: 'blur' }],
    enableStatus: [{ required: true, message: '请选择启用状态', trigger: 'change' }],
    sortNum: [{ required: true, message: '排序不能为空', trigger: 'blur' }],
  },
})

const { queryParams, form, rules } = toRefs(data)

function projectNameOf(testProjectId) {
  if (testProjectId == null || testProjectId === '') return '—'
  return projectNameMap.value.get(String(testProjectId)) || testProjectId
}

function contentPreview(content) {
  if (!content) return ''
  return String(content).replace(/\s+/g, ' ').trim()
}

function rowSelectable(row) {
  return row.builtinStatus !== 1
}

function loadProjectOptions() {
  return listTestProject({ pageNum: 1, pageSize: 999 }).then((response) => {
    projectOptions.value = response.rows || []
  })
}

function getList() {
  loading.value = true
  listAiPromptTemplate(queryParams.value)
    .then((response) => {
      aiPromptTemplateList.value = response.rows
      total.value = response.total
    })
    .finally(() => {
      loading.value = false
    })
}

function cancel() {
  open.value = false
  reset()
}

function reset() {
  isEdit.value = false
  form.value = {
    aiPromptTemplateId: null,
    templateScope: 'platform',
    testProjectId: null,
    sessionScene: DEFAULT_SESSION_SCENE,
    templateTitle: null,
    templateDescription: null,
    templateContent: null,
    builtinStatus: 0,
    enableStatus: 1,
    sortNum: 0,
    remark: null,
  }
  proxy.resetForm('aiPromptTemplateRef')
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
  ids.value = selection.map((item) => item.aiPromptTemplateId)
  single.value = selection.length !== 1
  multiple.value = !selection.length
}

function handleAdd() {
  reset()
  if (queryParams.value.templateScope) {
    form.value.templateScope = queryParams.value.templateScope
  }
  if (queryParams.value.testProjectId && form.value.templateScope === 'project') {
    form.value.testProjectId = queryParams.value.testProjectId
  }
  open.value = true
  title.value = '新增提示词模板'
}

function handleUpdate(row) {
  reset()
  const id = row?.aiPromptTemplateId || ids.value
  getAiPromptTemplate(id).then((response) => {
    form.value = response.data
    isEdit.value = true
    open.value = true
    title.value = form.value.builtinStatus === 1 ? '修改内置模板' : '修改提示词模板'
  })
}

function normalizeFormBeforeSubmit() {
  if (form.value.templateScope === 'platform') {
    form.value.testProjectId = null
  }
  if (form.value.aiPromptTemplateId == null) {
    form.value.builtinStatus = 0
  }
}

function submitForm() {
  proxy.$refs.aiPromptTemplateRef.validate((valid) => {
    if (!valid) return
    normalizeFormBeforeSubmit()
    const request = form.value.aiPromptTemplateId != null
      ? updateAiPromptTemplate(form.value)
      : addAiPromptTemplate(form.value)
    request.then(() => {
      proxy.$modal.msgSuccess(form.value.aiPromptTemplateId != null ? '修改成功' : '新增成功')
      open.value = false
      getList()
    })
  })
}

function handleDelete(row) {
  const selectedIds = row?.aiPromptTemplateId ? [row.aiPromptTemplateId] : ids.value
  const targets = aiPromptTemplateList.value.filter((item) => selectedIds.includes(item.aiPromptTemplateId))
  const deletable = targets.filter((item) => item.builtinStatus !== 1)
  if (!deletable.length) {
    proxy.$modal.msgWarning('内置模板不可删除，可改为禁用')
    return
  }
  const names = deletable.map((item) => item.templateTitle).join('、')
  proxy.$modal.confirm(`是否确认删除模板「${names}」？`).then(() => {
    return delAiPromptTemplate(deletable.map((item) => item.aiPromptTemplateId).join(','))
  }).then(() => {
    getList()
    proxy.$modal.msgSuccess('删除成功')
  }).catch(() => {})
}

function handleExport() {
  proxy.download('ai/aiPromptTemplate/export', {
    ...queryParams.value,
  }, `aiPromptTemplate_${Date.now()}.xlsx`)
}

watch(
  () => form.value.templateScope,
  (scope) => {
    if (scope === 'platform') {
      form.value.testProjectId = null
    }
  },
)

loadProjectOptions().then(() => {
  getList()
})
</script>

<style scoped>
.ai-prompt-template-form :deep(.el-form-item__label) {
  white-space: nowrap;
}

.ai-prompt-template-form :deep(.el-form-item) {
  margin-bottom: 18px;
}

.ai-prompt-template-form :deep(.el-divider) {
  margin: 8px 0 20px;
}

.ai-prompt-template-form :deep(.el-divider__text) {
  font-size: 13px;
  color: var(--el-text-color-secondary);
}

.ai-prompt-template-form .form-control-full {
  width: 100%;
}

.ai-prompt-template-form .form-control-sort {
  width: 160px;
}

.ai-prompt-template-form .form-hint {
  margin-left: 12px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.builtin-tag {
  margin-left: 6px;
}

.builtin-alert {
  margin-bottom: 16px;
}

.content-preview {
  color: var(--el-text-color-secondary);
  font-size: 13px;
}

.text-muted {
  color: var(--el-text-color-placeholder);
}
</style>

<template>
  <div class="app-container">
    <el-form :model="queryParams" ref="queryRef" :inline="true" v-show="showSearch" label-width="80px">
      <el-form-item label="厂商" prop="aiLlmVendorId">
        <el-select
          v-model="queryParams.aiLlmVendorId"
          placeholder="请选择厂商"
          clearable
          style="width: 200px"
        >
          <el-option
            v-for="item in vendorOptions"
            :key="item.aiLlmVendorId"
            :label="item.vendorName"
            :value="item.aiLlmVendorId"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="模型名" prop="modelName">
        <el-input
          v-model="queryParams.modelName"
          placeholder="请输入模型名"
          clearable
          style="width: 200px"
          @keyup.enter="handleQuery"
        />
      </el-form-item>
      <el-form-item label="启用状态" prop="enableStatus">
        <el-select
          v-model="queryParams.enableStatus"
          placeholder="启用状态"
          clearable
          style="width: 200px"
        >
          <el-option
            v-for="item in aiEnableStatusOptions"
            :key="item.value"
            :label="item.label"
            :value="item.value"
          />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button>
        <el-button icon="Refresh" @click="resetQuery">重置</el-button>
      </el-form-item>
    </el-form>

    <el-row :gutter="10" class="mb8">
      <el-col :span="1.5">
        <el-button
          type="primary"
          plain
          icon="Plus"
          @click="handleAdd"
          v-hasPermi="['ai:aiLlmModel:add']"
        >新增</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button
          type="success"
          plain
          icon="Edit"
          :disabled="single"
          @click="handleUpdate"
          v-hasPermi="['ai:aiLlmModel:edit']"
        >修改</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button
          type="danger"
          plain
          icon="Delete"
          :disabled="multiple"
          @click="handleDelete"
          v-hasPermi="['ai:aiLlmModel:remove']"
        >删除</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button
          type="info"
          plain
          icon="Refresh"
          :disabled="!queryParams.aiLlmVendorId"
          @click="handleDiscoverModels"
          v-hasPermi="['ai:aiLlmModel:list']"
        >获取模型</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button
          type="warning"
          plain
          icon="Download"
          @click="handleExport"
          v-hasPermi="['ai:aiLlmModel:export']"
        >导出</el-button>
      </el-col>
      <right-toolbar v-model:showSearch="showSearch" @queryTable="getList" :columns="columns"></right-toolbar>
    </el-row>

    <el-table v-loading="loading" :data="aiLlmModelList" row-key="aiLlmModelId" @selection-change="handleSelectionChange">
      <el-table-column type="selection" width="55" align="center" />
      <el-table-column label="模型ID" align="center" key="aiLlmModelId" prop="aiLlmModelId" v-if="columnVisible['aiLlmModelId']" />
      <el-table-column label="厂商ID" align="center" key="aiLlmVendorId" prop="aiLlmVendorId" v-if="columnVisible['aiLlmVendorId']" />
      <el-table-column label="厂商名" align="center" key="vendorName" prop="vendorName" v-if="columnVisible['vendorName']" />
      <el-table-column label="模型名" align="center" key="modelName" prop="modelName" v-if="columnVisible['modelName']" />
      <el-table-column label="展示名" align="center" key="displayName" prop="displayName" v-if="columnVisible['displayName']">
        <template #default="scope">
          <span>{{ scope.row.displayName || scope.row.modelName }}</span>
          <el-tag v-if="scope.row.builtinStatus === 1" type="info" size="small" class="builtin-tag">内置</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="启用状态" align="center" key="enableStatus" prop="enableStatus" v-if="columnVisible['enableStatus']">
        <template #default="scope">
          <dict-tag :options="aiEnableStatusOptions" :value="scope.row.enableStatus" />
        </template>
      </el-table-column>
      <el-table-column label="排序" align="center" key="sortNum" prop="sortNum" v-if="columnVisible['sortNum']" />
      <el-table-column label="支持思考" align="center" key="thinkingCapable" prop="thinkingCapable" v-if="columnVisible['thinkingCapable']">
        <template #default="scope">
          <el-tag :type="scope.row.thinkingCapable === 1 ? 'success' : 'info'" size="small">
            {{ scope.row.thinkingCapable === 1 ? '是' : '否' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="默认思考" align="center" key="thinkingDefault" prop="thinkingDefault" v-if="columnVisible['thinkingDefault']">
        <template #default="scope">
          <span v-if="scope.row.thinkingCapable !== 1">—</span>
          <el-tag v-else :type="scope.row.thinkingDefault === 1 ? 'success' : 'info'" size="small">
            {{ scope.row.thinkingDefault === 1 ? '开' : '关' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="思考预算" align="center" key="thinkingBudgetTokens" prop="thinkingBudgetTokens" v-if="columnVisible['thinkingBudgetTokens']" />
      <el-table-column label="备注" align="center" key="remark" prop="remark" v-if="columnVisible['remark']" show-overflow-tooltip />
      <el-table-column label="操作" align="center" class-name="small-padding fixed-width">
        <template #default="scope">
          <el-button link type="primary" icon="Edit" @click="handleUpdate(scope.row)" v-hasPermi="['ai:aiLlmModel:edit']">修改</el-button>
          <el-button
            v-if="scope.row.builtinStatus !== 1"
            link
            type="primary"
            icon="Delete"
            @click="handleDelete(scope.row)"
            v-hasPermi="['ai:aiLlmModel:remove']"
          >删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <pagination
      v-show="total>0"
      :total="total"
      v-model:page="queryParams.pageNum"
      v-model:limit="queryParams.pageSize"
      @pagination="getList"
    />

    <!-- 添加或修改AI 模型对话框 -->
    <el-dialog :title="title" v-model="open" width="560px" append-to-body class="ai-llm-dialog">
      <el-form ref="aiLlmModelRef" :model="form" :rules="rules" label-width="108px" class="ai-llm-form">
        <el-divider content-position="left">基本信息</el-divider>
        <el-form-item label="厂商" prop="aiLlmVendorId">
          <el-select v-model="form.aiLlmVendorId" placeholder="请选择厂商" class="form-control-full">
            <el-option
              v-for="item in vendorOptions"
              :key="item.aiLlmVendorId"
              :label="item.vendorName"
              :value="item.aiLlmVendorId"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="模型名" prop="modelName">
          <el-input v-model="form.modelName" placeholder="请输入上游模型 ID" maxlength="128" show-word-limit />
        </el-form-item>
        <el-form-item label="展示名" prop="displayName">
          <el-input v-model="form.displayName" placeholder="留空则使用模型名" maxlength="128" show-word-limit />
        </el-form-item>
        <el-divider content-position="left">思考能力</el-divider>
        <el-form-item label="支持思考" prop="thinkingCapable">
          <el-radio-group v-model="form.thinkingCapable">
            <el-radio :value="1">是</el-radio>
            <el-radio :value="0">否</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item v-if="form.thinkingCapable === 1" label="默认开启" prop="thinkingDefault">
          <el-radio-group v-model="form.thinkingDefault">
            <el-radio :value="1">开</el-radio>
            <el-radio :value="0">关</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item v-if="form.thinkingCapable === 1" label="思考预算" prop="thinkingBudgetTokens">
          <el-input-number
            v-model="form.thinkingBudgetTokens"
            :min="1"
            :max="200000"
            controls-position="right"
            placeholder="留空用全局默认"
            class="form-control-sort"
          />
          <span class="form-hint">token 数，留空使用系统默认</span>
        </el-form-item>
        <el-divider content-position="left">其他设置</el-divider>
        <el-form-item label="启用状态" prop="enableStatus">
          <el-radio-group v-model="form.enableStatus">
            <el-radio
              v-for="item in aiEnableStatusOptions"
              :key="item.value"
              :value="item.value"
            >{{ item.label }}</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="排序" prop="sortNum">
          <el-input-number v-model="form.sortNum" controls-position="right" :min="0" class="form-control-sort" />
        </el-form-item>
        <el-form-item label="备注" prop="remark">
          <el-input v-model="form.remark" type="textarea" :rows="3" placeholder="请输入备注" maxlength="256" show-word-limit />
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button type="primary" @click="submitForm">确 定</el-button>
          <el-button @click="cancel">取 消</el-button>
        </div>
      </template>
    </el-dialog>

    <el-drawer v-model="discoverOpen" :title="discoverTitle" size="520px" append-to-body>
      <div v-loading="discoverLoading" class="discover-panel">
        <div class="discover-toolbar">
          <el-button type="primary" :loading="discoverLoading" @click="loadDiscoverModels(true)">刷新列表</el-button>
          <span v-if="discoverResult?.fromCache" class="discover-cache-tip">来自缓存</span>
        </div>
        <el-table :data="discoverModels" row-key="modelId" @selection-change="handleDiscoverSelectionChange">
          <el-table-column type="selection" width="48" :selectable="discoverRowSelectable" />
          <el-table-column label="模型 ID" prop="modelId" min-width="140" show-overflow-tooltip />
          <el-table-column label="展示名" prop="displayName" min-width="120" show-overflow-tooltip />
          <el-table-column label="状态" prop="status" width="90">
            <template #default="scope">
              <el-tag v-if="scope.row.status === 'NEW'" type="success" size="small">新增</el-tag>
              <el-tag v-else-if="scope.row.status === 'EXISTING'" type="info" size="small">已有</el-tag>
              <el-tag v-else type="warning" size="small">远端无</el-tag>
            </template>
          </el-table-column>
        </el-table>
        <div class="discover-footer">
          <el-button type="primary" :loading="syncingModels" :disabled="!selectedDiscoverIds.length" @click="handleSyncModels">同步入库</el-button>
        </div>
      </div>
    </el-drawer>
  </div>
</template>

<script setup name="AiLlmModel">
import { useRoute } from "vue-router"
import { listAiLlmModel, getAiLlmModel, delAiLlmModel, addAiLlmModel, updateAiLlmModel } from "@/api/ai/aiLlmModel"
import { listAiLlmVendor } from "@/api/ai/aiLlmVendor"
import { discoverLlmModels, syncLlmModels } from "@/api/ai/aiLlmManage"
import { buildProjectTabTitle } from "@/views/project/testProject/utils/projectTabTitle"

const { proxy } = getCurrentInstance()
const route = useRoute()

const aiEnableStatusOptions = [
  { label: "启用", value: 1, elTagType: "success" },
  { label: "禁用", value: 0, elTagType: "danger" }
]

const aiLlmModelList = ref([])
const vendorOptions = ref([])
const open = ref(false)
const loading = ref(true)
const showSearch = ref(true)
const ids = ref([])
const single = ref(true)
const multiple = ref(true)
const total = ref(0)
const title = ref("")
const discoverOpen = ref(false)
const discoverLoading = ref(false)
const syncingModels = ref(false)
const discoverTitle = ref("获取模型列表")
const discoverResult = ref(null)
const discoverModels = ref([])
const selectedDiscoverIds = ref([])

const columns = ref([
  { key: "aiLlmModelId", label: "模型ID", visible: false },
  { key: "aiLlmVendorId", label: "厂商ID", visible: false },
  { key: "vendorName", label: "厂商名", visible: true },
  { key: "modelName", label: "模型名", visible: true },
  { key: "displayName", label: "展示名", visible: true },
  { key: "enableStatus", label: "启用状态", visible: true },
  { key: "sortNum", label: "排序", visible: true },
  { key: "thinkingCapable", label: "支持思考", visible: true },
  { key: "thinkingDefault", label: "默认思考", visible: true },
  { key: "thinkingBudgetTokens", label: "思考预算", visible: false },
  { key: "remark", label: "备注", visible: true },
])

const columnVisible = computed(() => {
  const result = {}
  columns.value.forEach(col => {
    result[col.key] = col.visible
  })
  return result
})

const data = reactive({
  form: {},
  queryParams: {
    pageNum: 1,
    pageSize: 10,
    aiLlmVendorId: null,
    modelName: null,
    enableStatus: null,
  },
  rules: {
    aiLlmVendorId: [
      { required: true, message: "厂商不能为空", trigger: "change" }
    ],
    modelName: [
      { required: true, message: "模型名不能为空", trigger: "blur" }
    ],
    enableStatus: [
      { required: true, message: "启用状态不能为空", trigger: "change" }
    ],
    sortNum: [
      { required: true, message: "排序不能为空", trigger: "blur" }
    ],
    thinkingCapable: [
      { required: true, message: "请选择是否支持思考", trigger: "change" }
    ],
    thinkingDefault: [
      { required: true, message: "请选择默认思考开关", trigger: "change" }
    ],
  }
})

const { queryParams, form, rules } = toRefs(data)

function loadVendorOptions() {
  return listAiLlmVendor({ pageNum: 1, pageSize: 999 }).then(response => {
    vendorOptions.value = response.rows || []
  })
}

/** 从路由 query 应用厂商筛选（由 AI 配置页跳转带入） */
function applyRouteVendorFilter() {
  const vendorId = route.query.aiLlmVendorId
  if (vendorId == null || vendorId === "") {
    return false
  }
  const matched = vendorOptions.value.find(item => String(item.aiLlmVendorId) === String(vendorId))
  queryParams.value.aiLlmVendorId = matched ? matched.aiLlmVendorId : vendorId
  if (matched?.vendorName) {
    updatePageTitle(matched.vendorName)
  }
  return true
}

/** 按厂商名更新 TagsView 页签标题 */
function updatePageTitle(vendorName) {
  nextTick(() => {
    proxy.$tab.updatePage(Object.assign({}, route, {
      title: buildProjectTabTitle(vendorName, "AI 模型"),
    }))
  })
}

function initFromRoute() {
  applyRouteVendorFilter()
  getList()
}

function getList() {
  loading.value = true
  listAiLlmModel(queryParams.value).then(response => {
    aiLlmModelList.value = response.rows
    total.value = response.total
  }).finally(() => {
    loading.value = false
  })
}

function cancel() {
  open.value = false
  reset()
}

function reset() {
  form.value = {
    aiLlmModelId: null,
    aiLlmVendorId: null,
    modelName: null,
    displayName: null,
    builtinStatus: 0,
    enableStatus: 1,
    thinkingCapable: 0,
    thinkingDefault: 0,
    thinkingBudgetTokens: null,
    sortNum: 0,
    remark: null,
  }
  proxy.resetForm("aiLlmModelRef")
}

function handleQuery() {
  queryParams.value.pageNum = 1
  getList()
}

function resetQuery() {
  proxy.resetForm("queryRef")
  handleQuery()
}

function handleSelectionChange(selection) {
  ids.value = selection.map(item => item.aiLlmModelId)
  single.value = selection.length != 1
  multiple.value = !selection.length
}

function handleAdd() {
  reset()
  const vendorId = queryParams.value.aiLlmVendorId
  if (vendorId != null && vendorId !== "") {
    form.value.aiLlmVendorId = vendorId
  }
  open.value = true
  title.value = "添加AI 模型"
}

function handleUpdate(row) {
  reset()
  const _aiLlmModelId = row.aiLlmModelId || ids.value
  getAiLlmModel(_aiLlmModelId).then(response => {
    form.value = response.data
    open.value = true
    title.value = "修改AI 模型"
  })
}

function submitForm() {
  proxy.$refs["aiLlmModelRef"].validate(valid => {
    if (valid) {
      if (form.value.thinkingCapable !== 1) {
        form.value.thinkingDefault = 0
        form.value.thinkingBudgetTokens = null
      }
      if (form.value.aiLlmModelId != null) {
        updateAiLlmModel(form.value).then(() => {
          proxy.$modal.msgSuccess("修改成功")
          open.value = false
          getList()
        })
      } else {
        addAiLlmModel(form.value).then(() => {
          proxy.$modal.msgSuccess("新增成功")
          open.value = false
          getList()
        })
      }
    }
  })
}

function handleDelete(row) {
  const _aiLlmModelIds = row.aiLlmModelId || ids.value
  proxy.$modal.confirm('是否确认删除AI 模型编号为"' + _aiLlmModelIds + '"的数据项？').then(function() {
    return delAiLlmModel(_aiLlmModelIds)
  }).then(() => {
    getList()
    proxy.$modal.msgSuccess("删除成功")
  }).catch(() => {})
}

function handleExport() {
  proxy.download("ai/aiLlmModel/export", {
    ...queryParams.value
  }, `aiLlmModel_${new Date().getTime()}.xlsx`)
}

function handleDiscoverModels() {
  const vendor = vendorOptions.value.find(item => item.aiLlmVendorId === queryParams.value.aiLlmVendorId)
  discoverTitle.value = `获取模型 - ${vendor?.vendorName || ""}`
  discoverOpen.value = true
  selectedDiscoverIds.value = []
  loadDiscoverModels(false)
}

function loadDiscoverModels(refresh) {
  if (!queryParams.value.aiLlmVendorId) {
    return
  }
  discoverLoading.value = true
  discoverLlmModels(queryParams.value.aiLlmVendorId, refresh).then(response => {
    discoverResult.value = response.data
    discoverModels.value = response.data?.models || []
  }).finally(() => {
    discoverLoading.value = false
  })
}

function discoverRowSelectable(row) {
  return row.status === "NEW" || row.status === "EXISTING"
}

function handleDiscoverSelectionChange(selection) {
  selectedDiscoverIds.value = selection.map(item => item.modelId)
}

function handleSyncModels() {
  if (!queryParams.value.aiLlmVendorId || !selectedDiscoverIds.value.length) {
    return
  }
  syncingModels.value = true
  syncLlmModels(queryParams.value.aiLlmVendorId, { modelIds: selectedDiscoverIds.value }).then(response => {
    proxy.$modal.msgSuccess(`已同步 ${response.data || 0} 个模型`)
    loadDiscoverModels(true)
    getList()
  }).finally(() => {
    syncingModels.value = false
  })
}

loadVendorOptions().then(() => {
  initFromRoute()
})

watch(
  () => route.query.aiLlmVendorId,
  () => {
    if (!vendorOptions.value.length) {
      return
    }
    applyRouteVendorFilter()
    handleQuery()
  }
)
</script>

<style scoped>
.ai-llm-form :deep(.el-form-item__label) {
  white-space: nowrap;
}

.ai-llm-form :deep(.el-form-item) {
  margin-bottom: 18px;
}

.ai-llm-form :deep(.el-divider) {
  margin: 8px 0 20px;
}

.ai-llm-form :deep(.el-divider__text) {
  font-size: 13px;
  color: var(--el-text-color-secondary);
}

.ai-llm-form .form-control-full {
  width: 100%;
}

.ai-llm-form .form-control-sort {
  width: 160px;
}

.ai-llm-form .form-hint {
  margin-left: 12px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.ai-llm-form :deep(.el-radio) {
  margin-right: 24px;
}

.builtin-tag {
  margin-left: 6px;
}

.discover-panel {
  display: flex;
  flex-direction: column;
  height: 100%;
}

.discover-toolbar {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 12px;
}

.discover-cache-tip {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.discover-footer {
  margin-top: 16px;
  text-align: right;
}
</style>

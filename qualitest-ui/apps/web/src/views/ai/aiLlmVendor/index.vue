<template>
  <div class="app-container">
    <el-form :model="queryParams" ref="queryRef" :inline="true" v-show="showSearch" label-width="80px">
      <el-form-item label="厂商名" prop="vendorName">
        <el-input
          v-model="queryParams.vendorName"
          placeholder="请输入厂商名"
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
          v-hasPermi="['ai:aiLlmVendor:add']"
        >新增</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button
          type="success"
          plain
          icon="Edit"
          :disabled="single"
          @click="handleUpdate"
          v-hasPermi="['ai:aiLlmVendor:edit']"
        >修改</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button
          type="danger"
          plain
          icon="Delete"
          :disabled="multiple"
          @click="handleDelete"
          v-hasPermi="['ai:aiLlmVendor:remove']"
        >删除</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button
          type="warning"
          plain
          icon="Download"
          @click="handleExport"
          v-hasPermi="['ai:aiLlmVendor:export']"
        >导出</el-button>
      </el-col>
      <right-toolbar v-model:showSearch="showSearch" @queryTable="getList" :columns="columns"></right-toolbar>
    </el-row>

    <el-table v-loading="loading" :data="aiLlmVendorList" row-key="aiLlmVendorId" @selection-change="handleSelectionChange">
      <el-table-column type="selection" width="55" align="center" />
      <el-table-column label="厂商ID" align="left" key="aiLlmVendorId" prop="aiLlmVendorId" v-if="columnVisible['aiLlmVendorId']" />
      <el-table-column label="厂商名" align="left" key="vendorName" prop="vendorName" v-if="columnVisible['vendorName']" min-width="140">
        <template #default="scope">
          <div class="vendor-name-cell">
            <LlmVendorIcon
              :template-id="scope.row.templateId"
              :vendor-name="scope.row.vendorName"
              :size="20"
            />
            <span>{{ scope.row.vendorName }}</span>
          </div>
        </template>
      </el-table-column>
      <el-table-column label="内置状态" align="center" key="builtinStatus" prop="builtinStatus" v-if="columnVisible['builtinStatus']" width="100">
        <template #default="scope">
          <dict-tag :options="aiBuiltinStatusOptions" :value="scope.row.builtinStatus" />
        </template>
      </el-table-column>
      <el-table-column label="协议类型" align="center" key="provider" prop="provider" v-if="columnVisible['provider']">
        <template #default="scope">
          <span>{{ formatProviderLabel(scope.row.provider) }}</span>
        </template>
      </el-table-column>
      <el-table-column label="接口地址" align="center" key="baseUrl" prop="baseUrl" v-if="columnVisible['baseUrl']" min-width="180" show-overflow-tooltip />
      <el-table-column label="启用状态" align="center" key="enableStatus" prop="enableStatus" v-if="columnVisible['enableStatus']">
        <template #default="scope">
          <dict-tag :options="aiEnableStatusOptions" :value="scope.row.enableStatus" />
        </template>
      </el-table-column>
      <el-table-column label="排序" align="center" key="sortNum" prop="sortNum" v-if="columnVisible['sortNum']" />
      <el-table-column label="备注" align="center" key="remark" prop="remark" v-if="columnVisible['remark']" show-overflow-tooltip />
      <el-table-column label="操作" align="center" class-name="small-padding fixed-width" width="200">
        <template #default="scope">
          <el-button link type="primary" icon="Cpu" @click="handleViewModels(scope.row)" v-hasPermi="['ai:aiLlmModel:list']">模型</el-button>
          <el-button link type="primary" icon="Edit" @click="handleUpdate(scope.row)" v-hasPermi="['ai:aiLlmVendor:edit']">修改</el-button>
          <el-button
            v-if="scope.row.builtinStatus !== 1"
            link
            type="primary"
            icon="Delete"
            @click="handleDelete(scope.row)"
            v-hasPermi="['ai:aiLlmVendor:remove']"
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

    <!-- 添加或修改 AI 配置对话框 -->
    <el-dialog :title="title" v-model="open" width="560px" append-to-body class="ai-llm-dialog">
      <el-form ref="aiLlmVendorRef" :model="form" :rules="rules" label-width="108px" class="ai-llm-form">
        <el-divider content-position="left">基本信息</el-divider>
        <el-form-item v-if="!form.aiLlmVendorId" label="厂商模板" prop="templateId">
          <el-select v-model="form.templateId" placeholder="可选模板快速填充" clearable class="form-control-full" @change="handleTemplateChange">
            <el-option
              v-for="item in templateOptions"
              :key="item.templateId"
              :label="item.displayName"
              :value="item.templateId"
            >
              <div class="vendor-template-option">
                <LlmVendorIcon :template-id="item.templateId" :icon="item.icon" :vendor-name="item.displayName" :size="18" />
                <span>{{ item.displayName }}</span>
              </div>
            </el-option>
          </el-select>
        </el-form-item>
        <el-form-item label="厂商名" prop="vendorName">
          <el-input v-model="form.vendorName" placeholder="请输入厂商名" maxlength="64" show-word-limit :disabled="form.builtinStatus === 1" />
        </el-form-item>
        <el-form-item label="协议标识" prop="provider">
          <el-select v-model="form.provider" placeholder="请选择协议标识" class="form-control-full">
            <el-option
              v-for="item in providerOptions"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </el-select>
          <div v-if="form.provider === 'anthropic_compatible'" class="form-tip">
            Anthropic 协议支持 Tool Use、Structured Outputs、Prompt Caching、Extended Thinking 与流式输出；接口地址示例：https://api.deepseek.com/anthropic
          </div>
        </el-form-item>
        <el-divider content-position="left">连接配置</el-divider>
        <el-form-item label="接口地址" prop="baseUrl">
          <el-input v-model="form.baseUrl" :placeholder="baseUrlPlaceholder" />
        </el-form-item>
        <el-form-item label="API Key" prop="apiKey">
          <div class="api-key-row">
            <el-input v-model="apiKeyDraft" type="password" show-password :placeholder="apiKeyPlaceholder" class="api-key-input" />
            <el-button :loading="testingConnection" @click="handleTestConnection">检测</el-button>
          </div>
          <div v-if="testConnectionMessage" :class="['form-tip', testConnectionSuccess ? 'text-success' : 'text-danger']">
            {{ testConnectionMessage }}
          </div>
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
  </div>
</template>

<script setup name="AiLlmVendor">
import { useRouter } from "vue-router"
import { listAiLlmVendor, getAiLlmVendor, delAiLlmVendor, addAiLlmVendor, updateAiLlmVendor } from "@/api/ai/aiLlmVendor"
import { listProviderTemplates, testLlmConnection } from "@/api/ai/aiLlmManage"
import LlmVendorIcon from "@/components/LlmVendorIcon/index.vue"

const { proxy } = getCurrentInstance()
const router = useRouter()

/** 启用状态选项（1=启用，0=禁用） */
const aiEnableStatusOptions = [
  { label: "启用", value: 1, elTagType: "success" },
  { label: "禁用", value: 0, elTagType: "danger" }
]

/** 内置状态选项（1=内置，0=自定义） */
const aiBuiltinStatusOptions = [
  { label: "内置", value: 1, elTagType: "info" },
  { label: "自定义", value: 0, elTagType: "warning" }
]

/**
 * 协议类型选项。
 * baseUrlPlaceholder 随协议切换，提示厂商接口根地址格式。
 */
const providerOptions = [
  { label: "OpenAI 兼容", value: "openai_compatible", baseUrlPlaceholder: "如 https://api.deepseek.com" },
  { label: "Anthropic 兼容", value: "anthropic_compatible", baseUrlPlaceholder: "如 https://api.deepseek.com/anthropic" }
]

const aiLlmVendorList = ref([])
const templateOptions = ref([])
const open = ref(false)
const loading = ref(true)
const testingConnection = ref(false)
const testConnectionMessage = ref("")
const testConnectionSuccess = ref(false)
/** 编辑时已保存密钥（详情接口仅返回脱敏占位符） */
const apiKeyConfigured = ref(false)
/** 与 form 分离，避免详情回填覆盖用户正在输入的密钥 */
const apiKeyDraft = ref("")

const MASKED_API_KEY = "******"
const showSearch = ref(true)
const ids = ref([])
const single = ref(true)
const multiple = ref(true)
const total = ref(0)
const title = ref("")

const columns = ref([
  { key: "aiLlmVendorId", label: "厂商ID", visible: false },
  { key: "vendorName", label: "厂商名", visible: true },
  { key: "builtinStatus", label: "内置状态", visible: true },
  { key: "provider", label: "协议类型", visible: true },
  { key: "baseUrl", label: "接口地址", visible: true },
  { key: "enableStatus", label: "启用状态", visible: true },
  { key: "sortNum", label: "排序", visible: true },
  { key: "remark", label: "备注", visible: true },
])

const columnVisible = computed(() => {
  const result = {}
  columns.value.forEach(col => {
    result[col.key] = col.visible
  })
  return result
})

/** 按当前所选协议返回接口地址输入框 placeholder */
const baseUrlPlaceholder = computed(() => {
  const item = providerOptions.find(option => option.value === form.value.provider)
  return item?.baseUrlPlaceholder || "如 https://api.example.com"
})

/** API Key 输入框 placeholder：编辑且未改密钥时提示留空即可 */
const apiKeyPlaceholder = computed(() => {
  if (apiKeyConfigured.value && !apiKeyDraft.value) {
    return "已配置密钥，留空表示不修改"
  }
  return "请输入 API Key"
})

function requiresApiKeyForTest() {
  const discoveryType = form.value.discoveryType
  return discoveryType !== "ollama_tags" && discoveryType !== "none"
}

function buildConnectionParams() {
  const trimmedKey = (apiKeyDraft.value || "").trim()
  const vendorId = form.value.aiLlmVendorId
  return {
    aiLlmVendorId: vendorId != null && vendorId !== "" ? String(vendorId) : null,
    templateId: form.value.templateId,
    provider: form.value.provider,
    discoveryType: form.value.discoveryType,
    baseUrl: form.value.baseUrl,
    apiKey: trimmedKey || null
  }
}

function buildSubmitPayload() {
  const payload = { ...form.value }
  const trimmedKey = (apiKeyDraft.value || "").trim()
  if (trimmedKey) {
    payload.apiKey = trimmedKey
  } else if (payload.aiLlmVendorId != null) {
    payload.apiKey = null
  }
  return payload
}

const data = reactive({
  form: {},
  queryParams: {
    pageNum: 1,
    pageSize: 10,
    vendorName: null,
    provider: null,
    enableStatus: null,
  },
  rules: {
    vendorName: [
      { required: true, message: "厂商名不能为空", trigger: "blur" }
    ],
    provider: [
      { required: true, message: "协议标识不能为空", trigger: "change" }
    ],
    baseUrl: [
      { required: true, message: "接口地址不能为空", trigger: "blur" }
    ],
    enableStatus: [
      { required: true, message: "启用状态不能为空", trigger: "change" }
    ],
    sortNum: [
      { required: true, message: "排序不能为空", trigger: "blur" }
    ],
  }
})

const { queryParams, form, rules } = toRefs(data)

/** 将协议标识 value 转为列表展示用的中文标签 */
function formatProviderLabel(provider) {
  const item = providerOptions.find(option => option.value === provider)
  return item ? item.label : (provider || "-")
}

function getList() {
  loading.value = true
  listAiLlmVendor(queryParams.value).then(response => {
    aiLlmVendorList.value = response.rows
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
    aiLlmVendorId: null,
    vendorName: null,
    builtinStatus: 0,
    templateId: null,
    provider: "openai_compatible",
    discoveryType: null,
    baseUrl: null,
    apiKey: null,
    enableStatus: 1,
    sortNum: 0,
    remark: null,
  }
  testConnectionMessage.value = ""
  testConnectionSuccess.value = false
  apiKeyConfigured.value = false
  apiKeyDraft.value = ""
  proxy.resetForm("aiLlmVendorRef")
}

function loadTemplateOptions() {
  listProviderTemplates().then(response => {
    templateOptions.value = response.data || []
  })
}

function handleTemplateChange(templateId) {
  const template = templateOptions.value.find(item => item.templateId === templateId)
  if (!template) {
    return
  }
  form.value.vendorName = template.displayName
  form.value.provider = template.provider
  form.value.discoveryType = template.discoveryType
  form.value.baseUrl = template.defaultBaseUrl || null
}

function handleTestConnection() {
  if (!form.value.baseUrl) {
    proxy.$modal.msgWarning("请先填写接口地址")
    return
  }
  const params = buildConnectionParams()
  if (!params.apiKey && !params.aiLlmVendorId && requiresApiKeyForTest()) {
    proxy.$modal.msgWarning("请先填写 API Key")
    return
  }
  testingConnection.value = true
  testConnectionMessage.value = ""
  testLlmConnection(params).then(response => {
    const result = response.data || {}
    testConnectionSuccess.value = !!result.success
    testConnectionMessage.value = result.success
      ? result.message + (result.modelCount != null ? `（发现 ${result.modelCount} 个模型）` : "")
      : result.message
  }).catch(() => {
    testConnectionSuccess.value = false
    testConnectionMessage.value = "检测失败"
  }).finally(() => {
    testingConnection.value = false
  })
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
  ids.value = selection.map(item => item.aiLlmVendorId)
  single.value = selection.length != 1
  multiple.value = !selection.length
}

function handleAdd() {
  reset()
  open.value = true
  title.value = "添加 AI 配置"
}

function handleUpdate(row) {
  reset()
  const _aiLlmVendorId = row.aiLlmVendorId || ids.value
  getAiLlmVendor(_aiLlmVendorId).then(response => {
    form.value = response.data
    apiKeyConfigured.value = response.data.apiKey === MASKED_API_KEY
    form.value.apiKey = null
    apiKeyDraft.value = ""
    open.value = true
    title.value = "修改 AI 配置"
  })
}

function submitForm() {
  proxy.$refs["aiLlmVendorRef"].validate(valid => {
    if (valid) {
      const payload = buildSubmitPayload()
      if (!payload.aiLlmVendorId && requiresApiKeyForTest() && !(payload.apiKey || "").trim()) {
        proxy.$modal.msgWarning("请先填写 API Key")
        return
      }
      if (payload.aiLlmVendorId != null) {
        updateAiLlmVendor(payload).then(() => {
          proxy.$modal.msgSuccess("修改成功")
          open.value = false
          getList()
        })
      } else {
        addAiLlmVendor(payload).then(() => {
          proxy.$modal.msgSuccess("新增成功")
          open.value = false
          getList()
        })
      }
    }
  })
}

function handleDelete(row) {
  const _aiLlmVendorIds = row.aiLlmVendorId || ids.value
  proxy.$modal.confirm('是否确认删除 AI 配置编号为"' + _aiLlmVendorIds + '"的数据项？').then(function() {
    return delAiLlmVendor(_aiLlmVendorIds)
  }).then(() => {
    getList()
    proxy.$modal.msgSuccess("删除成功")
  }).catch(() => {})
}

/** 跳转到 AI 模型列表，并按当前配置筛选 */
function handleViewModels(row) {
  router.push({
    path: "/aiManages/aiLlmModel",
    query: {
      aiLlmVendorId: row.aiLlmVendorId
    }
  })
}

function handleExport() {
  proxy.download("ai/aiLlmVendor/export", {
    ...queryParams.value
  }, `aiLlmVendor_${new Date().getTime()}.xlsx`)
}

getList()
loadTemplateOptions()
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

.ai-llm-form :deep(.el-radio) {
  margin-right: 24px;
}

.ai-llm-form .form-tip {
  margin-top: 6px;
  font-size: 12px;
  line-height: 1.5;
  color: var(--el-text-color-secondary);
}

.ai-llm-form .text-success {
  color: var(--el-color-success);
}

.ai-llm-form .text-danger {
  color: var(--el-color-danger);
}

.api-key-row {
  display: flex;
  gap: 8px;
  width: 100%;
}

.api-key-input {
  flex: 1;
}

.vendor-name-cell {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  flex-wrap: wrap;
}

.vendor-template-option {
  display: inline-flex;
  align-items: center;
  gap: 8px;
}
</style>

<template>
  <div class="app-container">
    <el-form :model="queryParams" ref="queryRef" :inline="true" v-show="showSearch" label-width="68px">
      <el-form-item label="会话ID" prop="aiChatSessionId">
        <el-input
          v-model="queryParams.aiChatSessionId"
          placeholder="请输入会话ID"
          clearable
          @keyup.enter="handleQuery"
        />
      </el-form-item>
      <el-form-item label="消息角色" prop="messageRole">
        <el-input
          v-model="queryParams.messageRole"
          placeholder="请输入消息角色"
          clearable
          @keyup.enter="handleQuery"
        />
      </el-form-item>
      <el-form-item label="模型ID" prop="aiLlmModelId">
        <el-input
          v-model="queryParams.aiLlmModelId"
          placeholder="请输入模型ID"
          clearable
          @keyup.enter="handleQuery"
        />
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
          v-hasPermi="['ai:aiChatMessage:add']"
        >新增</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button
          type="success"
          plain
          icon="Edit"
          :disabled="single"
          @click="handleUpdate"
          v-hasPermi="['ai:aiChatMessage:edit']"
        >修改</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button
          type="danger"
          plain
          icon="Delete"
          :disabled="multiple"
          @click="handleDelete"
          v-hasPermi="['ai:aiChatMessage:remove']"
        >删除</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button
          type="warning"
          plain
          icon="Download"
          @click="handleExport"
          v-hasPermi="['ai:aiChatMessage:export']"
        >导出</el-button>
      </el-col>
      <right-toolbar v-model:showSearch="showSearch" @queryTable="getList" :columns="columns"></right-toolbar>
    </el-row>

    <el-table v-loading="loading" :data="aiChatMessageList" row-key="aiChatMessageId" @selection-change="handleSelectionChange">
      <el-table-column type="selection" width="55" align="center" />
      <el-table-column label="消息ID" align="center" key="aiChatMessageId" prop="aiChatMessageId" v-if="columnVisible['aiChatMessageId']" />
      <el-table-column label="会话ID" align="center" key="aiChatSessionId" prop="aiChatSessionId" v-if="columnVisible['aiChatSessionId']" />
      <el-table-column label="消息角色" align="center" key="messageRole" prop="messageRole" v-if="columnVisible['messageRole']" />
      <el-table-column label="模型ID" align="center" key="aiLlmModelId" prop="aiLlmModelId" v-if="columnVisible['aiLlmModelId']" />
      <el-table-column label="消息内容" align="center" key="messageContent" prop="messageContent" v-if="columnVisible['messageContent']" />
      <el-table-column label="思考内容" align="center" key="thinkingContent" prop="thinkingContent" v-if="columnVisible['thinkingContent']" show-overflow-tooltip />
      <el-table-column label="结果摘要" align="center" key="resultMetaJson" prop="resultMetaJson" v-if="columnVisible['resultMetaJson']" />
      <el-table-column label="操作" align="center" class-name="small-padding fixed-width">
        <template #default="scope">
          <el-button link type="primary" icon="Edit" @click="handleUpdate(scope.row)" v-hasPermi="['ai:aiChatMessage:edit']">修改</el-button>
          <el-button link type="primary" icon="Delete" @click="handleDelete(scope.row)" v-hasPermi="['ai:aiChatMessage:remove']">删除</el-button>
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

    <!-- 添加或修改AI 会话消息对话框 -->
    <el-dialog :title="title" v-model="open" width="500px" append-to-body>
      <el-form ref="aiChatMessageRef" :model="form" :rules="rules" label-width="80px">
        <el-form-item label="会话ID" prop="aiChatSessionId">
          <el-input v-model="form.aiChatSessionId" placeholder="请输入会话ID" />
        </el-form-item>
        <el-form-item label="消息角色" prop="messageRole">
          <el-input v-model="form.messageRole" placeholder="请输入消息角色" />
        </el-form-item>
        <el-form-item label="模型ID" prop="aiLlmModelId">
          <el-input v-model="form.aiLlmModelId" placeholder="请输入模型ID" />
        </el-form-item>
        <el-form-item label="消息内容">
          <editor v-model="form.messageContent" :min-height="192"/>
        </el-form-item>
        <el-form-item label="思考内容">
          <el-input v-model="form.thinkingContent" type="textarea" :rows="4" placeholder="模型思考过程" />
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

<script setup name="AiChatMessage">
  import { listAiChatMessage, getAiChatMessage, delAiChatMessage, addAiChatMessage, updateAiChatMessage } from "@/api/ai/aiChatMessage";

  const { proxy } = getCurrentInstance()

const aiChatMessageList = ref([])
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
  { key: 'aiChatMessageId', label: `消息ID`, visible: true },
  { key: 'aiChatSessionId', label: `会话ID`, visible: true },
  { key: 'messageRole', label: `消息角色`, visible: true },
  { key: 'aiLlmModelId', label: `模型ID`, visible: true },
  { key: 'messageContent', label: `消息内容`, visible: true },
  { key: 'thinkingContent', label: `思考内容`, visible: false },
  { key: 'resultMetaJson', label: `结果摘要`, visible: true },
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
    aiChatSessionId: null,
    messageRole: null,
    aiLlmModelId: null,
  },
  rules: {
    aiChatSessionId: [
      { required: true, message: "会话ID不能为空", trigger: "blur" }
    ],
    messageRole: [
      { required: true, message: "消息角色不能为空", trigger: "blur" }
    ],
    messageContent: [
      { required: true, message: "消息内容不能为空", trigger: "blur" }
    ],
    createTime: [
      { required: true, message: "创建时间不能为空", trigger: "blur" }
    ]
  }
})

const { queryParams, form, rules } = toRefs(data)

/** 查询AI 会话消息列表 */
function getList() {
  loading.value = true
  listAiChatMessage(queryParams.value).then(response => {
    aiChatMessageList.value = response.rows
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
    aiChatMessageId: null,
    aiChatSessionId: null,
    messageRole: null,
    aiLlmModelId: null,
    messageContent: null,
    thinkingContent: null,
    resultMetaJson: null,
    createTime: null
  }
  proxy.resetForm("aiChatMessageRef")
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
  ids.value = selection.map(item => item.aiChatMessageId)
  single.value = selection.length != 1
  multiple.value = !selection.length
}

/** 新增按钮操作 */
function handleAdd() {
  reset()
  open.value = true
  title.value = "添加AI 会话消息"
}

/** 修改按钮操作 */
function handleUpdate(row) {
  reset()
  const _aiChatMessageId = row.aiChatMessageId || ids.value
  getAiChatMessage(_aiChatMessageId).then(response => {
    form.value = response.data
    open.value = true
    title.value = "修改AI 会话消息"
  })
}

/** 提交按钮 */
function submitForm() {
  proxy.$refs["aiChatMessageRef"].validate(valid => {
    if (valid) {
      if (form.value.aiChatMessageId != null) {
        updateAiChatMessage(form.value).then(response => {
          proxy.$modal.msgSuccess("修改成功")
          open.value = false
          getList()
        })
      } else {
        addAiChatMessage(form.value).then(response => {
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
  const _aiChatMessageIds = row.aiChatMessageId || ids.value
  proxy.$modal.confirm('是否确认删除AI 会话消息编号为"' + _aiChatMessageIds + '"的数据项？').then(function() {
    return delAiChatMessage(_aiChatMessageIds)
  }).then(() => {
    getList()
    proxy.$modal.msgSuccess("删除成功")
  }).catch(() => {})
}

/** 导出按钮操作 */
function handleExport() {
  proxy.download('ai/aiChatMessage/export', {
    ...queryParams.value
  }, `aiChatMessage_${new Date().getTime()}.xlsx`)
}

getList()
</script>

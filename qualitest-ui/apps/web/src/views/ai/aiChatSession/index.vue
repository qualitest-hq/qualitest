<template>
  <div class="app-container">
    <el-form :model="queryParams" ref="queryRef" :inline="true" v-show="showSearch" label-width="68px">
      <el-form-item label="项目ID" prop="testProjectId">
        <el-input
          v-model="queryParams.testProjectId"
          placeholder="请输入项目ID"
          clearable
          @keyup.enter="handleQuery"
        />
      </el-form-item>
      <el-form-item label="用户ID" prop="userId">
        <el-input
          v-model="queryParams.userId"
          placeholder="请输入用户ID"
          clearable
          @keyup.enter="handleQuery"
        />
      </el-form-item>
      <el-form-item label="会话场景" prop="sessionScene">
        <el-input
          v-model="queryParams.sessionScene"
          placeholder="请输入会话场景"
          clearable
          @keyup.enter="handleQuery"
        />
      </el-form-item>
      <el-form-item label="当前模型ID" prop="currentModelId">
        <el-input
          v-model="queryParams.currentModelId"
          placeholder="请输入当前模型ID"
          clearable
          @keyup.enter="handleQuery"
        />
      </el-form-item>
      <el-form-item label="会话标题" prop="sessionTitle">
        <el-input
          v-model="queryParams.sessionTitle"
          placeholder="请输入会话标题"
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
          v-hasPermi="['ai:aiChatSession:add']"
        >新增</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button
          type="success"
          plain
          icon="Edit"
          :disabled="single"
          @click="handleUpdate"
          v-hasPermi="['ai:aiChatSession:edit']"
        >修改</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button
          type="danger"
          plain
          icon="Delete"
          :disabled="multiple"
          @click="handleDelete"
          v-hasPermi="['ai:aiChatSession:remove']"
        >删除</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button
          type="warning"
          plain
          icon="Download"
          @click="handleExport"
          v-hasPermi="['ai:aiChatSession:export']"
        >导出</el-button>
      </el-col>
      <right-toolbar v-model:showSearch="showSearch" @queryTable="getList" :columns="columns"></right-toolbar>
    </el-row>

    <el-table v-loading="loading" :data="aiChatSessionList" row-key="aiChatSessionId" @selection-change="handleSelectionChange">
      <el-table-column type="selection" width="55" align="center" />
      <el-table-column label="会话ID" align="center" key="aiChatSessionId" prop="aiChatSessionId" v-if="columnVisible['aiChatSessionId']" />
      <el-table-column label="项目ID" align="center" key="testProjectId" prop="testProjectId" v-if="columnVisible['testProjectId']" />
      <el-table-column label="用户ID" align="center" key="userId" prop="userId" v-if="columnVisible['userId']" />
      <el-table-column label="会话场景" align="center" key="sessionScene" prop="sessionScene" v-if="columnVisible['sessionScene']" />
      <el-table-column label="业务锚点" align="center" key="bizRefJson" prop="bizRefJson" v-if="columnVisible['bizRefJson']" />
      <el-table-column label="当前模型ID" align="center" key="currentModelId" prop="currentModelId" v-if="columnVisible['currentModelId']" />
      <el-table-column label="会话标题" align="center" key="sessionTitle" prop="sessionTitle" v-if="columnVisible['sessionTitle']" />
      <el-table-column label="操作" align="center" class-name="small-padding fixed-width">
        <template #default="scope">
          <el-button link type="primary" icon="Edit" @click="handleUpdate(scope.row)" v-hasPermi="['ai:aiChatSession:edit']">修改</el-button>
          <el-button link type="primary" icon="Delete" @click="handleDelete(scope.row)" v-hasPermi="['ai:aiChatSession:remove']">删除</el-button>
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

    <!-- 添加或修改AI 会话对话框 -->
    <el-dialog :title="title" v-model="open" width="500px" append-to-body>
      <el-form ref="aiChatSessionRef" :model="form" :rules="rules" label-width="80px">
        <el-form-item label="项目ID" prop="testProjectId">
          <el-input v-model="form.testProjectId" placeholder="请输入项目ID" />
        </el-form-item>
        <el-form-item label="用户ID" prop="userId">
          <el-input v-model="form.userId" placeholder="请输入用户ID" />
        </el-form-item>
        <el-form-item label="会话场景" prop="sessionScene">
          <el-input v-model="form.sessionScene" placeholder="请输入会话场景" />
        </el-form-item>
        <el-form-item label="当前模型ID" prop="currentModelId">
          <el-input v-model="form.currentModelId" placeholder="请输入当前模型ID" />
        </el-form-item>
        <el-form-item label="会话标题" prop="sessionTitle">
          <el-input v-model="form.sessionTitle" placeholder="请输入会话标题" />
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

<script setup name="AiChatSession">
  import { listAiChatSession, getAiChatSession, delAiChatSession, addAiChatSession, updateAiChatSession } from "@/api/ai/aiChatSession";

  const { proxy } = getCurrentInstance()

const aiChatSessionList = ref([])
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
  { key: 'aiChatSessionId', label: `会话ID`, visible: true },
  { key: 'testProjectId', label: `项目ID`, visible: true },
  { key: 'userId', label: `用户ID`, visible: true },
  { key: 'sessionScene', label: `会话场景`, visible: true },
  { key: 'bizRefJson', label: `业务锚点`, visible: true },
  { key: 'currentModelId', label: `当前模型ID`, visible: true },
  { key: 'sessionTitle', label: `会话标题`, visible: true },
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
    testProjectId: null,
    userId: null,
    sessionScene: null,
    currentModelId: null,
    sessionTitle: null,
  },
  rules: {
    testProjectId: [
      { required: true, message: "项目ID不能为空", trigger: "blur" }
    ],
    userId: [
      { required: true, message: "用户ID不能为空", trigger: "blur" }
    ],
    sessionScene: [
      { required: true, message: "会话场景不能为空", trigger: "blur" }
    ],
    bizRefJson: [
      { required: true, message: "业务锚点不能为空", trigger: "blur" }
    ],
    currentModelId: [
      { required: true, message: "当前模型ID不能为空", trigger: "blur" }
    ],
    createTime: [
      { required: true, message: "创建时间不能为空", trigger: "blur" }
    ]
  }
})

const { queryParams, form, rules } = toRefs(data)

/** 查询AI 会话列表 */
function getList() {
  loading.value = true
  listAiChatSession(queryParams.value).then(response => {
    aiChatSessionList.value = response.rows
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
    aiChatSessionId: null,
    testProjectId: null,
    userId: null,
    sessionScene: null,
    bizRefJson: null,
    currentModelId: null,
    sessionTitle: null,
    createTime: null
  }
  proxy.resetForm("aiChatSessionRef")
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
  ids.value = selection.map(item => item.aiChatSessionId)
  single.value = selection.length != 1
  multiple.value = !selection.length
}

/** 新增按钮操作 */
function handleAdd() {
  reset()
  open.value = true
  title.value = "添加AI 会话"
}

/** 修改按钮操作 */
function handleUpdate(row) {
  reset()
  const _aiChatSessionId = row.aiChatSessionId || ids.value
  getAiChatSession(_aiChatSessionId).then(response => {
    form.value = response.data
    open.value = true
    title.value = "修改AI 会话"
  })
}

/** 提交按钮 */
function submitForm() {
  proxy.$refs["aiChatSessionRef"].validate(valid => {
    if (valid) {
      if (form.value.aiChatSessionId != null) {
        updateAiChatSession(form.value).then(response => {
          proxy.$modal.msgSuccess("修改成功")
          open.value = false
          getList()
        })
      } else {
        addAiChatSession(form.value).then(response => {
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
  const _aiChatSessionIds = row.aiChatSessionId || ids.value
  proxy.$modal.confirm('是否确认删除AI 会话编号为"' + _aiChatSessionIds + '"的数据项？').then(function() {
    return delAiChatSession(_aiChatSessionIds)
  }).then(() => {
    getList()
    proxy.$modal.msgSuccess("删除成功")
  }).catch(() => {})
}

/** 导出按钮操作 */
function handleExport() {
  proxy.download('ai/aiChatSession/export', {
    ...queryParams.value
  }, `aiChatSession_${new Date().getTime()}.xlsx`)
}

getList()
</script>

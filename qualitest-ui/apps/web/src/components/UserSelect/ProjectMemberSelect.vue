<template>
  <div class="user-select" :class="{ 'is-disabled': disabled }" :style="{ width: width }">
    <el-input
      :value="displayValue"
      placeholder="请选择用户"
      readonly
      class="user-select__input"
    >
      <template #append>
        <el-button :disabled="disabled" @click="handleSelect">选择</el-button>
      </template>
    </el-input>
  </div>

  <el-dialog v-model="visible" title="选择用户" width="1000px" top="5vh" append-to-body>
    <el-row :gutter="10" class="mb8">
      <right-toolbar v-model:showSearch="showSearch" @queryTable="getList" :columns="columns" />
    </el-row>

    <el-form
      v-show="showSearch"
      ref="queryRef"
      :model="queryParams"
      class="search-form"
      :inline="true"
      label-width="88px"
    >
      <el-form-item label="用户名称" prop="nickName">
        <el-input
          v-model="queryParams.nickName"
          placeholder="请输入用户名称"
          clearable
          @keyup.enter="handleQuery"
        />
      </el-form-item>
      <el-form-item label="登录名称" prop="userName">
        <el-input
          v-model="queryParams.userName"
          placeholder="请输入登录名称"
          clearable
          @keyup.enter="handleQuery"
        />
      </el-form-item>
      <el-form-item label="手机号码" prop="phonenumber">
        <el-input
          v-model="queryParams.phonenumber"
          placeholder="请输入手机号码"
          clearable
          @keyup.enter="handleQuery"
        />
      </el-form-item>
      <el-form-item label="状态" prop="status">
        <el-select v-model="queryParams.status" placeholder="用户状态" clearable>
          <el-option
            v-for="dict in sys_normal_disable"
            :key="dict.value"
            :label="dict.label"
            :value="dict.value"
          />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button>
        <el-button icon="Refresh" @click="resetQuery">重置</el-button>
      </el-form-item>
    </el-form>

    <el-table v-loading="loading" :data="userList" :fit="true" height="55vh">
      <el-table-column label="" width="55" align="center" fixed="left">
        <template #default="scope">
          <el-radio
            v-model="selectedId"
            :label="scope.row.userId"
            @change="handleRadioChange(scope.row)"
          >
            &nbsp;
          </el-radio>
        </template>
      </el-table-column>
      <el-table-column type="index" label="序号" width="55" align="center" fixed="left" />
      <el-table-column
        label="用户编号"
        align="center"
        prop="userId"
        width="100"
        fixed="left"
        v-if="columnVisible['userId']"
      />
      <el-table-column
        label="用户名称"
        align="center"
        prop="nickName"
        min-width="100"
        show-overflow-tooltip
        v-if="columnVisible['nickName']"
      />
      <el-table-column
        label="登录名称"
        align="center"
        prop="userName"
        min-width="100"
        show-overflow-tooltip
        v-if="columnVisible['userName']"
      />
      <el-table-column
        label="部门"
        align="center"
        min-width="120"
        show-overflow-tooltip
        v-if="columnVisible['deptName']"
      >
        <template #default="scope">
          <span>{{ scope.row.dept && scope.row.dept.deptName }}</span>
        </template>
      </el-table-column>
      <el-table-column
        label="手机号码"
        align="center"
        prop="phonenumber"
        width="120"
        v-if="columnVisible['phonenumber']"
      />
      <el-table-column label="状态" align="center" prop="status" width="90" v-if="columnVisible['status']">
        <template #default="scope">
          <dict-tag :options="sys_normal_disable" :value="scope.row.status" />
        </template>
      </el-table-column>
    </el-table>

    <div class="dialog-footer-bar">
      <pagination
        v-show="total > 0"
        v-model:page="queryParams.pageNum"
        v-model:limit="queryParams.pageSize"
        :total="total"
        @pagination="getList"
      />
      <div class="dialog-actions">
        <el-button type="primary" @click="confirm">确定</el-button>
        <el-button @click="cancel">取消</el-button>
      </div>
    </div>
  </el-dialog>
</template>

<script setup name="UserSelect">
import { listUser } from '@/api/system/user'
import { listSelectableUsersForProject } from '@/api/project/testProjectMember'
import RightToolbar from '@/components/RightToolbar/index.vue'

const props = defineProps({
  userId: {
    type: [String, Number],
    default: null
  },
  nickName: {
    type: String,
    default: ''
  },
  userName: {
    type: String,
    default: ''
  },
  disabled: {
    type: Boolean,
    default: false
  },
  width: {
    type: String,
    default: '100%'
  },
  /** 若设置，则拉取「可加入该测试项目」的用户（排除已是项目成员），否则走系统用户列表 */
  pickUsersForTestProjectId: {
    type: [String, Number],
    default: null
  }
})

const emit = defineEmits(['update:userId', 'update:nickName', 'update:userName', 'change'])

const { proxy } = getCurrentInstance()
const { sys_normal_disable } = proxy.useDict('sys_normal_disable')

const visible = ref(false)
const loading = ref(true)
const userList = ref([])
const selectedId = ref(null)
const currentRow = ref(null)
const total = ref(0)
const showSearch = ref(true)

const columns = ref([
  { key: 'userId', label: '用户编号', visible: true },
  { key: 'nickName', label: '用户名称', visible: true },
  { key: 'userName', label: '登录名称', visible: true },
  { key: 'deptName', label: '部门', visible: true },
  { key: 'phonenumber', label: '手机号码', visible: true },
  { key: 'status', label: '状态', visible: true }
])

const columnVisible = computed(() => {
  const result = {}
  columns.value.forEach((col) => {
    result[col.key] = col.visible
  })
  return result
})

const data = reactive({
  queryParams: {
    pageNum: 1,
    pageSize: 10,
    nickName: null,
    userName: null,
    phonenumber: null,
    status: null
  }
})

const { queryParams } = toRefs(data)

const displayValue = computed(() => {
  return props.nickName || props.userName || props.userId || ''
})

function handleSelect() {
  if (props.disabled) return
  visible.value = true
  selectedId.value = props.userId != null && props.userId !== '' ? props.userId : null
  currentRow.value = null
  getList()
}

function getList() {
  loading.value = true
  const useProjectPick =
    props.pickUsersForTestProjectId != null && props.pickUsersForTestProjectId !== ''
  const req = useProjectPick
    ? listSelectableUsersForProject({
        ...queryParams.value,
        testProjectId: props.pickUsersForTestProjectId
      })
    : listUser(queryParams.value)
  req
    .then((response) => {
      userList.value = response.rows
      total.value = response.total
      loading.value = false
      if (props.userId != null && props.userId !== '') {
        const row = userList.value.find((item) => item.userId == props.userId)
        if (row) {
          selectedId.value = row.userId
          currentRow.value = row
        } else {
          selectedId.value = null
          currentRow.value = null
        }
      }
    })
    .catch(() => {
      loading.value = false
    })
}

function handleQuery() {
  queryParams.value.pageNum = 1
  getList()
}

function resetQuery() {
  proxy.resetForm('queryRef')
  queryParams.value.pageNum = 1
  queryParams.value.nickName = null
  queryParams.value.userName = null
  queryParams.value.phonenumber = null
  queryParams.value.status = null
  handleQuery()
}

function handleRadioChange(row) {
  currentRow.value = row
  selectedId.value = row.userId
}

function confirm() {
  if (selectedId.value == null || !currentRow.value) {
    proxy.$modal.msgError('请选择用户')
    return
  }
  handleConfirm(currentRow.value)
  visible.value = false
}

function cancel() {
  visible.value = false
  selectedId.value = null
  currentRow.value = null
}

function handleConfirm(user) {
  if (user && user.userId != null) {
    emit('update:userId', user.userId)
    emit('update:nickName', user.nickName || '')
    emit('update:userName', user.userName || '')
    emit('change', {
      userId: user.userId,
      nickName: user.nickName || '',
      userName: user.userName || ''
    })
  }
}
</script>

<style scoped lang="scss">
.user-select {
  display: inline-block;
  vertical-align: middle;

  &.is-disabled {
    cursor: not-allowed;
    opacity: 0.92;

    :deep(.user-select__input .el-input__wrapper) {
      background-color: var(--el-disabled-bg-color, var(--el-fill-color-light));
      box-shadow: 0 0 0 1px var(--el-disabled-border-color, var(--el-border-color-light)) inset;
      cursor: not-allowed;
    }

    :deep(.user-select__input .el-input__inner) {
      color: var(--el-disabled-text-color, var(--el-text-color-placeholder));
      -webkit-text-fill-color: var(--el-disabled-text-color, var(--el-text-color-placeholder));
      cursor: not-allowed;
    }

    :deep(.user-select__input .el-input-group__append) {
      background-color: var(--el-disabled-bg-color, var(--el-fill-color-light));
      box-shadow:
        0 1px 0 0 var(--el-disabled-border-color, var(--el-border-color-light)) inset,
        0 -1px 0 0 var(--el-disabled-border-color, var(--el-border-color-light)) inset,
        -1px 0 0 0 var(--el-disabled-border-color, var(--el-border-color-light)) inset;
    }

    :deep(.user-select__input .el-input-group__append .el-button) {
      color: var(--el-disabled-text-color, var(--el-text-color-placeholder));
      border-color: transparent;
      background-color: transparent;
    }
  }
}

.user-select__input {
  width: 100%;
}

.search-form .el-select {
  --el-select-width: 220px;
}
.search-form .el-input {
  --el-input-width: 220px;
}
.dialog-footer-bar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-top: 12px;
}
.dialog-actions {
  display: flex;
  gap: 8px;
  padding-top: 4px;
}
</style>

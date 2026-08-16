<template>
  <div class="app-container">
    <el-form v-show="showSearch" ref="queryRef" :inline="true" :model="queryParams" label-width="68px">
      <el-form-item label="用户名称" prop="userName">
        <el-input
            v-model="queryParams.userName"
            clearable
            placeholder="请输入用户名称"
            @keyup.enter="handleQuery"
        />
      </el-form-item>
      <el-form-item label="成员角色" prop="memberRole">
        <el-select
            v-model="queryParams.memberRole"
            clearable
            placeholder="请选择成员角色"
            style="width: 200px"
        >
          <el-option
              v-for="item in memberRoleOptions"
              :key="item.value"
              :label="item.label"
              :value="item.value"
          ></el-option>
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button icon="Search" type="primary" @click="handleQuery">搜索</el-button>
        <el-button icon="Refresh" @click="resetQuery">重置</el-button>
      </el-form-item>
    </el-form>

    <el-row :gutter="10" class="mb8">
      <el-col v-if="canManage" :span="1.5">
        <el-button
            v-hasPermi="['project:testProjectMember:add']"
            icon="Plus"
            plain
            type="primary"
            @click="handleAdd"
        >新增
        </el-button>
      </el-col>
      <el-col v-if="canManage && !toolbarEditDisabled" :span="1.5">
        <el-button
            v-hasPermi="['project:testProjectMember:edit']"
            icon="Edit"
            plain
            type="success"
            @click="handleUpdate"
        >修改
        </el-button>
      </el-col>
      <el-col v-if="canManage && !toolbarDeleteDisabled" :span="1.5">
        <el-button
            v-hasPermi="['project:testProjectMember:remove']"
            icon="Delete"
            plain
            type="danger"
            @click="handleDelete"
        >删除
        </el-button>
      </el-col>
      <el-col v-if="canManage" :span="1.5">
        <el-button
            v-hasPermi="['project:testProjectMember:export']"
            icon="Download"
            plain
            type="warning"
            @click="handleExport"
        >导出
        </el-button>
      </el-col>
      <right-toolbar v-model:showSearch="showSearch" :columns="columns" @queryTable="getList"></right-toolbar>
    </el-row>

    <el-table v-loading="loading" :data="testProjectMemberList" row-key="testProjectMemberId"
              @selection-change="handleSelectionChange">
      <el-table-column v-if="canManage" :selectable="canSelectMemberRow" align="center" type="selection" width="55"/>
      <el-table-column v-if="columnVisible['testProjectMemberId']" key="testProjectMemberId" align="center" label="项目成员ID"
                       prop="testProjectMemberId"/>
      <el-table-column v-if="columnVisible['testProjectId']" key="testProjectId" align="center" label="测试项目ID"
                       prop="testProjectId"/>
      <el-table-column v-if="columnVisible['userId']" key="userId" align="center" label="用户ID" prop="userId"/>
      <el-table-column v-if="columnVisible['nickName']" key="nickName" align="center" label="用户名称" prop="nickName"/>
      <el-table-column v-if="columnVisible['memberRole']" key="memberRole" align="center" label="成员角色"
                       prop="memberRole">
        <template #default="scope">
          <span>{{ formatMemberRole(scope.row.memberRole) }}</span>
        </template>
      </el-table-column>
      <el-table-column v-if="canManage" align="center" class-name="small-padding fixed-width" label="操作">
        <template #default="scope">
          <el-button v-if="canSelectMemberRow(scope.row)" v-hasPermi="['project:testProjectMember:edit']" icon="Edit" link
                     type="primary" @click="handleUpdate(scope.row)">修改
          </el-button>
          <el-button v-if="canDeleteMemberRow(scope.row)" v-hasPermi="['project:testProjectMember:remove']" icon="Delete" link
                     type="primary" @click="handleDelete(scope.row)">删除
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

    <el-dialog v-model="open" :title="title" append-to-body width="500px">
      <el-form ref="testProjectMemberRef" :model="form" :rules="rules" label-width="80px">
        <el-form-item label="测试项目" prop="testProjectId">
          <el-input v-model="form.projectName" disabled placeholder="项目名称"/>
        </el-form-item>
        <el-form-item label="用户" prop="userId">
          <project-member-select
              v-model:nick-name="form.nickName"
              v-model:user-id="form.userId"
              v-model:user-name="form.userName"
              :disabled="form.testProjectMemberId != null"
              :pick-users-for-test-project-id="form.testProjectMemberId ? null : form.testProjectId"
              width="100%"
              @change="onUserSelectChange"
          />
        </el-form-item>
        <el-form-item label="成员角色" prop="memberRole">
          <el-select
              v-model="form.memberRole"
              :disabled="editingCurrentOwner"
              placeholder="请选择成员角色"
              style="width: 100%"
          >
            <el-option
                v-for="item in dialogMemberRoleOptions"
                :key="item.value"
                :label="item.label"
                :value="item.value"
            ></el-option>
          </el-select>
          <div v-if="editingCurrentOwner" style="color: var(--el-text-color-secondary); font-size: 12px; line-height: 1.5; padding-top: 4px;">
            当前为项目所有者，不可降级；请先将所有权转让给其他成员
          </div>
          <div v-else-if="form.memberRole === 'owner' && hasOtherOwner" style="color: var(--el-color-warning); font-size: 12px; line-height: 1.5; padding-top: 4px;">
            指定后，原所有者将降为管理员
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
  </div>
</template>

<script name="TestProjectMember" setup>
import {
  addTestProjectMember,
  delTestProjectMember,
  getTestProjectMember,
  listTestProjectMember,
  updateTestProjectMember
} from "@/api/project/testProjectMember";
import {getTestProject, myProjectContext} from "@/api/project/testProject";
import ProjectMemberSelect from '@/components/UserSelect/ProjectMemberSelect.vue'
import {useRoute} from 'vue-router'
import useTagsViewStore from '@/store/modules/tagsView'
import useUserStore from '@/store/modules/user'
import { buildProjectTabTitle } from '@/views/project/testProject/utils/projectTabTitle'

const {proxy} = getCurrentInstance()
const route = useRoute()
const tagsViewStore = useTagsViewStore()
const userStore = useUserStore()

const testProjectMemberList = ref([])
const open = ref(false)
const loading = ref(true)
const showSearch = ref(true)
const ids = ref([])
const total = ref(0)
const title = ref("")

const columns = ref([
  {key: 'testProjectMemberId', label: `项目成员ID`, visible: false},
  {key: 'testProjectId', label: `测试项目ID`, visible: false},
  {key: 'userId', label: `用户ID`, visible: false},
  {key: 'nickName', label: `用户名称`, visible: true},
  {key: 'memberRole', label: `成员角色`, visible: true},
])

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
    userName: null,
    memberRole: null,
  },
  rules: {
    testProjectId: [
      {required: true, message: "测试项目不能为空", trigger: "change"}
    ],
    userId: [
      {required: true, message: "请选择用户", trigger: "change"}
    ],
    memberRole: [
      {required: true, message: "成员角色不能为空", trigger: "change"}
    ],
  }
})

const {queryParams, form, rules} = toRefs(data)

const currentProjectInfo = ref(null)
/** 当前用户在该项目中的成员角色 */
const selfMemberRole = ref(null)
/** 修改弹窗打开时记录的角色与用户，提交前做权限校验用 */
const editOriginal = ref(null)

// 系统管理员（用户 ID 为 1）
function isSysAdmin() {
  return Number(userStore.id) === 1
}

// 系统管理员、项目所有者、项目管理员可管理成员
const canManage = computed(() =>
    isSysAdmin() ||
    selfMemberRole.value === 'owner' ||
    selfMemberRole.value === 'admin'
)

// 项目管理员不能操作所有者；不能操作其他管理员（本人除外）
function canSelectMemberRow(row) {
  if (!canManage.value || !row) return false
  if (isSysAdmin() || selfMemberRole.value === 'owner') return true
  if (selfMemberRole.value !== 'admin') return false
  if (row.memberRole === 'owner') return false
  if (row.memberRole === 'admin' && Number(row.userId) !== Number(userStore.id)) return false
  return true
}

// 所有者不可删除，须先转让
function canDeleteMemberRow(row) {
  return canSelectMemberRow(row) && row.memberRole !== 'owner'
}

function findRowByMemberId(memberId) {
  return testProjectMemberList.value.find((r) => r.testProjectMemberId === memberId)
}

const toolbarEditDisabled = computed(() => {
  if (ids.value.length !== 1) return true
  const row = findRowByMemberId(ids.value[0])
  return !row || !canSelectMemberRow(row)
})

const toolbarDeleteDisabled = computed(() => {
  if (!ids.value.length) return true
  return ids.value.some((id) => {
    const row = findRowByMemberId(id)
    return !row || !canDeleteMemberRow(row)
  })
})

const hasOtherOwner = computed(() => {
  const currentId = form.value.testProjectMemberId
  return testProjectMemberList.value.some(
      (r) => r.memberRole === 'owner' && r.testProjectMemberId !== currentId
  )
})

const editingCurrentOwner = computed(() => editOriginal.value?.memberRole === 'owner')

const dialogMemberRoleOptions = computed(() => {
  // 编辑现任所有者：角色锁定为所有者
  if (editingCurrentOwner.value) {
    return memberRoleOptions.value.filter((o) => o.value === 'owner')
  }
  // 项目管理员不能指定成员为所有者
  if (selfMemberRole.value === 'admin' && !isSysAdmin()) {
    return memberRoleOptions.value.filter((o) => o.value !== 'owner')
  }
  return memberRoleOptions.value
})

const memberRoleOptions = ref([
  {value: 'owner', label: '所有者'},
  {value: 'admin', label: '管理员'},
  {value: 'developer', label: '开发者'},
  {value: 'tester', label: '测试员'}
])

function formatMemberRole(memberRole) {
  const role = memberRoleOptions.value.find(item => item.value === memberRole)
  return role ? role.label : memberRole
}

function onUserSelectChange() {
  nextTick(() => {
    proxy.$refs['testProjectMemberRef']?.validateField('userId')
  })
}

function getList() {
  loading.value = true
  listTestProjectMember(queryParams.value)
      .then(response => {
        testProjectMemberList.value = response.rows
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
  editOriginal.value = null
  form.value = {
    testProjectMemberId: null,
    testProjectId: null,
    projectName: null,
    nickName: null,
    userName: null,
    userId: null,
    memberRole: 'developer',
    delStatus: null,
    createTime: null,
    updateTime: null
  }
  proxy.resetForm("testProjectMemberRef")
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
  ids.value = selection.map(item => item.testProjectMemberId)
}

function handleAdd() {
  reset()
  const testProjectId = route.query.testProjectId
  if (testProjectId) {
    form.value.testProjectId = testProjectId
    if (currentProjectInfo.value && currentProjectInfo.value.projectName) {
      form.value.projectName = currentProjectInfo.value.projectName
    }
  }
  open.value = true
  title.value = "添加测试项目成员"
}

function handleUpdate(row) {
  const fromRow = row && row.testProjectMemberId != null ? row.testProjectMemberId : null
  const fromToolbar = ids.value.length === 1 ? ids.value[0] : null
  const targetId = fromRow ?? fromToolbar
  if (targetId == null) {
    proxy.$modal.msgWarning('请选择一条要修改的记录')
    return
  }
  reset()
  getTestProjectMember(targetId).then(response => {
    form.value = response.data
    editOriginal.value = {
      memberRole: response.data.memberRole,
      userId: response.data.userId
    }
    open.value = true
    title.value = "修改测试项目成员"
  })
}

function submitForm() {
  proxy.$refs["testProjectMemberRef"].validate(valid => {
    if (!valid) return
    const isAdd = form.value.testProjectMemberId == null
    if (!isSysAdmin() && selfMemberRole.value === 'admin') {
      if (form.value.memberRole === 'owner') {
        proxy.$modal.msgError('项目管理员不能指定成员为所有者')
        return
      }
      if (!isAdd && editOriginal.value) {
        if (editOriginal.value.memberRole === 'owner') {
          proxy.$modal.msgError('项目管理员不能操作所有者')
          return
        }
        if (editOriginal.value.memberRole === 'admin' && Number(editOriginal.value.userId) !== Number(userStore.id)) {
          proxy.$modal.msgError('项目管理员不能操作其他管理员')
          return
        }
      }
    }
    if (!isAdd && editingCurrentOwner.value && form.value.memberRole !== 'owner') {
      proxy.$modal.msgError('项目必须保留一名所有者，请先将所有权转让给其他成员')
      return
    }

    const doSubmit = () => {
      const req = isAdd ? addTestProjectMember(form.value) : updateTestProjectMember(form.value)
      req.then(() => {
        proxy.$modal.msgSuccess(isAdd ? "新增成功" : "修改成功")
        open.value = false
        getList()
      })
    }

    if (form.value.memberRole === 'owner' && hasOtherOwner.value) {
      proxy.$modal.confirm('指定后，原所有者将降为管理员，是否继续？').then(() => {
        doSubmit()
      }).catch(() => {
      })
      return
    }
    doSubmit()
  })
}

function handleDelete(row) {
  const idList = row && row.testProjectMemberId != null ? [row.testProjectMemberId] : [...ids.value]
  if (!idList.length) {
    proxy.$modal.msgWarning('请选择要删除的数据')
    return
  }
  for (const id of idList) {
    const r = findRowByMemberId(id)
    if (!r) continue
    if (r.memberRole === 'owner') {
      proxy.$modal.msgWarning('不能删除所有者，请先将所有权转让给其他成员')
      return
    }
    if (!canDeleteMemberRow(r)) {
      proxy.$modal.msgWarning('存在无权删除的成员')
      return
    }
  }
  proxy.$modal.confirm('是否确认删除测试项目成员编号为"' + idList + '"的数据项？').then(function () {
    return delTestProjectMember(idList)
  }).then(() => {
    getList()
    proxy.$modal.msgSuccess("删除成功")
  }).catch(() => {
  })
}

function handleExport() {
  proxy.download('project/testProjectMember/export', {
    ...queryParams.value
  }, `testProjectMember_${new Date().getTime()}.xlsx`)
}

function updatePageTitle() {
  if (currentProjectInfo.value?.projectName) {
    const routeObj = Object.assign({}, route, {
      title: buildProjectTabTitle(currentProjectInfo.value.projectName, '项目成员'),
    })
    tagsViewStore.updateVisitedView(routeObj)
  }
}

onMounted(() => {
  if (!route.query.testProjectId) {
    proxy.$tab.closePage();
    return;
  }

  getTestProject(route.query.testProjectId).then(response => {
    if (!response.data) {
      proxy.$tab.closePage();
      return;
    }

    currentProjectInfo.value = response.data

    queryParams.value.testProjectId = route.query.testProjectId

    updatePageTitle()

    myProjectContext(route.query.testProjectId).then(res => {
      selfMemberRole.value = res.data?.memberRole ?? null
    })

    getList()
  }).catch(() => {
    proxy.$tab.closePage();
  })
})
</script>

<template>
  <div class="app-container">
    <el-form v-show="showSearch" ref="queryRef" :inline="true" :model="queryParams" label-width="80px">
      <el-form-item label="角色名称" prop="roleName">
        <el-input v-model="queryParams.roleName" clearable placeholder="角色名称" @keyup.enter="handleQuery"/>
      </el-form-item>
      <el-form-item label="角色标识" prop="roleKey">
        <el-input v-model="queryParams.roleKey" clearable placeholder="role_key" @keyup.enter="handleQuery"/>
      </el-form-item>
      <el-form-item>
        <el-button icon="Search" type="primary" @click="handleQuery">搜索</el-button>
        <el-button icon="Refresh" @click="resetQuery">重置</el-button>
      </el-form-item>
    </el-form>

    <el-row :gutter="10" class="mb8">
      <el-col :span="1.5">
        <el-button v-hasPermi="['project:testProjectMemberRole:add']" icon="Plus" plain type="primary" @click="handleAdd">
          新增
        </el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button
            v-hasPermi="['project:testProjectMemberRole:edit']"
            :disabled="single"
            icon="Edit"
            plain
            type="success"
            @click="handleUpdate"
        >
          修改
        </el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button
            v-hasPermi="['project:testProjectMemberRole:remove']"
            :disabled="multiple"
            icon="Delete"
            plain
            type="danger"
            @click="handleDelete"
        >
          删除
        </el-button>
      </el-col>
      <right-toolbar v-model:showSearch="showSearch" @queryTable="getList"/>
    </el-row>

    <el-table v-loading="loading" :data="roleList" @selection-change="handleSelectionChange">
      <el-table-column align="center" type="selection" width="55"/>
      <el-table-column align="center" label="角色标识" prop="roleKey" width="140"/>
      <el-table-column :show-overflow-tooltip="true" align="center" label="角色名称" prop="roleName"/>
      <el-table-column align="center" label="排序" prop="sortNum" width="80"/>
      <el-table-column align="center" label="状态" prop="enableStatus" width="90">
        <template #default="scope">
          <el-tag :type="scope.row.enableStatus == 1 ? 'success' : 'info'" size="small">
            {{ scope.row.enableStatus == 1 ? '启用' : '禁用' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column align="center" class-name="small-padding fixed-width" label="操作" width="160">
        <template #default="scope">
          <el-button
              v-hasPermi="['project:testProjectMemberRole:edit']"
              link
              type="primary"
              @click="handleUpdate(scope.row)"
          >修改
          </el-button>
          <el-button
              v-hasPermi="['project:testProjectMemberRole:remove']"
              link
              type="primary"
              @click="handleDelete(scope.row)"
          >删除
          </el-button>
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

    <el-dialog v-model="open" :title="title" append-to-body width="720px">
      <el-form ref="roleRef" :model="form" :rules="rules" label-width="90px">
        <el-form-item label="角色标识" prop="roleKey">
          <el-input v-model="form.roleKey" maxlength="64" placeholder="英文标识，如 backend"/>
        </el-form-item>
        <el-form-item label="角色名称" prop="roleName">
          <el-input v-model="form.roleName" maxlength="50" placeholder="显示名称"/>
        </el-form-item>
        <el-form-item label="排序" prop="sortNum">
          <el-input-number v-model="form.sortNum" :min="0" controls-position="right"/>
        </el-form-item>
        <el-form-item label="状态" prop="enableStatus">
          <el-radio-group v-model="form.enableStatus">
            <el-radio :value="1">启用</el-radio>
            <el-radio :value="0">禁用</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="备注" prop="remark">
          <el-input v-model="form.remark" type="textarea"/>
        </el-form-item>
        <el-form-item label="权限">
          <div class="perm-tree-toolbar">
            <el-checkbox v-model="permExpand" @change="handlePermTreeExpand">展开/折叠</el-checkbox>
            <el-checkbox v-model="permNodeAll" @change="handlePermTreeNodeAll">全选/全不选</el-checkbox>
            <el-checkbox v-model="permCheckStrictly" @change="handlePermTreeConnect">父子联动</el-checkbox>
          </div>
          <el-tree
              ref="permTreeRef"
              :check-strictly="!permCheckStrictly"
              :data="permCatalog"
              :props="{ label: 'label', children: 'children' }"
              class="perm-tree"
              empty-text="加载中，请稍候"
              node-key="id"
              show-checkbox
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button type="primary" @click="submitForm">确 定</el-button>
        <el-button @click="cancel">取 消</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup name="TestProjectMemberRole">
/**
 * 项目角色管理：维护 roleKey、排序与启用状态，并为角色绑定细粒度权限（permCodes）。
 * 编辑弹窗用父子树勾选权限：父节点为业务分组，子节点为具体权限编码。
 */
import {
  addTestProjectMemberRole,
  delTestProjectMemberRole,
  getTestProjectMemberRole,
  listTestProjectMemberRole,
  listMemberRolePermCatalog,
  updateTestProjectMemberRole
} from '@/api/project/testProjectMemberRole'
import {nextTick} from 'vue'

const {proxy} = getCurrentInstance()

const roleList = ref([])
const open = ref(false)
const loading = ref(true)
const showSearch = ref(true)
const ids = ref([])
const single = ref(true)
const multiple = ref(true)
const total = ref(0)
const title = ref('')
/** 权限父子树（父=分组，子=权限 code） */
const permCatalog = ref([])
const permTreeRef = ref(null)
const permExpand = ref(true)
const permNodeAll = ref(false)
/** true 表示勾选时父子联动 */
const permCheckStrictly = ref(true)

const data = reactive({
  form: {},
  queryParams: {
    pageNum: 1,
    pageSize: 10,
    roleKey: undefined,
    roleName: undefined
  },
  rules: {
    roleKey: [{required: true, message: '角色标识不能为空', trigger: 'blur'}],
    roleName: [{required: true, message: '角色名称不能为空', trigger: 'blur'}]
  }
})

const {queryParams, form, rules} = toRefs(data)

function getList() {
  loading.value = true
  listTestProjectMemberRole(queryParams.value).then(res => {
    roleList.value = res.rows
    total.value = res.total
  }).finally(() => {
    loading.value = false
  })
}

/** 加载权限树目录 */
function loadCatalog() {
  return listMemberRolePermCatalog().then(res => {
    permCatalog.value = res.data || []
  })
}

function cancel() {
  open.value = false
  reset()
}

function reset() {
  form.value = {
    testProjectMemberRoleId: undefined,
    roleKey: undefined,
    roleName: undefined,
    sortNum: 0,
    enableStatus: 1,
    remark: undefined,
    permCodes: []
  }
  permExpand.value = true
  permNodeAll.value = false
  permCheckStrictly.value = true
  proxy.resetForm('roleRef')
  nextTick(() => {
    permTreeRef.value?.setCheckedKeys([])
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
  ids.value = selection.map(item => item.testProjectMemberRoleId)
  single.value = selection.length !== 1
  multiple.value = !selection.length
}

function handleAdd() {
  reset()
  loadCatalog().then(() => {
    open.value = true
    title.value = '新增项目角色'
    nextTick(() => {
      handlePermTreeExpand(true)
      permTreeRef.value?.setCheckedKeys([])
    })
  })
}

function handleUpdate(row) {
  reset()
  const id = row?.testProjectMemberRoleId || ids.value[0]
  Promise.all([loadCatalog(), getTestProjectMemberRole(id)]).then(([, res]) => {
    form.value = {
      ...res.data,
      enableStatus: Number(res.data.enableStatus ?? 1),
      sortNum: Number(res.data.sortNum ?? 0),
      permCodes: res.data.permCodes || []
    }
    open.value = true
    title.value = '修改项目角色'
    nextTick(() => {
      handlePermTreeExpand(true)
      // 只勾选叶子权限；父子联动时父节点会自动半选/全选
      permTreeRef.value?.setCheckedKeys(form.value.permCodes || [], false)
    })
  })
}

/** 展开或折叠权限树一级分组节点 */
function handlePermTreeExpand(value) {
  const tree = permTreeRef.value
  if (!tree?.store?.nodesMap) return
  for (const root of permCatalog.value) {
    const node = tree.store.nodesMap[root.id]
    if (node) {
      node.expanded = !!value
    }
  }
}

/** 全选或清空权限树 */
function handlePermTreeNodeAll(value) {
  permTreeRef.value?.setCheckedNodes(value ? permCatalog.value : [])
}

/** 切换父子联动：勾选时 true 为联动 */
function handlePermTreeConnect(value) {
  permCheckStrictly.value = !!value
}

/**
 * 收集已勾选的叶子权限 code（忽略分组父节点 g:...）
 */
function collectLeafPermCodes() {
  const tree = permTreeRef.value
  if (!tree) return []
  const checked = tree.getCheckedKeys(false) || []
  return checked.filter(id => typeof id === 'string' && !id.startsWith('g:'))
}

function submitForm() {
  proxy.$refs['roleRef'].validate(valid => {
    if (!valid) return
    const payload = {
      ...form.value,
      permCodes: collectLeafPermCodes()
    }
    const req = form.value.testProjectMemberRoleId
        ? updateTestProjectMemberRole(payload)
        : addTestProjectMemberRole(payload)
    req.then(() => {
      proxy.$modal.msgSuccess('操作成功')
      open.value = false
      getList()
    })
  })
}

function handleDelete(row) {
  const roleIds = row?.testProjectMemberRoleId || ids.value
  proxy.$modal.confirm('是否确认删除所选角色？').then(() => {
    return delTestProjectMemberRole(roleIds)
  }).then(() => {
    getList()
    proxy.$modal.msgSuccess('删除成功')
  }).catch(() => {
  })
}

getList()
</script>

<style scoped>
.perm-tree-toolbar {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  margin-bottom: 8px;
  width: 100%;
}

.perm-tree {
  max-height: 360px;
  overflow: auto;
  width: 100%;
  border: 1px solid var(--el-border-color);
  border-radius: 6px;
  padding: 8px 12px;
}
</style>

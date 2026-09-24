<template>
  <el-aside
      v-loading="treeLoading"
      :class="{ 'is-resizing': isResizing }"
      :width="asideWidth + 'px'"
      class="flow-group-aside"
  >
    <div class="tree-header">
      <div class="tree-search-row">
        <el-input
            v-model="treeFilterText"
            class="tree-search-input"
            clearable
            placeholder="搜索目录"
            prefix-icon="Search"
        />
        <el-tooltip content="新建根目录" placement="bottom">
          <el-button
              v-hasPermi="['project:testProject:add']"
              class="tree-add-btn"
              icon="Plus"
              type="primary"
              @click="handleAddRoot"
          />
        </el-tooltip>
      </div>
      <div class="tree-list-toolbar">
        <div class="tree-list-toolbar-title">
          <svg-icon class="tree-list-title-icon" icon-class="folder"/>
          <span class="tree-list-title-text">测试流目录</span>
        </div>
        <div class="tree-list-toolbar-actions">
          <el-tooltip content="刷新" placement="bottom">
            <el-button circle class="tree-toolbar-icon-btn" icon="Refresh" text @click="refreshTree"/>
          </el-tooltip>
          <el-tooltip :content="treeAllExpanded ? '折叠全部' : '展开全部'" placement="bottom">
            <el-button
                :icon="treeAllExpanded ? 'Fold' : 'Expand'"
                circle
                class="tree-toolbar-icon-btn"
                text
                @click="toggleTreeExpandAll"
            />
          </el-tooltip>
        </div>
      </div>
    </div>
    <div class="tree-wrapper">
      <el-tree
          ref="treeRef"
          :data="treeData"
          :empty-text="'暂无目录'"
          :expand-on-click-node="false"
          :filter-node-method="filterNode"
          :props="treeProps"
          class="flow-group-tree"
          highlight-current
          node-key="treeNodeKey"
          @node-click="handleNodeClick"
      >
        <template #default="{ node, data }">
          <span class="tree-node-label" @contextmenu.stop.prevent="openContextMenu($event, data)">
            <svg-icon class="tree-icon tree-icon-folder" icon-class="folder"/>
            <span class="node-text">{{ node.label }}</span>
            <el-dropdown
                v-if="hasNodeActions(data)"
                class="tree-node-more"
                trigger="click"
                @command="(cmd) => handleNodeCommand(cmd, data)"
                @click.stop
            >
              <span class="tree-more-btn" title="更多操作" @click.stop>
                <el-icon><MoreFilled/></el-icon>
              </span>
              <template #dropdown>
                <el-dropdown-menu>
                  <template v-if="data.nodeType === 'all'">
                    <el-dropdown-item
                        v-hasPermi="['project:testProject:add']"
                        command="addRoot"
                    >新建根目录
                    </el-dropdown-item>
                  </template>
                  <template v-else-if="data.nodeType === 'group'">
                    <el-dropdown-item
                        v-hasPermi="['project:testProject:add']"
                        command="addChild"
                    >新建子目录
                    </el-dropdown-item>
                    <el-dropdown-item
                        v-hasPermi="['project:testProject:edit']"
                        command="rename"
                    >重命名
                    </el-dropdown-item>
                    <el-dropdown-item
                        v-hasPermi="['project:testProject:remove']"
                        class="is-danger"
                        command="delete"
                        divided
                    >删除
                    </el-dropdown-item>
                  </template>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </span>
        </template>
      </el-tree>
    </div>
    <div
        :title="'拖动调整宽度'"
        class="resizer"
        @mousedown="handleResizeStart"
    ></div>

    <ul
        v-show="ctxVisible"
        :style="{ left: ctxLeft + 'px', top: ctxTop + 'px' }"
        class="flow-group-ctx"
        @click.stop
    >
      <template v-if="ctxNode?.nodeType === 'all'">
        <li v-hasPermi="['project:testProject:add']" @click="handleAddRoot">新建根目录</li>
      </template>
      <template v-else-if="ctxNode?.nodeType === 'group'">
        <li v-hasPermi="['project:testProject:add']" @click="handleAddChild()">新建子目录</li>
        <li v-hasPermi="['project:testProject:edit']" @click="handleRename()">重命名</li>
        <li v-hasPermi="['project:testProject:remove']" class="danger" @click="handleDeleteGroup()">删除</li>
      </template>
    </ul>

    <el-dialog v-model="groupDialogVisible" :title="groupDialogTitle" append-to-body width="420px">
      <el-form ref="groupFormRef" :model="groupForm" :rules="groupRules" label-width="80px">
        <el-form-item label="名称" prop="groupName">
          <el-input v-model="groupForm.groupName" maxlength="100" placeholder="目录名称"/>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="groupDialogVisible = false">取消</el-button>
        <el-button :loading="groupSubmitting" type="primary" @click="submitGroupForm">确定</el-button>
      </template>
    </el-dialog>
  </el-aside>
</template>

<script setup>
/**
 * 左侧测试流目录树。
 * 固定「全部」「未分组」虚拟节点，其下为可嵌套真实目录；
 * 点选后通知父页过滤列表；增删改目录后通知父页刷新表格与下拉。
 */
import { nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { MoreFilled } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'

import {
  addTestFlowGroup,
  delTestFlowGroup,
  getTestFlowGroupTree,
  updateTestFlowGroup,
} from '@/api/project/testFlowGroup'

/** 虚拟节点：看全部流 */
const ALL_KEY = 'all'
/** 虚拟节点：只看未挂目录的流 */
const UNGROUPED_KEY = 'ungrouped'

const props = defineProps({
  testProjectId: {
    type: [String, Number],
    required: true,
  },
})

/** select：点选过滤；tree-loaded：树数据就绪；changed：目录增删改后需刷新右侧 */
const emit = defineEmits(['select', 'tree-loaded', 'changed'])

const treeRef = ref()
const treeLoading = ref(false)
const treeFilterText = ref('')
const treeAllExpanded = ref(false)
/** 服务端返回的真实目录树（不含虚拟根） */
const groupTree = ref([])
/** 含「全部」「未分组」的展示用树 */
const treeData = ref([])
const currentKey = ref(ALL_KEY)

const asideWidth = ref(280)
const isResizing = ref(false)
const minAsideWidth = 220
const maxAsideWidth = 480

const treeProps = {
  children: 'children',
  label: 'label',
}

const ctxVisible = ref(false)
const ctxLeft = ref(0)
const ctxTop = ref(0)
const ctxNode = ref(null)

const groupDialogVisible = ref(false)
const groupDialogTitle = ref('')
const groupSubmitting = ref(false)
const groupFormRef = ref()
const groupForm = ref({
  flowGroupId: null,
  parentId: 0,
  groupName: '',
  mode: 'add',
})
const groupRules = {
  groupName: [{ required: true, message: '名称不能为空', trigger: 'blur' }],
}

/** 在真实目录前插入「全部」「未分组」 */
function buildVirtualRoots(groups) {
  return [
    {
      treeNodeKey: ALL_KEY,
      nodeType: 'all',
      label: '全部',
      flowGroupId: null,
      children: [],
    },
    {
      treeNodeKey: UNGROUPED_KEY,
      nodeType: 'ungrouped',
      label: '未分组',
      flowGroupId: null,
      children: [],
    },
    ...mapGroupNodes(groups),
  ]
}

/** 把接口树节点转成 el-tree 所需字段 */
function mapGroupNodes(nodes) {
  if (!Array.isArray(nodes)) return []
  return nodes.map((n) => ({
    treeNodeKey: `g-${n.flowGroupId}`,
    nodeType: 'group',
    label: n.label || n.groupName,
    flowGroupId: n.flowGroupId,
    parentId: n.parentId,
    groupName: n.groupName,
    children: mapGroupNodes(n.children),
  }))
}

/** 目录名本地过滤；虚拟节点始终保留 */
function filterNode(value, data) {
  if (!value) return true
  if (data.nodeType === 'all' || data.nodeType === 'ungrouped') return true
  const v = String(value).toLowerCase()
  return String(data.label || data.groupName || '').toLowerCase().includes(v)
}

/** 拉取目录树并通知父页 */
async function loadTree() {
  treeLoading.value = true
  try {
    const res = await getTestFlowGroupTree(props.testProjectId)
    groupTree.value = Array.isArray(res?.data) ? res.data : []
    treeData.value = buildVirtualRoots(groupTree.value)
    emit('tree-loaded', groupTree.value)
    await nextTick()
    treeRef.value?.setCurrentKey(currentKey.value)
    treeRef.value?.filter(treeFilterText.value)
  } finally {
    treeLoading.value = false
  }
}

function refreshTree() {
  loadTree()
}

/** 把当前树节点转成列表过滤条件发给父页 */
function emitSelection(data) {
  if (!data) return
  if (data.nodeType === 'all') {
    emit('select', { mode: 'all' })
  } else if (data.nodeType === 'ungrouped') {
    emit('select', { mode: 'ungrouped' })
  } else if (data.nodeType === 'group') {
    emit('select', { mode: 'group', flowGroupId: data.flowGroupId })
  }
}

function handleNodeClick(data) {
  currentKey.value = data.treeNodeKey
  emitSelection(data)
  hideContextMenu()
}

function setTreeExpanded(expanded) {
  treeAllExpanded.value = expanded
  nextTick(() => {
    const store = treeRef.value?.store
    const map = store?.nodesMap
    if (!map) return
    Object.values(map).forEach((node) => {
      if (node.level > 0) {
        node.expanded = expanded
      }
    })
  })
}

function toggleTreeExpandAll() {
  setTreeExpanded(!treeAllExpanded.value)
}

/** 「未分组」无目录操作；「全部」可新建根；真实目录可增删改 */
function hasNodeActions(data) {
  return data?.nodeType === 'all' || data?.nodeType === 'group'
}

function openContextMenu(e, data) {
  if (!hasNodeActions(data)) {
    hideContextMenu()
    return
  }
  ctxNode.value = data
  ctxLeft.value = e.clientX
  ctxTop.value = e.clientY
  ctxVisible.value = true
}

function hideContextMenu() {
  ctxVisible.value = false
  ctxNode.value = null
}

/** 三点菜单命令 */
function handleNodeCommand(command, data) {
  hideContextMenu()
  if (command === 'addRoot') {
    handleAddRoot()
    return
  }
  if (command === 'addChild') {
    handleAddChild(data)
    return
  }
  if (command === 'rename') {
    handleRename(data)
    return
  }
  if (command === 'delete') {
    handleDeleteGroup(data)
  }
}

/** 在项目根下新建目录 */
function handleAddRoot() {
  hideContextMenu()
  groupForm.value = { flowGroupId: null, parentId: 0, groupName: '', mode: 'add' }
  groupDialogTitle.value = '新建根目录'
  groupDialogVisible.value = true
}

/** 在当前目录下新建子目录 */
function handleAddChild(node = ctxNode.value) {
  hideContextMenu()
  if (!node || node.nodeType !== 'group') return
  groupForm.value = {
    flowGroupId: null,
    parentId: node.flowGroupId,
    groupName: '',
    mode: 'add',
  }
  groupDialogTitle.value = '新建子目录'
  groupDialogVisible.value = true
}

function handleRename(node = ctxNode.value) {
  hideContextMenu()
  if (!node || node.nodeType !== 'group') return
  groupForm.value = {
    flowGroupId: node.flowGroupId,
    parentId: node.parentId ?? 0,
    groupName: node.groupName || node.label || '',
    mode: 'edit',
  }
  groupDialogTitle.value = '重命名目录'
  groupDialogVisible.value = true
}

/** 删除目录；若删的是当前选中项则回到「全部」 */
async function handleDeleteGroup(node = ctxNode.value) {
  hideContextMenu()
  if (!node || node.nodeType !== 'group') return
  await ElMessageBox.confirm(
      `确认删除目录「${node.label}」？该目录下的测试流将变为未分组。`,
      '提示',
      { type: 'warning' },
  )
  await delTestFlowGroup(node.flowGroupId)
  ElMessage.success('删除成功')
  if (currentKey.value === node.treeNodeKey) {
    currentKey.value = ALL_KEY
    emit('select', { mode: 'all' })
    await loadTree()
  } else {
    await loadTree()
    emit('changed')
  }
}

async function submitGroupForm() {
  const formEl = groupFormRef.value
  if (!formEl) return
  const valid = await formEl.validate().catch(() => false)
  if (!valid) return
  const name = String(groupForm.value.groupName ?? '').trim()
  if (!name) {
    ElMessage.warning('名称不能为空')
    return
  }
  groupSubmitting.value = true
  try {
    if (groupForm.value.mode === 'edit') {
      await updateTestFlowGroup({
        flowGroupId: groupForm.value.flowGroupId,
        groupName: name,
      })
      ElMessage.success('修改成功')
    } else {
      await addTestFlowGroup({
        testProjectId: props.testProjectId,
        parentId: groupForm.value.parentId ?? 0,
        groupName: name,
      })
      ElMessage.success('创建成功')
    }
    groupDialogVisible.value = false
    await loadTree()
    emit('changed')
  } finally {
    groupSubmitting.value = false
  }
}

let resizeMoveHandler = null
let resizeEndHandler = null

/** 拖拽侧栏右缘调整宽度 */
function handleResizeStart(e) {
  e.preventDefault()
  e.stopPropagation()
  isResizing.value = true
  const startX = e.clientX
  const startWidth = asideWidth.value

  resizeMoveHandler = (moveEvent) => {
    if (!isResizing.value) return
    const diff = moveEvent.clientX - startX
    const newWidth = startWidth + diff
    if (newWidth >= minAsideWidth && newWidth <= maxAsideWidth) {
      asideWidth.value = newWidth
    } else if (newWidth < minAsideWidth) {
      asideWidth.value = minAsideWidth
    } else {
      asideWidth.value = maxAsideWidth
    }
  }

  resizeEndHandler = () => {
    isResizing.value = false
    document.removeEventListener('mousemove', resizeMoveHandler)
    document.removeEventListener('mouseup', resizeEndHandler)
    document.body.style.cursor = ''
    document.body.style.userSelect = ''
    resizeMoveHandler = null
    resizeEndHandler = null
  }

  document.addEventListener('mousemove', resizeMoveHandler)
  document.addEventListener('mouseup', resizeEndHandler)
  document.body.style.cursor = 'col-resize'
  document.body.style.userSelect = 'none'
}

function onDocClick() {
  hideContextMenu()
}

watch(treeFilterText, (val) => {
  treeRef.value?.filter(val)
})

watch(
    () => props.testProjectId,
    () => {
      currentKey.value = ALL_KEY
      loadTree()
      emit('select', { mode: 'all' })
    },
)

onMounted(() => {
  loadTree()
  document.addEventListener('click', onDocClick)
})

onBeforeUnmount(() => {
  document.removeEventListener('click', onDocClick)
  if (resizeMoveHandler) {
    document.removeEventListener('mousemove', resizeMoveHandler)
  }
  if (resizeEndHandler) {
    document.removeEventListener('mouseup', resizeEndHandler)
  }
  document.body.style.cursor = ''
  document.body.style.userSelect = ''
})

defineExpose({
  loadTree,
  /** 供父页复用已加载的真实目录树，避免再请求 */
  getGroupTree: () => groupTree.value,
})
</script>

<style lang="scss" scoped>
.flow-group-aside {
  border-right: 1px solid var(--pd-border-subtle, #c5d8ec);
  background: var(--pd-bg-sidebar, #dfeaf8);
  display: flex;
  flex-direction: column;
  height: 100%;
  position: relative;
  transition: width 0.1s ease-out;
  padding: 0 !important;
  margin: 0 !important;
  box-sizing: border-box;

  &.is-resizing {
    transition: none;
  }

  .tree-header {
    padding: 0;
    background: var(--pd-surface-elevated, #fff);
    border-bottom: 1px solid var(--pd-border-subtle, #c5d8ec);
    flex-shrink: 0;

    .tree-search-row {
      display: flex;
      align-items: stretch;
      gap: 8px;
      padding: 8px 10px;
      background: var(--pd-surface-elevated, #fff);
      border-bottom: 1px solid var(--pd-border-muted, #d6e6f5);
    }

    .tree-search-input {
      flex: 1;
      min-width: 0;
      margin: 0;
    }

    .tree-add-btn {
      flex-shrink: 0;
      width: 36px;
      height: 36px;
      padding: 0;
      border-radius: 4px;
    }

    .tree-list-toolbar {
      display: flex;
      align-items: center;
      justify-content: space-between;
      height: 40px;
      padding: 0 10px;
      background: var(--pd-bg-toolbar, #d0e2f4);
      border-bottom: 1px solid var(--pd-border-subtle, #c5d8ec);
    }

    .tree-list-toolbar-title {
      display: flex;
      align-items: center;
      gap: 8px;
      font-size: 14px;
      font-weight: 600;
      color: #303133;

      .tree-list-title-icon {
        font-size: 16px;
        color: #606266;
      }
    }

    .tree-list-toolbar-actions {
      display: flex;
      align-items: center;
      gap: 2px;

      .tree-toolbar-icon-btn {
        padding: 6px;
      }
    }
  }

  .tree-wrapper {
    flex: 1;
    overflow-y: auto;
    padding: 10px 0;
  }

  .flow-group-tree {
    background: transparent;
    padding: 0 12px;

    :deep(.el-tree-node) {
      margin: 2px 0;
    }

    :deep(.el-tree-node__content) {
      height: 32px;
      border-radius: 4px;
      transition: background-color 0.2s;
      padding-right: 5px;
      box-sizing: border-box;

      &:hover {
        background-color: var(--pd-bg-sunken, #e9f2fc);
      }
    }

    :deep(.el-tree-node.is-current > .el-tree-node__content) {
      background-color: rgba(11, 110, 220, 0.12);
      color: var(--pd-primary, #0b6edc);
    }

    .tree-node-label {
      display: flex;
      align-items: center;
      font-size: 14px;
      flex: 1;
      min-width: 0;
      overflow: hidden;
      gap: 6px;

      .tree-icon-folder {
        color: #1a1a1a;
        flex-shrink: 0;
      }

      .node-text {
        flex: 1;
        min-width: 0;
        overflow: hidden;
        text-overflow: ellipsis;
        white-space: nowrap;
      }

      .tree-node-more {
        flex-shrink: 0;
        margin-left: auto;
        line-height: 1;
      }

      .tree-more-btn {
        display: inline-flex;
        align-items: center;
        justify-content: center;
        width: 22px;
        height: 22px;
        border-radius: 4px;
        color: #909399;
        opacity: 0;
        cursor: pointer;
        transition: opacity 0.15s, background-color 0.15s, color 0.15s;

        &:hover {
          color: var(--pd-primary, #0b6edc);
          background: rgba(11, 110, 220, 0.1);
        }
      }
    }

    :deep(.el-tree-node__content:hover) .tree-more-btn,
    :deep(.el-tree-node.is-current > .el-tree-node__content) .tree-more-btn {
      opacity: 1;
    }
  }

  .resizer {
    position: absolute;
    top: 0;
    right: 0;
    width: 4px;
    height: 100%;
    cursor: col-resize;
    z-index: 10;

    &:hover {
      background: rgba(11, 110, 220, 0.25);
    }
  }
}

.flow-group-ctx {
  position: fixed;
  z-index: 3000;
  margin: 0;
  padding: 4px 0;
  list-style: none;
  background: #fff;
  border: 1px solid #e4e7ed;
  border-radius: 4px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.12);
  min-width: 120px;

  &:empty {
    display: none;
  }

  li {
    padding: 8px 14px;
    font-size: 13px;
    cursor: pointer;
    color: #303133;

    &:hover {
      background: #f5f7fa;
    }

    &.danger {
      color: #f56c6c;
    }
  }
}

</style>

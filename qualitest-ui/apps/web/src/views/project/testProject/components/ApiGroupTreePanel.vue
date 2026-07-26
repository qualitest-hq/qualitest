<template>
  <el-aside
      v-loading="treeLoading"
      :class="{ 'is-resizing': isResizing }"
      :width="asideWidth + 'px'"
      class="api-tree-aside"
  >
    <div class="tree-header">
      <div class="tree-search-row">
        <el-input
            v-model="treeFilterText"
            class="tree-search-input"
            clearable
            placeholder="关键字 / URL"
            prefix-icon="Search"
        />
        <el-tooltip content="添加接口" placement="bottom">
          <el-button class="tree-add-btn" icon="Plus" type="primary" @click="handleTreeAddHint"/>
        </el-tooltip>
      </div>
      <div class="tree-list-toolbar">
        <div class="tree-list-toolbar-title">
          <svg-icon class="tree-list-title-icon" icon-class="list"/>
          <span class="tree-list-title-text">API 列表</span>
        </div>
        <div class="tree-list-toolbar-actions">
          <el-tooltip content="刷新列表" placement="bottom">
            <el-button circle class="tree-toolbar-icon-btn" icon="Refresh" text @click="refreshApiTree"/>
          </el-tooltip>
          <el-tooltip content="定位到当前选中接口" placement="bottom">
            <el-button circle class="tree-toolbar-icon-btn" icon="Location" text @click="locateSelectedApi"/>
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
          :data="apiGroupTree"
          :empty-text="apiGroupTree.length === 0 ? '暂无API分组' : '暂无匹配数据'"
          :filter-node-method="filterNode"
          :props="treeProps"
          class="api-group-tree"
          highlight-current
          node-key="treeNodeKey"
          @node-click="handleNodeClick"
      >
        <template #default="{ node, data }">
          <span class="tree-node-label">
            <svg-icon v-if="data.nodeType !== 'api'" class="tree-icon tree-icon-folder" icon-class="folder"/>
            <span
                v-else
                :class="getApiHttpMethodBadgeClass(data.httpMethod)"
                class="tree-method-badge"
            >{{ data.httpMethod || '—' }}</span>
            <span class="node-text">{{ node.label }}</span>
            <span
                v-if="data.nodeType !== 'api' && groupDisplayCount(data) > 0"
                class="api-count"
            >（{{ groupDisplayCount(data) }}）</span>
          </span>
        </template>
      </el-tree>
    </div>
    <div
        :title="'拖动调整宽度'"
        class="resizer"
        @mousedown="handleResizeStart"
    ></div>
  </el-aside>
</template>

<script setup>
import {getTestProjectApiTree} from '@/api/project/testProjectApi'
import {getApiHttpMethodBadgeClass} from '@/views/project/testProject/utils/httpMethodMeta'
import {
  getCurrentInstance,
  nextTick,
  onBeforeUnmount,
  ref,
  watch
} from 'vue'

const props = defineProps({
  testProjectId: {
    type: [String, Number],
    required: true
  }
})

const emit = defineEmits(['refresh-start', 'select-api', 'clear-detail', 'tree-refreshed'])

const {proxy} = getCurrentInstance()

const treeFilterText = ref('')
/** 加载后默认全部折叠；工具栏「展开全部」可一键展开 */
const treeAllExpanded = ref(false)
const lastSelectedApiKey = ref(null)
const lastSelectedApiId = ref(null)

const asideWidth = ref(360)
const isResizing = ref(false)
const minAsideWidth = 280
const maxAsideWidth = 680

const treeRef = ref()
const treeLoading = ref(false)
const apiGroupTree = ref([])
const treeProps = {
  children: 'children',
  label: 'label'
}

function filterNode(value, data) {
  if (!value) return true
  const v = String(value).toLowerCase()
  if (data.nodeType === 'api') {
    const name = (data.apiName || data.label || '').toLowerCase()
    const path = (data.apiPath || '').toLowerCase()
    const method = (data.httpMethod || '').toLowerCase()
    return name.includes(v) || path.includes(v) || method.includes(v)
  }
  const gn = (data.groupName || data.label || '').toLowerCase()
  return gn.includes(v)
}

function countFilteredSubtreeApis(node, filterVal, ancestorGroupStack) {
  if (!filterVal) {
    return node.nodeType === 'group' ? (node.apiCount ?? 0) : 0
  }
  const stackForChildren = node.nodeType === 'group' ? [...ancestorGroupStack, node] : ancestorGroupStack
  let sum = 0
  for (const child of node.children || []) {
    if (child.nodeType === 'api') {
      if (apiVisibleUnderFilter(child, filterVal, stackForChildren)) sum++
    } else {
      sum += countFilteredSubtreeApis(child, filterVal, stackForChildren)
    }
  }
  return sum
}

function apiVisibleUnderFilter(apiNode, filterVal, ancestorGroupStack) {
  if (filterNode(filterVal, apiNode)) return true
  return ancestorGroupStack.some(g => filterNode(filterVal, g))
}

function groupDisplayCount(groupNode) {
  const f = (treeFilterText.value || '').trim()
  if (!f) return groupNode.apiCount ?? 0
  return countFilteredSubtreeApis(groupNode, f, [])
}

/** 刷新或首次加载后默认全部折叠，减轻首屏 DOM；「展开全部」仍可一键展开 */
function collapseAllTreeNodes() {
  nextTick(() => {
    const store = treeRef.value?.store
    const map = store?.nodesMap
    if (!map) return
    Object.values(map).forEach((node) => {
      node.expanded = false
    })
    treeAllExpanded.value = false
  })
}

function expandAncestorsOfTreeNodeKey(key) {
  const t = treeRef.value
  if (!t || key == null) return
  const node = t.getNode(key)
  if (!node) return
  let p = node.parent
  while (p && p.level > 0) {
    p.expanded = true
    p = p.parent
  }
}

function applyTreeStateAfterLoad() {
  nextTick(() => {
    treeRef.value?.filter(treeFilterText.value)
    const k = lastSelectedApiKey.value
    const id = lastSelectedApiId.value
    if (k != null && id != null) {
      nextTick(() => {
        const t = treeRef.value
        const node = t?.getNode?.(k)
        const data = node?.data
        if (node && data?.nodeType === 'api' && data.testProjectApiId != null) {
          expandAncestorsOfTreeNodeKey(k)
          t.setCurrentKey(k)
          emit('select-api', {
            testProjectApiId: data.testProjectApiId,
            httpMethod: data.httpMethod || '',
            apiName: data.apiName || data.label || ''
          })
        } else {
          lastSelectedApiKey.value = null
          lastSelectedApiId.value = null
          emit('clear-detail')
          collapseAllTreeNodes()
        }
      })
    } else {
      nextTick(() => collapseAllTreeNodes())
    }
  })
}

function getApiGroupTree(fromManualRefresh = false) {
  emit('refresh-start')
  treeLoading.value = true
  getTestProjectApiTree({testProjectId: props.testProjectId})
      .then((res) => {
        const tree = res?.data
        apiGroupTree.value = Array.isArray(tree) ? tree : []
        applyTreeStateAfterLoad()
      })
      .catch(() => {
      })
      .finally(() => {
        treeLoading.value = false
        if (fromManualRefresh) {
          emit('tree-refreshed')
        }
      })
}

function handleNodeClick(data) {
  if (data.nodeType === 'api' && data.testProjectApiId != null) {
    lastSelectedApiKey.value = data.treeNodeKey
    lastSelectedApiId.value = data.testProjectApiId
    emit('select-api', {
      testProjectApiId: data.testProjectApiId,
      httpMethod: data.httpMethod || '',
      apiName: data.apiName || data.label || ''
    })
  } else {
    emit('clear-detail')
  }
}

function handleTreeAddHint() {
  proxy.$modal.msg('接口通常由同步或导入产生')
}

function refreshApiTree() {
  getApiGroupTree(true)
}

function setTreeExpanded(expanded) {
  treeAllExpanded.value = expanded
  nextTick(() => {
    const store = treeRef.value?.store
    const map = store?.nodesMap
    if (!map) return
    Object.values(map).forEach((node) => {
      node.expanded = expanded
    })
  })
}

function toggleTreeExpandAll() {
  setTreeExpanded(!treeAllExpanded.value)
}

function locateSelectedApi() {
  const key = lastSelectedApiKey.value
  if (!key) {
    proxy.$modal.msgWarning('请先在树中选择一个 API')
    return
  }
  const tree = treeRef.value
  if (!tree) return
  const node = tree.getNode(key)
  if (!node) {
    proxy.$modal.msgWarning('当前选中节点不在树上，请刷新列表后重试')
    return
  }
  let p = node.parent
  while (p && p.level > 0) {
    p.expanded = true
    p = p.parent
  }
  tree.setCurrentKey(key)
  nextTick(() => {
    tree.setCurrentKey(key)
    const wrap = document.querySelector('.api-tree-aside .tree-wrapper')
    const cur = wrap?.querySelector?.('.el-tree-node.is-current')
    cur?.scrollIntoView({block: 'nearest', behavior: 'smooth'})
  })
}

watch(treeFilterText, (val) => {
  treeRef.value?.filter(val)
})

let resizeMoveHandler = null
let resizeEndHandler = null

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
    } else if (newWidth > maxAsideWidth) {
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

onBeforeUnmount(() => {
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
  loadTree: getApiGroupTree
})
</script>

<style lang="scss" scoped>
.api-tree-aside {
  border-right: 1px solid var(--pd-border-subtle, #c5d8ec);
  background: var(--pd-bg-sidebar, #dfeaf8);
  display: flex;
  flex-direction: column;
  height: 100%;
  position: relative;
  transition: width 0.1s ease-out;
  /* 覆盖全局 index.scss 中 aside 的 padding / margin-bottom，避免与主列之间出现缝 */
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

      :deep(.el-input__wrapper) {
        border-radius: 4px;
        box-shadow: 0 0 0 1px #dcdfe6 inset;
      }

      :deep(.el-input__inner) {
        padding: 8px 10px;
      }
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
    scrollbar-gutter: stable;
    padding: 10px 0;
  }

  .api-group-tree {
    background: transparent;
    padding: 0 12px;

    :deep(.el-tree-node) {
      margin: 2px 0;
    }

    :deep(.el-tree-node__content) {
      height: 32px;
      border-radius: 4px;
      transition: background-color 0.2s;
      padding-right: 10px;
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
      width: 100%;
      overflow: hidden;
      gap: 6px;

      .tree-icon {
        margin-right: 0;
        font-size: 16px;
        flex-shrink: 0;
        color: #909399;
      }

      .tree-icon-folder {
        color: #1a1a1a;
      }

      .tree-method-badge {
        flex-shrink: 0;
        min-width: max(32px, min-content);
        padding: 0 3px;
        text-align: center;
        font-size: 10px;
        font-weight: 700;
        font-family: ui-monospace, 'Consolas', monospace;
        line-height: 1.5;
        border-radius: 2px;
        letter-spacing: 0.02em;
        background: var(--pd-bg-sunken, #e9f2fc);
        color: #606266;
        border: 1px solid var(--pd-border-muted, #d6e6f5);
        box-sizing: border-box;
        white-space: nowrap;

        &.is-GET {
          color: #67c23a;
          border-color: #c2e7b0;
          background: #f0f9eb;
        }

        &.is-POST {
          color: #e6a23c;
          border-color: #f5dab1;
          background: #fdf6ec;
        }

        &.is-PUT {
          color: #409eff;
          border-color: #b3d8ff;
          background: #ecf5ff;
        }

        &.is-PATCH {
          color: #909399;
          border-color: #dcdfe6;
          background: #f4f4f5;
        }

        &.is-DELETE {
          color: #f56c6c;
          border-color: #fbc4c4;
          background: #fef0f0;
        }

        &.is-HEAD,
        &.is-OPTIONS {
          color: #909399;
          border-color: #dcdfe6;
          background: #fafafa;
        }

        &.is-other {
          color: #606266;
        }

        &.is-unknown {
          font-weight: 500;
          color: #c0c4cc;
        }
      }

      .node-text {
        flex: 1;
        overflow: hidden;
        text-overflow: ellipsis;
        white-space: nowrap;
        min-width: 0;
      }

      .api-count {
        margin-left: 4px;
        margin-right: 2px;
        color: #909399;
        font-size: 12px;
        flex-shrink: 0;
      }
    }
  }

  .resizer {
    position: absolute;
    right: 0;
    top: 0;
    bottom: 0;
    width: 4px;
    cursor: col-resize;
    background: transparent;
    z-index: 10;
    transition: background-color 0.2s;
    user-select: none;

    &:hover {
      background-color: #409eff;
    }

    &:active {
      background-color: #409eff;
    }

    &::after {
      content: '';
      position: absolute;
      left: 50%;
      top: 50%;
      transform: translate(-50%, -50%);
      width: 2px;
      height: 20px;
      background: #c0c4cc;
      border-radius: 1px;
      opacity: 0;
      transition: opacity 0.2s;
    }

    &:hover::after {
      opacity: 1;
    }
  }
}
</style>

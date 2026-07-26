<template>
  <div class="detail-doc-tab-strip" role="tablist" aria-label="已打开接口">
    <div ref="scrollRef" class="detail-doc-tab-scroll">
      <el-dropdown
          v-for="tab in tabs"
          :key="tab.key"
          class="detail-doc-tab-dropdown"
          popper-class="detail-doc-tab-ctx-popper"
          trigger="contextmenu"
          @command="cmd => emitMenuCommand(cmd, tab.key)"
      >
        <span class="detail-doc-tab-trigger-wrap">
          <button
              :aria-selected="activeKey === tab.key"
              :class="{
                active: activeKey === tab.key,
                'is-pinned': tab.pinned
              }"
              class="detail-doc-tab"
              role="tab"
              type="button"
              @click="emit('select', tab.key)"
          >
            <span
                :class="documentTabMethodClass(tab.httpMethod)"
                class="detail-doc-tab-method"
            >{{ (tab.httpMethod || '').trim() || '—' }}</span>
            <span class="detail-doc-tab-title" :title="tab.apiName || ''">{{
                tab.testProjectApiId == null
                    ? '未选择接口'
                    : (tab.apiName || '未命名接口')
            }}</span>
            <span
                class="detail-doc-tab-close"
                role="presentation"
                title="关闭"
                @click.stop="emit('close', tab.key)"
            >
              <el-icon :size="16">
                <Close/>
              </el-icon>
            </span>
          </button>
        </span>
        <template #dropdown>
          <el-dropdown-menu class="detail-doc-tab-ctx-menu">
            <el-dropdown-item command="pin">
              <span class="detail-doc-ctx-row">
                <svg
                    class="detail-doc-ctx-svg detail-doc-ctx-svg--pin"
                    width="16"
                    height="16"
                    viewBox="0 0 16 16"
                    aria-hidden="true"
                    xmlns="http://www.w3.org/2000/svg"
                >
                  <path
                      fill="currentColor"
                      d="M9.828.722a.5.5 0 0 1 .354.146l4.95 4.95a.5.5 0 0 1 0 .707c-.48.48-1.072.588-1.503.588-.177 0-.335-.018-.46-.039l-3.134 3.134a5.927 5.927 0 0 1 .16 1.013c.046.702-.032 1.687-.72 2.375a.5.5 0 0 1-.707 0l-2.829-2.828-3.182 3.182c-.195.195-.526.195-.721 0-.195-.195-.195-.512 0-.707l3.182-3.182-2.828-2.829a.5.5 0 0 1 0-.707c.688-.688 1.673-.767 2.375-.72a5.922 5.922 0 0 1 1.013.16l3.182-3.182a5.57 5.57 0 0 1-.041-.461c0-.431.108-1.023.589-1.503a.5.5 0 0 1 .353-.146z"
                  />
                </svg>
                <span class="detail-doc-ctx-label">{{ tab.pinned ? '取消固定' : '固定标签页' }}</span>
              </span>
            </el-dropdown-item>
            <el-dropdown-item command="closeCurrent" divided>
              <span class="detail-doc-ctx-row">
                <svg
                    class="detail-doc-ctx-svg detail-doc-ctx-svg--close"
                    width="16"
                    height="16"
                    viewBox="0 0 24 24"
                    aria-hidden="true"
                    xmlns="http://www.w3.org/2000/svg"
                >
                  <path
                      fill="currentColor"
                      d="M19 6.41L17.59 5 12 10.59 6.41 5 5 6.41 10.59 12 5 17.59 6.41 19 12 13.41 17.59 19 19 17.59 13.41 12z"
                  />
                </svg>
                <span class="detail-doc-ctx-label">关闭当前标签页</span>
              </span>
            </el-dropdown-item>
            <el-dropdown-item command="closeOthers">
              <span class="detail-doc-ctx-row">
                <span class="detail-doc-ctx-lead" aria-hidden="true"/>
                <span class="detail-doc-ctx-label">关闭其它标签页</span>
              </span>
            </el-dropdown-item>
            <el-dropdown-item command="closeAll">
              <span class="detail-doc-ctx-row">
                <span class="detail-doc-ctx-lead" aria-hidden="true"/>
                <span class="detail-doc-ctx-label">关闭全部标签页</span>
              </span>
            </el-dropdown-item>
          </el-dropdown-menu>
        </template>
      </el-dropdown>
    </div>
    <div v-if="$slots.actions" class="detail-doc-tab-actions">
      <slot name="actions"/>
    </div>
  </div>
</template>

<script setup>
import {Close} from '@element-plus/icons-vue'

defineProps({
  tabs: {
    type: Array,
    default: () => []
  },
  activeKey: {
    type: [String, null],
    default: null
  }
})

const emit = defineEmits(['select', 'close', 'menuCommand'])

const scrollRef = ref(null)

function documentTabMethodClass(m) {
  const x = (m || '').toUpperCase()
  if (!x || x === '—') return 'is-unknown'
  if (['GET', 'POST', 'PUT', 'PATCH', 'DELETE', 'HEAD', 'OPTIONS'].includes(x)) {
    return `is-${x}`
  }
  return 'is-other'
}

function emitMenuCommand(command, key) {
  emit('menuCommand', command, key)
}

function onScrollWheel(e) {
  const el = scrollRef.value
  if (!el || el.scrollWidth <= el.clientWidth + 1) return

  const dy = e.deltaY
  const dx = e.deltaX

  if (e.shiftKey) {
    const delta = Math.abs(dx) > Math.abs(dy) ? dx : dy
    if (delta !== 0) {
      e.preventDefault()
      el.scrollLeft += delta
    }
    return
  }

  if (Math.abs(dx) > Math.abs(dy)) return

  if (dy !== 0) {
    e.preventDefault()
    el.scrollLeft += dy
  }
}

onMounted(() => {
  nextTick(() => {
    scrollRef.value?.addEventListener('wheel', onScrollWheel, {passive: false})
  })
})

onBeforeUnmount(() => {
  scrollRef.value?.removeEventListener('wheel', onScrollWheel)
})
</script>

<style lang="scss" scoped>
.detail-doc-tab-strip {
  display: flex;
  align-items: stretch;
  flex-shrink: 0;
  box-sizing: border-box;
  height: var(--pd-doc-strip-h, 53px);
  min-height: var(--pd-doc-strip-h, 53px);
  overflow: hidden;
  border-bottom: 1px solid var(--pd-border-subtle);
  /* 文档页签条背景，与主区色带系同一套色值 */
  background: #d0e2f4;
  box-shadow: none;
}

.detail-doc-tab-scroll {
  flex: 1;
  min-width: 0;
  align-self: stretch;
  display: flex;
  flex-wrap: nowrap;
  align-items: stretch;
  gap: 0;
  overflow-x: auto;
  overflow-y: hidden;
  padding: 0 0 0 10px;
  scrollbar-width: none;
  -ms-overflow-style: none;

  &::-webkit-scrollbar {
    display: none;
    width: 0;
    height: 0;
  }
}

/* 页签条右侧固定操作区：不参与横向滚动 */
.detail-doc-tab-actions {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  align-self: stretch;
  padding: 0 10px 0 6px;
  border-left: 1px solid rgba(15, 23, 42, 0.06);
  background: #d0e2f4;
}

.detail-doc-tab-dropdown {
  flex-shrink: 0;
  align-self: stretch;
  display: inline-flex;
  vertical-align: top;
}

.detail-doc-tab-trigger-wrap {
  display: inline-flex;
  align-items: stretch;
  height: 100%;
  cursor: pointer;
}

.detail-doc-tab {
  position: relative;
  box-sizing: border-box;
  display: inline-flex;
  align-items: center;
  justify-content: flex-start;
  gap: 8px;
  align-self: stretch;
  min-height: 0;
  max-width: min(260px, 44vw);
  margin: 0;
  padding: 0 12px;
  border: none;
  border-radius: 0;
  background: transparent;
  font-family: inherit;
  font-size: 13px;
  line-height: 1.35;
  color: var(--pd-text-tab);
  cursor: pointer;
  flex-shrink: 0;
  transition: background 0.15s ease, color 0.15s ease;

  &:hover {
    background: rgba(255, 255, 255, 0.72);
    color: var(--pd-text);
  }

  &.is-pinned {
    border-left: 3px solid var(--pd-primary);
    padding-left: 9px;
  }

  &.active {
    color: var(--pd-text);
    font-weight: 600;
    background: var(--pd-surface-elevated);

    &::after {
      content: '';
      position: absolute;
      left: 0;
      right: 0;
      bottom: 0;
      height: 3px;
      border-radius: 0;
      background: var(--pd-primary);
    }
  }
}

.detail-doc-tab-method {
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
  background: var(--pd-bg-sunken);
  color: #606266;
  border: 1px solid var(--pd-border-muted);
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

.detail-doc-tab-title {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  text-align: left;
  display: flex;
  align-items: center;
  line-height: 1.35;
}

.detail-doc-tab-close {
  flex-shrink: 0;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  margin: 0;
  padding: 4px;
  border-radius: 4px;
  opacity: 0.55;
  transition: opacity 0.15s ease, background 0.15s ease;

  &:hover {
    opacity: 1;
    background: rgba(15, 23, 42, 0.06);
  }
}
</style>

<style lang="scss">
.detail-doc-tab-ctx-popper.el-popper {
  min-width: 220px;
}

ul.detail-doc-tab-ctx-menu.el-dropdown-menu {
  min-width: 220px;
  padding: 4px 0;

  .detail-doc-ctx-row {
    box-sizing: border-box;
    display: flex;
    flex-direction: row;
    align-items: center;
    gap: 8px;
    width: 100%;
    min-height: 22px;
    font-size: 14px;
    line-height: 22px;
  }

  .detail-doc-ctx-svg {
    box-sizing: border-box;
    flex: 0 0 16px;
    width: 16px !important;
    height: 16px !important;
    max-width: 16px !important;
    max-height: 16px !important;
    margin: 0;
    padding: 0;
    display: block;
    overflow: hidden;
    color: #606266;
  }

  .detail-doc-ctx-lead {
    flex: 0 0 16px;
    width: 16px;
    height: 16px;
    flex-shrink: 0;
  }

  .detail-doc-ctx-label {
    flex: 1;
    min-width: 0;
  }

  .el-dropdown-menu__item {
    padding: 8px 16px;
    line-height: 22px;
    display: flex;
    align-items: center;
  }

  svg {
    flex: 0 0 16px;
    width: 16px !important;
    height: 16px !important;
    max-width: 16px !important;
    max-height: 16px !important;
  }
}
</style>

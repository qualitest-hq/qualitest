<template>
  <div class="detail-footer">
    <div class="footer-left">
      <el-tooltip content="刷新详情" placement="top">
        <el-button
            circle
            class="footer-refresh-detail-btn"
            icon="Refresh"
            size="small"
            text
            @click="$emit('refresh-detail')"
        />
      </el-tooltip>
      <span class="footer-sync-label">最新API同步</span>
      <span class="footer-sync-time" :title="syncTimeTitle">{{ syncTimeDisplay }}</span>
    </div>
    <div class="footer-right">
      <el-tooltip :content="isFullscreen ? '退出全屏' : '全屏'" placement="top">
        <button
            :class="{ 'is-active': isFullscreen }"
            class="detail-footer-fullscreen-btn"
            type="button"
            @click="$emit('toggle-fullscreen')"
        >
          <svg-icon
              :icon-class="isFullscreen ? 'exit-fullscreen' : 'fullscreen'"
              class="fullscreen-icon"
          />
        </button>
      </el-tooltip>
    </div>
  </div>
</template>

<script setup>
import {computed} from 'vue'
import {parseTime} from '@/utils/qualitest'

const props = defineProps({
  isFullscreen: {
    type: Boolean,
    default: false
  },
  /** 父组件传入的同步时间：项目级或当前接口的 lastApiSyncTime */
  lastApiSyncTime: {
    type: [String, Number, Date],
    default: null
  }
})

defineEmits(['toggle-fullscreen', 'refresh-detail'])

const syncTimeDisplay = computed(() => {
  const v = props.lastApiSyncTime
  if (v == null || v === '') return '—'
  const s = parseTime(v, '{y}-{m}-{d} {h}:{i}:{s}')
  return s || '—'
})

const syncTimeTitle = computed(() => {
  const v = props.lastApiSyncTime
  if (v == null || v === '') return '暂无同步记录'
  return String(syncTimeDisplay.value)
})
</script>

<style lang="scss" scoped>
.detail-footer {
  height: 40px;
  padding: 0 8px 0 16px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  border-top: 1px solid #e4e7ed;
  background: #fff;
  flex-shrink: 0;
  box-shadow: 0 -1px 4px rgba(0, 0, 0, 0.04);

  .footer-left {
    flex: 1;
    min-width: 0;
    display: flex;
    align-items: center;
    gap: 8px;
    font-size: 12px;
    line-height: 1.4;
    color: var(--pd-text-muted, #909399);
  }

  .footer-refresh-detail-btn {
    flex-shrink: 0;
    padding: 6px;
  }

  .footer-sync-label {
    flex-shrink: 0;
    font-weight: 500;
    color: var(--pd-text-muted, #909399);
  }

  .footer-sync-time {
    min-width: 0;
    font-variant-numeric: tabular-nums;
    color: var(--pd-text, #303133);
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .footer-right {
    display: flex;
    align-items: center;

    .detail-footer-fullscreen-btn {
      display: inline-flex;
      align-items: center;
      justify-content: center;
      margin: 0;
      padding: 4px 10px;
      min-height: 28px;
      border: 1px solid #dcdcdc;
      border-radius: 4px;
      background: #fff;
      color: #909399;
      cursor: pointer;
      transition: color 0.2s, background-color 0.2s, border-color 0.2s;

      &:hover {
        color: var(--el-color-primary);
        border-color: var(--el-color-primary-light-5);
        background: #fff;
      }

      &.is-active {
        background: var(--el-color-primary);
        border-color: var(--el-color-primary);
        color: #fff;

        &:hover {
          background: var(--el-color-primary);
          border-color: var(--el-color-primary);
          color: #fff;
          filter: brightness(1.05);
        }
      }

      &:focus-visible {
        outline: 2px solid var(--el-color-primary-light-5);
        outline-offset: 1px;
      }

      .fullscreen-icon {
        font-size: 16px;
        vertical-align: middle;
      }
    }
  }
}
</style>

<template>
  <div class="debug-response-pane">
    <div class="debug-response-head">
      <span class="debug-response-title">返回响应</span>
      <div class="debug-response-meta">
        <template v-if="debugResponse.sent && debugResponse.status != null">
          <span :class="{ 'is-ok': debugResponse.ok, 'is-err': !debugResponse.ok }" class="debug-status-pill">
            {{ debugResponse.status }} {{ debugResponse.statusText }}
          </span>
          <span v-if="debugResponse.durationMs != null" class="debug-duration">{{ debugResponse.durationMs }} ms</span>
        </template>
      </div>
    </div>
    <div v-if="!debugResponse.sent" class="debug-response-empty">
      <div class="debug-response-empty-inner">
        <div class="debug-response-empty-icon" aria-hidden="true">
          <svg fill="none" height="56" viewBox="0 0 72 56" width="72" xmlns="http://www.w3.org/2000/svg">
            <path
                d="M8 16c0-4.418 3.582-8 8-8h40c4.418 0 8 3.582 8 8v28H8V16z"
                stroke="currentColor"
                stroke-width="2"
            />
            <path d="M20 36h32M20 44h20" stroke="currentColor" stroke-linecap="round" stroke-width="2"/>
          </svg>
        </div>
        <p class="debug-response-empty-title">尚未发送请求</p>
        <p class="debug-response-empty-desc">点击上方「发送」查看响应内容与耗时</p>
      </div>
    </div>
    <div v-else class="debug-response-body">
      <el-alert
          v-if="debugResponse.error"
          :closable="false"
          :title="debugResponse.error"
          class="debug-error-alert"
          show-icon
          type="error"
      />
      <el-alert
          v-if="debugResponse.corsHint"
          :closable="false"
          :title="debugResponse.corsHint"
          class="debug-cors-hint-alert"
          show-icon
          type="info"
      />
      <el-alert
          v-if="debugResponse.preScriptError"
          :closable="false"
          :title="'前置脚本：' + debugResponse.preScriptError"
          class="debug-error-alert"
          show-icon
          type="error"
      />
      <el-alert
          v-if="debugResponse.postScriptError"
          :closable="false"
          :title="'后置脚本：' + debugResponse.postScriptError"
          class="debug-error-alert"
          show-icon
          type="error"
      />
      <template v-if="debugResponse.sent && debugResponse.status != null">
        <nav class="debug-resp-tablist" role="tablist">
          <button
              :class="{ active: activeRespTab === 'body' }"
              class="debug-resp-tab"
              type="button"
              @click="emit('update:activeRespTab', 'body')"
          >
            Body
          </button>
          <button
              :class="{ active: activeRespTab === 'headers' }"
              class="debug-resp-tab"
              type="button"
              @click="emit('update:activeRespTab', 'headers')"
          >
            Headers
          </button>
          <button
              :class="{ active: activeRespTab === 'tests' }"
              class="debug-resp-tab"
              type="button"
              @click="emit('update:activeRespTab', 'tests')"
          >
            Tests
            <span v-if="debugScriptTestsBadge > 0" class="debug-tab-count">{{ debugScriptTestsBadge }}</span>
          </button>
        </nav>
        <div v-show="activeRespTab === 'body'" class="debug-resp-panel">
          <ResponseMediaPreview
              :body-base64="debugResponse.bodyBase64"
              :body-encoding="debugResponse.bodyEncoding"
              :body-text="debugResponse.bodyText"
              :headers="debugResponse.headers"
          />
          <el-input
              :model-value="debugResponse.bodyText"
              :rows="12"
              class="debug-resp-textarea"
              readonly
              type="textarea"
          />
        </div>
        <div v-show="activeRespTab === 'headers'" class="debug-resp-panel">
          <el-input
              :model-value="formatResponseHeaders(debugResponse.headers)"
              :rows="12"
              class="debug-resp-textarea"
              readonly
              type="textarea"
          />
        </div>
        <div v-show="activeRespTab === 'tests'" class="debug-resp-panel debug-resp-panel--tests">
          <div v-if="!(debugResponse.scriptTests || []).length && !(debugResponse.scriptLogs || []).length" class="debug-script-tests-empty">
            暂无脚本测试结果
          </div>
          <ul v-if="(debugResponse.scriptTests || []).length" class="debug-script-test-list">
            <li
                v-for="(item, idx) in debugResponse.scriptTests"
                :key="idx"
                :class="{ 'is-pass': item.passed, 'is-fail': item.passed === false }"
                class="debug-script-test-item"
            >
              <span class="debug-script-test-name">{{ item.name || ('测试 ' + (idx + 1)) }}</span>
              <span class="debug-script-test-status">{{ item.passed ? '通过' : '失败' }}</span>
              <span v-if="item.message" class="debug-script-test-msg">{{ item.message }}</span>
            </li>
          </ul>
          <el-input
              v-if="(debugResponse.scriptLogs || []).length"
              :model-value="(debugResponse.scriptLogs || []).join('\n')"
              :rows="8"
              class="debug-resp-textarea debug-script-log-textarea"
              readonly
              type="textarea"
          />
        </div>
      </template>
    </div>
  </div>
</template>

<script setup>
import ResponseMediaPreview from '@/components/ResponseMediaPreview/index.vue'

defineProps({
  debugResponse: {type: Object, required: true},
  activeRespTab: {type: String, default: 'body'},
  debugScriptTestsBadge: {type: Number, default: 0},
  formatResponseHeaders: {type: Function, required: true}
})

const emit = defineEmits(['update:activeRespTab'])
</script>

<style lang="scss" scoped>
.debug-response-pane {
  flex: 0 0 220px;
  min-height: 176px;
  display: flex;
  flex-direction: column;
  border: none;
  border-top: 1px solid var(--pd-divider, var(--pd-border-muted));
  border-radius: 0;
  overflow: hidden;
  background: var(--pd-surface-elevated);
  box-shadow: none;
}

.debug-response-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 6px 6px;
  border-bottom: 1px solid var(--pd-divider, var(--pd-border-muted));
  background: var(--pd-surface-elevated);
  flex-shrink: 0;
}

.debug-response-title {
  font-size: 13px;
  font-weight: 600;
  color: var(--pd-text);
}

.debug-response-meta {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 12px;
}

.debug-status-pill {
  padding: 3px 10px;
  border-radius: 6px;
  font-weight: 600;
  font-size: 12px;

  &.is-ok {
    background: #ecfdf5;
    color: #047857;
  }

  &.is-err {
    background: #fef2f2;
    color: #b91c1c;
  }
}

.debug-duration {
  color: var(--pd-text-muted);
}

.debug-response-empty {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 120px;
  padding: 16px 0;
  background: var(--pd-bg-sunken, #e9f2fc);
}

.debug-response-empty-inner {
  text-align: center;
  max-width: 280px;
}

.debug-response-empty-icon {
  margin: 0 auto 12px;
  color: var(--pd-primary);
  opacity: 0.45;
}

.debug-response-empty-title {
  margin: 0 0 6px;
  font-size: 14px;
  font-weight: 600;
  color: var(--pd-text-muted);
}

.debug-response-empty-desc {
  margin: 0;
  font-size: 12px;
  color: var(--pd-text-muted);
  line-height: 1.5;
}

.debug-response-body {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
  padding: 4px 0 6px 6px;
  overflow: auto;
  scrollbar-gutter: stable;
  background: var(--pd-surface-elevated);
}

.debug-error-alert {
  margin-bottom: 8px;
}

.debug-resp-tablist {
  display: flex;
  gap: 4px;
  margin-bottom: 8px;
  flex-shrink: 0;
}

.debug-resp-tab {
  padding: 6px 14px;
  margin: 0;
  border: 1px solid var(--pd-border-muted);
  border-radius: 6px;
  background: var(--pd-bg-sunken, #e9f2fc);
  font-size: 12px;
  font-weight: 500;
  color: var(--pd-text-muted);
  cursor: pointer;
  font-family: inherit;
  transition: background 0.15s ease, border-color 0.15s ease, color 0.15s ease;

  &:hover {
    background: var(--pd-surface-elevated);
    border-color: var(--pd-border-subtle);
  }

  &.active {
    color: var(--pd-primary);
    font-weight: 600;
    background: var(--pd-primary-soft);
    border-color: rgba(11, 110, 220, 0.35);
  }
}

.debug-tab-count {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 18px;
  height: 18px;
  padding: 0 5px;
  border-radius: 9px;
  font-size: 11px;
  font-weight: 600;
  line-height: 1;
  color: #fff;
  background: #ef4444;
}

.debug-resp-panel {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
}

.debug-resp-textarea {
  flex: 1;
  min-height: 0;

  :deep(.el-textarea__inner) {
    font-family: Consolas, 'Courier New', monospace;
    font-size: 12px;
    border-radius: var(--pd-radius-sm, 8px);
  }
}

.debug-resp-panel--tests {
  gap: 8px;
  padding: 4px 2px 0;
}

.debug-script-tests-empty {
  font-size: 12px;
  color: var(--pd-text-muted);
  padding: 8px 4px;
}

.debug-script-test-list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.debug-script-test-item {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
  padding: 6px 8px;
  border-radius: 6px;
  font-size: 12px;
  background: var(--pd-bg-sunken, rgba(233, 242, 252, 0.5));
  border: 1px solid var(--pd-border-muted);

  &.is-pass {
    border-color: rgba(22, 163, 74, 0.35);
  }

  &.is-fail {
    border-color: rgba(220, 38, 38, 0.35);
    background: rgba(254, 242, 242, 0.6);
  }
}

.debug-script-test-name {
  font-weight: 600;
}

.debug-script-test-status {
  color: var(--pd-text-muted);
}

.debug-script-test-msg {
  flex: 1 1 100%;
  color: #b91c1c;
  word-break: break-word;
}

.debug-script-log-textarea {
  margin-top: 4px;
}
</style>

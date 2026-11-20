<template>
  <div class="run-inspector">
    <div class="run-inspector__tabs">
      <button
        v-for="tab in tabs"
        :key="tab.id"
        :class="{ 'is-active': activeTab === tab.id }"
        class="run-inspector__tab"
        type="button"
        @click="$emit('update:activeTab', tab.id)"
      >
        {{ tab.label }}
      </button>
    </div>
    <div class="run-inspector__body">
      <div v-if="!currentStep" class="prop-empty" style="padding:20px">选择步骤</div>
      <template v-else>
        <div v-if="activeTab === 'summary'" v-html="summaryHtml" />
        <template v-else-if="activeTab === 'http'">
          <div v-if="!currentStep.http" class="prop-empty" style="padding:20px">该步骤无 HTTP 请求/响应</div>
          <template v-else>
            <div style="margin-bottom:8px;font-weight:600">
              {{ currentStep.http.method }} {{ decodeUrlForDisplay(currentStep.http.url) }} · {{ currentStep.http.status }}
            </div>
            <div
              v-if="currentStep.http.bizCheck"
              class="run-biz-check"
              :class="{ 'is-failed': currentStep.http.bizCheck.passed === false }"
            >
              <div class="run-biz-check__title">
                业务码校验 · {{ currentStep.http.bizCheck.passed === false ? '失败' : '通过' }}
              </div>
              <div v-if="currentStep.http.bizCheck.actualCode != null">
                实际 {{ currentStep.http.bizCheck.codePath || 'code' }} =
                {{ currentStep.http.bizCheck.actualCode }}
                <template v-if="currentStep.http.bizCheck.successValues?.length">
                  （成功值 {{ currentStep.http.bizCheck.successValues.join(', ') }}）
                </template>
              </div>
              <div v-if="currentStep.http.bizCheck.message">
                {{ currentStep.http.bizCheck.message }}
              </div>
            </div>
            <div style="margin-bottom:4px;color:var(--pd-text-muted)">请求</div>
            <pre>{{ JSON.stringify(currentStep.http.request, null, 2) }}</pre>
            <div style="margin:8px 0 4px;color:var(--pd-text-muted)">响应</div>
            <ResponseMediaPreview
              :body="currentStep.http.response?.body"
              :body-media="currentStep.http.response?.bodyMedia"
              :headers="currentStep.http.response?.headers || {}"
            />
            <pre>{{ JSON.stringify(currentStep.http.response, null, 2) }}</pre>
          </template>
        </template>
        <pre v-else-if="activeTab === 'flow'">{{ JSON.stringify(currentStep.flowAfter || {}, null, 2) }}</pre>
      </template>
    </div>
  </div>
</template>

<script setup>
/**
 * Run 步骤检视区：摘要 / 请求响应 / 流程变量三个 Tab。
 * 摘要由父组件拼好 HTML 注入；HTTP Tab 展示业务码校验、请求响应与媒体预览。
 */
import ResponseMediaPreview from '@/components/ResponseMediaPreview/index.vue'
import { decodeUrlForDisplay } from '../utils/runStepDisplay'

defineProps({
  tabs: { type: Array, required: true },
  activeTab: { type: String, required: true },
  currentStep: { type: Object, default: null },
  summaryHtml: { type: String, default: '' },
})

defineEmits(['update:activeTab'])
</script>

<style scoped lang="scss">
@use './runDetailBadges.scss' as *;

.run-biz-check {
  margin-bottom: 10px;
  padding: 8px 10px;
  border-radius: 6px;
  font-size: 11px;
  line-height: 1.5;
  background: #dcfce7;
  border: 1px solid #bbf7d0;
  color: #166534;

  &.is-failed {
    background: #ffedd5;
    border-color: #fed7aa;
    color: #c2410c;
  }
}

.run-biz-check__title {
  font-weight: 700;
  margin-bottom: 4px;
}

.run-inspector__tabs {
  display: flex;
  gap: 0;
  border-bottom: 1px solid var(--pd-divider);
  margin-bottom: 8px;
}

.run-inspector__tab {
  flex: 1;
  height: 32px;
  border: none;
  background: transparent;
  font-size: 11px;
  font-weight: 600;
  color: var(--pd-text-muted);
  cursor: pointer;
  border-bottom: 2px solid transparent;

  &.is-active {
    color: var(--pd-primary);
    border-bottom-color: var(--pd-primary);
  }
}

.run-inspector__body {
  font-size: 11px;
  line-height: 1.5;
  max-height: 280px;
  overflow: auto;

  pre {
    font-size: 10px;
    white-space: pre-wrap;
    word-break: break-all;
    background: var(--pd-bg-sunken);
    padding: 8px;
    border-radius: 6px;
  }
}

.prop-empty {
  text-align: center;
  padding: 40px 16px;
  color: var(--pd-text-muted);
  font-size: 12px;
  line-height: 1.7;
}

/* summaryHtml 内联徽标（v-html 需 :deep） */
:deep {
  @include run-badges;
}
</style>

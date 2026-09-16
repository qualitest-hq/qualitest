<script setup lang="ts">
/**
 * 助手气泡内的工具调用轨迹（默认折叠）。
 * 展示序号、工具名、成败与耗时；展开单条可看脱敏后的 args / result。
 */
import { computed } from 'vue';

import type { AiToolTraceView } from '@/utils/ai/toolTrace';

const props = defineProps<{
  /** 本轮已解析的工具轨迹 */
  toolTrace: AiToolTraceView;
}>();

/** 按序的调用列表；无则空数组 */
const calls = computed(() => props.toolTrace.calls ?? []);

/** 折叠条摘要：条数、步数占用、是否截断 */
const summaryLabel = computed(() => {
  const n = calls.value.length;
  const steps = props.toolTrace.stepsUsed;
  const max = props.toolTrace.maxSteps;
  const parts = [`工具轨迹（${n}）`];
  if (typeof steps === 'number' && typeof max === 'number') {
    parts.push(`${steps}/${max} 步`);
  }
  if (props.toolTrace.truncated) {
    parts.push('已截断');
  }
  return parts.join(' · ');
});

/** 将 args/result 格式化为可读 JSON 文本 */
function formatJson(value: unknown): string {
  if (value == null) return '';
  try {
    return JSON.stringify(value, null, 2);
  } catch {
    return String(value);
  }
}

/** 折叠行标题：工具名 + 若干关键入参字段预览 */
function callHeadline(call: { name: string; args?: unknown }): string {
  const args = call.args;
  if (!args || typeof args !== 'object' || Array.isArray(args)) {
    return call.name;
  }
  const obj = args as Record<string, unknown>;
  const bits: string[] = [call.name];
  for (const key of ['op', 'source', 'target', 'label', 'id'] as const) {
    const v = obj[key];
    if (typeof v === 'string' && v.trim()) {
      bits.push(`${key}=${v.trim()}`);
    }
  }
  return bits.join(' ');
}
</script>

<template>
  <details class="ai-tool-trace">
    <summary>{{ summaryLabel }}</summary>
    <ul v-if="calls.length" class="ai-tool-trace__list">
      <li v-for="call in calls" :key="call.i" class="ai-tool-trace__item">
        <details class="ai-tool-trace__call">
          <summary>
            <span class="ai-tool-trace__idx">{{ call.i }}.</span>
            <span
                class="ai-tool-trace__status"
                :class="call.ok ? 'ai-tool-trace__status--ok' : 'ai-tool-trace__status--fail'"
            >
              {{ call.ok ? 'ok' : 'fail' }}
            </span>
            <span class="ai-tool-trace__name">{{ callHeadline(call) }}</span>
            <span v-if="typeof call.ms === 'number'" class="ai-tool-trace__ms">{{ call.ms }}ms</span>
          </summary>
          <pre v-if="call.args != null" class="ai-tool-trace__json">args: {{ formatJson(call.args) }}</pre>
          <pre v-if="call.result != null" class="ai-tool-trace__json">result: {{ formatJson(call.result) }}</pre>
        </details>
      </li>
    </ul>
    <p v-else class="ai-tool-trace__empty">本轮无工具调用</p>
  </details>
</template>

<style scoped lang="scss">
.ai-tool-trace {
  margin-top: 8px;
  font-size: 12px;
  color: var(--pd-text-muted);

  > summary {
    cursor: pointer;
    user-select: none;
  }
}

.ai-tool-trace__list {
  margin: 6px 0 0;
  padding: 0;
  list-style: none;
}

.ai-tool-trace__item + .ai-tool-trace__item {
  margin-top: 4px;
}

.ai-tool-trace__call {
  > summary {
    display: flex;
    flex-wrap: wrap;
    gap: 6px;
    align-items: baseline;
    cursor: pointer;
    user-select: none;
  }
}

.ai-tool-trace__idx {
  font-variant-numeric: tabular-nums;
}

.ai-tool-trace__status {
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 0.02em;
}

.ai-tool-trace__status--ok {
  color: var(--pd-success, #2f9e44);
}

.ai-tool-trace__status--fail {
  color: var(--pd-danger, #e03131);
}

.ai-tool-trace__name {
  color: var(--pd-text);
  word-break: break-all;
}

.ai-tool-trace__ms {
  opacity: 0.8;
}

.ai-tool-trace__json {
  margin: 4px 0 0;
  padding: 6px 8px;
  max-height: 160px;
  overflow: auto;
  border-radius: 6px;
  background: var(--pd-bg-elevated, rgba(0, 0, 0, 0.04));
  font-size: 11px;
  line-height: 1.4;
  white-space: pre-wrap;
  word-break: break-word;
}

.ai-tool-trace__empty {
  margin: 6px 0 0;
}
</style>

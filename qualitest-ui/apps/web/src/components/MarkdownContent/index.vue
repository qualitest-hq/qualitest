<template>
  <div class="markdown-body" v-html="html" />
</template>

<script setup lang="ts">
/**
 * 只读 Markdown 渲染（marked + DOMPurify），用于 AI 助手消息等场景。
 */
import { computed } from 'vue';

import { renderMarkdown } from '@/utils/markdown';

const props = defineProps<{
  source: string;
}>();

const html = computed(() => renderMarkdown(props.source));
</script>

<style scoped lang="scss">
.markdown-body {
  margin: 0;
  font-size: inherit;
  line-height: 1.5;
  word-break: break-word;
  color: inherit;

  :deep(p) {
    margin: 0 0 0.65em;

    &:last-child {
      margin-bottom: 0;
    }
  }

  :deep(h1),
  :deep(h2),
  :deep(h3),
  :deep(h4) {
    margin: 0.75em 0 0.4em;
    font-size: 1em;
    font-weight: 600;
    line-height: 1.35;

    &:first-child {
      margin-top: 0;
    }
  }

  :deep(ul),
  :deep(ol) {
    margin: 0.4em 0 0.65em;
    padding-left: 1.25em;
  }

  :deep(li + li) {
    margin-top: 0.2em;
  }

  :deep(blockquote) {
    margin: 0.5em 0;
    padding: 0.35em 0.75em;
    border-left: 3px solid color-mix(in srgb, var(--pd-primary, #0b6edc) 35%, #cbd5e1);
    color: var(--pd-text-muted, #5a6b86);
    background: color-mix(in srgb, var(--pd-bg-sunken, #f1f5f9) 80%, transparent);
  }

  :deep(code) {
    padding: 0.1em 0.35em;
    border-radius: 4px;
    font-size: 0.92em;
    font-family: ui-monospace, 'Cascadia Code', 'Consolas', monospace;
    background: color-mix(in srgb, var(--pd-text, #0f172a) 6%, transparent);
  }

  :deep(pre) {
    margin: 0.5em 0;
    padding: 8px 10px;
    border-radius: 6px;
    overflow-x: auto;
    background: color-mix(in srgb, var(--pd-text, #0f172a) 6%, transparent);
    border: 1px solid var(--pd-divider, #dbe8f4);

    code {
      padding: 0;
      background: transparent;
    }
  }

  :deep(table) {
    width: 100%;
    margin: 0.5em 0;
    border-collapse: collapse;
    font-size: 0.92em;
  }

  :deep(th),
  :deep(td) {
    padding: 5px 8px;
    border: 1px solid var(--pd-divider, #dbe8f4);
    text-align: left;
    vertical-align: top;
    white-space: pre-wrap;
    word-break: break-word;
    overflow-wrap: anywhere;
  }

  /* 首列多为序号或短标签，尽量收窄，把宽度留给内容列 */
  :deep(th:first-child),
  :deep(td:first-child) {
    width: 1%;
    white-space: nowrap;
  }

  :deep(th) {
    font-weight: 600;
    background: color-mix(in srgb, var(--pd-bg-sunken, #f1f5f9) 90%, #fff);
  }

  :deep(a) {
    color: var(--pd-primary, #0b6edc);
    text-decoration: none;

    &:hover {
      text-decoration: underline;
    }
  }

  :deep(hr) {
    margin: 0.75em 0;
    border: none;
    border-top: 1px solid var(--pd-divider, #dbe8f4);
  }
}
</style>

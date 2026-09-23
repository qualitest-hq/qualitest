<template>
  <div v-if="preview" class="response-media-preview">
    <div class="response-media-preview__head">
      <span class="response-media-preview__label">媒体预览</span>
      <span class="response-media-preview__kind">{{ kindLabel }}</span>
    </div>
    <div v-if="preview.truncated" class="response-media-preview__hint">
      响应已截断，预览可能不完整
    </div>
    <div class="response-media-preview__stage" :class="'is-' + preview.kind">
      <img
        v-if="preview.kind === 'image'"
        :src="preview.src"
        alt="响应媒体预览"
        class="response-media-preview__media response-media-preview__img"
      />
      <video
        v-else-if="preview.kind === 'video'"
        :src="preview.src"
        class="response-media-preview__media"
        controls
      />
      <audio
        v-else-if="preview.kind === 'audio'"
        :src="preview.src"
        class="response-media-preview__audio"
        controls
      />
      <div v-else-if="preview.kind === 'pdf'" class="response-media-preview__pdf">
        <iframe :src="preview.src" class="response-media-preview__iframe" title="PDF 预览" />
        <a :href="preview.src" class="response-media-preview__link" rel="noopener noreferrer" target="_blank">
          新窗口打开
        </a>
      </div>
      <div v-else-if="preview.kind === 'link'" class="response-media-preview__link-wrap">
        <a :href="preview.src" class="response-media-preview__link" rel="noopener noreferrer" target="_blank">
          {{ preview.src }}
        </a>
      </div>
    </div>
  </div>
  <div v-else-if="omittedLabel" class="response-media-preview response-media-preview--omitted">
    <div class="response-media-preview__hint">
      {{ omittedLabel }}（未保存预览）
    </div>
  </div>
</template>

<script setup>
/**
 * HTTP 响应媒体预览面板。
 * 能识别出图/音视频/PDF/外链时直接渲染；
 * 若仅有 bodyMedia 且 stored=false（裸媒体未落库），显示「mime · N bytes（未保存预览）」。
 * 无媒体时不渲染任何内容。
 */
import { computed } from 'vue'
import { resolveResponseMediaPreview } from '@/utils/responseMediaPreview'

const props = defineProps({
  /** 已解析的响应 body（对象或字符串） */
  body: { type: [Object, Array, String, Number, Boolean], default: undefined },
  /** 响应体文本 */
  bodyText: { type: String, default: '' },
  /** 响应头 */
  headers: { type: Object, default: () => ({}) },
  /** 裸媒体 Base64 */
  bodyBase64: { type: String, default: '' },
  /** text | base64 */
  bodyEncoding: { type: String, default: '' },
  /**
   * Run 步骤中的媒体元数据。
   * stored===false 时表示识别到媒体但未保存字节，用于展示提示文案。
   */
  bodyMedia: { type: Object, default: null },
  /** 是否截断 */
  truncated: { type: Boolean, default: false }
})

/** 可渲染的预览描述；无则 null */
const preview = computed(() =>
  resolveResponseMediaPreview({
    body: props.body,
    bodyText: props.bodyText,
    headers: props.headers,
    bodyBase64: props.bodyBase64 || null,
    bodyEncoding: props.bodyEncoding || null,
    truncated: props.truncated || String(props.bodyText || '').includes('响应体已截断')
  })
)

const KIND_LABELS = {
  image: '图片',
  video: '视频',
  audio: '音频',
  pdf: 'PDF',
  link: '链接'
}

/** 预览类型短标签，用于标题栏 */
const kindLabel = computed(() => {
  const kind = preview.value?.kind
  return (kind && KIND_LABELS[kind]) || '媒体'
})

/** 裸媒体未落库时的一行说明；有可渲染预览时不使用 */
const omittedLabel = computed(() => {
  const meta = props.bodyMedia
  if (!meta || typeof meta !== 'object' || meta.stored !== false) {
    return ''
  }
  const mime = meta.mime || meta.kind || 'media'
  const bytes = meta.bytes != null ? ` · ${meta.bytes} bytes` : ''
  const trunc = meta.truncated ? '，已截断' : ''
  return `${mime}${bytes}${trunc}`
})
</script>

<style scoped>
.response-media-preview {
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 8px 10px 10px;
  border: 1px solid var(--pd-border-muted, var(--el-border-color-lighter, #ebeef5));
  border-radius: var(--pd-radius-sm, 8px);
  background: var(--pd-bg-sunken, #f5f8fc);
}

.response-media-preview--omitted {
  padding: 8px 10px;
}

.response-media-preview__head {
  display: flex;
  align-items: center;
  gap: 8px;
  min-height: 18px;
}

.response-media-preview__label {
  font-size: 12px;
  font-weight: 600;
  color: var(--pd-text-muted, #64748b);
}

.response-media-preview__kind {
  padding: 1px 6px;
  border-radius: 4px;
  font-size: 11px;
  font-weight: 500;
  line-height: 1.4;
  color: var(--pd-text-muted, #64748b);
  background: var(--pd-surface-elevated, #fff);
  border: 1px solid var(--pd-border-muted, #e2e8f0);
}

.response-media-preview__hint {
  font-size: 12px;
  color: var(--el-color-warning);
}

.response-media-preview__stage {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 12px;
  border-radius: 6px;
  border: 1px solid var(--pd-border-muted, #e8edf3);
  background: var(--pd-surface-elevated, #fff);
  overflow: auto;
}

.response-media-preview__stage.is-image {
  background: repeating-conic-gradient(#eef1f5 0% 25%, #fff 0% 50%) 50% / 14px 14px;
}

.response-media-preview__stage.is-audio,
.response-media-preview__stage.is-link {
  justify-content: stretch;
  padding: 10px 12px;
}

.response-media-preview__stage.is-pdf {
  padding: 0;
  overflow: hidden;
}

.response-media-preview__media {
  display: block;
  max-width: 100%;
  max-height: 200px;
}

.response-media-preview__img {
  object-fit: contain;
  border-radius: 2px;
  box-shadow: 0 1px 3px rgba(15, 23, 42, 0.08);
}

.response-media-preview__audio {
  width: 100%;
}

.response-media-preview__pdf {
  display: flex;
  flex-direction: column;
  gap: 8px;
  width: 100%;
  padding: 8px;
}

.response-media-preview__iframe {
  width: 100%;
  height: 240px;
  border: 0;
  background: #fafafa;
}

.response-media-preview__link-wrap {
  word-break: break-all;
}

.response-media-preview__link {
  font-size: 13px;
  color: var(--pd-primary, var(--el-color-primary));
}
</style>

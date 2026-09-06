<template>
  <div v-if="preview" class="response-media-preview">
    <div v-if="preview.truncated" class="response-media-preview__hint">
      响应已截断，预览可能不完整
    </div>
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
  <div v-else-if="omittedLabel" class="response-media-preview">
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
  margin-bottom: 10px;
  padding: 10px 12px;
  border: 1px solid var(--el-border-color-lighter, #ebeef5);
  border-radius: 6px;
  background: var(--el-fill-color-blank, #fff);
}

.response-media-preview__hint {
  margin-bottom: 8px;
  font-size: 12px;
  color: var(--el-color-warning);
}

.response-media-preview__hint:last-child {
  margin-bottom: 0;
}

.response-media-preview__media {
  display: block;
  max-width: 100%;
  max-height: 280px;
}

.response-media-preview__img {
  object-fit: contain;
  background: repeating-conic-gradient(#f0f0f0 0% 25%, #fff 0% 50%) 50% / 16px 16px;
}

.response-media-preview__audio {
  width: 100%;
}

.response-media-preview__pdf {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.response-media-preview__iframe {
  width: 100%;
  height: 280px;
  border: 0;
  background: #fafafa;
}

.response-media-preview__link-wrap {
  word-break: break-all;
}

.response-media-preview__link {
  font-size: 13px;
  color: var(--el-color-primary);
}
</style>

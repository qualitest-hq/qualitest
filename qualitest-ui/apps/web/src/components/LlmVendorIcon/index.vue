<template>
  <span
    class="llm-vendor-icon"
    :style="{ width: `${size}px`, height: `${size}px`, fontSize: `${Math.max(10, Math.round(size * 0.45))}px` }"
  >
    <img
      v-if="iconSrc && !imgFailed"
      :class="['llm-vendor-icon__img', { 'llm-vendor-icon__img--avatar': isAvatar }]"
      :src="iconSrc"
      :alt="vendorName || 'vendor icon'"
      loading="lazy"
      @error="imgFailed = true"
    />
    <span
      v-else
      class="llm-vendor-icon__fallback"
      :style="{ backgroundColor: fallbackColor }"
    >{{ fallbackLetter }}</span>
  </span>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import {
  buildLobeIconUrl,
  resolveFallbackColor,
  resolveFallbackLetter,
  resolveLobeIconConfig
} from '@/utils/ai/llmVendorIcon'

const props = defineProps({
  templateId: {
    type: String,
    default: null
  },
  icon: {
    type: String,
    default: null
  },
  vendorName: {
    type: String,
    default: ''
  },
  size: {
    type: Number,
    default: 20
  }
})

const imgFailed = ref(false)

const lobeIconConfig = computed(() => resolveLobeIconConfig(props.templateId, props.icon))
const iconSrc = computed(() => buildLobeIconUrl(lobeIconConfig.value))
const isAvatar = computed(() => lobeIconConfig.value?.format === 'avatar')
const fallbackLetter = computed(() => resolveFallbackLetter(props.vendorName))
const fallbackColor = computed(() => resolveFallbackColor(props.vendorName || props.templateId))

watch([() => props.templateId, () => props.icon, iconSrc], () => {
  imgFailed.value = false
})
</script>

<style scoped>
.llm-vendor-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  vertical-align: middle;
}

.llm-vendor-icon__img {
  width: 100%;
  height: 100%;
  object-fit: contain;
  display: block;
}

.llm-vendor-icon__img--avatar {
  border-radius: 4px;
  object-fit: cover;
}

.llm-vendor-icon__fallback {
  width: 100%;
  height: 100%;
  border-radius: 4px;
  color: #fff;
  font-weight: 600;
  line-height: 1;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  user-select: none;
}
</style>

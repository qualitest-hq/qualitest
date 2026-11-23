<template>
  <div ref="hostEl" class="plugin-env-remote-panel" v-loading="loading">
    <el-alert v-if="errorText" :closable="false" :title="errorText" show-icon type="error" />
  </div>
</template>

<script setup>
/**
 * 加载插件包 ui.env.panel.entry（ESM），约定导出 mount(el, ctx) / unmount()。
 * 经鉴权请求拉取 JS 后 blob import，避免动态 import 丢 Authorization。
 */
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { getToken } from '@/utils/auth'
import { getEnvPluginUiAssetUrl } from '@/api/project/pluginHost'

const props = defineProps({
  pluginId: { type: String, required: true },
  entry: { type: String, required: true },
  ctx: { type: Object, default: () => ({}) },
})

const hostEl = ref(null)
const loading = ref(false)
const errorText = ref('')
let mountedApi = null

async function load() {
  unload()
  errorText.value = ''
  if (!props.pluginId || !props.entry || !hostEl.value) return
  loading.value = true
  try {
    const url = getEnvPluginUiAssetUrl(props.pluginId, props.entry)
    const headers = {}
    const token = getToken?.()
    if (token) headers.Authorization = 'Bearer ' + token
    const res = await fetch(url, { headers, credentials: 'same-origin' })
    if (!res.ok) {
      throw new Error(`加载面板失败 HTTP ${res.status}`)
    }
    const text = await res.text()
    const blob = new Blob([text], { type: 'text/javascript' })
    const blobUrl = URL.createObjectURL(blob)
    try {
      const mod = await import(/* @vite-ignore */ blobUrl)
      const api = mod?.default || mod
      if (typeof api?.mount !== 'function') {
        throw new Error('面板未导出 mount(el, ctx)')
      }
      mountedApi = api
      await api.mount(hostEl.value, props.ctx || {})
    } finally {
      URL.revokeObjectURL(blobUrl)
    }
  } catch (e) {
    errorText.value = e?.message || '远程面板加载失败'
  } finally {
    loading.value = false
  }
}

function unload() {
  try {
    if (mountedApi && typeof mountedApi.unmount === 'function') {
      mountedApi.unmount()
    }
  } catch {
    // ignore
  }
  mountedApi = null
  if (hostEl.value) hostEl.value.innerHTML = ''
}

onMounted(load)
watch(() => [props.pluginId, props.entry], load)
onBeforeUnmount(unload)
</script>

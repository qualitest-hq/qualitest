<template>
  <section v-if="envId" class="env-section plugin-env-section">
    <div class="env-section-head">
      <h3 class="env-section-title">平台插件</h3>
      <el-button :loading="loading" link type="primary" @click="reload">刷新</el-button>
    </div>
    <el-alert v-if="errorText" :closable="false" :title="errorText" show-icon type="error" class="plugin-env-section__alert" />
    <el-empty v-if="!loading && !items.length" description="暂无已登记插件" :image-size="64" />

    <div v-for="item in items" :key="item.pluginId" class="plugin-env-card">
      <div class="plugin-env-card__head">
        <div>
          <div class="plugin-env-card__title">
            {{ item.displayName || item.pluginId }}
            <el-tag v-if="item.env?.badge?.text" size="small" :type="toneToTag(item.env.badge.tone)">
              {{ item.env.badge.text }}
            </el-tag>
          </div>
          <p class="plugin-env-card__hint">
            <code>{{ item.pluginId }}</code>
            <span v-if="item.remarks"> · {{ item.remarks }}</span>
          </p>
        </div>
        <el-switch
            :model-value="!!item.enabled"
            :disabled="readonly || busyId === item.pluginId || enableBlocked(item)"
            :loading="busyId === item.pluginId"
            active-text="启用"
            inactive-text="停用"
            @change="(v) => onToggle(item, v)"
        />
      </div>

      <!-- 按插件自带的 configSchema 渲染环境配置；启用前可编辑，启用后只读展示 -->
      <PluginSchemaForm
          v-if="item.configSchema && (!item.enabled || draftConfigs[item.pluginId])"
          v-model="draftConfigs[item.pluginId]"
          :disabled="readonly || !!item.enabled"
          :schema="item.configSchema"
      />

      <div v-if="item.enabled" class="plugin-env-card__actions">
        <template v-for="action in actionsOf(item)" :key="action.id || action.capability">
          <el-button size="small" @click="openAction(item, action)">{{ action.label || action.capability }}</el-button>
        </template>
        <template v-if="item.channelProvider">
          <el-button size="small" :loading="pingBusy === item.pluginId" @click="onPing(item)">代理探活</el-button>
        </template>
        <template v-if="item.enabled && item.pushProducts?.length">
          <el-button
              v-for="p in item.pushProducts"
              :key="p.productId"
              size="small"
              :loading="pushBusy === p.productId"
              @click="onPushInstall(item, p.productId)"
          >
            推送安装 {{ p.productId }}
          </el-button>
          <el-button size="small" :loading="bindBusy" @click="onPushBind(item)">推送 bind</el-button>
        </template>
      </div>

      <p v-if="itemErrors[item.pluginId]" class="env-prod-warn">{{ itemErrors[item.pluginId] }}</p>

      <PluginEnvRemotePanel
          v-if="item.enabled && panelEntry(item) && openPanels[item.pluginId] === 'remote'"
          :plugin-id="item.pluginId"
          :entry="panelEntry(item)"
          :ctx="remoteCtx(item)"
      />
      <PluginInvokeTablePanel
          v-else-if="item.enabled && openPanels[item.pluginId]?.panel === 'invoke-table'"
          :env-id="envId"
          :plugin-id="item.pluginId"
          :list-capability="openPanels[item.pluginId].capability || 'list'"
          :list-key="openPanels[item.pluginId].listKey || 'saves'"
          :id-field="openPanels[item.pluginId].idField || 'saveId'"
          :columns="openPanels[item.pluginId].columns"
          :delete-capability="guessDeleteCapability(item)"
          :readonly="readonly"
          :title="openPanels[item.pluginId].label || '列表'"
      />
    </div>
  </section>
</template>

<script setup>
/**
 * 环境管理里的平台插件通用壳。
 * 拉取该环境下的插件列表后，统一画启用开关、configSchema 配置表单、能力按钮、远程面板等；
 * 具体字段与按钮由各插件返回数据驱动，本组件不写死某一插件的表单项。
 */
import { computed, reactive, ref, watch } from 'vue'
import { getCurrentInstance } from 'vue'
import {
  disableEnvPlugin,
  enableEnvPlugin,
  getEnvPluginUi,
  pingSutPushAgent,
  sutPushBind,
  sutPushInstall,
} from '@/api/project/pluginHost'
/** 环境 configSchema 表单：JSON Schema 原样渲染 */
import PluginSchemaForm from './PluginSchemaForm.vue'
import PluginEnvRemotePanel from './PluginEnvRemotePanel.vue'
import PluginInvokeTablePanel from './PluginInvokeTablePanel.vue'

const props = defineProps({
  envId: { type: [String, Number], required: true },
  readonly: { type: Boolean, default: false },
  /** 已持久化的允许还原标记；破坏性插件启用前须为 1 */
  allowDestructiveReset: { type: Number, default: 0 },
})

const emit = defineEmits(['changed'])
const { proxy } = getCurrentInstance() || {}

const loading = ref(false)
const errorText = ref('')
/** 当前环境插件列表（含 configSchema、启用态等） */
const items = ref([])
/** 各插件配置草稿：pluginId → 表单对象；启用时序列化提交，启用后只读回显 */
const draftConfigs = reactive({})
const itemErrors = reactive({})
const busyId = ref('')
const openPanels = reactive({})
const pingBusy = ref('')
const pushBusy = ref('')
const bindBusy = ref(false)

function toneToTag(tone) {
  if (tone === 'warning') return 'warning'
  if (tone === 'danger') return 'danger'
  if (tone === 'success') return 'success'
  return 'info'
}

function actionsOf(item) {
  const actions = item?.env?.actions
  return Array.isArray(actions) ? actions : []
}

function panelEntry(item) {
  return item?.env?.panel?.entry || ''
}

function capabilitiesOf(item) {
  const caps = item?.capabilities
  return Array.isArray(caps) ? caps : []
}

function enableBlocked(item) {
  // 按说明书 capabilities[].gate=destructive 判断是否须先开总闸；后端仍会再校验
  if (Number(props.allowDestructiveReset) === 1) return false
  if (item?.enabled) return false
  const caps = capabilitiesOf(item)
  if (caps.length) {
    return caps.some((c) => c?.gate === 'destructive')
  }
  // 兼容旧接口未返回 capabilities 时，仍按 hooks 粗判
  const hooks = item?.hooks || []
  return hooks.some((h) => h?.name === 'onRestore' || h?.name === 'onRunPrepare')
}

function guessDeleteCapability(item) {
  // 优先说明书 actions 上声明的 deleteCapability；再扫 capabilities 名含 .delete
  const fromAction = actionsOf(item).find((a) => a?.deleteCapability)?.deleteCapability
  if (fromAction) return fromAction
  const del = capabilitiesOf(item).find((c) => typeof c?.name === 'string' && c.name.endsWith('.delete'))
  return del?.name || ''
}

/** 把后端 GATE_DENIED: … 等拼进 msg 的原因码拆开，方便展示 */
function formatPluginError(raw) {
  const text = String(raw || '').trim()
  if (!text) return ''
  const m = text.match(/^([A-Z][A-Z0-9_]+)\s*:\s*(.*)$/s)
  if (!m) return text
  const code = m[1]
  const detail = (m[2] || '').trim()
  const hints = {
    GATE_DENIED: '请先在环境里打开「允许还原」总闸',
    AGENT_UNREACHABLE: '被测端工具探活失败，请检查 CLI/代理是否安装并可连库',
    PROVIDER_NOT_FOUND: '插件未启用或不存在',
    REQUIRES_UNSATISFIED: '请先启用其依赖的插件（如存档点）',
    PROJECT_MOUNT_REQUIRED: '请先在项目设置中完成挂载（如 bindAlias）',
    CONFIG_SCHEMA_INVALID: '环境配置未通过校验',
  }
  const hint = hints[code]
  if (hint && detail) return `${code}：${detail}（${hint}）`
  if (hint) return `${code}：${hint}`
  return detail ? `${code}：${detail}` : code
}

function remoteCtx(item) {
  return {
    pluginId: item.pluginId,
    envId: props.envId,
    config: parseJson(item.configJson),
    invoke: async (capability, args = {}) => {
      const { invokeEnvPlugin } = await import('@/api/project/pluginHost')
      return invokeEnvPlugin(props.envId, { pluginId: item.pluginId, capability, args })
    },
  }
}

function parseJson(text) {
  if (!text) return {}
  try {
    return JSON.parse(text) || {}
  } catch {
    return {}
  }
}

async function reload() {
  if (!props.envId) return
  loading.value = true
  errorText.value = ''
  try {
    const res = await getEnvPluginUi(props.envId)
    const list = Array.isArray(res?.data?.env) ? res.data.env : []
    items.value = list
    for (const item of list) {
      // 服务端已存 config 灌入表单草稿，供编辑或只读展示
      draftConfigs[item.pluginId] = parseJson(item.configJson)
      if (!item.enabled) openPanels[item.pluginId] = null
    }
  } catch (e) {
    errorText.value = e?.message || '加载插件失败'
    items.value = []
  } finally {
    loading.value = false
  }
}

async function onToggle(item, val) {
  if (props.readonly || !props.envId) return
  if (val && enableBlocked(item)) {
    proxy?.$modal?.msgWarning?.('请先保存环境（含允许还原）')
    return
  }
  busyId.value = item.pluginId
  itemErrors[item.pluginId] = ''
  try {
    if (val) {
      // 把 Schema 表单当前值作为启用配置提交；服务端再做 Schema 校验
      const configJson = JSON.stringify(draftConfigs[item.pluginId] || {})
      const res = await enableEnvPlugin(props.envId, { pluginId: item.pluginId, configJson })
      if (res?.code !== undefined && res.code !== 200) {
        itemErrors[item.pluginId] = formatPluginError(res.msg) || '启用失败'
        return
      }
      proxy?.$modal?.msgSuccess?.(`已启用 ${item.displayName || item.pluginId}`)
    } else {
      await disableEnvPlugin(props.envId, { pluginId: item.pluginId })
      openPanels[item.pluginId] = null
      proxy?.$modal?.msgSuccess?.(`已停用 ${item.displayName || item.pluginId}`)
    }
    await reload()
    emit('changed')
  } catch (e) {
    itemErrors[item.pluginId] = formatPluginError(e?.message) || '操作失败'
  } finally {
    busyId.value = ''
  }
}

function openAction(item, action) {
  // action 显式声明 panel（如 invoke-table）时优先，避免被远程 panel.entry 盖住
  if (action?.panel === 'invoke-table' || action?.panel === 'table') {
    const enriched = { ...action }
    if (action.capability === 'seed.list') {
      enriched.listKey = 'scenarios'
      enriched.idField = 'id'
      enriched.columns = [
        { prop: 'id', label: 'scenarioId', minWidth: 100 },
        { prop: 'name', label: '名称', minWidth: 160 },
        { prop: 'file', label: '文件', minWidth: 220 },
      ]
    } else if (action.capability === 'list') {
      enriched.listKey = 'saves'
      enriched.idField = 'saveId'
      enriched.columns = [
        { prop: 'saveId', label: 'saveId', minWidth: 220 },
        { prop: 'runId', label: 'runId', minWidth: 120 },
        { prop: 'createdAt', label: '创建时间', minWidth: 168 },
      ]
    }
    openPanels[item.pluginId] = enriched
    return
  }
  if (panelEntry(item)) {
    openPanels[item.pluginId] = 'remote'
    return
  }
  openPanels[item.pluginId] = action
}

async function onPing(item) {
  pingBusy.value = item.pluginId
  try {
    const res = await pingSutPushAgent(props.envId)
    if (res?.data?.reachable) {
      proxy?.$modal?.msgSuccess?.(`可达 ${res.data.version || ''}`.trim())
    } else {
      proxy?.$modal?.msgWarning?.(res?.data?.message || '不可达')
    }
  } catch (e) {
    itemErrors[item.pluginId] = e?.message || '探活失败'
  } finally {
    pingBusy.value = ''
  }
}

async function onPushInstall(item, productId) {
  pushBusy.value = productId
  try {
    const res = await sutPushInstall(props.envId, { productId })
    if (res?.code !== undefined && res.code !== 200) {
      itemErrors[item.pluginId] = res.msg || '推送失败'
      return
    }
    proxy?.$modal?.msgSuccess?.(`已推送 ${productId}`)
  } catch (e) {
    itemErrors[item.pluginId] = e?.message || '推送失败'
  } finally {
    pushBusy.value = ''
  }
}

async function onPushBind(item) {
  bindBusy.value = true
  try {
    const productId = item.pushProducts?.[0]?.productId
    const res = await sutPushBind(props.envId, { productId })
    if (res?.code !== undefined && res.code !== 200) {
      itemErrors[item.pluginId] = res.msg || '写入 bind 失败'
      return
    }
    proxy?.$modal?.msgSuccess?.('已写入 bind')
  } catch (e) {
    itemErrors[item.pluginId] = e?.message || '写入 bind 失败'
  } finally {
    bindBusy.value = false
  }
}

watch(
  () => props.envId,
  () => reload(),
  { immediate: true },
)

defineExpose({ reload })
</script>

<style scoped>
.plugin-env-section__alert {
  margin-bottom: 8px;
}
.plugin-env-card {
  border-top: 1px solid var(--el-border-color-lighter);
  padding: 12px 0;
}
.plugin-env-card__head {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  align-items: flex-start;
}
.plugin-env-card__title {
  font-weight: 600;
  display: flex;
  gap: 8px;
  align-items: center;
}
.plugin-env-card__hint {
  margin: 4px 0 0;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
.plugin-env-card__actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 10px;
}
</style>

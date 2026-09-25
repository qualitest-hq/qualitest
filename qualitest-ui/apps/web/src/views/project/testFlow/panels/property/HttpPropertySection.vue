<template>
  <div class="http-prop-card">
    <div class="field">
      <label>调用目标</label>
      <div class="call-mode-toggle">
        <label class="call-mode-opt">
          <input
              :checked="callMode === 'project'"
              name="callMode"
              type="radio"
              value="project"
              @change="setCallMode('project')"
          />
          项目接口
        </label>
        <label class="call-mode-opt">
          <input
              :checked="callMode === 'external'"
              name="callMode"
              type="radio"
              value="external"
              @change="setCallMode('external')"
          />
          外联 URL
        </label>
      </div>
    </div>

    <template v-if="callMode === 'project'">
      <div v-if="node.data.testProjectApiId" class="api-preview">
        <div class="api-preview__row">
          <span :class="methodBadgeClass">{{ httpMethod }}</span>
          <span class="api-preview__path">{{ node.data.apiPath || '—' }}</span>
        </div>
        <div class="api-preview__name">{{ node.data.apiName || '未命名接口' }}</div>
        <div
            v-if="authLabel"
            :class="{ 'http-prop-auth--conflict': authScope.conflict }"
            class="http-prop-auth"
        >
          {{ authLabel }}
          <span v-if="authScope.conflict"> · {{ authScope.conflictReason }}</span>
        </div>
      </div>
      <div v-else class="api-preview is-empty">尚未绑定项目接口</div>
      <div class="http-prop-meta">{{ httpMeta }}</div>
      <p v-if="authLabel && authScope.kind === 'inherit'" class="field__hint">
        未命中 pathPrefix 时用数组第一条 Profile。
      </p>
      <ul v-if="nodeHealthWarnings.length" class="http-prop-health">
        <li v-for="(w, i) in nodeHealthWarnings" :key="i">{{ w.message || w.detail || w.code }}</li>
      </ul>
      <button class="btn btn--primary" style="width:100%" type="button" @click="emit('open-http-config', node.id)">
        {{ node.data.testProjectApiId ? '编辑接口与参数' : '选择接口并配置参数' }}
      </button>
      <div class="field__hint">请求参数仅存于本 HTTP 节点，不改项目 API 资产定义</div>
    </template>

    <template v-else>
      <div class="field">
        <label>HTTP 方法</label>
        <select :value="externalMethod" @change="onExternalMethodChange">
          <option v-for="m in HTTP_METHODS" :key="m" :value="m">{{ m }}</option>
        </select>
      </div>
      <div class="field">
        <label>外联 URL</label>
        <input
            :value="node.data.externalUrl || ''"
            placeholder="https://oauth.example.com/token 或 {{env.oauthTokenUrl}}"
            type="text"
            @input="onExternalUrlInput"
        />
        <div class="field__hint">支持 http/https 任意地址；凭证用 asset.* / env.* 占位符</div>
      </div>
      <div class="field">
        <label>Headers</label>
        <DebugKvSheet v-model="headersModel" label-key="headers" />
      </div>
      <div class="field">
        <label>Request Body</label>
        <textarea
            :value="node.data.requestBody || ''"
            placeholder="grant_type=client_credentials&client_id={{asset.oauth.clientId}}"
            rows="5"
            @input="onRequestBodyInput"
        />
      </div>
      <div class="field http-prop-scripts">
        <label>前置脚本（preScript）</label>
        <ApiScriptWorkbench
            :min-height="120"
            :model-value="node.data.preScript || ''"
            phase="pre"
            @update:model-value="onPreScriptChange"
        />
      </div>
      <div class="field http-prop-scripts">
        <label>后置脚本（postScript）</label>
        <ApiScriptWorkbench
            :min-height="120"
            :model-value="node.data.postScript || ''"
            phase="post"
            @update:model-value="onPostScriptChange"
        />
        <div class="field__hint">使用 api.request / api.response 对象，与项目接口调试脚本一致</div>
      </div>
    </template>
  </div>

  <div class="field">
    <label>超时时间（ms）</label>
    <input
        :value="timeoutVal"
        min="0"
        placeholder="30000"
        step="1000"
        type="number"
        @input="onTimeoutInput"
    />
    <div class="field__hint">单次 HTTP 请求最长等待；留空则执行时沿用环境配置</div>
  </div>

  <div v-if="callMode === 'project'" class="field-group">
    <div class="field-group__title">成功判定</div>
    <div class="field">
      <label>HTTP 状态校验</label>
      <select :value="statusCheckMode" @change="onStatusCheckModeChange">
        <option value="2xx">仅 2xx（默认）</option>
        <option value="whitelist">白名单（如 200,401）</option>
        <option value="off">关闭（任意状态码继续）</option>
      </select>
      <div class="field__hint">探活再登录选白名单 200/401，再用 Condition 看 http.status</div>
      <input
          v-if="statusCheckMode === 'whitelist'"
          class="field__inline"
          :value="statusCheckValuesText"
          placeholder="200,401"
          @change="onStatusCheckValuesChange"
      />
    </div>
    <div class="field">
      <label>业务 Code 校验</label>
      <select :value="successCheckMode" @change="onSuccessCheckModeChange">
        <option value="inherit">继承项目响应约定</option>
        <option value="off">关闭（不校验业务码）</option>
      </select>
      <div class="field__hint">先 HTTP 状态、后业务码；无 code 字段或探活节点可选关闭</div>
    </div>
  </div>

  <div class="field http-prop-extracts">
    <label>响应提取</label>
    <DebugExtractEditor v-model="extractsModel" :auto-seed-row="false" :trial-body="trialBody" />
  </div>

  <SnapshotPropertySection :node="node" />
</template>

<script setup>
/** HTTP 节点属性：调用模式、接口/外联、超时、业务码、响应提取；提取表达式可对 Run 响应或本接口响应示例试算 */
import { computed, onMounted } from 'vue'

import DebugExtractEditor from '@/views/project/testProject/components/DebugExtractEditor.vue'

import ApiScriptWorkbench from '@/components/script/ApiScriptWorkbench.vue'
import DebugKvSheet from '../../components/DebugKvSheet.vue'
import SnapshotPropertySection from './SnapshotPropertySection.vue'
import { useFlowNodes } from '../../composables/useFlowNodes'
import { useFlowTrialBody } from '../../composables/useFlowTrialBody'
import { getMethodBadgeClass } from '../../constants/flowConfig'
import { useApiHealthStore } from '../../stores/apiHealthStore'
import { useNodeAuthScope } from '../../composables/useNodeAuthScope'
import { countHttpParamStats, emptyKVRow, HTTP_METHODS } from '../../utils/httpWorkbenchUtils'
import { isExternalCallMode } from '../../utils/httpSummary'
import { updateSummary } from '../../utils/nodeDataUtils'

const props = defineProps({
  node: { type: Object, required: true },
})

const { trialBody } = useFlowTrialBody({ node: () => props.node })

const emit = defineEmits(['open-http-config'])

const { patchNodeData, replaceNodeData } = useFlowNodes()
const apiHealth = useApiHealthStore()

const { authScope, authLabel } = useNodeAuthScope(() => props.node?.data)

/** 打开属性时若缺 callMode，补 project（与 AI Normalizer / 拖拽默认一致） */
onMounted(() => {
  const raw = props.node?.data?.callMode
  if (raw == null || String(raw).trim() === '') {
    patchNodeData(props.node.id, { callMode: 'project' })
  }
})

/** 当前选中节点上的语义告警（孤儿测值、抽取路径等） */
const nodeHealthWarnings = computed(() => apiHealth.warningsForNode(props.node?.id))

const callMode = computed(() => (isExternalCallMode(props.node?.data?.callMode) ? 'external' : 'project'))

const httpMethod = computed(() => {
  const d = props.node?.data || {}
  return String(d.httpMethod || '—').toUpperCase()
})

const externalMethod = computed(() => String(props.node?.data?.httpMethod || 'POST').toUpperCase())

const methodBadgeClass = computed(() => getMethodBadgeClass(httpMethod.value))

const httpMeta = computed(() => {
  if (!props.node?.data?.testProjectApiId) return '点击配置接口与请求参数'
  const stats = countHttpParamStats(props.node.data)
  return `Query ${stats.query} · Path ${stats.path} · Headers ${stats.headers} · Body ${stats.body}`
})

const timeoutVal = computed(() => {
  const v = props.node?.data?.timeoutMs
  return v != null && v !== '' ? v : ''
})

/** 当前节点业务码校验模式。
 * inherit：按项目响应约定校验 body 业务码；
 * off：关闭业务码校验。
 * 未配置时按 inherit 展示。
 */
const successCheckMode = computed(() => {
  const mode = props.node?.data?.successCheck?.mode
  if (mode === 'off') return 'off'
  return 'inherit'
})

/** HTTP 状态门禁：2xx（默认）/ whitelist / off */
const statusCheckMode = computed(() => {
  const mode = props.node?.data?.statusCheck?.mode
  if (mode === 'off' || mode === 'whitelist') return mode
  return '2xx'
})

const statusCheckValuesText = computed(() => {
  const values = props.node?.data?.statusCheck?.values
  if (!Array.isArray(values) || !values.length) return '200,401'
  return values.join(',')
})

const extractsModel = computed({
  get() {
    return props.node?.data?.extracts || []
  },
  set(val) {
    applyPatch({ extracts: val })
  },
})

const headersModel = computed({
  get() {
    const rows = props.node?.data?.headers
    return Array.isArray(rows) && rows.length ? rows : [emptyKVRow()]
  },
  set(val) {
    applyPatch({ headers: val })
  },
})

function applyPatch(patch) {
  const data = { ...props.node.data, ...patch }
  updateSummary('http', data)
  patchNodeData(props.node.id, { ...patch, summary: data.summary })
}

function setCallMode(mode) {
  if (mode === 'external') {
    // 切外联：清掉项目绑定与测值覆盖
    const data = {
      ...props.node.data,
      callMode: 'external',
      testProjectApiId: '',
      apiName: '',
      apiPath: '',
      httpMethod: props.node.data.httpMethod || 'POST',
      externalUrl: props.node.data.externalUrl || '',
      headers: props.node.data.headers || [emptyKVRow()],
      requestBody: props.node.data.requestBody || '',
      successCheck: { mode: 'off' },
    }
    delete data.requestValueOverrides
    updateSummary('http', data)
    replaceNodeData(props.node.id, data)
    return
  }
  // 切回项目接口：清掉外联字段与节点上误写的整份 requestConfig
  const data = {
    ...props.node.data,
    callMode: 'project',
    externalUrl: '',
    requestBody: '',
    successCheck: { mode: 'inherit' },
  }
  delete data.requestConfig
  updateSummary('http', data)
  replaceNodeData(props.node.id, data)
}

/** 切换业务码校验：inherit 按项目约定校验；off 关闭 */
function onSuccessCheckModeChange(e) {
  applyPatch({ successCheck: { mode: e.target.value === 'off' ? 'off' : 'inherit' } })
}

/** 切换 HTTP 状态门禁（2xx / 白名单 / 关闭） */
function onStatusCheckModeChange(e) {
  const mode = e.target.value
  if (mode === 'off') {
    applyPatch({ statusCheck: { mode: 'off' } })
    return
  }
  if (mode === 'whitelist') {
    const existing = props.node?.data?.statusCheck?.values
    // 探活默认白名单：200 活着，401/403 去登录
    const values =
      Array.isArray(existing) && existing.length ? existing : [200, 401, 403]
    applyPatch({ statusCheck: { mode: 'whitelist', values } })
    return
  }
  applyPatch({ statusCheck: { mode: '2xx' } })
}

/** 编辑白名单状态码文本（如 200,401,403）；空输入回落到探活默认白名单 */
function onStatusCheckValuesChange(e) {
  const raw = String(e.target.value || '')
  const values = raw
    .split(/[,，\s]+/)
    .map((s) => Number(s.trim()))
    .filter((n) => Number.isInteger(n) && n > 0)
  applyPatch({
    statusCheck: {
      mode: 'whitelist',
      values: values.length ? values : [200, 401, 403],
    },
  })
}

function onExternalMethodChange(e) {
  applyPatch({ httpMethod: e.target.value })
}

function onExternalUrlInput(e) {
  applyPatch({ externalUrl: e.target.value })
}

function onRequestBodyInput(e) {
  applyPatch({ requestBody: e.target.value })
}

function onTimeoutInput(e) {
  const raw = e.target.value
  applyPatch({ timeoutMs: raw === '' ? null : Number(raw) })
}

function onPreScriptChange(val) {
  applyPatch({ preScript: val })
}

function onPostScriptChange(val) {
  applyPatch({ postScript: val })
}
</script>

<style scoped lang="scss">
.http-prop-card {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.call-mode-toggle {
  display: flex;
  gap: 16px;
}

.call-mode-opt {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  cursor: pointer;
}

.field-group {
  display: flex;
  flex-direction: column;
  gap: 10px;
  margin-bottom: 4px;
}

.field-group__title {
  font-size: 12px;
  font-weight: 600;
  color: var(--pd-text);
}

.field__inline {
  margin-top: 6px;
  width: 100%;
  box-sizing: border-box;
  padding: 4px 8px;
  border-radius: 6px;
  border: 1px solid var(--pd-border-subtle);
  font-size: 12px;
}

.api-preview {
  padding: 10px 12px;
  border-radius: var(--pd-radius-sm);
  border: 1px solid var(--pd-border-subtle);
  background: var(--pd-bg-sunken);

  &.is-empty {
    color: var(--pd-text-muted);
    font-size: 12px;
    text-align: center;
  }
}

.api-preview__row {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}

.api-preview__path {
  font-size: 12px;
  font-family: ui-monospace, Consolas, monospace;
  color: var(--pd-text);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.api-preview__name {
  margin-top: 6px;
  font-size: 11px;
  color: var(--pd-text-muted);
}

.http-prop-auth {
  margin-top: 6px;
  font-size: 11px;
  color: var(--pd-text-muted);
  line-height: 1.4;
}

.http-prop-auth--conflict {
  color: var(--el-color-danger, #f56c6c);
  font-weight: 600;
}

.http-prop-meta {
  font-size: 11px;
  color: var(--pd-text-muted);
  line-height: 1.5;
}

.http-prop-health {
  /* 属性面板：当前 HTTP 节点的语义告警列表 */
  margin: 0 0 8px;
  padding-left: 16px;
  font-size: 11px;
  color: #a16207;
  line-height: 1.45;
}

.method-badge {
  flex-shrink: 0;
  min-width: 36px;
  padding: 1px 4px;
  text-align: center;
  font-size: 10px;
  font-weight: 700;
  font-family: ui-monospace, Consolas, monospace;
  line-height: 1.5;
  border-radius: 2px;
  letter-spacing: 0.02em;
  background: var(--pd-bg-sunken);
  color: #606266;
  border: 1px solid var(--pd-border-muted);

  &.is-GET {
    color: #67c23a;
    border-color: #c2e7b0;
    background: #f0f9eb;
  }

  &.is-POST {
    color: #e6a23c;
    border-color: #f5dab1;
    background: #fdf6ec;
  }

  &.is-PUT {
    color: #409eff;
    border-color: #b3d8ff;
    background: #ecf5ff;
  }

  &.is-PATCH {
    color: #909399;
    border-color: #dcdfe6;
    background: #f4f4f5;
  }

  &.is-DELETE {
    color: #f56c6c;
    border-color: #fbc4c4;
    background: #fef0f0;
  }
}

textarea {
  width: 100%;
  padding: 6px 8px;
  border-radius: 6px;
  border: 1px solid var(--pd-border-subtle);
  font-size: 11px;
  font-family: ui-monospace, Consolas, monospace;
  resize: vertical;
  box-sizing: border-box;
}

.http-prop-scripts {
  :deep(.api-script-workbench) {
    grid-template-columns: minmax(0, 1fr);
  }

  :deep(.api-script-workbench__aside) {
    display: none;
  }
}
</style>

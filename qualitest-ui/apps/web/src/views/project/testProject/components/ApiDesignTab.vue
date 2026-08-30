<template>
  <div class="api-design-workbench">
    <div class="design-scroll">
      <section class="design-strip">
        <header class="design-strip__title">基本信息</header>
        <div class="design-strip__body design-strip__body--form">
          <el-form
              class="design-meta-form"
              label-width="100px"
              size="default"
          >
            <el-row :gutter="12">
              <el-col :md="12" :span="24">
                <el-form-item label="API 名称" required>
                  <el-input v-model="meta.apiName" clearable maxlength="128" placeholder="接口名称"/>
                </el-form-item>
              </el-col>
              <el-col :md="12" :span="24">
                <el-form-item label="协议类型">
                  <el-select v-model="meta.protocolType" class="design-w100" clearable placeholder="如 HTTP">
                    <el-option label="HTTP" value="HTTP"/>
                    <el-option label="HTTPS" value="HTTPS"/>
                  </el-select>
                </el-form-item>
              </el-col>
              <el-col :span="24">
                <el-form-item label="API 描述">
                  <el-input
                      v-model="meta.apiDescription"
                      :rows="2"
                      maxlength="2000"
                      placeholder="接口说明（可选）"
                      type="textarea"
                  />
                </el-form-item>
              </el-col>
              <el-col :span="24">
                <el-form-item label="造流设计提示">
                  <el-input
                      v-model="designHintsText"
                      :rows="3"
                      maxlength="2000"
                      placeholder="每行一条；人机可维护，接口导入不会覆盖"
                      type="textarea"
                  />
                  <div class="design-hints-tip">用于造流 AI 读取业务约定；随本页「保存」一并写入（导入不会覆盖）</div>
                </el-form-item>
              </el-col>
              <el-col :md="12" :span="24">
                <el-form-item label="状态">
                  <el-radio-group v-model="meta.apiStatus">
                    <el-radio-button value="1">启用</el-radio-button>
                    <el-radio-button value="0">禁用</el-radio-button>
                  </el-radio-group>
                </el-form-item>
              </el-col>
              <el-col :md="12" :span="24">
                <el-form-item label="上传保护">
                  <el-switch v-model="meta.syncProtected" inline-prompt active-text="开" inactive-text="关"/>
                  <div class="design-hints-tip">开启后插件/批量导入不会覆盖本接口；需要同步时先关闭，上传后再打开</div>
                </el-form-item>
              </el-col>
              <el-col :md="12" :span="24">
                <el-form-item label="API 分组">
                  <span class="design-readonly-text">{{ apiDetail.apiGroup || '—' }}</span>
                </el-form-item>
              </el-col>
              <el-col :md="12" :span="24">
                <el-form-item label="鉴权模式">
                  <el-select v-model="auth.mode" class="design-w100" placeholder="inherit">
                    <el-option label="继承项目配置 (inherit)" value="inherit"/>
                    <el-option label="免登录 (none)" value="none"/>
                    <el-option label="接口自定义 (override)" value="override"/>
                  </el-select>
                </el-form-item>
              </el-col>
              <el-col :md="12" :span="24">
                <el-form-item label="鉴权 Profile">
                  <el-input
                      v-model="auth.authProfileId"
                      :disabled="auth.mode !== 'inherit'"
                      clearable
                      maxlength="64"
                      placeholder="如 clientBearer / adminBearer，可空则按路径匹配"
                  />
                </el-form-item>
              </el-col>
              <template v-if="auth.mode === 'override'">
                <el-col :md="12" :span="24">
                  <el-form-item label="自定义头名" required>
                    <el-input
                        v-model="auth.headerName"
                        clearable
                        maxlength="128"
                        placeholder="Authorization"
                    />
                  </el-form-item>
                </el-col>
                <el-col :md="12" :span="24">
                  <el-form-item label="头值模板" required>
                    <el-input
                        v-model="auth.valueTemplate"
                        clearable
                        maxlength="512"
                        placeholder="Bearer {{flow.token}} 或 Bearer invalid-token"
                    />
                  </el-form-item>
                </el-col>
              </template>
            </el-row>
          </el-form>
        </div>
      </section>

      <section class="design-strip design-strip--flex">
        <header class="design-strip__title design-strip__title--with-hint">
          <span class="design-strip__title-text">请求设计</span>
        </header>
        <!-- 内嵌调试工作台以复用请求配置编辑；与详情页「调试」Tab 为不同 keep-alive 实例 -->
        <div class="design-embed-debug">
          <ApiDebugTab
              ref="debugTabRef"
              :api-detail="apiDetail"
              :embed-in-design="true"
              :env-list="[]"
              :test-project-env-id="null"
          />
        </div>
      </section>

      <section class="design-strip design-strip--flex design-strip--last">
        <header class="design-strip__title">响应配置</header>
        <div class="design-response-panel-wrap">
          <ResponseConfigPanel ref="responseConfigPanelRef" v-model="responseConfigText"/>
        </div>
      </section>
    </div>

    <div class="design-footer">
      <el-button v-hasPermi="['project:testProject:edit']" :loading="saving" type="primary" @click="handleSaveDesign">
        保存设计
      </el-button>
    </div>
  </div>
</template>

<script setup>
import {updateTestProjectApi} from '@/api/project/testProjectApi'
import ApiDebugTab from './ApiDebugTab.vue'
import ResponseConfigPanel from './ResponseConfigPanel.vue'

const props = defineProps({
  apiDetail: {
    type: Object,
    required: true
  }
})

const emit = defineEmits(['saved'])

const {proxy} = getCurrentInstance()

const debugTabRef = ref(null)
const responseConfigPanelRef = ref(null)
const saving = ref(false)

const meta = reactive({
  apiName: '',
  apiDescription: '',
  protocolType: '',
  apiStatus: '1',
  syncProtected: false
})

/** 造流设计提示：编辑区为换行分隔文本，保存时拆成 hints 数组 */
const designHintsText = ref('')

/** 接口薄鉴权标签（落库 auth_config） */
const auth = reactive({
  mode: 'inherit',
  authProfileId: '',
  headerName: 'Authorization',
  valueTemplate: ''
})

const responseConfigText = ref('')

function formatJsonForEdit(raw) {
  if (raw == null || raw === '') return ''
  if (typeof raw !== 'string') {
    try {
      return JSON.stringify(raw, null, 2)
    } catch {
      return String(raw)
    }
  }
  try {
    const parsed = JSON.parse(raw)
    return JSON.stringify(parsed, null, 2)
  } catch {
    return raw
  }
}

function emptyAuthParsed() {
  return {
    mode: 'inherit',
    authProfileId: '',
    headerName: 'Authorization',
    valueTemplate: ''
  }
}

function parseAuthConfig(raw) {
  if (raw == null || raw === '') {
    return emptyAuthParsed()
  }
  try {
    const obj = typeof raw === 'string' ? JSON.parse(raw) : raw
    const mode = String(obj?.mode || 'inherit').trim() || 'inherit'
    return {
      mode: ['none', 'inherit', 'override'].includes(mode) ? mode : 'inherit',
      authProfileId: obj?.authProfileId != null ? String(obj.authProfileId).trim() : '',
      headerName: obj?.header?.name != null && String(obj.header.name).trim()
          ? String(obj.header.name).trim()
          : 'Authorization',
      valueTemplate: obj?.header?.valueTemplate != null ? String(obj.header.valueTemplate).trim() : ''
    }
  } catch {
    return emptyAuthParsed()
  }
}

function buildAuthConfigPayload() {
  const mode = (auth.mode || 'inherit').trim()
  const payload = { mode }
  if (mode === 'inherit' && auth.authProfileId?.trim()) {
    payload.authProfileId = auth.authProfileId.trim()
  }
  if (mode === 'override') {
    const name = (auth.headerName || '').trim()
    const valueTemplate = (auth.valueTemplate || '').trim()
    if (!name || !valueTemplate) {
      throw new Error('接口自定义鉴权须填写头名称与头值模板')
    }
    payload.header = { name, valueTemplate }
  }
  return JSON.stringify(payload)
}

function normalizeResponseConfigForSave(text) {
  const t = (text || '').trim()
  if (!t) return ''
  try {
    JSON.parse(t)
    return t
  } catch {
    throw new Error('响应配置须为合法 JSON')
  }
}

function parseDesignHintsFromDetail(d) {
  const raw = d?.designHints
  if (raw == null || raw === '') return ''
  try {
    const obj = typeof raw === 'string' ? JSON.parse(raw) : raw
    const hints = Array.isArray(obj?.hints) ? obj.hints : Array.isArray(obj) ? obj : []
    return hints.map((h) => String(h ?? '').trim()).filter(Boolean).join('\n')
  } catch {
    return typeof raw === 'string' ? raw : ''
  }
}

function designHintsListFromText(text) {
  return String(text ?? '')
      .split(/\r?\n/)
      .map((l) => l.trim())
      .filter(Boolean)
}

/** 随主表单 PUT 落库的 design_hints JSON（空列表可清空） */
function buildDesignHintsJson(text) {
  return JSON.stringify({
    hints: designHintsListFromText(text),
    source: 'manual'
  })
}

function syncMetaFromDetail(d) {
  meta.apiName = d.apiName ?? ''
  meta.apiDescription = d.apiDescription ?? ''
  meta.protocolType = d.protocolType ?? ''
  const s = d.apiStatus
  meta.apiStatus = s === '0' || s === 0 || s === false ? '0' : '1'
  meta.syncProtected = d.syncProtected === 1 || d.syncProtected === true || d.syncProtected === '1'
  designHintsText.value = parseDesignHintsFromDetail(d)
  responseConfigText.value = formatJsonForEdit(d.responseConfig)
  const parsedAuth = parseAuthConfig(d.authConfig)
  auth.mode = parsedAuth.mode
  auth.authProfileId = parsedAuth.authProfileId
  auth.headerName = parsedAuth.headerName
  auth.valueTemplate = parsedAuth.valueTemplate
}

watch(
    () => props.apiDetail,
    (d) => {
      if (d) syncMetaFromDetail(d)
    },
    {immediate: true}
)

function handleSaveDesign() {
  if (!props.apiDetail?.testProjectApiId) return
  responseConfigPanelRef.value?.flushPendingModelEmit?.()
  let responseConfigStr = ''
  try {
    responseConfigStr = normalizeResponseConfigForSave(responseConfigText.value)
  } catch (e) {
    proxy.$modal.msgError(e.message || '响应配置 JSON 无效')
    return
  }
  // 用设计页当前响应稿一起拆测值，避免仍用详情里过期的 responseConfig
  const part = debugTabRef.value?.buildPersistPayload?.(responseConfigStr)
  if (!part) {
    proxy.$modal.msgError('无法读取请求配置')
    return
  }
  if (part.error) {
    proxy.$modal.msgError(part.error)
    return
  }

  let authConfigStr
  try {
    authConfigStr = buildAuthConfigPayload()
  } catch (e) {
    proxy.$modal.msgError(e.message || '鉴权配置无效')
    return
  }

  saving.value = true
  const designHintsJson = buildDesignHintsJson(designHintsText.value)
  const payload = {
    testProjectApiId: props.apiDetail.testProjectApiId,
    testProjectId: props.apiDetail.testProjectId,
    apiGroupId: props.apiDetail.apiGroupId,
    apiStatus: meta.apiStatus,
    apiGroup: props.apiDetail.apiGroup,
    apiName: meta.apiName.trim() || props.apiDetail.apiName,
    apiDescription: meta.apiDescription,
    apiPath: part.apiPath,
    protocolType: meta.protocolType || props.apiDetail.protocolType,
    requestConfig: part.requestConfig,
    headers: part.headers,
    cookies: part.cookies,
    responseConfig: part.responseConfig,
    testValueConfig: part.testValueConfig,
    preRequestScript: part.preRequestScript ?? '',
    postRequestScript: part.postRequestScript ?? '',
    authConfig: authConfigStr,
    designHints: designHintsJson,
    syncProtected: meta.syncProtected ? 1 : 0
  }

  updateTestProjectApi(payload)
      .then((res) => {
        if (res.code === 200) {
          proxy.$modal.msgSuccess('保存成功')
          emit('saved', {...props.apiDetail, ...payload})
        } else {
          proxy.$modal.msgError(res.msg || '保存失败')
        }
      })
      .catch((err) => proxy?.$modal?.msgError?.(err?.message || err?.msg || '保存失败'))
      .finally(() => {
        saving.value = false
      })
}
</script>

<style lang="scss" scoped>
/* 主列与 Tab 内容区左右贴边；各分区用通栏色带作标题 */
.api-design-workbench {
  position: relative;
  display: flex;
  flex-direction: column;
  height: 100%;
  min-height: 0;
  background: var(--pd-surface-elevated, #fff);
}

.design-scroll {
  flex: 1;
  min-height: 0;
  overflow: auto;
  scrollbar-gutter: stable;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 0;
}

.design-strip {
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  border-bottom: 1px solid var(--pd-divider, #dbe8f4);

  /* 不参与与下方响应区的纵向均分，否则 flex + min-height:0 会把区块压扁并导致层叠 */
  &--flex {
    flex-shrink: 0;
  }

  &--last {
    border-bottom: none;
  }
}

.design-strip__title {
  flex-shrink: 0;
  margin: 0;
  padding: 6px 6px;
  font-size: 14px;
  font-weight: 600;
  color: var(--pd-text, #0f172a);
  /* 三块分区（基本信息 / 请求设计 / 响应配置）共用标题条样式 */
  background: #d0e2f4;
  border-bottom: 1px solid var(--pd-divider, #dbe8f4);
  line-height: 1.4;

  &--with-hint {
    display: flex;
    flex-wrap: wrap;
    align-items: baseline;
    gap: 6px 10px;
  }
}

.design-strip__title-text {
  flex-shrink: 0;
}

.design-strip__body {
  background: var(--pd-surface-elevated, #fff);

  &--form {
    padding: 8px 6px 6px;
  }
}

.design-hint {
  font-size: 12px;
  font-weight: 400;
  color: var(--pd-text-muted, #5a6b86);
}

.design-hints-tip {
  margin-top: 4px;
  font-size: 12px;
  color: var(--pd-text-muted, #5a6b86);
  line-height: 1.4;
}

.design-field-desc {
  margin: 0;
  font-size: 12px;
  color: var(--pd-text-muted, #5a6b86);
  line-height: 1.45;

  &--strip {
    padding: 6px 6px 0;
    background: var(--pd-surface-elevated, #fff);
  }
}

.design-meta-form {
  padding-bottom: 2px;
}

.design-readonly-text {
  font-size: 13px;
  color: var(--pd-text-muted, #606266);
}

.design-w100 {
  width: 100%;
}

.design-embed-debug {
  display: flex;
  flex-direction: column;
  flex-shrink: 0;
  /* 限定高度区间，内层 flex 才能铺满；避免与下方响应配置区在同一纵坐标重叠 */
  height: clamp(320px, 44vh, 600px);
  min-height: 320px;
  margin: 0;
  border: none;
  border-radius: 0;
  overflow: hidden;
  background: var(--pd-surface-elevated, #fff);

  :deep(.api-debug-workbench) {
    flex: 1;
    min-height: 0;
    height: 100%;
  }
}

.design-response-panel-wrap {
  padding: 0;
  background: var(--pd-surface-elevated, #fff);

  /* 收紧响应区内边距，与调试区内层留白相近 */
  :deep(.resp-meta-grid) {
    padding: 10px 8px;
  }

  :deep(.resp-inner-tabs .el-tabs__header) {
    padding: 0 6px;
  }

  :deep(.resp-example-wrap) {
    padding: 8px 6px 10px;
  }
}

.design-footer {
  flex-shrink: 0;
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  padding: 8px 6px;
  border-top: 1px solid var(--pd-divider, #dbe8f4);
  background: var(--pd-bg-sunken, #f0f6fc);
}
</style>

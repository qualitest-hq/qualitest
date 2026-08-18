<template>
  <el-drawer
      v-model="visible"
      :title="drawerTitle"
      class="project-setting-drawer"
      destroy-on-close
      direction="rtl"
      size="560px"
      @opened="$emit('opened')"
  >
    <div v-loading="settingLoading" class="project-setting">
      <section v-if="showHttpTransportSetting" class="project-setting__card">
        <header class="project-setting__card-head">
          <h3 class="project-setting__card-title">API 调试发送</h3>
          <p class="project-setting__card-desc">接口调试页请求走浏览器或经质衡后端转发</p>
        </header>
        <el-tooltip
            :disabled="forwardAvailable"
            content="需配置 VITE_HTTP_FORWARD_API 并实现 Java 转发接口"
            placement="top"
        >
          <el-segmented
              v-model="httpTransport"
              :options="transportOptions"
              block
              class="project-setting__segmented"
          />
        </el-tooltip>
        <p class="project-setting__hint">
          服务端代理适用于规避浏览器 CORS；未配置转发接口时仅可使用浏览器直连。
        </p>
      </section>

      <template v-if="settingContext !== null">
        <section class="project-setting__card">
          <header class="project-setting__card-head">
            <h3 class="project-setting__card-title">响应约定</h3>
            <p class="project-setting__card-desc">
              项目级业务 Code 库：HTTP 节点默认按此校验 body 中的业务码（可在节点上关闭）。
            </p>
          </header>

          <div class="project-setting__conv-grid">
            <div class="project-setting__conv-field">
              <label>code 路径</label>
              <el-input v-model="conventionForm.codePath" placeholder="code" />
            </div>
            <div class="project-setting__conv-field">
              <label>成功值（逗号分隔）</label>
              <el-input v-model="conventionForm.successValuesText" placeholder="200" />
            </div>
            <div class="project-setting__conv-field">
              <label>消息路径</label>
              <el-input v-model="conventionForm.messagePath" placeholder="msg" />
            </div>
            <div class="project-setting__conv-field">
              <label>data 路径</label>
              <el-input v-model="conventionForm.dataPath" placeholder="data" />
            </div>
          </div>

          <pre class="project-setting__preview-block"><code>{{ conventionPreview }}</code></pre>
          <p class="project-setting__hint">示例响应判定：code ∈ 成功值 → 步骤通过；否则失败并展示 message 字段。</p>

          <div class="project-setting__actions">
            <el-button :loading="conventionSaving" type="primary" @click="saveResponseConvention">
              保存响应约定
            </el-button>
          </div>
        </section>

        <section class="project-setting__card">
          <header class="project-setting__card-head">
            <h3 class="project-setting__card-title">项目鉴权</h3>
            <p class="project-setting__card-desc">
              从模板库勾选开源/靶场 Profile；免登由预制接口 <code>authConfig.mode=none</code> 决定，不再维护匿名 path 清单。
              pathPrefix 禁止写 <code>/</code>。
            </p>
          </header>

          <el-alert
              v-if="authForm.needsAuthTemplateHint"
              class="project-setting__auth-hint"
              show-icon
              title="当前有 Profile 但缺少预制接口，请从模板库添加以补齐登录/注册/验证码等免登口。"
              type="warning"
              :closable="false"
          />

          <div class="project-setting__auth-toolbar">
            <el-button :loading="templateApplying" size="small" type="primary" @click="openTemplatePicker">
              从模板库添加
            </el-button>
            <el-button size="small" @click="addAuthProfile">手动添加 Profile</el-button>
          </div>

          <el-collapse v-if="authForm.profiles.length" v-model="authCollapseNames" class="project-setting__auth-collapse">
            <el-collapse-item
                v-for="(row, idx) in authForm.profiles"
                :key="'auth-p-' + idx"
                :name="String(idx)"
            >
              <template #title>
                <span class="project-setting__auth-collapse-title">
                  {{ row.name || row.id || `Profile ${idx + 1}` }}
                </span>
                <el-button
                    class="project-setting__auth-remove"
                    link
                    size="small"
                    type="danger"
                    @click.stop="removeAuthProfile(idx)"
                >
                  删除
                </el-button>
              </template>
              <div class="project-setting__conv-grid">
                <div class="project-setting__conv-field">
                  <label>id</label>
                  <el-input v-model="row.id" maxlength="64" placeholder="如 clientBearer" />
                </div>
                <div class="project-setting__conv-field">
                  <label>名称</label>
                  <el-input v-model="row.name" maxlength="64" placeholder="展示名" />
                </div>
                <div class="project-setting__conv-field project-setting__conv-field--full">
                  <label>pathPrefix（每行一条，禁止 /）</label>
                  <el-input
                      v-model="row.pathPrefixText"
                      :rows="2"
                      placeholder="/api/"
                      type="textarea"
                  />
                </div>
                <div class="project-setting__conv-field">
                  <label>头名称</label>
                  <el-input v-model="row.headerName" placeholder="Authorization" />
                </div>
                <div class="project-setting__conv-field">
                  <label>值模板</label>
                  <el-input v-model="row.valueTemplate" placeholder="Bearer {{flow.token}}" />
                </div>
                <div class="project-setting__conv-field">
                  <label>credentialApi.method</label>
                  <el-input v-model="row.credentialMethod" placeholder="POST" />
                </div>
                <div class="project-setting__conv-field">
                  <label>credentialApi.path</label>
                  <el-input v-model="row.credentialPath" placeholder="/login" />
                </div>
                <div class="project-setting__conv-field">
                  <label>loginHint.flowKey</label>
                  <el-input v-model="row.loginFlowKey" placeholder="token" />
                </div>
                <div class="project-setting__conv-field">
                  <label>loginHint.from</label>
                  <el-select v-model="row.loginFrom" clearable placeholder="body">
                    <el-option
                        v-for="opt in LOGIN_HINT_FROM_OPTIONS"
                        :key="opt.value"
                        :label="opt.label"
                        :value="opt.value"
                    />
                  </el-select>
                </div>
                <div class="project-setting__conv-field project-setting__conv-field--full">
                  <label>loginHint.expr</label>
                  <el-input v-model="row.loginExpr" placeholder="$.token 或 Cookie 名" />
                </div>
              </div>

              <div v-if="row.apis?.length" class="project-setting__apis-block">
                <label class="project-setting__apis-label">预制接口</label>
                <el-table :data="row.apis" border size="small" class="project-setting__apis-table">
                  <el-table-column label="方法" prop="method" width="72" />
                  <el-table-column label="路径" min-width="140" prop="apiPath" show-overflow-tooltip />
                  <el-table-column label="名称" min-width="100" prop="apiName" show-overflow-tooltip />
                  <el-table-column label="鉴权" width="88">
                    <template #default="{ row: apiRow }">
                      {{ formatAuthModeLabel(apiRow.authMode) }}
                    </template>
                  </el-table-column>
                </el-table>
              </div>
              <p v-else class="project-setting__hint project-setting__hint--inline">
                暂无预制接口；请从模板库添加，或保存后由后端种子接口。
              </p>
            </el-collapse-item>
          </el-collapse>
          <div v-else class="project-setting__empty-box project-setting__empty-box--compact">
            尚未配置 Profile，请从模板库添加或手动添加
          </div>

          <pre class="project-setting__preview-block"><code>{{ authPreview }}</code></pre>
          <p class="project-setting__hint">
            模板追加会写入项目 auth_config 并种子尚未存在的预制接口；手动保存仅更新当前表单中的 Profile。
          </p>

          <div class="project-setting__actions">
            <el-button :loading="authSaving" type="primary" @click="saveProjectAuth">
              保存项目鉴权
            </el-button>
          </div>
        </section>

        <el-dialog v-model="templatePickerVisible" append-to-body title="从模板库添加" width="520px">
          <div class="project-setting__template-picker">
            <p class="project-setting__hint">
              勾选后追加到当前项目；同名 Profile 整份跳过，已有 method+path 的预制口不会覆盖。
            </p>
            <AuthTemplateCheckboxList
                v-model="selectedTemplateIds"
                :empty-size="64"
                :loading="templateLoading"
                :templates="enabledTemplates"
            />
          </div>
          <template #footer>
            <el-button @click="templatePickerVisible = false">取消</el-button>
            <el-button
                :disabled="!selectedTemplateIds.length"
                :loading="templateApplying"
                type="primary"
                @click="confirmApplyTemplates"
            >
              添加所选模板
            </el-button>
          </template>
        </el-dialog>

        <section class="project-setting__card">
          <header class="project-setting__card-head">
            <h3 class="project-setting__card-title">项目 Token</h3>
            <p class="project-setting__card-desc">
              供 IDEA 插件、Cursor MCP 等外部工具鉴权；刷新后旧 Token 立即失效。
            </p>
          </header>

          <div v-if="settingForm.projectToken" class="project-setting__token-box">
            <code class="project-setting__token-value">{{ settingForm.projectToken }}</code>
          </div>
          <div v-else class="project-setting__empty-box">
            <span>尚未生成 Token</span>
            <span class="project-setting__empty-sub">打开本页时将自动为有权成员生成</span>
          </div>

          <div class="project-setting__actions">
            <el-button
                v-copyText="settingForm.projectToken"
                v-copyText:callback="() => proxy.$modal.msgSuccess('Token 已复制')"
                :disabled="!settingForm.projectToken"
                type="primary"
            >
              <svg-icon class="project-setting__btn-icon" icon-class="clipboard"/>
              复制 Token
            </el-button>
            <el-button @click="$emit('refresh-token')">刷新 Token</el-button>
          </div>
        </section>

        <section class="project-setting__card">
          <header class="project-setting__card-head project-setting__card-head--row">
            <div>
              <h3 class="project-setting__card-title">Cursor MCP</h3>
              <p class="project-setting__card-desc">
                粘贴到 Cursor 的 <code>mcp.json</code>，IDE 内只读查询本项目 API、测试流等。
              </p>
            </div>
            <el-button
                v-copyText="mcpConfigText"
                v-copyText:callback="() => proxy.$modal.msgSuccess('MCP 配置已复制')"
                :disabled="!settingForm.projectToken"
                link
                type="primary"
            >
              复制配置
            </el-button>
          </header>

          <pre
              v-if="settingForm.projectToken"
              class="project-setting__code-block"
          ><code>{{ mcpConfigText }}</code></pre>
          <div v-else class="project-setting__empty-box project-setting__empty-box--compact">
            请先生成项目 Token
          </div>

          <p class="project-setting__hint project-setting__hint--warn">
            含私密 Token，请勿提交到 git 或分享给无关人员。
          </p>
        </section>
      </template>

      <el-empty
          v-else
          class="project-setting__no-access"
          description="无法加载项目设置（非项目成员或无权限）"
          :image-size="72"
      />
    </div>
  </el-drawer>
</template>

<script setup>
/**
 * 项目设置侧栏：响应约定、项目鉴权、API 调试传输方式、项目 Token、Cursor MCP 配置。
 */
import { computed, getCurrentInstance, reactive, ref, watch } from 'vue'
import { buildCursorMcpConfig } from '../utils/mcpClientConfig'
import {
  buildAuthConfigPayload,
  emptyAuthForm,
  emptyProfileRow,
  formatAuthConfigPreview,
  formatAuthModeLabel,
  LOGIN_HINT_FROM_OPTIONS,
  parseAuthConfig,
} from '../utils/projectAuthConfig'
import { applyAuthTemplates, getTestProject, updateTestProject } from '@/api/project/testProject'
import AuthTemplateCheckboxList from './AuthTemplateCheckboxList.vue'
import { toTemplateIds, useEnabledAuthTemplates } from '../composables/useEnabledAuthTemplates'

const visible = defineModel('visible', { type: Boolean, default: false })
const httpTransport = defineModel('httpTransport', { type: String, default: 'browser' })

const { proxy } = getCurrentInstance()

const props = defineProps({
  settingLoading: {
    type: Boolean,
    default: false,
  },
  settingForm: {
    type: Object,
    required: true,
  },
  settingContext: {
    type: Object,
    default: null,
  },
  showHttpTransportSetting: {
    type: Boolean,
    default: false,
  },
  forwardAvailable: {
    type: Boolean,
    default: false,
  },
  drawerTitle: {
    type: String,
    default: '项目设置',
  },
  testProjectId: {
    type: [String, Number],
    default: null,
  },
})

const emit = defineEmits(['opened', 'refresh-token', 'auth-changed'])

const conventionSaving = ref(false)
/** 响应约定表单：业务码路径、成功值、消息路径、数据包装路径 */
const conventionForm = reactive({
  codePath: 'code',
  successValuesText: '200',
  messagePath: 'msg',
  dataPath: 'data',
})

const authSaving = ref(false)
const authForm = reactive(emptyAuthForm())
const authCollapseNames = ref([])
const templatePickerVisible = ref(false)
const templateApplying = ref(false)
const selectedTemplateIds = ref([])
const {
  templateLoading,
  enabledTemplates,
  loadEnabledTemplates,
} = useEnabledAuthTemplates()

const authPreview = computed(() => formatAuthConfigPreview(authForm))

function resolveProjectId() {
  return props.testProjectId || props.settingForm?.testProjectId
}

function syncAuthCollapse() {
  authCollapseNames.value = authForm.profiles.map((_, i) => String(i))
}

/** 把库中的 responseConvention JSON 填入表单 */
function applyConvention(raw) {
  let obj = null
  if (raw && typeof raw === 'string') {
    try {
      obj = JSON.parse(raw)
    } catch {
      obj = null
    }
  } else if (raw && typeof raw === 'object') {
    obj = raw
  }
  conventionForm.codePath = obj?.codePath || 'code'
  conventionForm.messagePath = obj?.messagePath || 'msg'
  conventionForm.dataPath = obj?.dataPath || 'data'
  const values = Array.isArray(obj?.successValues) ? obj.successValues : [200]
  conventionForm.successValuesText = values.join(',')
}

function applyAuthForm(next) {
  authForm.profiles = Array.isArray(next.profiles) ? next.profiles.map((p) => ({ ...p, apis: [...(p.apis || [])] })) : []
  authForm.needsAuthTemplateHint = !!next.needsAuthTemplateHint
  syncAuthCollapse()
}

/** 解析成功业务码输入框（逗号/空白分隔），空则默认 [200] */
function parseSuccessValuesText(text) {
  const values = String(text || '')
    .split(/[,，\s]+/)
    .map((s) => s.trim())
    .filter(Boolean)
    .map((s) => Number(s))
    .filter((n) => !Number.isNaN(n))
  return values.length ? values : [200]
}

/** 打开抽屉后拉取项目详情中的响应约定与鉴权配置 */
function loadProjectSettings() {
  const pid = resolveProjectId()
  if (!pid) {
    applyConvention(null)
    applyAuthForm(emptyAuthForm())
    return
  }
  getTestProject(pid)
    .then((res) => {
      applyConvention(res.data?.responseConvention)
      applyAuthForm(parseAuthConfig(res.data?.authConfig, {
        needsAuthTemplateHint: res.data?.needsAuthTemplateHint,
      }))
    })
    .catch(() => {
      applyConvention(null)
      applyAuthForm(emptyAuthForm())
    })
}

function openTemplatePicker() {
  selectedTemplateIds.value = []
  templatePickerVisible.value = true
  loadEnabledTemplates()
}

function confirmApplyTemplates() {
  const pid = resolveProjectId()
  if (!pid) {
    proxy?.$modal?.msgError?.('缺少项目 ID')
    return
  }
  const templateIds = toTemplateIds(selectedTemplateIds.value)
  if (!templateIds.length) {
    proxy?.$modal?.msgWarning?.('请至少选择一个模板')
    return
  }
  templateApplying.value = true
  applyAuthTemplates(pid, templateIds)
    .then((res) => {
      if (res.code === 200) {
        proxy?.$modal?.msgSuccess?.('模板已添加到项目')
        templatePickerVisible.value = false
        loadProjectSettings()
        emit('auth-changed')
      } else {
        proxy?.$modal?.msgError?.(res.msg || '添加失败')
      }
    })
    .catch((err) => proxy?.$modal?.msgError?.(err?.message || err?.msg || '添加失败'))
    .finally(() => {
      templateApplying.value = false
    })
}

watch(
  () => [visible.value, props.settingContext, props.testProjectId],
  () => {
    if (visible.value && props.settingContext !== null) {
      loadProjectSettings()
    }
  },
)

function addAuthProfile() {
  authForm.profiles.push(emptyProfileRow())
  syncAuthCollapse()
}

function removeAuthProfile(idx) {
  authForm.profiles.splice(idx, 1)
  syncAuthCollapse()
}

/** 保存项目鉴权到 auth_config */
function saveProjectAuth() {
  const pid = resolveProjectId()
  if (!pid) {
    proxy?.$modal?.msgError?.('缺少项目 ID')
    return
  }
  let authConfig
  try {
    authConfig = buildAuthConfigPayload(authForm)
  } catch (e) {
    proxy?.$modal?.msgError?.(e.message || '鉴权配置无效')
    return
  }
  authSaving.value = true
  updateTestProject({ testProjectId: pid, authConfig })
    .then((res) => {
      if (res.code === 200) {
        proxy?.$modal?.msgSuccess?.('项目鉴权已保存')
        loadProjectSettings()
        emit('auth-changed')
      } else {
        proxy?.$modal?.msgError?.(res.msg || '保存失败')
      }
    })
    .catch((err) => proxy?.$modal?.msgError?.(err?.message || err?.msg || '保存失败'))
    .finally(() => {
      authSaving.value = false
    })
}

/** 预览：用示例失败响应演示当前约定下是否判定通过 */
const conventionPreview = computed(() => {
  const successValues = parseSuccessValuesText(conventionForm.successValuesText)
  const sampleFail = {
    code: 500,
    msg: '手机号或密码错误',
    data: null,
  }
  const codePath = conventionForm.codePath || 'code'
  const msgPath = conventionForm.messagePath || 'msg'
  const actual = sampleFail[codePath]
  const passed = successValues.includes(actual)
  return JSON.stringify(
    {
      sample: sampleFail,
      successValues,
      actualCode: actual,
      message: sampleFail[msgPath],
      passed,
    },
    null,
    2,
  )
})

/** 保存响应约定到项目：规范化字段后调用 updateTestProject 写库 */
function saveResponseConvention() {
  const pid = resolveProjectId()
  if (!pid) {
    proxy?.$modal?.msgError?.('缺少项目 ID')
    return
  }
  const values = parseSuccessValuesText(conventionForm.successValuesText)
  const payload = {
    testProjectId: pid,
    responseConvention: JSON.stringify({
      codePath: (conventionForm.codePath || 'code').trim(),
      successValues: values,
      messagePath: (conventionForm.messagePath || 'msg').trim(),
      dataPath: (conventionForm.dataPath || 'data').trim(),
    }),
  }
  conventionSaving.value = true
  updateTestProject(payload)
    .then((res) => {
      if (res.code === 200) {
        proxy?.$modal?.msgSuccess?.('响应约定已保存')
      } else {
        proxy?.$modal?.msgError?.(res.msg || '保存失败')
      }
    })
    .catch(() => proxy?.$modal?.msgError?.('保存失败'))
    .finally(() => {
      conventionSaving.value = false
    })
}

const transportOptions = computed(() => [
  { label: '浏览器直连', value: 'browser' },
  {
    label: '服务端代理',
    value: 'browser-java-forward',
    disabled: !props.forwardAvailable,
  },
])

const mcpConfigText = computed(() => {
  if (!props.settingForm?.projectToken) {
    return ''
  }
  return buildCursorMcpConfig(props.settingForm.projectToken)
})
</script>

<style lang="scss" scoped>
.project-setting {
  display: flex;
  flex-direction: column;
  gap: 14px;
  min-height: 100%;
  padding: 4px 2px 24px;
}

.project-setting__card {
  padding: 16px 18px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 10px;
  background: var(--el-bg-color);
  box-shadow: 0 1px 2px rgba(15, 23, 42, 0.04);
}

.project-setting__card-head {
  margin-bottom: 14px;

  &--row {
    display: flex;
    align-items: flex-start;
    justify-content: space-between;
    gap: 12px;
  }
}

.project-setting__card-title {
  margin: 0 0 6px;
  font-size: 15px;
  font-weight: 600;
  line-height: 1.35;
  color: var(--el-text-color-primary);
}

.project-setting__card-desc {
  margin: 0;
  font-size: 12px;
  line-height: 1.55;
  color: var(--el-text-color-secondary);

  code {
    padding: 1px 5px;
    border-radius: 4px;
    font-family: ui-monospace, 'Cascadia Code', 'Courier New', monospace;
    font-size: 11px;
    color: var(--el-text-color-regular);
    background: var(--el-fill-color-light);
  }
}

.project-setting__segmented {
  width: 100%;

  :deep(.el-segmented__item) {
    flex: 1;
  }
}

.project-setting__hint {
  margin: 12px 0 0;
  font-size: 12px;
  line-height: 1.5;
  color: var(--el-text-color-secondary);

  &--warn {
    margin-top: 10px;
    margin-bottom: 0;
    color: var(--el-color-warning-dark-2);
  }
}

.project-setting__token-box {
  padding: 12px 14px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
  background: var(--el-fill-color-lighter);
  overflow-x: auto;
}

.project-setting__token-value {
  display: block;
  font-family: ui-monospace, 'Cascadia Code', 'Courier New', monospace;
  font-size: 12px;
  line-height: 1.5;
  word-break: break-all;
  color: var(--el-text-color-primary);
  white-space: pre-wrap;
}

.project-setting__code-block {
  margin: 0;
  padding: 14px 16px;
  max-height: 240px;
  overflow: auto;
  border-radius: 8px;
  background: #1e293b;
  color: #e2e8f0;
  font-family: ui-monospace, 'Cascadia Code', 'Courier New', monospace;
  font-size: 12px;
  line-height: 1.55;
  tab-size: 2;

  code {
    font-family: inherit;
    white-space: pre;
  }
}

.project-setting__empty-box {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 4px;
  min-height: 72px;
  padding: 16px;
  border: 1px dashed var(--el-border-color);
  border-radius: 8px;
  font-size: 13px;
  color: var(--el-text-color-secondary);
  background: var(--el-fill-color-blank);

  &--compact {
    min-height: 56px;
    padding: 12px;
  }
}

.project-setting__empty-sub {
  font-size: 12px;
  color: var(--el-text-color-placeholder);
}

.project-setting__actions {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-top: 14px;
}

.project-setting__conv-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
}

.project-setting__conv-field {
  display: flex;
  flex-direction: column;
  gap: 6px;

  label {
    font-size: 12px;
    color: var(--el-text-color-secondary);
  }

  &--full {
    grid-column: 1 / -1;
  }
}

.project-setting__auth-toolbar {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 12px;
}

.project-setting__auth-hint {
  margin-bottom: 12px;
}

.project-setting__apis-block {
  margin-top: 12px;
}

.project-setting__apis-label {
  display: block;
  margin-bottom: 8px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.project-setting__apis-table {
  width: 100%;
}

.project-setting__hint--inline {
  margin-top: 10px;
  margin-bottom: 0;
}

.project-setting__template-picker {
  min-height: 120px;
}

.project-setting__auth-collapse {
  border: none;

  :deep(.el-collapse-item__header) {
    height: auto;
    min-height: 40px;
    padding: 0 4px;
    line-height: 1.4;
  }

  :deep(.el-collapse-item__wrap) {
    border-bottom: none;
  }

  :deep(.el-collapse-item__content) {
    padding: 8px 4px 12px;
  }
}

.project-setting__auth-collapse-title {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 13px;
  font-weight: 500;
}

.project-setting__auth-remove {
  margin-right: 8px;
}

.project-setting__preview-block {
  margin: 14px 0 0;
  padding: 12px 14px;
  max-height: 160px;
  overflow: auto;
  border-radius: 8px;
  background: var(--el-fill-color-lighter);
  color: var(--el-text-color-regular);
  font-family: ui-monospace, 'Cascadia Code', 'Courier New', monospace;
  font-size: 11px;
  line-height: 1.5;

  code {
    font-family: inherit;
    white-space: pre;
  }
}

.project-setting__btn-icon {
  margin-right: 4px;
}

.project-setting__no-access {
  padding: 32px 0;
}
</style>

<style lang="scss">
.project-setting-drawer {
  .el-drawer__header {
    margin-bottom: 0;
    padding: 18px 20px 14px;
    border-bottom: 1px solid var(--el-border-color-lighter);
  }

  .el-drawer__title {
    font-size: 16px;
    font-weight: 600;
    color: var(--el-text-color-primary);
  }

  .el-drawer__body {
    padding: 16px 18px 20px;
    background: var(--el-fill-color-blank);
  }
}
</style>

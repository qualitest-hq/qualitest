<template>
  <div :class="{ 'is-embed-design': embedInDesign }" class="api-debug-workbench">
    <div class="debug-url-bar">
      <el-select
          v-model="draftRequestConfig.method"
          :class="getApiHttpMethodBadgeClass(draftRequestConfig.method)"
          class="debug-method-select"
          placeholder="方法"
      >
        <el-option v-for="m in HTTP_METHODS" :key="m" :label="m" :value="m"/>
      </el-select>
      <el-input v-model="draftApiPath" class="debug-url-input" clearable placeholder="路径，如 /api/foo"/>
      <el-button
          v-if="!embedInDesign"
          :loading="debugSending"
          class="debug-send-btn"
          type="primary"
          @click="handleDebugSend"
      >发送
      </el-button>
      <el-button
          v-if="!embedInDesign"
          v-hasPermi="['project:testProject:edit']"
          :loading="apiDebugSaving"
          class="debug-save-btn"
          @click="handleSaveApiDebug"
      >保存
      </el-button>
    </div>

    <div class="debug-request-response">
      <div class="debug-request-pane">
        <div class="debug-inner-tab-shell">
          <nav aria-label="请求配置" class="debug-inner-tablist" role="tablist">
            <button
                :aria-selected="activeDebugRequestTab === 'headers'"
                :class="{ active: activeDebugRequestTab === 'headers' }"
                class="debug-inner-tab"
                role="tab"
                type="button"
                @click="activeDebugRequestTab = 'headers'"
            >
              Headers
            </button>
            <button
                :aria-selected="activeDebugRequestTab === 'query'"
                :class="{ active: activeDebugRequestTab === 'query' }"
                class="debug-inner-tab"
                role="tab"
                type="button"
                @click="activeDebugRequestTab = 'query'"
            >
              <span>Query</span>
              <span v-if="debugQueryTabBadge > 0" class="debug-tab-count">{{ debugQueryTabBadge }}</span>
            </button>
            <button
                :aria-selected="activeDebugRequestTab === 'body'"
                :class="{ active: activeDebugRequestTab === 'body' }"
                class="debug-inner-tab"
                role="tab"
                type="button"
                @click="activeDebugRequestTab = 'body'"
            >
              <span>Body</span>
              <span v-if="debugBodyTabBadge > 0" class="debug-tab-count">{{ debugBodyTabBadge }}</span>
            </button>
            <button
                :aria-selected="activeDebugRequestTab === 'path'"
                :class="{ active: activeDebugRequestTab === 'path' }"
                class="debug-inner-tab"
                role="tab"
                type="button"
                @click="activeDebugRequestTab = 'path'"
            >
              <span>Path</span>
              <span v-if="debugPathTabBadge > 0" class="debug-tab-count">{{ debugPathTabBadge }}</span>
            </button>
            <button
                :aria-selected="activeDebugRequestTab === 'cookies'"
                :class="{ active: activeDebugRequestTab === 'cookies' }"
                class="debug-inner-tab"
                role="tab"
                type="button"
                @click="activeDebugRequestTab = 'cookies'"
            >
              Cookies
            </button>
            <button
                :aria-selected="activeDebugRequestTab === 'preScript'"
                :class="{ active: activeDebugRequestTab === 'preScript' }"
                class="debug-inner-tab"
                role="tab"
                type="button"
                @click="activeDebugRequestTab = 'preScript'"
            >
              前置脚本
            </button>
            <button
                :aria-selected="activeDebugRequestTab === 'postScript'"
                :class="{ active: activeDebugRequestTab === 'postScript' }"
                class="debug-inner-tab"
                role="tab"
                type="button"
                @click="activeDebugRequestTab = 'postScript'"
            >
              后置脚本
            </button>
          </nav>

          <div
              class="debug-inner-panels"
              :class="{
                'debug-inner-panels--script':
                  activeDebugRequestTab === 'preScript' || activeDebugRequestTab === 'postScript',
              }"
          >
            <!-- v-if：仅挂载当前子 Tab，避免 Headers/Query/Body/Path 等同时存在大量 DOM（设计内嵌时尤为明显） -->
            <div v-if="activeDebugRequestTab === 'headers'" class="debug-inner-panel" role="tabpanel">
              <DebugKvSheet
                  :rows="draftHeaderRows"
                  :virtual-min-rows="embedInDesign ? 28 : 52"
                  name-label="名称"
                  name-placeholder="Header 名"
                  value-label="值"
                  value-placeholder="值"
                  @remove="(i) => removeRow(draftHeaderRows, i)"
              />
            </div>

            <div v-if="activeDebugRequestTab === 'query'" class="debug-inner-panel" role="tabpanel">
              <DebugKvSheet
                  :rows="draftRequestConfig.queryParams"
                  :show-description-column="true"
                  :show-example-column="false"
                  :show-type-column="true"
                  :virtual-min-rows="embedInDesign ? 28 : 52"
                  description-placeholder="说明"
                  name-label="参数名"
                  name-placeholder="名称"
                  value-label="参数值"
                  value-placeholder="值"
                  @remove="(i) => removeRow(draftRequestConfig.queryParams, i)"
              >
                <template #type="{ row }">
                    <DebugParamTypeCell
                        :allow-type-create="false"
                        :row="row"
                        :type-class-fn="paramTypeSelectClass"
                        :type-options="PARAM_TYPES_QUERY_PATH"
                        @open-schema="openParamSchemaDialog(row, 'query')"
                        @toggle-required="toggleDebugParamRequired(row)"
                        @type-change="onKvParamTypeChange(row)"
                    />
                  </template>
                </DebugKvSheet>
            </div>

            <div v-if="activeDebugRequestTab === 'body'" class="debug-inner-panel debug-inner-panel--body" role="tabpanel">
              <div class="body-mode-row">
                <el-radio-group v-model="draftRequestConfig.body.mode" class="body-mode-group" size="small">
                  <el-radio-button v-for="opt in BODY_MODES" :key="opt.value" :value="opt.value">{{ opt.label }}</el-radio-button>
                </el-radio-group>
              </div>

              <template v-if="draftRequestConfig.body.mode === 'form-data'">
                <DebugKvSheet
                    :enable-file-upload-for-file-type="true"
                    :rows="draftRequestConfig.body.formData"
                    :show-description-column="true"
                    :show-example-column="false"
                    :show-type-column="true"
                    :virtual-min-rows="embedInDesign ? 28 : 52"
                    description-placeholder="说明"
                    name-label="参数名"
                    name-placeholder="名称"
                    value-label="参数值"
                    value-placeholder="值"
                    @remove="(i) => removeRow(draftRequestConfig.body.formData, i)"
                >
                  <template #type="{ row }">
                    <DebugParamTypeCell
                        :row="row"
                        :type-class-fn="paramTypeSelectClass"
                        :type-options="PARAM_TYPES_FORM_DATA"
                        @open-schema="openParamSchemaDialog(row, 'formData')"
                        @toggle-required="toggleDebugParamRequired(row)"
                        @type-change="onKvParamTypeChange(row)"
                    />
                  </template>
                </DebugKvSheet>
              </template>

              <template v-else-if="draftRequestConfig.body.mode === 'x-www-form-urlencoded'">
                <DebugKvSheet
                    :rows="draftRequestConfig.body.urlencoded"
                    :show-description-column="true"
                    :show-example-column="false"
                    :show-type-column="true"
                    :virtual-min-rows="embedInDesign ? 28 : 52"
                    description-placeholder="说明"
                    name-label="参数名"
                    name-placeholder="名称"
                    value-label="参数值"
                    value-placeholder="值"
                    @remove="(i) => removeRow(draftRequestConfig.body.urlencoded, i)"
                >
                  <template #type="{ row }">
                    <DebugParamTypeCell
                        :allow-type-create="false"
                        :row="row"
                        :type-class-fn="paramTypeSelectClass"
                        :type-options="PARAM_TYPES_URLENCODED"
                        @open-schema="openParamSchemaDialog(row, 'urlencoded')"
                        @toggle-required="toggleDebugParamRequired(row)"
                        @type-change="onKvParamTypeChange(row)"
                    />
                  </template>
                </DebugKvSheet>
              </template>

              <template v-else-if="draftRequestConfig.body.mode === 'json'">
                <div class="body-json-json-shell">
                  <el-tabs v-model="activeBodyJsonSubTab" class="body-json-inner-tabs">
                    <el-tab-pane label="数据结构" lazy name="schema">
                      <BodyJsonSchemaTree
                          ref="bodyJsonSchemaTreeRef"
                          compact
                          :show-example-column="false"
                          :virtual-min-flat-rows="embedInDesign ? 32 : 44"
                          v-model="draftRequestConfig.body.json.schema"
                          @open-schema="onBodyJsonOpenSchema"
                      />
                    </el-tab-pane>
                    <el-tab-pane label="请求示例" lazy name="raw">
                      <el-input
                          v-model="bodyJsonText"
                          :rows="14"
                          class="debug-body-raw"
                          placeholder="JSON"
                          type="textarea"
                      />
                    </el-tab-pane>
                  </el-tabs>
                </div>
              </template>

              <template v-else-if="draftRequestConfig.body.mode === 'xml' || draftRequestConfig.body.mode === 'text'">
                <el-input
                    v-model="draftRequestConfig.body.text"
                    :placeholder="draftRequestConfig.body.mode === 'xml' ? 'XML' : '文本'"
                    :rows="14"
                    class="debug-body-raw"
                    type="textarea"
                />
              </template>

              <template v-else-if="draftRequestConfig.body.mode === 'binary'">
                <div class="debug-binary-body">
                  <div class="debug-binary-simple">
                    <input
                        ref="binaryFileInputRef"
                        class="debug-binary-file-native"
                        tabindex="-1"
                        type="file"
                        @change="onBinaryFileInputChange"
                    />
                    <button class="debug-binary-upload-btn" type="button" @click="triggerBinaryFilePick">
                      <el-icon class="debug-binary-upload-btn-ico">
                        <Upload/>
                      </el-icon>
                      <span>上传</span>
                    </button>
                    <p :title="binaryFileLabel" class="debug-binary-file-line">
                      <span class="debug-binary-file-line-text">{{ binaryFileLabel }}</span>
                      <button
                          v-if="binaryBodyFile"
                          class="debug-binary-file-line-clear"
                          type="button"
                          @click="clearBinaryBodyFile"
                      >
                        清除
                      </button>
                    </p>
                  </div>
                </div>
              </template>

              <template v-else>
                <div class="debug-body-placeholder">
                  <p class="debug-body-placeholder-title">Body 为 none</p>
                  <p class="debug-body-placeholder-desc">无需请求体或在上方的 mode 中选择类型</p>
                </div>
              </template>
            </div>

            <div v-if="activeDebugRequestTab === 'path'" class="debug-inner-panel" role="tabpanel">
              <DebugKvSheet
                  :rows="draftRequestConfig.pathParams"
                  :show-description-column="true"
                  :show-example-column="false"
                  :show-type-column="true"
                  :virtual-min-rows="embedInDesign ? 28 : 52"
                  description-placeholder="说明"
                  name-label="参数名"
                  name-placeholder="名称"
                  value-label="参数值"
                  value-placeholder="值"
                  @remove="(i) => removeRow(draftRequestConfig.pathParams, i)"
              >
                <template #type="{ row }">
                  <DebugParamTypeCell
                      :allow-type-create="false"
                      :row="row"
                      :type-class-fn="paramTypeSelectClass"
                      :type-options="PARAM_TYPES_QUERY_PATH"
                      @open-schema="openParamSchemaDialog(row, 'path')"
                      @toggle-required="toggleDebugParamRequired(row)"
                      @type-change="onKvParamTypeChange(row)"
                  />
                </template>
              </DebugKvSheet>
            </div>

            <div v-if="activeDebugRequestTab === 'cookies'" class="debug-inner-panel" role="tabpanel">
              <DebugKvSheet
                  :rows="draftCookieRows"
                  :virtual-min-rows="embedInDesign ? 28 : 52"
                  name-label="名称"
                  name-placeholder="Cookie 名"
                  value-label="值"
                  value-placeholder="值"
                  @remove="(i) => removeRow(draftCookieRows, i)"
              />
            </div>

            <div
                v-if="activeDebugRequestTab === 'preScript'"
                class="debug-inner-panel debug-inner-panel--script"
                role="tabpanel"
            >
              <ApiScriptWorkbench
                  v-model="draftPreRequestScript"
                  phase="pre"
              />
            </div>
            <div
                v-if="activeDebugRequestTab === 'postScript'"
                class="debug-inner-panel debug-inner-panel--script"
                role="tabpanel"
            >
              <ApiScriptWorkbench
                  v-model="draftPostRequestScript"
                  phase="post"
              />
            </div>
          </div>
        </div>
      </div>

      <div v-if="!embedInDesign" class="debug-response-pane">
        <div class="debug-response-head">
          <span class="debug-response-title">返回响应</span>
          <div class="debug-response-meta">
            <template v-if="debugResponse.sent && debugResponse.status != null">
              <span :class="{ 'is-ok': debugResponse.ok, 'is-err': !debugResponse.ok }" class="debug-status-pill">
                {{ debugResponse.status }} {{ debugResponse.statusText }}
              </span>
              <span v-if="debugResponse.durationMs != null" class="debug-duration">{{ debugResponse.durationMs }} ms</span>
            </template>
          </div>
        </div>
        <div v-if="!debugResponse.sent" class="debug-response-empty">
          <div class="debug-response-empty-inner">
            <div class="debug-response-empty-icon" aria-hidden="true">
              <svg fill="none" height="56" viewBox="0 0 72 56" width="72" xmlns="http://www.w3.org/2000/svg">
                <path
                    d="M8 16c0-4.418 3.582-8 8-8h40c4.418 0 8 3.582 8 8v28H8V16z"
                    stroke="currentColor"
                    stroke-width="2"
                />
                <path d="M20 36h32M20 44h20" stroke="currentColor" stroke-linecap="round" stroke-width="2"/>
              </svg>
            </div>
            <p class="debug-response-empty-title">尚未发送请求</p>
            <p class="debug-response-empty-desc">点击上方「发送」查看响应内容与耗时</p>
          </div>
        </div>
        <div v-else class="debug-response-body">
          <el-alert
              v-if="debugResponse.error"
              :closable="false"
              :title="debugResponse.error"
              class="debug-error-alert"
              show-icon
              type="error"
          />
          <el-alert
              v-if="debugResponse.corsHint"
              :closable="false"
              :title="debugResponse.corsHint"
              class="debug-cors-hint-alert"
              show-icon
              type="info"
          />
          <el-alert
              v-if="debugResponse.preScriptError"
              :closable="false"
              :title="'前置脚本：' + debugResponse.preScriptError"
              class="debug-error-alert"
              show-icon
              type="error"
          />
          <el-alert
              v-if="debugResponse.postScriptError"
              :closable="false"
              :title="'后置脚本：' + debugResponse.postScriptError"
              class="debug-error-alert"
              show-icon
              type="error"
          />
          <template v-if="debugResponse.sent && debugResponse.status != null">
            <nav class="debug-resp-tablist" role="tablist">
              <button
                  :class="{ active: activeRespTab === 'body' }"
                  class="debug-resp-tab"
                  type="button"
                  @click="activeRespTab = 'body'"
              >
                Body
              </button>
              <button
                  :class="{ active: activeRespTab === 'headers' }"
                  class="debug-resp-tab"
                  type="button"
                  @click="activeRespTab = 'headers'"
              >
                Headers
              </button>
              <button
                  :class="{ active: activeRespTab === 'tests' }"
                  class="debug-resp-tab"
                  type="button"
                  @click="activeRespTab = 'tests'"
              >
                Tests
                <span v-if="debugScriptTestsBadge > 0" class="debug-tab-count">{{ debugScriptTestsBadge }}</span>
              </button>
            </nav>
            <div v-show="activeRespTab === 'body'" class="debug-resp-panel">
              <!-- Body 上方：自动识别图/音视频/PDF 并预览，下方文本保留 -->
              <ResponseMediaPreview
                  :body-base64="debugResponse.bodyBase64"
                  :body-encoding="debugResponse.bodyEncoding"
                  :body-text="debugResponse.bodyText"
                  :headers="debugResponse.headers"
              />
              <el-input
                  :model-value="debugResponse.bodyText"
                  :rows="12"
                  class="debug-resp-textarea"
                  readonly
                  type="textarea"
              />
            </div>
            <div v-show="activeRespTab === 'headers'" class="debug-resp-panel">
              <el-input
                  :model-value="formatResponseHeaders(debugResponse.headers)"
                  :rows="12"
                  class="debug-resp-textarea"
                  readonly
                  type="textarea"
              />
            </div>
            <div v-show="activeRespTab === 'tests'" class="debug-resp-panel debug-resp-panel--tests">
              <div v-if="!(debugResponse.scriptTests || []).length && !(debugResponse.scriptLogs || []).length" class="debug-script-tests-empty">
                暂无脚本测试结果
              </div>
              <ul v-if="(debugResponse.scriptTests || []).length" class="debug-script-test-list">
                <li
                    v-for="(item, idx) in debugResponse.scriptTests"
                    :key="idx"
                    :class="{ 'is-pass': item.passed, 'is-fail': item.passed === false }"
                    class="debug-script-test-item"
                >
                  <span class="debug-script-test-name">{{ item.name || ('测试 ' + (idx + 1)) }}</span>
                  <span class="debug-script-test-status">{{ item.passed ? '通过' : '失败' }}</span>
                  <span v-if="item.message" class="debug-script-test-msg">{{ item.message }}</span>
                </li>
              </ul>
              <el-input
                  v-if="(debugResponse.scriptLogs || []).length"
                  :model-value="(debugResponse.scriptLogs || []).join('\n')"
                  :rows="8"
                  class="debug-resp-textarea debug-script-log-textarea"
                  readonly
                  type="textarea"
              />
            </div>
          </template>
        </div>
      </div>
    </div>

  <el-dialog
      v-model="paramSchemaDialogVisible"
      append-to-body
      class="param-schema-dialog"
      destroy-on-close
      title="参数类型"
      width="520px"
      @closed="onParamSchemaDialogClosed"
  >
    <div v-if="paramSchemaRow" class="param-schema-dialog-inner">
      <div class="param-schema-body">
        <div class="param-schema-main-type-row">
          <el-select
              v-model="paramSchemaRow.type"
              :allow-create="paramSchemaSource === 'formData'"
              :class="paramTypeSelectClass(paramSchemaRow.type)"
              class="param-schema-type-select"
              default-first-option
              filterable
              placeholder="类型"
              size="small"
          >
            <el-option v-for="t in paramSchemaTypeOptions" :key="t" :label="t" :value="t"/>
          </el-select>
        </div>
        <div class="param-schema-switches">
          <el-switch v-model="paramSchemaRow.required" size="small"/>
          <span class="lbl">必需</span>
          <el-switch v-model="paramSchemaRow.nullable" size="small"/>
          <span class="lbl">允许 NULL</span>
          <el-switch v-model="paramSchemaRow.deprecated" size="small"/>
          <span class="lbl">废弃</span>
        </div>
        <el-form class="param-schema-form" label-position="top" size="small">
          <el-row v-if="paramSchemaFormatVisible" :gutter="8">
            <el-col :span="12">
              <el-form-item label="format">
                <el-input v-model="paramSchemaRow.format" clearable placeholder="如 date、uuid"/>
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item>
                <template #label>
                  <span>行为</span>
                  <el-tooltip content="文档中的读写语义" placement="top">
                    <el-icon class="param-schema-help">
                      <QuestionFilled/>
                    </el-icon>
                  </el-tooltip>
                </template>
                <el-select v-model="paramSchemaRow.behavior" class="param-schema-w100">
                  <el-option
                      v-for="o in PARAM_SCHEMA_BEHAVIOR"
                      :key="o.value"
                      :label="o.label"
                      :value="o.value"
                  />
                </el-select>
              </el-form-item>
            </el-col>
          </el-row>
          <el-row v-else :gutter="8">
            <el-col :span="24">
              <el-form-item>
                <template #label>
                  <span>行为</span>
                  <el-tooltip content="文档中的读写语义" placement="top">
                    <el-icon class="param-schema-help">
                      <QuestionFilled/>
                    </el-icon>
                  </el-tooltip>
                </template>
                <el-select v-model="paramSchemaRow.behavior" class="param-schema-w100">
                  <el-option
                      v-for="o in PARAM_SCHEMA_BEHAVIOR"
                      :key="o.value"
                      :label="o.label"
                      :value="o.value"
                  />
                </el-select>
              </el-form-item>
            </el-col>
          </el-row>
          <template v-if="paramSchemaStringConstraints">
            <el-row :gutter="8">
              <el-col :span="12">
                <el-form-item label="最小长度">
                  <el-input-number
                      v-model="paramSchemaRow.minLength"
                      :controls="false"
                      :min="0"
                      class="param-schema-w100"
                      placeholder=">= 0"
                  />
                </el-form-item>
              </el-col>
              <el-col :span="12">
                <el-form-item label="最大长度">
                  <el-input-number
                      v-model="paramSchemaRow.maxLength"
                      :controls="false"
                      :min="0"
                      class="param-schema-w100"
                      placeholder=">= 0"
                  />
                </el-form-item>
              </el-col>
            </el-row>
          </template>
          <template v-else-if="paramSchemaNumberConstraints">
            <el-row :gutter="8">
              <el-col :span="12">
                <el-form-item label="最小值">
                  <el-input-number v-model="paramSchemaRow.minValue" :controls="false" class="param-schema-w100"/>
                </el-form-item>
              </el-col>
              <el-col :span="12">
                <el-form-item label="最大值">
                  <el-input-number v-model="paramSchemaRow.maxValue" :controls="false" class="param-schema-w100"/>
                </el-form-item>
              </el-col>
            </el-row>
            <template v-if="paramSchemaAdvancedNumberConstraints">
              <div class="param-schema-advanced-label">高级区间</div>
              <el-row :gutter="8">
                <el-col :span="12">
                  <el-form-item label="exclusiveMinimum">
                    <el-input-number
                        v-model="paramSchemaRow.exclusiveMinimum"
                        :controls="false"
                        class="param-schema-w100"
                    />
                  </el-form-item>
                </el-col>
                <el-col :span="12">
                  <el-form-item label="exclusiveMaximum">
                    <el-input-number
                        v-model="paramSchemaRow.exclusiveMaximum"
                        :controls="false"
                        class="param-schema-w100"
                    />
                  </el-form-item>
                </el-col>
              </el-row>
              <el-row :gutter="8">
                <el-col :span="12">
                  <el-form-item label="multipleOf">
                    <el-input-number v-model="paramSchemaRow.multipleOf" :controls="false" class="param-schema-w100"/>
                  </el-form-item>
                </el-col>
              </el-row>
            </template>
          </template>
          <template v-else-if="paramSchemaArrayConstraints">
            <el-row :gutter="8">
              <el-col :span="12">
                <el-form-item label="最小元素数">
                  <el-input-number
                      v-model="paramSchemaRow.minItems"
                      :controls="false"
                      :min="0"
                      class="param-schema-w100"
                      placeholder=">= 0"
                  />
                </el-form-item>
              </el-col>
              <el-col :span="12">
                <el-form-item label="最大元素数">
                  <el-input-number
                      v-model="paramSchemaRow.maxItems"
                      :controls="false"
                      :min="0"
                      class="param-schema-w100"
                      placeholder=">= 0"
                  />
                </el-form-item>
              </el-col>
            </el-row>
          </template>
          <el-row v-if="!paramSchemaArrayConstraints" :gutter="8">
            <el-col :span="paramSchemaStringConstraints ? 12 : 24">
              <el-form-item label="默认值">
                <el-input v-model="paramSchemaRow.defaultValue" clearable/>
              </el-form-item>
            </el-col>
            <el-col v-if="paramSchemaStringConstraints" :span="12">
              <el-form-item>
                <template #label>
                  <span>pattern</span>
                  <el-tooltip content="正则约束（常用于 string）" placement="top">
                    <el-icon class="param-schema-help">
                      <QuestionFilled/>
                    </el-icon>
                  </el-tooltip>
                </template>
                <el-input v-model="paramSchemaRow.pattern" clearable placeholder="^[A-Za-z0-9_-]+"/>
              </el-form-item>
            </el-col>
          </el-row>
        </el-form>
      </div>
    </div>
  </el-dialog>
  </div>
</template>

<script setup>
import {updateTestProjectApi} from '@/api/project/testProjectApi'
import {QuestionFilled, Upload} from '@element-plus/icons-vue'
import BodyJsonSchemaTree from './BodyJsonSchemaTree.vue'
import DebugKvSheet from './DebugKvSheet.vue'
import DebugParamTypeCell from './DebugParamTypeCell.vue'
import ApiScriptWorkbench from '@/components/script/ApiScriptWorkbench.vue'
import ResponseMediaPreview from '@/components/ResponseMediaPreview/index.vue'
import {
  SCHEMA_JSON_BODY_TYPES,
  buildExampleFromSchemaDefaults,
  mergeJsonFillEmpty,
  mergeJsonPreferFiller,
  sanitizeBodyJsonSchemaForPersist
} from '@/views/project/testProject/utils/jsonSchemaTree'
import {
  bodyJsonSchemaNodeToParamRow,
  paramRowMergeIntoBodyJsonNode,
  paramRowMergeIntoFlatParamRow
} from '@/views/project/testProject/utils/bodyJsonSchemaParamBridge'
import {
  pruneConstraintsForType,
  sanitizeParamRowForPersist,
  supportsFormat
} from '@/views/project/testProject/utils/fieldTypeConstraints'
import {useApiDebugTrailingEmptyRows} from '@/views/project/testProject/composables/useApiDebugTrailingEmptyRows'
import {
  readAllDebugUiPrefs,
  VALID_REQUEST_TABS_WITH_SCRIPTS,
  VALID_RESP_TABS,
  writeDebugUiPrefs
} from '@/views/project/testProject/utils/apiDebugUiPrefs'
import {
  buildRequestWorkbenchStateFromDetail,
  coerceQueryPathRowTypes,
  coerceUrlencodedRowTypes,
  defaultDraftRequestConfig,
  emptyKVRow,
  ensureTrailingEmptyRow,
} from '@/views/project/testProject/utils/apiDetailRequestWorkbench'
import { REQUEST_CONFIG_VERSION } from '@/views/project/testProject/utils/apiConfigConstants'
import { peelTestValuesFromStructure } from '@/views/project/testProject/utils/peelTestValueConfig'
import { HTTP_METHODS, getApiHttpMethodBadgeClass } from '@/views/project/testProject/utils/httpMethodMeta'
import {executeDebugRequest} from '@/transport/debugTransport'
import {useApiDebugScript, runPostScript, runPreScript} from '@/views/project/testProject/composables/useApiDebugScript'
import {
  ensureHttpSchemeForRequest,
  resolveEnvBaseUrlForRequest
} from '@/views/project/testProject/utils/envConfigUtils'

const {proxy} = getCurrentInstance()

const props = defineProps({
  apiDetail: {type: Object, default: null},
  envList: {type: Array, default: () => []},
  testProjectEnvId: {type: [Number, String], default: null},
  /** 项目设置中的调试发送模式（Web）；桌面端由主进程固定，忽略此项 */
  httpTransportMode: {type: String, default: 'browser'},
  /** 嵌入「设计」页：仅请求配置，无发送/响应区/保存按钮 */
  embedInDesign: {type: Boolean, default: false}
})

const emit = defineEmits(['saved'])

const {scriptState, buildEnvironmentMap, applyServerState} = useApiDebugScript(() => ({
  testProjectId: props.apiDetail?.testProjectId,
  testProjectEnvId: props.testProjectEnvId,
  envList: props.envList
}))

/** 行是否算「有值」（启用且有名称） */
function namedEnabledRowCount(rows) {
  return (rows || []).filter((r) => r?._enabled !== false && String(r?.name || '').trim()).length
}

/**
 * 默认请求页签：优先有内容的 Tab。
 * 顺序 body → query → path → cookies → headers → 前后置脚本；全空则 headers。
 */
function pickDefaultRequestTab() {
  const body = draftRequestConfig.value?.body
  const bodyMode = String(body?.mode || 'none')
  const bodyHasContent =
      (bodyMode !== 'none' && bodyMode !== '') ||
      namedEnabledRowCount(body?.formData) > 0 ||
      namedEnabledRowCount(body?.urlencoded) > 0 ||
      String(bodyJsonText.value || '').trim().length > 0 ||
      String(body?.text || '').trim().length > 0 ||
      binaryBodyFile.value instanceof File
  if (bodyHasContent) return 'body'
  if (namedEnabledRowCount(draftRequestConfig.value?.queryParams) > 0) return 'query'
  if (namedEnabledRowCount(draftRequestConfig.value?.pathParams) > 0) return 'path'
  if (namedEnabledRowCount(draftCookieRows.value) > 0) return 'cookies'
  if (namedEnabledRowCount(draftHeaderRows.value) > 0) return 'headers'
  if (String(draftPreRequestScript.value || '').trim()) return 'preScript'
  if (String(draftPostRequestScript.value || '').trim()) return 'postScript'
  return 'headers'
}

function applySavedDebugUiTabs(detail) {
  const apiId = detail?.testProjectApiId
  const projectId = detail?.testProjectId
  if (apiId == null || projectId == null) {
    activeDebugRequestTab.value = pickDefaultRequestTab()
    activeRespTab.value = 'body'
    return
  }
  const all = readAllDebugUiPrefs(projectId)
  const saved = all[String(apiId)]
  const rt = saved?.requestTab
  const rst = saved?.respTab
  const allowReq = VALID_REQUEST_TABS_WITH_SCRIPTS
  activeDebugRequestTab.value = rt && allowReq.includes(rt) ? rt : pickDefaultRequestTab()
  activeRespTab.value =
      rst && VALID_RESP_TABS.includes(rst) ? rst : 'body'
}

let persistDebugUiTimer = null

function persistCurrentDebugUiTabs() {
  const d = props.apiDetail
  if (!d?.testProjectApiId || d.testProjectId == null) return
  const all = readAllDebugUiPrefs(d.testProjectId)
  all[String(d.testProjectApiId)] = {
    requestTab: activeDebugRequestTab.value,
    respTab: activeRespTab.value
  }
  writeDebugUiPrefs(d.testProjectId, all)
}

function schedulePersistDebugUiTabs() {
  if (persistDebugUiTimer != null) clearTimeout(persistDebugUiTimer)
  persistDebugUiTimer = setTimeout(() => {
    persistDebugUiTimer = null
    persistCurrentDebugUiTabs()
  }, 50)
}

const activeDebugRequestTab = ref('headers')
const activeRespTab = ref('body')
const draftApiPath = ref('')
const draftRequestConfig = ref(defaultDraftRequestConfig())
const draftHeaderRows = ref([emptyKVRow()])
const draftCookieRows = ref([emptyKVRow()])
const bodyJsonText = ref('')
/** binary 模式：本地文件仅用于调试发送，不入库 */
const binaryBodyFile = ref(null)
const binaryFileInputRef = ref(null)
/** Body JSON 子页：数据结构 | 原始 JSON 示例 */
const activeBodyJsonSubTab = ref('schema')
const draftPreRequestScript = ref('')
const draftPostRequestScript = ref('')
const debugResponse = ref(createEmptyDebugResponse())
const debugSending = ref(false)
const apiDebugSaving = ref(false)

const paramSchemaDialogVisible = ref(false)
const paramSchemaRow = ref(null)
/** 类型弹窗：urlencoded 不包含 file，file 仅保留在 form-data */
const paramSchemaSource = ref('default')
/** KV 行弹窗关闭时写回目标（非 body JSON 树节点） */
const paramSchemaKvTarget = ref(null)

const bodyJsonSchemaTreeRef = ref(null)
/** Body JSON 树标量节点 → 弹窗关闭后写回 */
const bodyJsonSchemaEditTarget = ref(null)

const paramSchemaTypeOptions = computed(() => {
  switch (paramSchemaSource.value) {
    case 'urlencoded':
      return PARAM_TYPES_URLENCODED
    case 'formData':
      return PARAM_TYPES_FORM_DATA
    case 'bodyJson':
      return SCHEMA_JSON_BODY_TYPES
    case 'query':
    case 'path':
      return PARAM_TYPES_QUERY_PATH
    default:
      return PARAM_TYPES_QUERY_PATH
  }
})

const paramSchemaStringConstraints = computed(() => {
  const r = paramSchemaRow.value
  if (!r) return false
  const t = String(r.type || '').toLowerCase()
  return t === 'string' || t === 'file' || t === 'any'
})

const paramSchemaNumberConstraints = computed(() => {
  const r = paramSchemaRow.value
  if (!r) return false
  const t = String(r.type || '').toLowerCase()
  return t === 'integer' || t === 'number'
})

const paramSchemaArrayConstraints = computed(() => {
  const r = paramSchemaRow.value
  if (!r) return false
  return String(r.type || '').toLowerCase() === 'array'
})

const paramSchemaFormatVisible = computed(() => {
  const r = paramSchemaRow.value
  if (!r) return false
  return supportsFormat(r.type)
})

/** body JSON schema 节点才展示数值高级项（flat KV 用 minValue/maxValue 即可） */
const paramSchemaAdvancedNumberConstraints = computed(() => {
  return paramSchemaNumberConstraints.value && paramSchemaSource.value === 'bodyJson'
})

const BODY_MODES = [
  {label: 'none', value: 'none'},
  {label: 'form-data', value: 'form-data'},
  {label: 'x-www-form-urlencoded', value: 'x-www-form-urlencoded'},
  {label: 'JSON', value: 'json'},
  {label: 'XML', value: 'xml'},
  {label: 'text', value: 'text'},
  {label: 'binary', value: 'binary'}
]

/** Query / Path：仅 URL 友好标量 */
const PARAM_TYPES_QUERY_PATH = ['string', 'integer', 'number', 'boolean']

/** x-www-form-urlencoded：无 file、无 object/array/any/null */
const PARAM_TYPES_URLENCODED = ['string', 'integer', 'number', 'boolean']

/** form-data：含 file 及结构化类型 */
const PARAM_TYPES_FORM_DATA = [
  'string',
  'integer',
  'number',
  'boolean',
  'array',
  'file',
  'object',
  'any',
  'null'
]

const DISALLOWED_URLENC_TYPES = ['file', 'object', 'array', 'any', 'null']

const DISALLOWED_QUERY_PATH_TYPES = ['object', 'array', 'any', 'null']

const PARAM_SCHEMA_BEHAVIOR = [
  {label: 'Read/Write', value: 'readWrite'},
  {label: 'Read only', value: 'readOnly'},
  {label: 'Write only', value: 'writeOnly'}
]

function paramTypeSelectClass(type) {
  const raw = type == null || type === '' ? 'string' : String(type)
  const safe = raw.toLowerCase().replace(/[^a-z0-9]/g, '') || 'custom'
  return ['param-type-select', `is-type-${safe}`]
}

const binaryFileLabel = computed(() => {
  const f = binaryBodyFile.value
  if (f instanceof File) return f.name
  return '未选择文件'
})

function triggerBinaryFilePick() {
  binaryFileInputRef.value?.click()
}

function onBinaryFileInputChange(e) {
  const input = e.target
  const f = input?.files?.[0]
  if (!f) return
  binaryBodyFile.value = f
  input.value = ''
}

function clearBinaryBodyFile() {
  binaryBodyFile.value = null
}

function syncBodyJsonTextFromDraft() {
  const ex = draftRequestConfig.value.body?.json?.example
  if (ex == null || ex === '') {
    bodyJsonText.value = ''
  } else if (typeof ex === 'string') {
    bodyJsonText.value = ex
  } else {
    try {
      bodyJsonText.value = JSON.stringify(ex, null, 2)
    } catch {
      bodyJsonText.value = String(ex)
    }
  }
}

/** 避免 schema→example 回写与用户编辑原始 JSON 互相打断 */
let syncingBodyJsonFromSchema = false

function parseBodyJsonTextOrEmpty() {
  const t = bodyJsonText.value.trim()
  if (!t) return {}
  try {
    const parsed = JSON.parse(t)
    return parsed && typeof parsed === 'object' && !Array.isArray(parsed) ? parsed : {}
  } catch {
    return null
  }
}

function ensureBodyJsonContainer() {
  const body = draftRequestConfig.value.body
  if (!body) return null
  if (!body.json || typeof body.json !== 'object') {
    body.json = {schema: null, example: null}
  }
  return body.json
}

/** 回写「请求示例」文本与 body.json.example（带同步锁，避免与 watch 互打断） */
function writeBodyJsonExample(value) {
  const json = ensureBodyJsonContainer()
  if (!json) return
  syncingBodyJsonFromSchema = true
  try {
    bodyJsonText.value = JSON.stringify(value, null, 2)
    json.example = value
  } finally {
    syncingBodyJsonFromSchema = false
  }
}

/**
 * 将「数据结构」里的 default（参数值）合并进「请求示例」。
 * @param {'preferSchema'|'fillEmpty'} mode preferSchema=编辑后回写；fillEmpty=补空串
 * @param {object} [fillerOverride] 若传入则优先用（发送时直接取自树，避免 v-model 未写回）
 */
function mergeSchemaDefaultsIntoBodyJson(mode, fillerOverride) {
  if (draftRequestConfig.value.body?.mode !== 'json') return
  if (!ensureBodyJsonContainer()) return
  const filler =
      fillerOverride !== undefined
          ? fillerOverride
          : buildExampleFromSchemaDefaults(draftRequestConfig.value.body?.json?.schema, {
            onlyExplicitDefaults: true
          })
  if (filler === undefined) return

  const current = parseBodyJsonTextOrEmpty()
  if (current === null) {
    // 原始 JSON 非法：仅在数据结构页时用 schema 覆盖，避免打字中途被冲掉
    if (activeBodyJsonSubTab.value === 'raw' && mode !== 'fillEmpty') return
    writeBodyJsonExample(filler)
    return
  }

  const merged =
      mode === 'preferSchema'
          ? mergeJsonPreferFiller(current, filler)
          : mergeJsonFillEmpty(current, filler)
  try {
    if (JSON.stringify(merged) === JSON.stringify(current) && bodyJsonText.value.trim()) {
      return
    }
  } catch {
    /* ignore */
  }
  writeBodyJsonExample(merged)
}

/** 从当前数据结构树读取显式 default（树未挂载则 undefined） */
function readJsonBodyFillerFromTree() {
  try {
    return bodyJsonSchemaTreeRef.value?.buildDebugExampleFromTree?.()
  } catch {
    return undefined
  }
}

/** flush 树后从 draft schema 取显式 default（不含读树） */
function readJsonBodyFillerFromDraftSchema() {
  try {
    bodyJsonSchemaTreeRef.value?.emitSchema?.()
  } catch {
    /* ignore */
  }
  return buildExampleFromSchemaDefaults(draftRequestConfig.value.body?.json?.schema, {
    onlyExplicitDefaults: true
  })
}

/**
 * 组装 JSON 发送体：
 * - 数据结构树已挂载：以树参数值为准（preferFiller），避免示例里旧非空值盖住刚改的参数值
 * - 仅请求示例页（树未挂载）：示例文本为主，schema default 只补空串
 * - 文本非法时若能从数据结构得到值则仍可发送（并回写示例）
 */
function buildJsonBodyDataForSend() {
  const fromTree = readJsonBodyFillerFromTree()
  const preferTree = fromTree !== undefined
  const filler = preferTree ? fromTree : readJsonBodyFillerFromDraftSchema()

  const current = parseBodyJsonTextOrEmpty()
  const textInvalid = current === null
  if (textInvalid && filler === undefined) {
    return {error: '请求体 JSON 格式无效'}
  }

  const base = textInvalid ? {} : current
  const data =
      filler !== undefined
          ? preferTree
              ? mergeJsonPreferFiller(base, filler)
              : mergeJsonFillEmpty(base, filler)
          : base

  try {
    writeBodyJsonExample(data)
  } catch {
    /* 回写失败不挡发送 */
  }

  return {data}
}

/** 与最近一次 init 入参为同一对象引用时跳过，避免 keep-alive 切回时重复整表重建 */
let lastInitDraftFromDetailRef = null

function initDraftFromApiDetail(detail) {
  resetDebugResponse()
  binaryBodyFile.value = null
  const s = buildRequestWorkbenchStateFromDetail(detail)
  draftApiPath.value = s.draftApiPath
  draftRequestConfig.value = s.draftRequestConfig
  draftHeaderRows.value = s.draftHeaderRows
  draftCookieRows.value = s.draftCookieRows
  draftPreRequestScript.value = s.draftPreRequestScript
  draftPostRequestScript.value = s.draftPostRequestScript
  syncBodyJsonTextFromDraft()
  applySavedDebugUiTabs(detail)
  lastInitDraftFromDetailRef = detail
}

function resetDebugWorkbench() {
  draftApiPath.value = ''
  binaryBodyFile.value = null
  draftRequestConfig.value = defaultDraftRequestConfig()
  draftHeaderRows.value = [emptyKVRow()]
  draftCookieRows.value = [emptyKVRow()]
  bodyJsonText.value = ''
  activeBodyJsonSubTab.value = 'schema'
  draftPreRequestScript.value = ''
  draftPostRequestScript.value = ''
  resetDebugResponse()
  activeDebugRequestTab.value = 'headers'
  activeRespTab.value = 'body'
}

/** 调试面板右侧「返回响应」的初始空状态（含 bodyEncoding / bodyBase64） */
function createEmptyDebugResponse() {
  return {
    sent: false,
    ok: false,
    status: null,
    statusText: '',
    headers: {},
    bodyText: '',
    /** text | base64；裸媒体为 base64 */
    bodyEncoding: 'text',
    /** 裸媒体 Base64，供 Body 区上方预览 */
    bodyBase64: '',
    error: null,
    errorCode: null,
    corsHint: null,
    durationMs: null,
    preScriptError: null,
    postScriptError: null,
    scriptLogs: [],
    scriptTests: []
  }
}

function resetDebugResponse() {
  debugResponse.value = createEmptyDebugResponse()
}

/** 保存前剥离 KV 行上的 UI 专用字段（_enabled、exampleValue 等），不入库 */
function stripRowsInternal(rows) {
  if (!Array.isArray(rows)) return rows
  return rows.map((r) => {
    if (!r || typeof r !== 'object') return r
    const {_enabled, _file, exampleValue, examples, ...rest} = r
    return rest
  })
}

function sanitizeParamRowsForSave(rows) {
  if (!Array.isArray(rows)) return rows
  return rows.map((r) => {
    if (!r || typeof r !== 'object') return r
    return sanitizeParamRowForPersist(r)
  })
}

/** 深拷贝 requestConfig 并写入 configVersion=1，规范化各参数数组与 json schema */
function cloneRequestConfigForSave(rc) {
  const o = JSON.parse(JSON.stringify(rc))
  o.configVersion = REQUEST_CONFIG_VERSION
  o.queryParams = sanitizeParamRowsForSave(stripRowsInternal(o.queryParams))
  o.pathParams = sanitizeParamRowsForSave(stripRowsInternal(o.pathParams))
  o.declaredHeaders = sanitizeParamRowsForSave(stripRowsInternal(o.declaredHeaders || []))
  if (o.body) {
    o.body.formData = sanitizeParamRowsForSave(stripRowsInternal(o.body.formData))
    o.body.urlencoded = sanitizeParamRowsForSave(stripRowsInternal(o.body.urlencoded))
    if (Array.isArray(o.body.urlencoded)) {
      o.body.urlencoded = o.body.urlencoded.map((r) => {
        if (!r || typeof r !== 'object') return r
        if (String(r.type || '').toLowerCase() === 'file') {
          return {...r, type: 'string'}
        }
        return r
      })
    }
    if (o.body.mode === 'json' && o.body.json && typeof o.body.json === 'object') {
      o.body.json = {
        ...o.body.json,
        schema: sanitizeBodyJsonSchemaForPersist(o.body.json.schema)
      }
    }
  }
  return o
}

function rowsToKeyValueObject(rows) {
  const out = {}
  for (const r of rows || []) {
    if (r._enabled === false) continue
    const k = (r.name || '').trim()
    if (!k) continue
    out[k] = r.value ?? ''
  }
  return out
}

function applyBodyJsonToDraftBeforeSave() {
  if (draftRequestConfig.value.body.mode !== 'json') return
  const t = bodyJsonText.value.trim()
  draftRequestConfig.value.body.json = draftRequestConfig.value.body.json || {schema: null, example: null}
  if (!t) {
    draftRequestConfig.value.body.json.example = {}
    return
  }
  try {
    draftRequestConfig.value.body.json.example = JSON.parse(t)
  } catch {
    throw new Error('invalid-json')
  }
}

const debugBodyTabBadge = computed(() => {
  const b = draftRequestConfig.value.body
  if (!b) return 0
  if (b.mode === 'form-data') {
    return (b.formData || []).filter((r) => r._enabled !== false && (r.name || '').trim()).length
  }
  if (b.mode === 'x-www-form-urlencoded') {
    return (b.urlencoded || []).filter((r) => r._enabled !== false && (r.name || '').trim()).length
  }
  if (b.mode === 'json') return bodyJsonText.value.trim() ? 1 : 0
  if (b.mode === 'text' || b.mode === 'xml') return (b.text || '').trim() ? 1 : 0
  if (b.mode === 'binary') return binaryBodyFile.value instanceof File ? 1 : 0
  return 0
})

const debugPathTabBadge = computed(() =>
    (draftRequestConfig.value.pathParams || []).filter((r) => r._enabled !== false && (r.name || '').trim()).length
)
const debugQueryTabBadge = computed(() =>
    (draftRequestConfig.value.queryParams || []).filter((r) => r._enabled !== false && (r.name || '').trim()).length
)

const debugScriptTestsBadge = computed(() => (debugResponse.value.scriptTests || []).length)

function formatResponseHeaders(h) {
  if (!h || typeof h !== 'object') return ''
  try {
    const flat = {}
    for (const k of Object.keys(h)) {
      flat[k] = h[k]
    }
    return JSON.stringify(flat, null, 2)
  } catch {
    return String(h)
  }
}

function buildSendUrlAndOptions() {
  const envId = props.testProjectEnvId
  const env = props.envList.find((e) => String(e.testProjectEnvId) === String(envId))
  const envBase = resolveEnvBaseUrlForRequest(env?.envUrl)
  if (!envBase) {
    return {error: '请先选择环境'}
  }
  let path = (draftApiPath.value || '').trim()
  if (!path.startsWith('/')) path = '/' + path

  let urlPath = path
  for (const row of draftRequestConfig.value.pathParams || []) {
    if (row._enabled === false) continue
    const n = (row.name || '').trim()
    if (!n) continue
    const val = row.value ?? ''
    urlPath = urlPath.split(`{${n}}`).join(encodeURIComponent(val))
  }

  const qs = new URLSearchParams()
  for (const row of draftRequestConfig.value.queryParams || []) {
    if (row._enabled === false) continue
    const k = (row.name || '').trim()
    if (!k) continue
    qs.append(k, row.value ?? '')
  }
  const qStr = qs.toString()
  const base = ensureHttpSchemeForRequest(envBase).replace(/\/$/, '')
  const fullUrl = base + urlPath + (qStr ? `?${qStr}` : '')

  const method = (draftRequestConfig.value.method || 'GET').toUpperCase()
  const headers = {...rowsToKeyValueObject(draftHeaderRows.value)}
  const cookieStr = Object.entries(rowsToKeyValueObject(draftCookieRows.value))
      .map(([k, v]) => `${k}=${v}`)
      .join('; ')
  if (cookieStr) {
    headers.Cookie = [headers.Cookie, cookieStr].filter(Boolean).join('; ')
  }

  const body = draftRequestConfig.value.body
  const mode = body?.mode || 'none'
  let data = undefined

  if (!['GET', 'HEAD'].includes(method)) {
    switch (mode) {
      case 'json': {
        const builtJson = buildJsonBodyDataForSend()
        if (builtJson.error) return {error: builtJson.error}
        data = builtJson.data
        if (!headers['Content-Type'] && !headers['content-type']) {
          headers['Content-Type'] = 'application/json'
        }
        break
      }
      case 'form-data': {
        const fd = new FormData()
        for (const row of body.formData || []) {
          if (row._enabled === false) continue
          const k = (row.name || '').trim()
          if (!k) continue
          const t = String(row.type || '').toLowerCase()
          if (t === 'file' && row._file instanceof File) {
            fd.append(k, row._file, row._file.name)
          } else {
            fd.append(k, row.value ?? '')
          }
        }
        data = fd
        // 必须清掉声明头里的 Content-Type，否则 axios 会按 application/json 序列化，FormData 变成普通 JSON
        for (const hk of Object.keys(headers)) {
          if (hk.toLowerCase() === 'content-type') delete headers[hk]
        }
        break
      }
      case 'x-www-form-urlencoded': {
        const p = new URLSearchParams()
        for (const row of body.urlencoded || []) {
          if (row._enabled === false) continue
          const k = (row.name || '').trim()
          if (!k) continue
          p.append(k, row.value ?? '')
        }
        data = p.toString()
        if (!headers['Content-Type'] && !headers['content-type']) {
          headers['Content-Type'] = 'application/x-www-form-urlencoded'
        }
        break
      }
      case 'xml':
      case 'text':
        data = body.text ?? ''
        if (mode === 'xml' && !headers['Content-Type'] && !headers['content-type']) {
          headers['Content-Type'] = 'application/xml'
        }
        if (mode === 'text' && !headers['Content-Type'] && !headers['content-type']) {
          headers['Content-Type'] = 'text/plain'
        }
        break
      case 'binary': {
        const f = binaryBodyFile.value
        if (!(f instanceof File)) {
          return {error: 'binary 请求体请先选择本地文件'}
        }
        data = f
        if (!headers['Content-Type'] && !headers['content-type']) {
          const ct = f.type && String(f.type).trim() ? f.type : 'application/octet-stream'
          headers['Content-Type'] = ct
        }
        break
      }
      default:
        data = undefined
    }
  }

  return {fullUrl, method, headers, data, testProjectApiId: props.apiDetail?.testProjectApiId}
}

async function handleDebugSend() {
  const built = buildSendUrlAndOptions()
  if (built.error) {
    if (built.error === '请先选择环境') {
      proxy.$modal.msgWarning(built.error)
    } else {
      proxy.$modal.msgError(built.error)
    }
    return
  }
  let sendBuilt = built
  const scriptSession = {
    variables: {...(scriptState.value.variables ?? {})},
    globals: {...(scriptState.value.globals ?? {})},
    environment: buildEnvironmentMap()
  }
  debugSending.value = true
  debugResponse.value = createEmptyDebugResponse()
  try {
    const preScript = draftPreRequestScript.value ?? props.apiDetail?.preRequestScript ?? ''
    const preOutcome = await runPreScript(preScript, sendBuilt, scriptSession)
    if (preOutcome.error) {
      debugResponse.value = {
        ...debugResponse.value,
        sent: true,
        ok: false,
        error: preOutcome.error,
        preScriptError: preOutcome.error,
        scriptLogs: preOutcome.result?.logs ?? []
      }
      return
    }
    if (preOutcome.result) {
      applyServerState(preOutcome.result)
      scriptSession.variables = {...(scriptState.value.variables ?? {})}
      scriptSession.globals = {...(scriptState.value.globals ?? {})}
      scriptSession.environment = buildEnvironmentMap()
    }
    sendBuilt = preOutcome.built

    const {fullUrl, method, headers, data} = sendBuilt
    const result = await executeDebugRequest(
        {fullUrl, method, headers, data},
        {transportMode: props.httpTransportMode}
    )
    debugResponse.value = {
      sent: true,
      ok: result.ok,
      status: result.status,
      statusText: result.statusText || '',
      headers: result.headers || {},
      bodyText: result.bodyText,
      bodyEncoding: result.bodyEncoding || 'text',
      bodyBase64: result.bodyBase64 || '',
      error: result.error,
      errorCode: result.errorCode,
      corsHint: result.corsHint || null,
      durationMs: result.durationMs,
      preScriptError: null,
      postScriptError: null,
      scriptLogs: preOutcome.result?.logs ?? [],
      scriptTests: []
    }

    const postScript = draftPostRequestScript.value ?? props.apiDetail?.postRequestScript ?? ''
    const postOutcome = await runPostScript(postScript, sendBuilt, debugResponse.value, scriptSession)
    if (postOutcome.result) {
      applyServerState(postOutcome.result)
      const logs = [
        ...(debugResponse.value.scriptLogs ?? []),
        ...(postOutcome.result.logs ?? [])
      ]
      debugResponse.value.scriptLogs = logs
      debugResponse.value.scriptTests = postOutcome.result.tests ?? []
    }
    if (postOutcome.error) {
      debugResponse.value.postScriptError = postOutcome.error
      debugResponse.value.ok = false
      if (!debugResponse.value.error) {
        debugResponse.value.error = postOutcome.error
      }
    }
    if ((debugResponse.value.scriptTests ?? []).some((t) => t && t.passed === false)) {
      debugResponse.value.ok = false
    }
  } catch (err) {
    debugResponse.value = {
      ...debugResponse.value,
      sent: true,
      ok: false,
      status: debugResponse.value.status,
      statusText: debugResponse.value.statusText || '',
      headers: debugResponse.value.headers || {},
      bodyText: debugResponse.value.bodyText ?? '',
      error: err.message || String(err),
      errorCode: debugResponse.value.errorCode,
      corsHint: debugResponse.value.corsHint,
      durationMs: debugResponse.value.durationMs
    }
    console.error(err)
  } finally {
    debugSending.value = false
  }
}

/**
 * 收集当前调试草稿并拆测值：结构只留定义，测值进 testValueConfig。
 * @param responseConfigOverride 可选；设计页/预制口传入当前响应稿，否则用详情里的 responseConfig
 */
function buildPersistPayload(responseConfigOverride) {
  try {
    applyBodyJsonToDraftBeforeSave()
  } catch {
    return {error: '请求体 JSON 格式无效'}
  }
  const rc = cloneRequestConfigForSave(draftRequestConfig.value)
  const peeled = peelTestValuesFromStructure(
      rc,
      responseConfigOverride !== undefined ? responseConfigOverride : props.apiDetail?.responseConfig,
      props.apiDetail?.testValueConfig,
  )
  return {
    apiPath: draftApiPath.value,
    requestConfig: JSON.stringify(peeled.requestConfig),
    responseConfig: JSON.stringify(peeled.responseConfig),
    testValueConfig: JSON.stringify(peeled.testValueConfig),
    headers: JSON.stringify(rowsToKeyValueObject(draftHeaderRows.value)),
    cookies: JSON.stringify(rowsToKeyValueObject(draftCookieRows.value)),
    preRequestScript: draftPreRequestScript.value ?? '',
    postRequestScript: draftPostRequestScript.value ?? ''
  }
}

function handleSaveApiDebug() {
  if (!props.apiDetail?.testProjectApiId) {
    proxy.$modal.msgError('缺少 API 信息')
    return
  }
  const part = buildPersistPayload()
  if (part.error) {
    proxy.$modal.msgError(part.error)
    return
  }
  apiDebugSaving.value = true
  const payload = {
    testProjectApiId: props.apiDetail.testProjectApiId,
    testProjectId: props.apiDetail.testProjectId,
    apiGroupId: props.apiDetail.apiGroupId,
    apiStatus: props.apiDetail.apiStatus,
    apiGroup: props.apiDetail.apiGroup,
    apiName: props.apiDetail.apiName,
    apiDescription: props.apiDetail.apiDescription,
    apiPath: part.apiPath,
    protocolType: props.apiDetail.protocolType,
    requestConfig: part.requestConfig,
    headers: part.headers,
    cookies: part.cookies,
    responseConfig: part.responseConfig ?? props.apiDetail.responseConfig,
    testValueConfig: part.testValueConfig,
    preRequestScript:
        part.preRequestScript !== undefined ? part.preRequestScript : props.apiDetail.preRequestScript,
    postRequestScript:
        part.postRequestScript !== undefined ? part.postRequestScript : props.apiDetail.postRequestScript
  }
  updateTestProjectApi(payload)
      .then((res) => {
        if (res.code === 200) {
          proxy.$modal.msgSuccess('保存成功')
          const merged = {...props.apiDetail, ...payload}
          initDraftFromApiDetail(merged)
          emit('saved', merged)
        } else {
          proxy.$modal.msgError(res.msg || '保存失败')
        }
      })
      .catch(() => {})
      .finally(() => {
        apiDebugSaving.value = false
      })
}

function removeRow(arr, index) {
  arr.splice(index, 1)
  if (!arr.length) arr.push(emptyKVRow())
  ensureTrailingEmptyRow(arr)
}

function ensureParamSchemaDefaults(row) {
  if (!row || typeof row !== 'object') return
  const d = emptyKVRow()
  for (const key of Object.keys(d)) {
    if (row[key] === undefined) row[key] = d[key]
  }
}

function toggleDebugParamRequired(row) {
  ensureParamSchemaDefaults(row)
  row.required = !row.required
}

function onKvParamTypeChange(row) {
  if (!row || typeof row !== 'object') return
  pruneConstraintsForType(row, row.type, { flatParam: true })
}

function openParamSchemaDialog(row, source = 'default') {
  ensureParamSchemaDefaults(row)
  paramSchemaSource.value = source
  const t = String(row.type || '').toLowerCase()
  if (source === 'urlencoded' && DISALLOWED_URLENC_TYPES.includes(t)) {
    row.type = 'string'
  } else if ((source === 'query' || source === 'path') && DISALLOWED_QUERY_PATH_TYPES.includes(t)) {
    row.type = 'string'
  }
  paramSchemaKvTarget.value = source === 'bodyJson' ? null : row
  paramSchemaRow.value = source === 'bodyJson' ? row : JSON.parse(JSON.stringify(row))
  paramSchemaDialogVisible.value = true
}

function onBodyJsonOpenSchema(node) {
  if (!node || typeof node !== 'object') return
  const t = String(node.type || '').toLowerCase()
  if (t === 'object') {
    proxy?.$modal?.msgInfo?.(
        'object 请在树中编辑子节点与类型列；标量 / array 字段可使用齿轮打开高级设置。'
    )
    return
  }
  bodyJsonSchemaEditTarget.value = node
  paramSchemaSource.value = 'bodyJson'
  paramSchemaKvTarget.value = null
  paramSchemaRow.value = bodyJsonSchemaNodeToParamRow(node)
  paramSchemaDialogVisible.value = true
}

function onParamSchemaDialogClosed() {
  if (bodyJsonSchemaEditTarget.value && paramSchemaRow.value) {
    paramRowMergeIntoBodyJsonNode(bodyJsonSchemaEditTarget.value, paramSchemaRow.value)
    bodyJsonSchemaEditTarget.value = null
    nextTick(() => bodyJsonSchemaTreeRef.value?.emitSchema?.())
  } else if (paramSchemaKvTarget.value && paramSchemaRow.value) {
    paramRowMergeIntoFlatParamRow(paramSchemaKvTarget.value, paramSchemaRow.value)
    paramSchemaKvTarget.value = null
  }
  paramSchemaRow.value = null
  paramSchemaSource.value = 'default'
}

useApiDebugTrailingEmptyRows({
  draftHeaderRows,
  draftCookieRows,
  draftRequestConfig,
  ensureTrailingEmptyRow
})

watch(
    () => draftRequestConfig.value.body?.mode,
    (mode, prevMode) => {
      if (prevMode === 'binary' && mode !== 'binary') {
        binaryBodyFile.value = null
      }
      syncBodyJsonTextFromDraft()
      if (mode === 'x-www-form-urlencoded') {
        coerceUrlencodedRowTypes(draftRequestConfig.value.body?.urlencoded || [])
      }
      nextTick(() => {
        const b = draftRequestConfig.value.body
        if (!b) return
        if (mode === 'form-data') ensureTrailingEmptyRow(b.formData)
        if (mode === 'x-www-form-urlencoded') ensureTrailingEmptyRow(b.urlencoded)
      })
    }
)

watch(
    () => draftRequestConfig.value.body?.json?.schema,
    () => {
      if (syncingBodyJsonFromSchema) return
      if (draftRequestConfig.value.body?.mode !== 'json') return
      mergeSchemaDefaultsIntoBodyJson('preferSchema')
    },
    {deep: true}
)

watch(
    () => props.apiDetail,
    (d) => {
      if (!d) {
        lastInitDraftFromDetailRef = null
        resetDebugWorkbench()
        return
      }
      if (d === lastInitDraftFromDetailRef) return
      initDraftFromApiDetail(d)
    },
    {immediate: true}
)

watch([activeDebugRequestTab, activeRespTab], () => {
  if (!props.apiDetail?.testProjectApiId) return
  schedulePersistDebugUiTabs()
})

defineExpose({
  buildPersistPayload
})
</script>

<style lang="scss" scoped>
.api-debug-workbench {
  display: flex;
  flex-direction: column;
  gap: 0;
  height: 100%;
  min-height: 360px;
  padding: 0;
  position: relative;

  &.is-embed-design {
    /* 由设计页 .design-embed-debug 给出高度，避免与下方区块 flex 均分导致叠层 */
    min-height: 0;
    height: 100%;

    .debug-request-response {
      flex: 1;
      min-height: 0;
    }

    .debug-request-pane {
      flex: 1;
      min-height: 0;
    }
  }

  .debug-url-bar {
    display: flex;
    align-items: center;
    gap: 10px;
    flex-shrink: 0;
    padding: 8px 6px;
    border-radius: 0;
    background: var(--pd-bg-sunken, #e9f2fc);
    border: none;
    border-bottom: 1px solid var(--pd-divider, var(--pd-border-muted));
    box-shadow: none;

    .debug-method-select {
      width: 118px;
      flex-shrink: 0;

      :deep(.el-select__wrapper),
      :deep(.el-input__wrapper) {
        min-height: 38px;
        height: 38px;
        padding-left: 12px;
        padding-right: 12px;
        align-items: center;
      }

      :deep(.el-select__selection),
      :deep(.el-select__placeholder),
      :deep(.el-select__selected-item),
      :deep(.el-input__inner) {
        font-size: 14px;
        font-weight: 600;
      }

      &.is-POST :deep(.el-select__wrapper),
      &.is-POST :deep(.el-input__wrapper) {
        background: #fff7ed;
        box-shadow: 0 0 0 1px #fdba74 inset;
      }

      &.is-GET :deep(.el-select__wrapper),
      &.is-GET :deep(.el-input__wrapper) {
        background: #ecfdf5;
        box-shadow: 0 0 0 1px #6ee7b7 inset;
      }

      &.is-PUT :deep(.el-select__wrapper),
      &.is-PUT :deep(.el-input__wrapper) {
        background: #eff6ff;
        box-shadow: 0 0 0 1px #93c5fd inset;
      }

      &.is-PATCH :deep(.el-select__wrapper),
      &.is-PATCH :deep(.el-input__wrapper) {
        background: #faf5ff;
        box-shadow: 0 0 0 1px #d8b4fe inset;
      }

      &.is-DELETE :deep(.el-select__wrapper),
      &.is-DELETE :deep(.el-input__wrapper) {
        background: #fef2f2;
        box-shadow: 0 0 0 1px #fca5a5 inset;
      }
    }

    .debug-url-input {
      flex: 1;
      min-width: 200px;

      :deep(.el-input__wrapper) {
        border-radius: var(--pd-radius-sm, 8px);
        font-family: ui-monospace, Consolas, monospace;
      }
    }

    .debug-send-btn {
      min-width: 88px;
    }

    .debug-save-btn {
      border-color: var(--pd-border-subtle);
      background: var(--pd-surface-elevated);
      color: var(--pd-text);

      &:hover {
        border-color: var(--pd-primary);
        color: var(--pd-primary);
        background: var(--pd-primary-soft);
      }
    }
  }

  .debug-request-response {
    flex: 1;
    min-height: 0;
    display: flex;
    flex-direction: column;
    gap: 0;
  }

  .debug-request-pane {
    flex: 1;
    min-height: 0;
    display: flex;
    flex-direction: column;
  }

  .debug-inner-tab-shell {
    flex: 1;
    min-height: 0;
    display: flex;
    flex-direction: column;
    border: none;
    border-radius: 0;
    background: var(--pd-surface-elevated);
    box-shadow: none;
    overflow: hidden;
  }

  .debug-inner-tablist {
    display: flex;
    flex-wrap: wrap;
    align-items: flex-end;
    gap: 2px;
    padding: 4px 6px 0;
    border-bottom: 1px solid var(--pd-border-subtle, var(--pd-divider, var(--pd-border-muted)));
    background: var(--pd-gradient-tabstrip);
    flex-shrink: 0;
  }

  .debug-inner-tab {
    position: relative;
    display: inline-flex;
    align-items: center;
    gap: 6px;
    margin: 0;
    padding: 0 14px;
    height: 36px;
    border: 1px solid transparent;
    border-bottom: none;
    background: transparent;
    font-family: inherit;
    font-size: var(--pd-font-tab, 14px);
    font-weight: 500;
    color: var(--pd-text-muted, #5a6b86);
    cursor: pointer;
    border-radius: 6px 6px 0 0;
    transition:
      color 0.15s ease,
      background 0.15s ease,
      border-color 0.15s ease,
      box-shadow 0.15s ease;

    &:hover:not(.active) {
      color: var(--pd-text-tab, #334c6e);
      background: rgba(255, 255, 255, 0.6);
      border-color: var(--pd-border-muted, #d6e6f5);
    }

    &.active {
      color: var(--pd-primary, #0b6edc);
      font-weight: 600;
      background: var(--pd-surface-elevated, #fff);
      border-color: var(--pd-border-subtle, #c5d8ec);
      border-bottom: none;
      margin-bottom: -1px;
      z-index: 1;
      box-shadow: 0 1px 3px rgba(20, 60, 120, 0.06);

      &::after {
        content: '';
        position: absolute;
        left: 0;
        right: 0;
        bottom: -1px;
        height: 3px;
        background: var(--pd-primary, #0b6edc);
        border-radius: 0;
        z-index: 2;
      }
    }

    &:focus-visible {
      outline: 2px solid color-mix(in srgb, var(--pd-primary, #0b6edc) 45%, transparent);
      outline-offset: 1px;
    }
  }

  .debug-tab-count {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    min-width: 18px;
    height: 18px;
    padding: 0 5px;
    border-radius: 9px;
    font-size: 11px;
    font-weight: 600;
    line-height: 1;
    color: #fff;
    background: #ef4444;
  }

  .debug-inner-panels {
    flex: 1;
    min-height: 0;
    overflow: auto;
    scrollbar-gutter: stable;
    padding: 0 0 4px 0;

    &--script {
      display: flex;
      flex-direction: column;
      overflow: hidden;
      padding: 0;
    }
  }

  .debug-inner-panel {
    min-height: 0;

    &--body {
      display: flex;
      flex-direction: column;
      gap: 0;
    }
  }

  .body-mode-row {
    flex-shrink: 0;
    margin: 0;
    padding: 4px 6px 6px;
    overflow-x: auto;
    background: var(--pd-bg-sunken, rgba(233, 242, 252, 0.5));
    border-bottom: 1px solid var(--pd-divider, var(--pd-border-muted));
  }

  .body-mode-group {
    display: flex;
    flex-wrap: wrap;
    align-items: center;
    gap: 6px;
    width: 100%;
    max-width: 100%;
    border: none;
    background: transparent;

    :deep(.el-radio-button) {
      margin: 0 !important;
    }

    :deep(.el-radio-button__inner) {
      display: inline-flex;
      align-items: center;
      justify-content: center;
      min-height: 28px;
      padding: 4px 11px;
      border-radius: 6px !important;
      border: 1px solid var(--pd-border-muted, #d6e6f5) !important;
      box-shadow: none !important;
      font-size: 12px;
      font-weight: 500;
      line-height: 1.3;
      color: var(--pd-text-tab);
      background: rgba(255, 255, 255, 0.92);
      transition: background 0.15s ease, border-color 0.15s ease, color 0.15s ease;
    }

    :deep(.el-radio-button:first-child .el-radio-button__inner) {
      border-radius: 6px !important;
    }

    :deep(.el-radio-button:last-child .el-radio-button__inner) {
      border-radius: 6px !important;
    }

    :deep(.el-radio-button:not(.is-active):hover .el-radio-button__inner) {
      color: var(--pd-text);
      border-color: var(--pd-border-subtle, #c5d8ec) !important;
      background: var(--pd-surface-elevated);
    }

    :deep(.el-radio-button.is-active .el-radio-button__inner) {
      color: #fff !important;
      background: var(--pd-primary, #0b6edc) !important;
      border-color: var(--pd-primary, #0b6edc) !important;
    }
  }

  /* Body：类型条下方通栏铺满，不另加顶/侧垫白 */
  .debug-inner-panel--body > .body-mode-row ~ * {
    padding: 0;
    box-sizing: border-box;
  }

  /* 须高于上一则，否则 padding 被清空后 binary 区顶距不生效 */
  .debug-inner-panel--body > .body-mode-row ~ .debug-binary-body {
    padding: 24px 4px 12px;
  }

  .debug-inner-panel--script {
    display: flex;
    flex-direction: column;
    flex: 1;
    min-height: 0;
    padding: 6px;
    box-sizing: border-box;

    :deep(.api-script-workbench) {
      flex: 1;
      min-height: 0;
      height: 100%;
    }
  }

  .debug-script-hint {
    margin: 0;
    font-size: 12px;
    color: var(--pd-text-muted);
    line-height: 1.45;
  }

  .body-json-json-shell {
    width: 100%;
  }

  .body-json-inner-tabs {
    width: 100%;

    :deep(.el-tabs__header) {
      margin: 0 0 4px 0;
    }

    :deep(.el-tabs__nav-wrap) {
      padding: 0 6px;
    }

    :deep(.el-tabs__content) {
      padding: 0;
    }
  }

  .body-json-example-hint {
    margin: 0 0 6px;
    font-size: 12px;
    color: var(--pd-text-muted);
    line-height: 1.45;
  }

  .debug-body-raw {
    width: 100%;
    max-width: 100%;
    font-family: ui-monospace, Consolas, 'Courier New', monospace;
    font-size: 12px;

    :deep(.el-textarea) {
      width: 100%;
    }

    :deep(.el-textarea__inner) {
      font-family: inherit;
      line-height: 1.5;
      border-radius: 6px;
      padding: 10px 12px;
      color: var(--pd-text);
      background: var(--pd-surface-elevated);
      border: 1px solid var(--pd-divider, var(--pd-border-muted));
      box-shadow: none;

      &::placeholder {
        color: var(--pd-text-muted);
        opacity: 0.85;
      }

      &:hover {
        border-color: var(--pd-border-subtle, #c5d8ec);
      }

      &:focus {
        border-color: var(--pd-primary, #0b6edc);
        box-shadow: 0 0 0 1px var(--pd-primary, #0b6edc) inset;
      }
    }
  }

  .debug-body-placeholder {
    margin: 0;
    padding: 16px 6px 20px;
    text-align: center;
    border: none;
    border-radius: 0;
    background: transparent;
  }

  .debug-body-placeholder-title {
    margin: 0 0 6px;
    font-size: 14px;
    font-weight: 600;
    color: var(--pd-text-muted);
  }

  .debug-body-placeholder-desc {
    margin: 0;
    font-size: 12px;
    color: var(--pd-text-muted);
    line-height: 1.5;
  }

  .debug-binary-body {
    display: flex;
    flex-direction: column;
    align-items: center;
  }

  .debug-binary-simple {
    position: relative;
    display: flex;
    flex-direction: column;
    align-items: stretch;
    gap: 8px;
    width: 100%;
    max-width: 320px;
    margin: 0 auto;
  }

  .debug-binary-file-native {
    position: absolute;
    width: 1px;
    height: 1px;
    margin: -1px;
    padding: 0;
    overflow: hidden;
    clip: rect(0 0 0 0);
    clip-path: inset(50%);
    white-space: nowrap;
    border: 0;
  }

  .debug-binary-upload-btn {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    gap: 8px;
    width: 100%;
    min-height: 40px;
    margin: 0;
    padding: 0 16px;
    border: 1px solid var(--pd-divider, #dcdfe6);
    border-radius: 8px;
    background: #fff;
    color: var(--pd-text, #303133);
    font-size: 14px;
    font-weight: 500;
    cursor: pointer;
    transition:
      border-color 0.15s ease,
      color 0.15s ease,
      background 0.15s ease;
  }

  .debug-binary-upload-btn:hover {
    border-color: color-mix(in srgb, var(--pd-primary, #409eff) 45%, var(--pd-divider, #dcdfe6));
    color: var(--pd-primary, #409eff);
    background: color-mix(in srgb, var(--pd-primary, #409eff) 6%, #fff);
  }

  .debug-binary-upload-btn:active {
    background: color-mix(in srgb, var(--pd-primary, #409eff) 10%, #fff);
  }

  .debug-binary-upload-btn-ico {
    font-size: 18px;
  }

  .debug-binary-file-line {
    display: flex;
    align-items: center;
    justify-content: center;
    gap: 10px;
    margin: 0;
    width: 100%;
    min-height: 18px;
    font-size: 12px;
    color: var(--pd-text-muted);
    text-align: center;
  }

  .debug-binary-file-line-text {
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
    min-width: 0;
    max-width: 100%;
  }

  .debug-binary-file-line-clear {
    flex-shrink: 0;
    margin: 0;
    padding: 0;
    border: none;
    background: none;
    font: inherit;
    font-size: 12px;
    color: var(--el-color-danger, #f56c6c);
    cursor: pointer;
    text-decoration: underline;
    text-underline-offset: 2px;
  }

  .debug-binary-file-line-clear:hover {
    opacity: 0.85;
  }

  .debug-response-pane {
    flex: 0 0 220px;
    min-height: 176px;
    display: flex;
    flex-direction: column;
    border: none;
    border-top: 1px solid var(--pd-divider, var(--pd-border-muted));
    border-radius: 0;
    overflow: hidden;
    background: var(--pd-surface-elevated);
    box-shadow: none;
  }

  .debug-response-head {
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding: 6px 6px;
    border-bottom: 1px solid var(--pd-divider, var(--pd-border-muted));
    background: var(--pd-surface-elevated);
    flex-shrink: 0;
  }

  .debug-response-title {
    font-size: 13px;
    font-weight: 600;
    color: var(--pd-text);
  }

  .debug-response-meta {
    display: flex;
    align-items: center;
    gap: 10px;
    font-size: 12px;
  }

  .debug-status-pill {
    padding: 3px 10px;
    border-radius: 6px;
    font-weight: 600;
    font-size: 12px;

    &.is-ok {
      background: #ecfdf5;
      color: #047857;
    }

    &.is-err {
      background: #fef2f2;
      color: #b91c1c;
    }
  }

  .debug-duration {
    color: var(--pd-text-muted);
  }

  .debug-response-empty {
    flex: 1;
    display: flex;
    align-items: center;
    justify-content: center;
    min-height: 120px;
    padding: 16px 0;
    background: var(--pd-bg-sunken, #e9f2fc);
  }

  .debug-response-empty-inner {
    text-align: center;
    max-width: 280px;
  }

  .debug-response-empty-icon {
    margin: 0 auto 12px;
    color: var(--pd-primary);
    opacity: 0.45;
  }

  .debug-response-empty-title {
    margin: 0 0 6px;
    font-size: 14px;
    font-weight: 600;
    color: var(--pd-text-muted);
  }

  .debug-response-empty-desc {
    margin: 0;
    font-size: 12px;
    color: var(--pd-text-muted);
    line-height: 1.5;
  }

  .debug-response-body {
    flex: 1;
    min-height: 0;
    display: flex;
    flex-direction: column;
    padding: 4px 0 6px 6px;
    overflow: auto;
    scrollbar-gutter: stable;
    background: var(--pd-surface-elevated);
  }

  .debug-error-alert {
    margin-bottom: 8px;
  }

  .debug-resp-tablist {
    display: flex;
    gap: 4px;
    margin-bottom: 8px;
    flex-shrink: 0;
  }

  .debug-resp-tab {
    padding: 6px 14px;
    margin: 0;
    border: 1px solid var(--pd-border-muted);
    border-radius: 6px;
    background: var(--pd-bg-sunken, #e9f2fc);
    font-size: 12px;
    font-weight: 500;
    color: var(--pd-text-muted);
    cursor: pointer;
    font-family: inherit;
    transition: background 0.15s ease, border-color 0.15s ease, color 0.15s ease;

    &:hover {
      background: var(--pd-surface-elevated);
      border-color: var(--pd-border-subtle);
    }

    &.active {
      color: var(--pd-primary);
      font-weight: 600;
      background: var(--pd-primary-soft);
      border-color: rgba(11, 110, 220, 0.35);
    }
  }

  .debug-resp-panel {
    flex: 1;
    min-height: 0;
    display: flex;
    flex-direction: column;
  }

  .debug-resp-textarea {
    flex: 1;
    min-height: 0;

    :deep(.el-textarea__inner) {
      font-family: Consolas, 'Courier New', monospace;
      font-size: 12px;
      border-radius: var(--pd-radius-sm, 8px);
    }
  }

  .debug-resp-panel--tests {
    gap: 8px;
    padding: 4px 2px 0;
  }

  .debug-script-tests-empty {
    font-size: 12px;
    color: var(--pd-text-muted);
    padding: 8px 4px;
  }

  .debug-script-test-list {
    list-style: none;
    margin: 0;
    padding: 0;
    display: flex;
    flex-direction: column;
    gap: 6px;
  }

  .debug-script-test-item {
    display: flex;
    flex-wrap: wrap;
    align-items: center;
    gap: 8px;
    padding: 6px 8px;
    border-radius: 6px;
    font-size: 12px;
    background: var(--pd-bg-sunken, rgba(233, 242, 252, 0.5));
    border: 1px solid var(--pd-border-muted);

    &.is-pass {
      border-color: rgba(22, 163, 74, 0.35);
    }

    &.is-fail {
      border-color: rgba(220, 38, 38, 0.35);
      background: rgba(254, 242, 242, 0.6);
    }
  }

  .debug-script-test-name {
    font-weight: 600;
  }

  .debug-script-test-status {
    color: var(--pd-text-muted);
  }

  .debug-script-test-msg {
    flex: 1 1 100%;
    color: #b91c1c;
    word-break: break-word;
  }

  .debug-script-log-textarea {
    margin-top: 4px;
  }
}
</style>

<style lang="scss">
.param-schema-dialog {
  .param-schema-dialog-inner {
    margin-top: -6px;
  }

  .param-schema-body {
    min-height: 0;
  }

  .param-schema-main-type-row {
    margin-bottom: 8px;
  }

  .param-schema-type-select {
    width: 100%;

    :deep(.el-input__inner) {
      font-size: 13px;
      font-weight: 500;
      color: #606266;
    }

    &.is-type-string :deep(.el-input__inner) {
      color: #16a34a;
    }

    &.is-type-integer :deep(.el-input__inner),
    &.is-type-number :deep(.el-input__inner) {
      color: #0b6edc;
    }

    &.is-type-boolean :deep(.el-input__inner) {
      color: #7c3aed;
    }

    &.is-type-array :deep(.el-input__inner) {
      color: #c2410c;
    }

    &.is-type-file :deep(.el-input__inner) {
      color: #db2777;
    }
  }

  .param-schema-switches {
    display: flex;
    flex-wrap: wrap;
    align-items: center;
    gap: 8px 14px;
    margin-bottom: 8px;
    font-size: 12px;
    color: #606266;

    .lbl {
      margin-right: 4px;
      margin-left: -6px;
    }

    &--2 {
      margin-bottom: 10px;
    }
  }

  .param-schema-form {
    :deep(.el-form-item) {
      margin-bottom: 10px;
    }

    :deep(.el-form-item__label) {
      font-size: 12px;
      padding-bottom: 2px;
    }
  }

  .param-schema-help {
    margin-left: 4px;
    font-size: 14px;
    color: #c0c4cc;
    vertical-align: -2px;
    cursor: help;
  }

  .param-schema-w100 {
    width: 100%;
  }

  .param-schema-advanced-label {
    margin: 4px 0 8px;
    font-size: 12px;
    font-weight: 600;
    color: #606266;
  }
}
</style>

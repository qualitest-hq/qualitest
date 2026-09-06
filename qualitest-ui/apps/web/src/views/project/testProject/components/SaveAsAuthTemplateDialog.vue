<template>
  <el-dialog
    v-model="visible"
    append-to-body
    title="另存为项目模板"
    width="720px"
    destroy-on-close
    @opened="onOpened"
  >
    <div v-loading="loading" class="save-as-tpl">
      <el-form label-position="top" @submit.prevent>
        <el-form-item label="模板名称" required>
          <el-input v-model="form.templateName" maxlength="100" placeholder="如 管理端 Bearer" />
        </el-form-item>

        <el-form-item label="Auth Profile" required>
          <el-radio-group v-model="form.profileId" class="save-as-tpl__profiles">
            <el-radio
              v-for="p in profiles"
              :key="p.id"
              :value="p.id"
            >
              {{ p.name || p.id }}
            </el-radio>
          </el-radio-group>
          <p v-if="!profiles.length" class="save-as-tpl__hint">当前项目尚无 Profile</p>
        </el-form-item>

        <el-form-item label="测试流（主）">
          <el-checkbox-group v-model="form.flowIds" class="save-as-tpl__checks">
            <el-checkbox
              v-for="f in flows"
              :key="String(f.testFlowId)"
              :label="String(f.testFlowId)"
            >
              {{ f.flowName || f.testFlowId }}
            </el-checkbox>
          </el-checkbox-group>
          <p v-if="!flows.length" class="save-as-tpl__hint">项目下暂无测试流</p>
        </el-form-item>

        <div class="save-as-tpl__section">
          <div class="save-as-tpl__section-title">流已关联 · 必带</div>
          <p class="save-as-tpl__locked">
            <span>接口：</span>
            {{ lockedApiLabels.length ? lockedApiLabels.join(' · ') : '（无）' }}
          </p>
          <p class="save-as-tpl__locked">
            <span>素材：</span>
            {{ lockedAssetKeys.length ? lockedAssetKeys.join(' · ') : '（无）' }}
          </p>
          <p class="save-as-tpl__hint">口令等素材明文会写入模板，请确认后再另存。</p>
        </div>

        <div class="save-as-tpl__section">
          <div class="save-as-tpl__section-title">可追加</div>

          <el-form-item label="接口（Profile 预制且流未引用）">
            <el-checkbox-group v-model="form.extraApiIds" class="save-as-tpl__checks">
              <el-checkbox
                v-for="api in extraApiCandidates"
                :key="String(api.testProjectApiId)"
                :label="String(api.testProjectApiId)"
              >
                {{ apiLabelOf(api) }}
              </el-checkbox>
            </el-checkbox-group>
            <p v-if="!extraApiCandidates.length" class="save-as-tpl__hint">无额外鉴权口可追加</p>
          </el-form-item>

          <el-form-item label="素材">
            <el-checkbox-group v-model="form.extraAssetKeys" class="save-as-tpl__checks">
              <el-checkbox
                v-for="key in extraAssetCandidates"
                :key="key"
                :label="key"
              >
                {{ key }}
              </el-checkbox>
            </el-checkbox-group>
            <p v-if="!extraAssetCandidates.length" class="save-as-tpl__hint">无其它素材可追加</p>
          </el-form-item>

          <el-form-item>
            <el-checkbox v-model="form.includeEnv">包含首环境</el-checkbox>
          </el-form-item>

          <el-form-item label="提示词">
            <el-checkbox-group v-model="form.promptIds" class="save-as-tpl__checks">
              <el-checkbox
                v-for="p in prompts"
                :key="String(p.aiPromptTemplateId)"
                :label="String(p.aiPromptTemplateId)"
              >
                {{ p.templateTitle || p.aiPromptTemplateId }}
              </el-checkbox>
            </el-checkbox-group>
            <p v-if="!prompts.length" class="save-as-tpl__hint">无项目级提示词</p>
          </el-form-item>
        </div>

        <el-form-item>
          <el-checkbox v-model="form.overwriteByName">覆盖同名自定义模板</el-checkbox>
        </el-form-item>
      </el-form>
    </div>

    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button :loading="saving" type="primary" @click="submit">另存</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
/**
 * 从当前测试项目另存完整鉴权模板：测试流为主轴，流关联接口/素材必带，其余可追加。
 */
import { computed, getCurrentInstance, reactive, ref, watch } from 'vue'
import { listAiPromptTemplate } from '@/api/ai/aiPromptTemplate'
import { listTestFlow } from '@/api/project/testFlow'
import { saveAsAuthTemplate } from '@/api/project/testProject'
import { listTestProjectApi } from '@/api/project/testProjectApi'
import { listTestProjectAsset } from '@/api/project/testProjectAsset'
import {
  apiIdentityOf,
  apiLabelOf,
  collectAssetKeysFromText,
  collectGraphHttpApiIds,
  prefabIdentityOf,
} from '../utils/saveAsAuthTemplatePreview'

const visible = defineModel('visible', { type: Boolean, default: false })

const props = defineProps({
  testProjectId: {
    type: [String, Number],
    required: true,
  },
  profiles: {
    type: Array,
    default: () => [],
  },
})

const emit = defineEmits(['saved'])

const { proxy } = getCurrentInstance()

const loading = ref(false)
const saving = ref(false)
const flows = ref([])
const apis = ref([])
const assets = ref([])
const prompts = ref([])

const form = reactive({
  templateName: '',
  profileId: '',
  flowIds: [],
  extraApiIds: [],
  extraAssetKeys: [],
  includeEnv: true,
  promptIds: [],
  overwriteByName: false,
})

const selectedProfile = computed(() =>
  (props.profiles || []).find((p) => String(p.id) === String(form.profileId)) || null,
)

const lockedApiIds = computed(() => {
  const set = new Set()
  const selected = new Set(form.flowIds.map(String))
  for (const flow of flows.value) {
    if (!selected.has(String(flow.testFlowId))) continue
    for (const id of collectGraphHttpApiIds(flow.graphJson)) {
      set.add(String(id))
    }
  }
  return [...set]
})

const lockedApiLabels = computed(() => {
  const byId = new Map(apis.value.map((a) => [String(a.testProjectApiId), a]))
  return lockedApiIds.value.map((id) => {
    const api = byId.get(id)
    return api ? apiLabelOf(api) : `id=${id}`
  })
})

const lockedAssetKeys = computed(() => {
  const keys = new Set()
  const selected = new Set(form.flowIds.map(String))
  const byId = new Map(apis.value.map((a) => [String(a.testProjectApiId), a]))
  for (const flow of flows.value) {
    if (!selected.has(String(flow.testFlowId))) continue
    collectAssetKeysFromText(flow.graphJson, keys)
  }
  for (const id of lockedApiIds.value) {
    const api = byId.get(id)
    if (!api) continue
    collectAssetKeysFromText(api.requestConfig, keys)
    collectAssetKeysFromText(api.headers, keys)
    collectAssetKeysFromText(api.testValueConfig, keys)
  }
  const profile = selectedProfile.value
  const headerTpl = profile?.headerValueTemplate || profile?.valueTemplate
  if (headerTpl) {
    collectAssetKeysFromText(headerTpl, keys)
  }
  return [...keys]
})

/** Profile 预制口对应的项目接口，且未被流锁定 */
const extraApiCandidates = computed(() => {
  const locked = new Set(lockedApiIds.value)
  const profile = selectedProfile.value
  const prefabs = Array.isArray(profile?.apis) ? profile.apis : []
  if (!prefabs.length) return []
  const prefabIds = new Set(prefabs.map(prefabIdentityOf))
  return apis.value.filter((api) => {
    const id = String(api.testProjectApiId)
    if (locked.has(id)) return false
    return prefabIds.has(apiIdentityOf(api))
  })
})

const extraAssetCandidates = computed(() => {
  const locked = new Set(lockedAssetKeys.value)
  return assets.value
    .map((a) => a.key)
    .filter((key) => key && !locked.has(key))
})

watch(
  () => form.profileId,
  () => {
    const profile = selectedProfile.value
    if (profile?.name && !form.templateName) {
      form.templateName = profile.name
    }
    preselectFlowsForProfile()
    preselectExtraApis()
  },
)

watch(lockedApiIds, () => {
  // 从 extra 中去掉已锁定的
  const locked = new Set(lockedApiIds.value)
  form.extraApiIds = form.extraApiIds.filter((id) => !locked.has(String(id)))
  preselectExtraApis()
})

watch(lockedAssetKeys, () => {
  const locked = new Set(lockedAssetKeys.value)
  form.extraAssetKeys = form.extraAssetKeys.filter((k) => !locked.has(k))
})

function preselectFlowsForProfile() {
  const profile = selectedProfile.value
  const prefabPaths = new Set(
    (profile?.apis || [])
      .map((a) => String(a.apiPath || '').trim())
      .filter(Boolean),
  )
  const next = []
  for (const flow of flows.value) {
    const name = String(flow.flowName || '')
    const ids = collectGraphHttpApiIds(flow.graphJson)
    const byId = new Map(apis.value.map((a) => [String(a.testProjectApiId), a]))
    const hitsProfile = ids.some((id) => {
      const api = byId.get(String(id))
      return api && prefabPaths.has(String(api.apiPath || '').trim())
    })
    if (name.includes('登录') || hitsProfile) {
      next.push(String(flow.testFlowId))
    }
  }
  form.flowIds = next
}

function preselectExtraApis() {
  const locked = new Set(lockedApiIds.value)
  form.extraApiIds = extraApiCandidates.value
    .map((a) => String(a.testProjectApiId))
    .filter((id) => !locked.has(id))
}

async function onOpened() {
  loading.value = true
  try {
    form.overwriteByName = false
    form.includeEnv = true
    form.promptIds = []
    form.extraAssetKeys = []

    const profileList = props.profiles || []
    form.profileId = profileList[0]?.id ? String(profileList[0].id) : ''
    form.templateName = profileList[0]?.name || ''

    const pid = props.testProjectId
    const [flowRes, apiRes, assetRes, promptRes] = await Promise.all([
      listTestFlow({ testProjectId: pid, pageNum: 1, pageSize: 200 }),
      listTestProjectApi({ testProjectId: pid, pageNum: 1, pageSize: 500 }),
      listTestProjectAsset({ testProjectId: pid, pageNum: 1, pageSize: 200 }),
      listAiPromptTemplate({
        testProjectId: pid,
        templateScope: 'project',
        pageNum: 1,
        pageSize: 200,
      }),
    ])
    flows.value = flowRes?.rows || flowRes?.data || []
    apis.value = apiRes?.rows || apiRes?.data || []
    assets.value = assetRes?.rows || assetRes?.data || []
    prompts.value = (promptRes?.rows || promptRes?.data || []).filter(
      (p) => String(p.templateScope || 'project') === 'project',
    )

    preselectFlowsForProfile()
    preselectExtraApis()
  } catch (e) {
    proxy?.$modal?.msgError?.(e?.message || '加载项目数据失败')
  } finally {
    loading.value = false
  }
}

async function submit() {
  if (!String(form.templateName || '').trim()) {
    proxy?.$modal?.msgWarning?.('请填写模板名称')
    return
  }
  if (!form.profileId) {
    proxy?.$modal?.msgWarning?.('请选择 Auth Profile')
    return
  }
  saving.value = true
  try {
    const res = await saveAsAuthTemplate(props.testProjectId, {
      templateName: String(form.templateName).trim(),
      profileId: form.profileId,
      flowIds: form.flowIds,
      extraApiIds: form.extraApiIds,
      extraAssetKeys: form.extraAssetKeys,
      includeEnv: form.includeEnv,
      promptIds: form.promptIds,
      overwriteByName: form.overwriteByName,
      dryRun: false,
    })
    const data = res?.data ?? res
    const warnings = data?.warnings || []
    const id = data?.testProjectTemplateId
    let msg = id ? `已另存为项目模板（id=${id}）` : '已另存为项目模板'
    if (warnings.length) {
      msg += `；提示：${warnings.slice(0, 3).join('；')}`
    }
    proxy?.$modal?.msgSuccess?.(msg)
    visible.value = false
    emit('saved', data)
  } catch (e) {
    proxy?.$modal?.msgError?.(e?.message || '另存失败')
  } finally {
    saving.value = false
  }
}
</script>

<style scoped>
.save-as-tpl__profiles {
  display: flex;
  flex-wrap: wrap;
  gap: 8px 16px;
}

.save-as-tpl__checks {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 4px;
  max-height: 160px;
  overflow: auto;
}

.save-as-tpl__section {
  margin-bottom: 12px;
  padding: 10px 12px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 6px;
}

.save-as-tpl__section-title {
  font-weight: 600;
  margin-bottom: 8px;
}

.save-as-tpl__locked {
  margin: 4px 0;
  font-size: 13px;
  line-height: 1.5;
  word-break: break-all;
}

.save-as-tpl__locked span {
  color: var(--el-text-color-secondary);
}

.save-as-tpl__hint {
  margin: 4px 0 0;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
</style>

/**
 * 项目设置抽屉：Token、MCP 配置；供项目列表与 API 工作台复用。
 */
import { computed, reactive, ref, watch } from 'vue'

import { myProjectContext, getTestProject } from '@/api/project/testProject'
import { refreshProjectTokenByProjectId } from '@/api/project/testProjectUserSetting'
import { getHttpForwardAvailability, isWebDebugTransportSwitchable } from '@/transport/runtime'

import {
  readDebugHttpTransportPref,
  writeDebugHttpTransportPref,
} from '../utils/apiDebugUiPrefs'

const TOOLBAR_NO_ENV_ID = '0'

export function useProjectSettingDrawer(getProxy) {
  const projectSettingDrawerVisible = ref(false)
  const activeTestProjectId = ref(null)
  const activeProjectName = ref('')
  const settingContext = ref(null)
  const settingLoading = ref(false)
  const projectHttpTransport = ref('browser')

  const settingForm = reactive({
    testProjectUserSettingId: null,
    testProjectId: null,
    testProjectEnvId: TOOLBAR_NO_ENV_ID,
    projectToken: '',
  })

  const forwardAvail = computed(() => getHttpForwardAvailability())
  const showHttpTransportSetting = computed(() => isWebDebugTransportSwitchable())

  const drawerTitle = computed(() => {
    const name = activeProjectName.value?.trim()
    return name ? `${name} · 项目设置` : '项目设置'
  })

  function loadHttpTransportPref() {
    const pid = activeTestProjectId.value
    if (!pid) {
      projectHttpTransport.value = 'browser'
      return
    }
    projectHttpTransport.value = readDebugHttpTransportPref(pid) || 'browser'
  }

  watch(projectHttpTransport, (mode) => {
    const pid = activeTestProjectId.value
    if (pid && (mode === 'browser' || mode === 'browser-java-forward')) {
      writeDebugHttpTransportPref(pid, mode)
    }
  })

  function applySettingFromContext(setting) {
    if (setting) {
      settingForm.testProjectUserSettingId = setting.testProjectUserSettingId
      settingForm.testProjectId = setting.testProjectId
      settingForm.testProjectEnvId = setting.testProjectEnvId ?? TOOLBAR_NO_ENV_ID
      settingForm.projectToken = setting.projectToken || ''
    } else {
      settingForm.testProjectUserSettingId = null
      settingForm.testProjectId = activeTestProjectId.value
      settingForm.testProjectEnvId = TOOLBAR_NO_ENV_ID
      settingForm.projectToken = ''
    }
  }

  function fetchProjectContext(drawerLoading = false) {
    const testProjectId = activeTestProjectId.value
    if (!testProjectId) {
      return Promise.resolve()
    }
    if (drawerLoading) {
      settingLoading.value = true
    }
    return myProjectContext(testProjectId)
      .then((res) => {
        const setting = res.data?.setting ?? null
        settingContext.value = setting
        applySettingFromContext(setting)
      })
      .catch(() => {
        settingContext.value = null
        applySettingFromContext(null)
      })
      .finally(() => {
        if (drawerLoading) {
          settingLoading.value = false
        }
      })
  }

  function openProjectSetting(row) {
    openProjectSettingById(row.testProjectId, row.projectName || '')
  }

  function openProjectSettingById(testProjectId, projectName = '') {
    activeTestProjectId.value = testProjectId
    activeProjectName.value = projectName || ''
    if (!projectName && testProjectId) {
      void getTestProject(testProjectId).then((res) => {
        if (String(activeTestProjectId.value) === String(testProjectId)) {
          activeProjectName.value = res.data?.projectName || ''
        }
      })
    }
    loadHttpTransportPref()
    projectSettingDrawerVisible.value = true
  }

  function getSettingOnDrawerOpen() {
    return fetchProjectContext(true)
  }

  function handleRefreshToken() {
    const proxy = getProxy()
    const testProjectId = activeTestProjectId.value
    if (!testProjectId || !proxy) {
      return
    }
    proxy.$modal.confirm('刷新Token后，旧的Token将失效，是否确认刷新？').then(() => {
      refreshProjectTokenByProjectId(testProjectId).then((response) => {
        if (response.code === 200) {
          proxy.$modal.msgSuccess('Token刷新成功，新Token已生成')
          settingForm.projectToken = response.data
          if (settingContext.value !== null) {
            settingContext.value = {
              ...settingContext.value,
              projectToken: response.data,
            }
          }
        } else {
          proxy.$modal.msgError(response.msg || 'Token刷新失败')
        }
      }).catch(() => {
        proxy.$modal.msgError('Token刷新失败')
      })
    }).catch(() => {})
  }

  return {
    projectSettingDrawerVisible,
    activeTestProjectId,
    activeProjectName,
    settingContext,
    settingLoading,
    settingForm,
    projectHttpTransport,
    forwardAvail,
    showHttpTransportSetting,
    drawerTitle,
    openProjectSetting,
    openProjectSettingById,
    getSettingOnDrawerOpen,
    handleRefreshToken,
  }
}

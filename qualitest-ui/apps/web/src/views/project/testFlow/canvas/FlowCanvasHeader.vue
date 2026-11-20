<template>
  <header class="flow-canvas-header">
    <div class="flow-canvas-header__brand">
      <button class="flow-canvas-header__back" type="button" @click="emit('back')">
        <svg aria-hidden="true" class="flow-canvas-header__back-icon" viewBox="0 0 24 24">
          <path
              d="M15 18l-6-6 6-6"
              fill="none"
              stroke="currentColor"
              stroke-linecap="round"
              stroke-linejoin="round"
              stroke-width="2"
          />
        </svg>
        {{ backLabel }}
      </button>
      <div class="flow-canvas-header__title-wrap">
        <span class="flow-canvas-header__title">{{ flowName || '测试流' }}</span>
        <span
            v-if="dirty"
            class="flow-canvas-header__dirty"
            title="画布有未保存的修改"
        >
          <span class="flow-canvas-header__dirty-dot" aria-hidden="true" />
          未保存
        </span>
        <span
            v-if="leaseBadge"
            :class="[
              'flow-canvas-header__lease',
              leaseBadge.kind === 'self' ? 'is-self' : 'is-other',
            ]"
            :title="leaseBadge.text"
        >
          {{ leaseBadge.text }}
        </span>
      </div>
    </div>
    <div
        v-for="banner in externalConflictBanners"
        :key="banner.id"
        class="flow-canvas-header__external-banner"
        role="status"
    >
      <span>{{ banner.text }}</span>
      <button class="btn btn--ghost" type="button" @click="banner.onAccept">
        {{ banner.acceptLabel }}
      </button>
      <button class="btn btn--ghost" type="button" @click="banner.onDismiss">
        稍后
      </button>
    </div>
    <div class="flow-canvas-header__actions">
      <ProjectRunHubEntry
          v-if="!isTemplateCanvas && testProjectId"
          :test-project-id="testProjectId"
          variant="icon"
          button-class="flow-canvas-header__icon-btn"
          icon-class="flow-canvas-header__header-icon"
      />
      <button
          v-if="!isTemplateCanvas"
          type="button"
          class="flow-canvas-header__icon-btn"
          title="项目设置"
          aria-label="项目设置"
          @click="emit('open-project-setting')"
      >
        <svg-icon class="flow-canvas-header__header-icon" icon-class="system"/>
      </button>
      <button
          v-if="!isTemplateCanvas"
          type="button"
          class="flow-canvas-header__icon-btn"
          title="API"
          aria-label="跳转到 API 工作台"
          @click="emit('open-api-workspace')"
      >
        <svg-icon class="flow-canvas-header__header-icon" icon-class="project-api"/>
      </button>
      <button :disabled="!canUndo" class="btn btn--ghost" title="撤销" type="button" @click="emit('undo')">
        ↩ 撤销
      </button>
      <div
          ref="tidySplitEl"
          :class="{ 'is-open': tidyMenuOpen, 'is-disabled': !canEditFlow || isTemplateCanvas }"
          class="tidy-split"
      >
        <el-tooltip :content="tidyMainTip" :disabled="tidyMenuOpen" placement="bottom">
          <button
              :disabled="!canEditFlow || isTemplateCanvas"
              class="tidy-split__main"
              type="button"
              @click="emit('run-tidy')"
          >
            {{ tidyLabel }}
          </button>
        </el-tooltip>
        <button
            :aria-expanded="tidyMenuOpen"
            :disabled="!canEditFlow || isTemplateCanvas"
            aria-haspopup="menu"
            aria-label="选择排版方式"
            class="tidy-split__more"
            type="button"
            @click.stop="tidyMenuOpen = !tidyMenuOpen"
        >
          <span aria-hidden="true" class="tidy-split__caret"/>
        </button>
        <div v-if="tidyMenuOpen" class="tidy-split__menu" role="menu">
          <button
              v-for="preset in FLOW_LAYOUT_PRESETS"
              :key="preset.id"
              :class="{ 'is-active': preset.id === layoutPresetId }"
              class="tidy-split__item"
              role="menuitem"
              type="button"
              @click="onPickTidy(preset.id)"
          >
            {{ preset.label }}
          </button>
        </div>
      </div>
      <button
          v-if="canEditFlow"
          class="btn"
          type="button"
          @click="emit('import')"
      >导入</button>
      <button
          v-if="canExportFlow"
          class="btn"
          type="button"
          @click="emit('export')"
      >导出</button>
      <button
          :disabled="!canUseAiDesign"
          :title="aiAssistantTitle"
          class="btn btn--ai"
          type="button"
          @click="emit('open-ai')"
      >
        AI 助手
        <span v-if="stagingPendingCount > 0" class="flow-canvas-header__ai-badge">{{ stagingPendingCount }}</span>
      </button>
      <button
          v-if="stagingPendingCount > 0 && canEditFlow"
          :disabled="stagingBatchBusy || stagingReadyPendingCount === 0"
          :title="stagingConfirmAllTitle"
          class="btn btn--ghost"
          type="button"
          @click="emit('confirm-all-ready')"
      >
        {{ stagingConfirmAllLabel }}
      </button>
      <button
          v-if="stagingBatchPausedOnUnitId && canEditFlow"
          :disabled="stagingBatchBusy"
          class="btn btn--ghost"
          title="跳过失败项，继续确认其余就绪变更"
          type="button"
          @click="emit('resume-confirm-all')"
      >
        跳过失败继续
      </button>
      <FlowCanvasHelpPopover v-model:open="helpOpenProxy" />
      <button
          v-if="!isTemplateCanvas"
          :disabled="loading || !canEditFlow || refreshAuthLoading"
          :title="canEditFlow ? '按项目鉴权配置刷新本流托管头（进 Staging 确认）' : '当前账号无编辑权限'"
          class="btn btn--ghost"
          type="button"
          @click="emit('refresh-auth')"
      >
        {{ refreshAuthLoading ? '刷新中…' : '刷新鉴权头' }}
      </button>
      <button
          :disabled="loading || !canEditFlow"
          :title="saveButtonTitle"
          :class="['btn', 'btn--primary', { 'is-dirty': dirty && !loading }]"
          type="button"
          @click="emit('save')"
      >
        {{ loading ? '保存中…' : (dirty ? '保存更改' : '保存') }}
        <span v-if="stagingPendingCount > 0" class="flow-canvas-header__ai-badge">{{ stagingPendingCount }}</span>
      </button>
    </div>
  </header>
</template>

<script setup>
/**
 * 测试流画布顶栏：返回、脏标记、写锁、排版、导入导出、AI/Staging、保存。
 */
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'

import ProjectRunHubEntry from '@/views/project/testProject/components/ProjectRunHubEntry.vue'

import FlowCanvasHelpPopover from '../components/FlowCanvasHelpPopover.vue'
import { FLOW_LAYOUT_PRESETS } from '../utils/flowLayoutPreset'

const props = defineProps({
  backLabel: { type: String, default: '返回列表' },
  flowName: { type: String, default: '' },
  dirty: { type: Boolean, default: false },
  loading: { type: Boolean, default: false },
  leaseBadge: { type: Object, default: null },
  externalConflictBanners: { type: Array, default: () => [] },
  isTemplateCanvas: { type: Boolean, default: false },
  testProjectId: { type: String, default: '' },
  canUndo: { type: Boolean, default: false },
  canEditFlow: { type: Boolean, default: false },
  canExportFlow: { type: Boolean, default: false },
  canUseAiDesign: { type: Boolean, default: false },
  tidyLabel: { type: String, default: '' },
  tidyMainTip: { type: String, default: '' },
  layoutPresetId: { type: String, default: '' },
  aiAssistantTitle: { type: String, default: '' },
  saveButtonTitle: { type: String, default: '' },
  stagingPendingCount: { type: Number, default: 0 },
  stagingReadyPendingCount: { type: Number, default: 0 },
  stagingBatchBusy: { type: Boolean, default: false },
  stagingBatchPausedOnUnitId: { type: [String, null], default: null },
  stagingConfirmAllLabel: { type: String, default: '' },
  stagingConfirmAllTitle: { type: String, default: '' },
  refreshAuthLoading: { type: Boolean, default: false },
  helpOpen: { type: Boolean, default: false },
})

const emit = defineEmits([
  'back',
  'undo',
  'run-tidy',
  'pick-tidy-preset',
  'import',
  'export',
  'open-ai',
  'confirm-all-ready',
  'resume-confirm-all',
  'refresh-auth',
  'save',
  'open-project-setting',
  'open-api-workspace',
  'update:helpOpen',
])

const tidySplitEl = ref(null)
const tidyMenuOpen = ref(false)

const helpOpenProxy = computed({
  get: () => props.helpOpen,
  set: (v) => emit('update:helpOpen', v),
})

function onPickTidy(id) {
  tidyMenuOpen.value = false
  emit('pick-tidy-preset', id)
}

function onTidyMenuPointerDown(ev) {
  if (!tidyMenuOpen.value) return
  const root = tidySplitEl.value
  if (root && ev.target instanceof Node && root.contains(ev.target)) return
  tidyMenuOpen.value = false
}

onMounted(() => document.addEventListener('pointerdown', onTidyMenuPointerDown))
onBeforeUnmount(() => document.removeEventListener('pointerdown', onTidyMenuPointerDown))
</script>

<template>
  <el-dialog
      :model-value="visible"
      :close-on-click-modal="!running"
      append-to-body
      :title="`运行套件：${suiteName || ''}`"
      width="720px"
      @close="handleClose"
      @update:model-value="emit('update:visible', $event)"
  >
    <div v-if="!started" class="batch-setup">
      <el-form label-width="100px">
        <el-form-item label="队列">
          <span>{{ itemCountText }}</span>
        </el-form-item>
        <el-form-item label="环境覆盖">
          <el-select
              v-model="envOverrideId"
              clearable
              filterable
              placeholder="不覆盖（用各场景自身环境）"
              style="width: 100%"
          >
            <el-option
                v-for="env in envOptions"
                :key="env.testProjectEnvId"
                :label="env.envName"
                :value="String(env.testProjectEnvId)"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="失败策略">
          <el-switch
              v-model="stopOnNonPass"
              active-text="非通过即停"
              inactive-text="继续跑剩余"
          />
        </el-form-item>
      </el-form>
    </div>

    <SuiteRunResultPanel
        v-else
        :hint="running ? '串行执行中，可中止后续队列（已触发的 Run 不会强制中断）。' : ''"
        :items="progress.items"
        :test-project-id="testProjectId"
    />

    <template #footer>
      <template v-if="!started">
        <el-button @click="emit('update:visible', false)">取消</el-button>
        <el-button type="primary" @click="handleStart">开始运行</el-button>
      </template>
      <template v-else>
        <el-button v-if="running" type="danger" plain @click="cancel">中止队列</el-button>
        <el-button :disabled="running" type="primary" @click="emit('update:visible', false)">关闭</el-button>
      </template>
    </template>
  </el-dialog>
</template>

<script setup>
/**
 * 套件运行对话框：开跑配置 + 进度汇总。
 */
import { computed, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'

import { listTestProjectEnv } from '@/api/project/testProjectEnv'
import { useFlowBatchRun } from '../composables/useFlowBatchRun'
import SuiteRunResultPanel from './SuiteRunResultPanel.vue'

const props = defineProps({
  visible: { type: Boolean, default: false },
  testProjectId: { type: String, required: true },
  suiteId: { type: String, default: '' },
  suiteName: { type: String, default: '' },
  itemCount: { type: Number, default: 0 },
  presetStopOnNonPass: { type: Boolean, default: true },
})

const emit = defineEmits(['update:visible'])

const envOptions = ref([])
const envOverrideId = ref('')
const stopOnNonPass = ref(true)
const started = ref(false)
const { running, progress, start, cancel, reset } = useFlowBatchRun()

const itemCountText = computed(() => {
  const n = Number(props.itemCount) || 0
  return n > 0 ? `${n} 项（来自套件）` : '项数以套件定义为准'
})

watch(
    () => props.visible,
    async (open) => {
      if (!open) return
      reset()
      started.value = false
      envOverrideId.value = ''
      stopOnNonPass.value = props.presetStopOnNonPass !== false
      await loadEnvs()
    },
)

async function loadEnvs() {
  try {
    const res = await listTestProjectEnv({
      testProjectId: props.testProjectId,
      pageNum: 1,
      pageSize: 200,
    })
    envOptions.value = res?.rows ?? []
  } catch {
    envOptions.value = []
  }
}

async function handleStart() {
  if (!props.suiteId) {
    ElMessage.warning('缺少套件 ID')
    return
  }
  started.value = true
  await start({
    testProjectId: props.testProjectId,
    testSuiteId: props.suiteId,
    suiteNameSnapshot: props.suiteName || undefined,
    testProjectEnvId: envOverrideId.value || undefined,
    stopOnNonPass: stopOnNonPass.value,
  })
}

function handleClose() {
  if (running.value) {
    cancel()
  }
  emit('update:visible', false)
}
</script>

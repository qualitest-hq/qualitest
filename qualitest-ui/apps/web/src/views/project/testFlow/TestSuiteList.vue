<template>
  <div class="suite-list-page">
    <el-row :gutter="10" class="mb8 toolbar-row">
      <el-col :span="1.5">
        <el-button icon="ArrowLeft" plain @click="handleBack">返回项目</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button icon="List" plain @click="goFlows">测试流</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button
            v-hasPermi="['project:testProject:add']"
            icon="Plus"
            plain
            type="primary"
            @click="openEdit()"
        >新建套件
        </el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button icon="Clock" plain @click="openHistory()">运行历史</el-button>
      </el-col>
      <el-col :span="6">
        <el-input
            v-model="suiteNameQuery"
            clearable
            placeholder="按名称搜索"
            prefix-icon="Search"
            @clear="getList"
            @keyup.enter="getList"
        />
      </el-col>
      <el-col :span="1.5">
        <el-button icon="Search" type="primary" @click="getList">搜索</el-button>
      </el-col>
    </el-row>

    <el-table v-loading="loading" :data="suiteList" table-layout="fixed">
      <el-table-column label="套件名称" min-width="140" prop="suiteName" show-overflow-tooltip />
      <el-table-column label="说明" min-width="160" prop="suiteDescription" show-overflow-tooltip />
      <el-table-column align="center" label="项数" min-width="80" prop="itemCount" />
      <el-table-column align="center" label="非通过即停" min-width="110">
        <template #default="{ row }">
          {{ isStopOnNonPass(row.stopOnNonPass) ? '是' : '否' }}
        </template>
      </el-table-column>
      <el-table-column align="center" label="更新时间" min-width="160" prop="updateTime" />
      <el-table-column align="center" label="操作" width="260">
        <template #default="{ row }">
          <el-button
              v-hasPermi="['project:testProject:edit']"
              link
              type="primary"
              @click="runSuite(row)"
          >运行
          </el-button>
          <el-button link type="primary" @click="openHistory(row)">历史</el-button>
          <el-button
              v-hasPermi="['project:testProject:edit']"
              link
              type="primary"
              @click="openEdit(row)"
          >编辑
          </el-button>
          <el-button
              v-hasPermi="['project:testProject:remove']"
              link
              type="danger"
              @click="handleDelete(row)"
          >删除
          </el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog
        v-model="editVisible"
        :title="editForm.testSuiteId ? '编辑套件' : '新建套件'"
        append-to-body
        width="640px"
        @opened="onEditOpened"
    >
      <el-form label-width="90px">
        <el-form-item label="名称" required>
          <el-input v-model="editForm.suiteName" maxlength="100" />
        </el-form-item>
        <el-form-item label="说明">
          <el-input v-model="editForm.suiteDescription" :rows="2" maxlength="500" type="textarea" />
        </el-form-item>
        <el-form-item label="失败策略">
          <el-switch
              v-model="editForm.stopOnNonPass"
              active-text="非通过即停"
              inactive-text="继续跑剩余"
          />
        </el-form-item>
        <el-form-item label="勾选场景">
          <el-tree
              ref="editTreeRef"
              :data="editTreeData"
              :default-checked-keys="editChecked"
              :props="{ label: 'label', children: 'children' }"
              node-key="key"
              show-checkbox
              default-expand-all
              style="width: 100%; max-height: 320px; overflow: auto"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editVisible = false">取消</el-button>
        <el-button :loading="editSubmitting" type="primary" @click="submitEdit">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog
        v-model="historyVisible"
        :title="historyTitle"
        append-to-body
        width="780px"
        @closed="resetHistoryExpand"
    >
      <el-table
          v-loading="historyLoading"
          :data="historyList"
          max-height="480"
          row-key="testSuiteRunId"
          @expand-change="onHistoryExpand"
      >
        <el-table-column type="expand">
          <template #default="{ row }">
            <div
                v-loading="!!historyExpandLoading[String(row.testSuiteRunId)]"
                class="history-expand"
            >
              <SuiteRunResultPanel
                  :items="historyExpandItems[String(row.testSuiteRunId)] || []"
                  :test-project-id="testProjectId"
              />
            </div>
          </template>
        </el-table-column>
        <el-table-column align="center" label="开始时间" prop="startedAt" width="170" />
        <el-table-column align="center" label="状态" prop="status" width="100">
          <template #default="{ row }">
            <el-tag :type="runStatusTagType(row.status)" size="small">
              {{ runStatusLabel(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column align="center" label="通过" prop="passedCount" width="70" />
        <el-table-column align="center" label="失败" prop="failedCount" width="70" />
        <el-table-column align="center" label="结束时间" prop="finishedAt" width="170" />
        <el-table-column align="center" label="总耗时" width="100">
          <template #default="{ row }">
            {{ formatHistoryDuration(row.startedAt, row.finishedAt) }}
          </template>
        </el-table-column>
      </el-table>
      <pagination
          v-show="historyTotal > 0"
          v-model:limit="historyQuery.pageSize"
          v-model:page="historyQuery.pageNum"
          :page-sizes="[10, 20, 50]"
          :total="historyTotal"
          class="history-pagination"
          layout="total, sizes, prev, pager, next"
          @pagination="loadHistoryList"
      />
      <template #footer>
        <el-button type="primary" @click="historyVisible = false">关闭</el-button>
      </template>
    </el-dialog>

    <FlowBatchRunDialog
        v-model:visible="batchVisible"
        :item-count="batchItemCount"
        :preset-stop-on-non-pass="batchStopOnNonPass"
        :suite-id="batchSuiteId"
        :suite-name="batchSuiteName"
        :test-project-id="testProjectId"
    />
  </div>
</template>

<script setup>
/**
 * 项目测试套件列表：CRUD、一键运行、运行历史回看。
 */
import { computed, nextTick, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useRoute, useRouter } from 'vue-router'

import { listTestFlow } from '@/api/project/testFlow'
import {
  addTestSuite,
  delTestSuite,
  getTestSuite,
  listTestSuite,
  updateTestSuite,
} from '@/api/project/testSuite'
import { getTestSuiteRun, listTestSuiteRun } from '@/api/project/testSuiteRun'
import { runStatusLabel, runStatusTagType } from './constants/runStatus'
import FlowBatchRunDialog from './components/FlowBatchRunDialog.vue'
import SuiteRunResultPanel from './components/SuiteRunResultPanel.vue'
import { useProjectTabTitle } from './composables/useProjectTabTitle'
import { formatDurationMs } from './utils/runStepDisplay'
import {
  buildBatchFlowTree,
  buildBatchQueueFromChecks,
  defaultCheckedKeysForTree,
  flowOnlyCheckKey,
  scenarioCheckKey,
} from './utils/batchRunScenarios'

const route = useRoute()
const router = useRouter()
const testProjectId = ref(String(route.params.testProjectId ?? ''))
const { loadProjectName } = useProjectTabTitle(route, () => '测试套件')

const loading = ref(false)
const suiteList = ref([])
const suiteNameQuery = ref('')

const editVisible = ref(false)
const editSubmitting = ref(false)
const editTreeRef = ref()
const allFlows = ref([])
const editChecked = ref([])
const editForm = reactive({
  testSuiteId: '',
  suiteName: '',
  suiteDescription: '',
  stopOnNonPass: true,
})

const historyVisible = ref(false)
const historyLoading = ref(false)
const historyList = ref([])
const historyTotal = ref(0)
const historyTitle = ref('套件运行历史')
const historyQuery = reactive({
  testSuiteId: '',
  pageNum: 1,
  pageSize: 10,
})
/** runId → 展开项列表（懒加载缓存） */
const historyExpandItems = reactive({})
/** runId → loading */
const historyExpandLoading = reactive({})

const batchVisible = ref(false)
const batchSuiteId = ref('')
const batchSuiteName = ref('')
const batchItemCount = ref(0)
const batchStopOnNonPass = ref(true)

function isStopOnNonPass(value) {
  return value === 1 || value === true
}

/** 历史总耗时：有结束时间才算，否则留空 */
function formatHistoryDuration(startedAt, finishedAt) {
  if (!startedAt || !finishedAt) return ''
  const start = new Date(startedAt).getTime()
  const end = new Date(finishedAt).getTime()
  if (Number.isNaN(start) || Number.isNaN(end) || end < start) return ''
  return formatDurationMs(end - start)
}

const editTree = computed(() => buildBatchFlowTree(allFlows.value))
const editTreeData = computed(() =>
    editTree.value.map((flow) => {
      if (!flow.scenarios.length) {
        return { key: flowOnlyCheckKey(flow.testFlowId), label: flow.flowName }
      }
      return {
        key: `flow:${flow.testFlowId}`,
        label: flow.flowName,
        children: flow.scenarios.map((sc) => ({
          key: scenarioCheckKey(flow.testFlowId, sc.scenarioId),
          label: sc.active ? `${sc.scenarioName}（活动）` : sc.scenarioName,
        })),
      }
    }),
)

function handleBack() {
  router.push(`/project/testProject/detail/${testProjectId.value}`)
}

function goFlows() {
  router.push(`/project/testProject/flows/${testProjectId.value}`)
}

async function getList() {
  loading.value = true
  try {
    const res = await listTestSuite({
      testProjectId: testProjectId.value,
      suiteName: String(suiteNameQuery.value ?? '').trim() || undefined,
      pageNum: 1,
      pageSize: 200,
    })
    suiteList.value = res.rows ?? []
  } finally {
    loading.value = false
  }
}

async function ensureFlows() {
  if (allFlows.value.length) return
  const res = await listTestFlow({ testProjectId: testProjectId.value, pageNum: 1, pageSize: 500 })
  allFlows.value = res.rows ?? []
}

/**
 * 默认套件名：测试套件1、测试套件2…（取项目内已有同名序号最大值 +1）
 */
function nextDefaultSuiteName(suites) {
  let max = 0
  for (const s of suites || []) {
    const m = String(s?.suiteName ?? '').trim().match(/^测试套件(\d+)$/)
    if (m) {
      max = Math.max(max, Number(m[1]) || 0)
    }
  }
  return `测试套件${max + 1}`
}

async function resolveDefaultSuiteName() {
  try {
    const res = await listTestSuite({
      testProjectId: testProjectId.value,
      pageNum: 1,
      pageSize: 500,
    })
    return nextDefaultSuiteName(res.rows ?? [])
  } catch {
    return nextDefaultSuiteName(suiteList.value)
  }
}

async function openEdit(row) {
  await ensureFlows()
  if (row?.testSuiteId) {
    const res = await getTestSuite(row.testSuiteId)
    const detail = res?.data ?? res
    editForm.testSuiteId = String(detail.testSuiteId)
    editForm.suiteName = detail.suiteName ?? ''
    editForm.suiteDescription = detail.suiteDescription ?? ''
    editForm.stopOnNonPass = isStopOnNonPass(detail.stopOnNonPass)
    const keys = []
    for (const it of detail.items || []) {
      if (it.runScenarioId) {
        keys.push(scenarioCheckKey(String(it.testFlowId), String(it.runScenarioId)))
      } else {
        keys.push(flowOnlyCheckKey(String(it.testFlowId)))
      }
    }
    editChecked.value = keys
  } else {
    editForm.testSuiteId = ''
    editForm.suiteName = await resolveDefaultSuiteName()
    editForm.suiteDescription = ''
    editForm.stopOnNonPass = true
    editChecked.value = defaultCheckedKeysForTree(editTree.value)
  }
  editVisible.value = true
}

async function onEditOpened() {
  await nextTick()
  editTreeRef.value?.setCheckedKeys?.(editChecked.value)
}

async function submitEdit() {
  const name = String(editForm.suiteName ?? '').trim()
  if (!name) {
    ElMessage.warning('请输入套件名称')
    return
  }
  const checked = (editTreeRef.value?.getCheckedKeys?.(true) ?? []).filter(
      (k) => !String(k).startsWith('flow:'),
  )
  const queue = buildBatchQueueFromChecks(editTree.value, checked)
  if (!queue.length) {
    ElMessage.warning('请至少勾选一个场景')
    return
  }
  const payload = {
    testProjectId: testProjectId.value,
    suiteName: name,
    suiteDescription: String(editForm.suiteDescription ?? '').trim(),
    stopOnNonPass: editForm.stopOnNonPass,
    items: queue.map((it, idx) => ({
      testFlowId: it.testFlowId,
      runScenarioId: it.runScenarioId,
      sortNum: idx,
    })),
  }
  editSubmitting.value = true
  try {
    if (editForm.testSuiteId) {
      await updateTestSuite({ ...payload, testSuiteId: editForm.testSuiteId })
      ElMessage.success('修改成功')
    } else {
      await addTestSuite(payload)
      ElMessage.success('创建成功')
    }
    editVisible.value = false
    await getList()
  } finally {
    editSubmitting.value = false
  }
}

async function handleDelete(row) {
  await ElMessageBox.confirm(`确认删除套件「${row.suiteName}」吗？`, '提示', { type: 'warning' })
  await delTestSuite(row.testSuiteId)
  ElMessage.success('删除成功')
  await getList()
}

function runSuite(row) {
  batchSuiteId.value = String(row.testSuiteId)
  batchSuiteName.value = row.suiteName ?? ''
  batchItemCount.value = Number(row.itemCount) || 0
  batchStopOnNonPass.value = isStopOnNonPass(row.stopOnNonPass)
  batchVisible.value = true
}

function resetHistoryExpand() {
  historyTitle.value = '套件运行历史'
  historyQuery.testSuiteId = ''
  historyQuery.pageNum = 1
  historyQuery.pageSize = 10
  historyTotal.value = 0
  historyList.value = []
  for (const key of Object.keys(historyExpandItems)) {
    delete historyExpandItems[key]
  }
  for (const key of Object.keys(historyExpandLoading)) {
    delete historyExpandLoading[key]
  }
}

async function loadHistoryList() {
  historyLoading.value = true
  try {
    const res = await listTestSuiteRun({
      testProjectId: testProjectId.value,
      testSuiteId: historyQuery.testSuiteId || undefined,
      pageNum: historyQuery.pageNum,
      pageSize: historyQuery.pageSize,
    })
    historyList.value = res.rows ?? []
    historyTotal.value = Number(res.total) || 0
  } finally {
    historyLoading.value = false
  }
}

async function openHistory(row) {
  resetHistoryExpand()
  historyQuery.testSuiteId = row?.testSuiteId ? String(row.testSuiteId) : ''
  const suiteName = String(row?.suiteName ?? '').trim()
  historyTitle.value = suiteName ? `${suiteName}·套件运行历史` : '套件运行历史'
  historyVisible.value = true
  await loadHistoryList()
}

/**
 * 展开行懒加载该次 suite_run 的项明细。
 * @param row 当前行
 * @param expandedRows 当前已展开行集合
 */
async function onHistoryExpand(row, expandedRows) {
  const runId = String(row?.testSuiteRunId ?? '').trim()
  if (!runId) return
  const opened = (expandedRows || []).some((r) => String(r?.testSuiteRunId) === runId)
  if (!opened) return
  if (historyExpandItems[runId] || historyExpandLoading[runId]) return

  historyExpandLoading[runId] = true
  try {
    const res = await getTestSuiteRun(runId)
    const detail = res?.data ?? res
    const items = Array.isArray(detail?.items) ? detail.items : []
    historyExpandItems[runId] = items.map((it) => ({
      key: String(it.testSuiteRunItemId ?? `${it.testFlowId}::${it.runScenarioId ?? ''}`),
      testFlowId: String(it.testFlowId ?? ''),
      flowName: String(it.flowNameSnapshot ?? it.testFlowId ?? ''),
      runScenarioId: it.runScenarioId ? String(it.runScenarioId) : undefined,
      scenarioName: String(it.scenarioNameSnapshot ?? '默认场景'),
      status: it.status || 'pending',
      runId: it.testFlowRunId != null ? String(it.testFlowRunId) : undefined,
      errorMessage: it.errorMessage || undefined,
      durationMs: it.durationMs != null ? Number(it.durationMs) : undefined,
    }))
  } catch {
    ElMessage.error('加载套件运行结果失败')
    historyExpandItems[runId] = []
  } finally {
    historyExpandLoading[runId] = false
  }
}

onMounted(async () => {
  await Promise.all([getList(), loadProjectName(testProjectId.value)])
})
</script>

<style scoped lang="scss">
.suite-list-page {
  height: 100%;
  padding: 16px 20px;
  overflow: auto;
  background: #fff;
}

.toolbar-row {
  align-items: center;
}

.history-expand {
  padding: 4px 8px 12px 12px;
  min-height: 48px;
}

.history-pagination {
  margin-top: 12px;
  margin-bottom: 0;
}
</style>

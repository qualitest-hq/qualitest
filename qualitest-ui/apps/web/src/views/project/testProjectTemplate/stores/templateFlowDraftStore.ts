/**
 * 模板预制流画布草稿桥：未点「确定」落库前，表单态经 Pinia + sessionStorage 与画布往返。
 * 画布只改 templateFlows[i].graphJson（及可选 flowName），回模板页再合并进抽屉表单。
 */
import { defineStore } from 'pinia'
import { ref } from 'vue'

const STORAGE_KEY = 'qualitest.templateFlowDraft.v2'

export type TemplateDialogMode = 'add' | 'edit' | 'view'

/** 与模板抽屉表单对齐的可序列化草稿 */
export interface TemplateFlowDraft {
  /** 模板主键；新建未保存时用 'new' */
  templateId: string
  dialogMode: TemplateDialogMode
  /** 打开画布时对应的 templateFlows 下标 */
  flowIndex: number
  /** 抽屉标题（回显用） */
  title?: string
  form: {
    testProjectTemplateId?: string | number | null
    templateName?: string
    pathPrefixText?: string
    enableStatus?: number
    sortNum?: number
    remark?: string
    builtinStatus?: number
    templateApis: unknown[]
    templateParams: unknown[]
    templateFlows: Array<{
      flowName?: string
      description?: string
      graphJson?: unknown
      [key: string]: unknown
    }>
    [key: string]: unknown
  }
  /** 画布保存后为 true，模板页应合并并保持抽屉打开 */
  dirtyFromCanvas?: boolean
  updatedAt?: number
}

function cloneDraft(draft: TemplateFlowDraft): TemplateFlowDraft {
  return JSON.parse(JSON.stringify(draft)) as TemplateFlowDraft
}

function readSession(): TemplateFlowDraft | null {
  try {
    const raw = sessionStorage.getItem(STORAGE_KEY)
    if (!raw) return null
    const parsed = JSON.parse(raw) as TemplateFlowDraft
    if (!parsed || typeof parsed !== 'object') return null
    if (!Array.isArray(parsed.form?.templateFlows)) return null
    return parsed
  } catch {
    return null
  }
}

function writeSession(draft: TemplateFlowDraft | null) {
  try {
    if (!draft) {
      sessionStorage.removeItem(STORAGE_KEY)
      return
    }
    sessionStorage.setItem(STORAGE_KEY, JSON.stringify(draft))
  } catch {
    // ignore quota / private mode
  }
}

export const useTemplateFlowDraftStore = defineStore('templateFlowDraft', () => {
  const draft = ref<TemplateFlowDraft | null>(readSession())

  function persist(next: TemplateFlowDraft | null) {
    draft.value = next ? cloneDraft(next) : null
    writeSession(draft.value)
  }

  /** 从模板抽屉打开画布前写入完整表单草稿 */
  function openCanvas(input: Omit<TemplateFlowDraft, 'updatedAt' | 'dirtyFromCanvas'>) {
    persist({
      ...cloneDraft(input as TemplateFlowDraft),
      dirtyFromCanvas: false,
      updatedAt: Date.now(),
    })
  }

  function getDraft(): TemplateFlowDraft | null {
    if (draft.value) return cloneDraft(draft.value)
    const fromSession = readSession()
    if (fromSession) {
      draft.value = fromSession
      return cloneDraft(fromSession)
    }
    return null
  }

  /** 画布内联改草稿表单字段（如补齐 templateApis 合成 id） */
  function patchForm(partial: Partial<TemplateFlowDraft['form']>) {
    const current = getDraft()
    if (!current) return false
    persist({
      ...current,
      form: { ...current.form, ...partial },
      updatedAt: Date.now(),
    })
    return true
  }

  /** 画布保存：写回指定下标的 graphJson（可选同步流名） */
  function saveFlowGraph(
    flowIndex: number,
    graphJson: unknown,
    meta?: { flowName?: string; description?: string },
  ): boolean {
    const current = getDraft()
    if (!current) return false
    const flows = [...(current.form.templateFlows || [])]
    if (flowIndex < 0 || flowIndex >= flows.length) return false
    const prev = flows[flowIndex] || {}
    flows[flowIndex] = {
      ...prev,
      graphJson,
      ...(meta?.flowName != null ? { flowName: meta.flowName } : {}),
      ...(meta?.description != null ? { description: meta.description } : {}),
    }
    persist({
      ...current,
      form: { ...current.form, templateFlows: flows },
      dirtyFromCanvas: true,
      updatedAt: Date.now(),
    })
    return true
  }

  /** 模板页消费草稿后清除「来自画布」脏标记（草稿本身可保留到取消/提交） */
  function clearCanvasDirtyFlag() {
    const current = getDraft()
    if (!current) return
    persist({ ...current, dirtyFromCanvas: false })
  }

  function clear() {
    persist(null)
  }

  return {
    draft,
    openCanvas,
    getDraft,
    patchForm,
    saveFlowGraph,
    clearCanvasDirtyFlag,
    clear,
  }
})

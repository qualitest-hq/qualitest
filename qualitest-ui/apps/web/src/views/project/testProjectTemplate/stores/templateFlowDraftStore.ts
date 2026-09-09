/**
 * 模板抽屉 ↔ 预制流只读画布 的草稿桥。
 *
 * 打开画布前：把整份未提交表单写入 Pinia + sessionStorage，避免跳转丢表单。
 * 画布页：只读加载 templateFlows[flowIndex].graphJson，不写回流图。
 * 返回抽屉：用草稿恢复表单与对话框模式（新增/编辑/查看），再由用户点「确定」才落库其它字段。
 */
import { defineStore } from 'pinia'
import { ref } from 'vue'

/** sessionStorage 键；换结构时改版本号以免读到坏数据 */
const STORAGE_KEY = 'qualitest.templateFlowDraft.v2'

/** 模板抽屉打开方式 */
export type TemplateDialogMode = 'add' | 'edit' | 'view'

/**
 * 可序列化草稿。
 * form 形状跟模板抽屉表单一致，便于来回合并。
 */
export interface TemplateFlowDraft {
  /** 模板主键；尚未保存的新建模板用 'new' */
  templateId: string
  /** 打开画布时的抽屉模式，返回时用来还原标题与是否可编辑其它字段 */
  dialogMode: TemplateDialogMode
  /** 当前查看的 templateFlows 下标 */
  flowIndex: number
  /** 抽屉标题文案 */
  title?: string
  /** 整份表单快照（含 templateApis / templateFlows 等） */
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
    templateEnvs?: unknown[]
    templateFlows: Array<{
      flowName?: string
      description?: string
      graphJson?: unknown
      [key: string]: unknown
    }>
    [key: string]: unknown
  }
  /**
   * 是否曾被画布改过草稿。
   * 当前预制流画布只读，不会置为 true；字段仍保留以免旧 session 解析报错。
   */
  dirtyFromCanvas?: boolean
  /** 最近写入时间戳 */
  updatedAt?: number
}

function cloneDraft(draft: TemplateFlowDraft): TemplateFlowDraft {
  return JSON.parse(JSON.stringify(draft)) as TemplateFlowDraft
}

/** 从 sessionStorage 读草稿；结构不对则当没有 */
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

/** 写入或清空 sessionStorage；配额满时静默忽略 */
function writeSession(draft: TemplateFlowDraft | null) {
  try {
    if (!draft) {
      sessionStorage.removeItem(STORAGE_KEY)
      return
    }
    sessionStorage.setItem(STORAGE_KEY, JSON.stringify(draft))
  } catch {
    // 隐私模式 / 配额：不影响主流程
  }
}

export const useTemplateFlowDraftStore = defineStore('templateFlowDraft', () => {
  const draft = ref<TemplateFlowDraft | null>(readSession())

  /** 内存 + session 双写 */
  function persist(next: TemplateFlowDraft | null) {
    draft.value = next ? cloneDraft(next) : null
    writeSession(draft.value)
  }

  /**
   * 跳转画布前调用：固化当前表单。
   * 返回抽屉后据此还原，避免用户在抽屉里未点确定的编辑丢失。
   */
  function openCanvas(input: Omit<TemplateFlowDraft, 'updatedAt' | 'dirtyFromCanvas'>) {
    persist({
      ...cloneDraft(input as TemplateFlowDraft),
      dirtyFromCanvas: false,
      updatedAt: Date.now(),
    })
  }

  /** 优先内存，否则回退 session（刷新画布页仍能加载） */
  function getDraft(): TemplateFlowDraft | null {
    if (draft.value) return cloneDraft(draft.value)
    const fromSession = readSession()
    if (fromSession) {
      draft.value = fromSession
      return cloneDraft(fromSession)
    }
    return null
  }

  /** 抽屉消费完草稿后把脏标记清掉（有旧 session 时也能清） */
  function clearCanvasDirtyFlag() {
    const current = getDraft()
    if (!current) return
    persist({ ...current, dirtyFromCanvas: false })
  }

  /** 取消抽屉或提交成功后清空，避免下次误用 */
  function clear() {
    persist(null)
  }

  return {
    draft,
    openCanvas,
    getDraft,
    clearCanvasDirtyFlag,
    clear,
  }
})

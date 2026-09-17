/**
 * 项目模板导入弹窗：精简包 AI 提示词的缓存与复制。
 *
 * 打开弹窗时预取正文；点击「复制提示词」时尽量同步写入剪贴板，
 * 避免按钮进入 loading 造成界面闪烁。
 */

import { copyTextSync } from '@/utils/clipboard'

/** 再导出，供仍从本模块引用 copyTextSync 的调用方使用 */
export { copyTextSync }

/** 已拉取的提示词正文缓存 */
let cachedAiPrompt = ''
/** 进行中的预取 Promise，避免并发重复请求 */
let aiPromptLoading = null

/**
 * 预取提示词正文。
 * @param {() => Promise<{ data?: string }>} fetcher 后端拉取函数
 * @returns {Promise<string>}
 */
export function prefetchTemplateAiPrompt(fetcher) {
  if (cachedAiPrompt) {
    return Promise.resolve(cachedAiPrompt)
  }
  if (aiPromptLoading) {
    return aiPromptLoading
  }
  aiPromptLoading = Promise.resolve()
    .then(() => fetcher())
    .then((res) => {
      cachedAiPrompt = String(res?.data || '').trim()
      return cachedAiPrompt
    })
    .finally(() => {
      aiPromptLoading = null
    })
  return aiPromptLoading
}

/**
 * 复制精简包生成提示词到剪贴板。
 * 有缓存则立即复制；否则先拉取再复制。
 *
 * @param {() => Promise<{ data?: string }>} fetcher
 * @returns {Promise<'ok'|'empty'|'fail'>}
 */
export function copyTemplateAiPrompt(fetcher) {
  const write = (text) => {
    if (!text) return 'empty'
    return copyTextSync(text) ? 'ok' : 'fail'
  }
  if (cachedAiPrompt) {
    return Promise.resolve(write(cachedAiPrompt))
  }
  return prefetchTemplateAiPrompt(fetcher)
    .then(write)
    .catch(() => 'fail')
}

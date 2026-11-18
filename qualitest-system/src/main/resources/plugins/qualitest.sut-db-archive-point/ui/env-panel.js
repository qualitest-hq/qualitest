/**
 * 库存档点环境面板（B）：浏览器 ESM，导出 mount(el, ctx) / unmount()。
 * ctx: { pluginId, envId, runId?, invoke(capability, args), config? }
 */
let host = null
let ctxRef = null
let loading = false
let errorText = ''
let rows = []
let deletingId = ''

function escapeHtml(s) {
  return String(s ?? '')
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
}

function formatCreatedAt(value) {
  if (value == null || value === '') return '-'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return String(value)
  return date.toLocaleString()
}

function extractSaves(res) {
  const data = res?.data
  if (Array.isArray(data?.saves)) return { saves: data.saves, invalid: false }
  const stdout = typeof data?.stdout === 'string' ? data.stdout : ''
  if (!stdout.trim()) return { saves: [], invalid: false }
  try {
    const parsed = JSON.parse(stdout)
    return { saves: Array.isArray(parsed?.saves) ? parsed.saves : [], invalid: false }
  } catch {
    return { saves: [], invalid: true }
  }
}

function visibleRows() {
  const runId = ctxRef?.runId
  if (runId == null || runId === '') return rows
  const id = String(runId)
  const prefix = `${id}-`
  return rows.filter((row) => {
    const saveId = String(row?.saveId ?? '')
    if (saveId.includes(prefix)) return true
    const metaRun = row?.runId ?? row?.meta?.runId
    return metaRun != null && String(metaRun) === id
  })
}

function render() {
  if (!host) return
  const list = visibleRows()
  const title = ctxRef?.runId ? '本运行存档' : '存档列表'
  const empty = ctxRef?.runId ? '本运行暂无存档' : '暂无存档'
  let body = ''
  if (errorText) {
    body += `<div style="color:#f56c6c;margin-bottom:8px;font-size:13px">${escapeHtml(errorText)}</div>`
  }
  if (!errorText && !loading && !list.length) {
    body += `<div style="color:#909399;padding:16px 0;text-align:center;font-size:13px">${escapeHtml(empty)}</div>`
  }
  if (list.length) {
    body += `<table style="width:100%;border-collapse:collapse;font-size:13px">
      <thead><tr>
        <th style="text-align:left;border-bottom:1px solid #ebeef5;padding:6px">saveId</th>
        <th style="text-align:left;border-bottom:1px solid #ebeef5;padding:6px">创建时间</th>
        <th style="text-align:center;border-bottom:1px solid #ebeef5;padding:6px;width:72px">操作</th>
      </tr></thead><tbody>`
    for (const row of list) {
      const sid = String(row?.saveId ?? '')
      const busy = deletingId === sid
      body += `<tr>
        <td style="padding:6px;border-bottom:1px solid #ebeef5;word-break:break-all">${escapeHtml(sid)}</td>
        <td style="padding:6px;border-bottom:1px solid #ebeef5">${escapeHtml(formatCreatedAt(row?.createdAt || row?.createTime))}</td>
        <td style="padding:6px;border-bottom:1px solid #ebeef5;text-align:center">
          <button type="button" data-del="${escapeHtml(sid)}" ${busy || !sid ? 'disabled' : ''}
            style="border:none;background:none;color:#f56c6c;cursor:pointer;font-size:13px">
            ${busy ? '…' : '删除'}
          </button>
        </td>
      </tr>`
    }
    body += '</tbody></table>'
  }
  host.innerHTML = `
    <div class="qt-archive-env-panel" style="min-height:120px">
      <div style="display:flex;align-items:center;justify-content:space-between;margin-bottom:8px">
        <strong style="font-size:13px">${escapeHtml(title)}</strong>
        <button type="button" data-refresh="1" ${loading ? 'disabled' : ''}
          style="border:none;background:none;color:#409eff;cursor:pointer;font-size:13px">刷新</button>
      </div>
      ${loading ? '<div style="color:#909399;font-size:13px">加载中…</div>' : body}
    </div>`
  host.querySelector('[data-refresh]')?.addEventListener('click', () => loadSaves())
  host.querySelectorAll('[data-del]').forEach((btn) => {
    btn.addEventListener('click', () => removeSave(btn.getAttribute('data-del')))
  })
}

async function loadSaves() {
  if (!ctxRef?.invoke) {
    errorText = '缺少 invoke'
    rows = []
    render()
    return
  }
  loading = true
  errorText = ''
  render()
  try {
    const args = {}
    const body = { }
    const res = await ctxRef.invoke('list', args)
    // 宿主 invoke 可能直接回 axios 风格，也可能已解包
    const parsed = extractSaves(res?.data !== undefined && res?.code !== undefined ? res : { data: res })
    rows = parsed.saves
    if (parsed.invalid) errorText = '存档列表格式无法解析'
  } catch (e) {
    rows = []
    errorText = e?.message || '加载存档失败'
  } finally {
    loading = false
    render()
  }
}

async function removeSave(saveId) {
  if (!saveId || !ctxRef?.invoke) return
  if (!window.confirm(`确认删除存档「${saveId}」？`)) return
  deletingId = String(saveId)
  render()
  try {
    await ctxRef.invoke('archive.delete', { saveId })
    await loadSaves()
  } catch (e) {
    errorText = e?.message || '删除存档失败'
  } finally {
    deletingId = ''
    render()
  }
}

export function mount(el, ctx) {
  host = el
  ctxRef = ctx || {}
  rows = []
  errorText = ''
  loadSaves()
  return { unmount }
}

export function unmount() {
  if (host) host.innerHTML = ''
  host = null
  ctxRef = null
  rows = []
  errorText = ''
  deletingId = ''
}

export default { mount, unmount }

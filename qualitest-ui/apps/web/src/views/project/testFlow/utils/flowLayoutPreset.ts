/**
 * 画布排版的四种方式。选中项写在这条测试流的 meta.layout 里，不记在浏览器本地。
 * 打开画布只恢复方式，不自动重排；点顶栏该项才重算全部节点坐标，并标成未保存。
 * 新生成节点的落点不走这里，仍按上游右侧或避让网格放置。
 *
 * - default 默认排版：内置从左往右分层，大约 3 到 5 列再折行，句柄左入右出
 * - horizontal 横向分层：分层引擎从左往右，不折行，句柄左入右出
 * - horizontal-wrap 横向折行：同样从左往右，长链大约四到六个节点折成下一排
 * - vertical 纵向分层：分层引擎自上而下，句柄上入下出
 */

export type FlowLayoutAxis = 'horizontal' | 'vertical'
export type FlowLayoutPresetId = 'default' | 'horizontal' | 'horizontal-wrap' | 'vertical'
export type FlowLayoutDirection = 'RIGHT' | 'DOWN'

export interface FlowLayoutPreset {
  id: FlowLayoutPresetId
  /** 菜单与成功提示用的名称 */
  label: string
  /** 句柄进出方向：horizontal 左入右出，vertical 上入下出 */
  axis: FlowLayoutAxis
  /** 分层引擎的走向；默认排版不用这个方向 */
  direction: FlowLayoutDirection
  /** default 用内置从左到右分层并折行；其余交给 ELK */
  engine: 'builtin' | 'elk'
  /** 长链折成多排，大约每排四到六个节点，句柄仍是左入右出 */
  wrap?: boolean
}

export const FLOW_LAYOUT_PRESETS: FlowLayoutPreset[] = [
  { id: 'default', label: '默认排版', axis: 'horizontal', direction: 'RIGHT', engine: 'builtin' },
  { id: 'horizontal', label: '横向分层', axis: 'horizontal', direction: 'RIGHT', engine: 'elk' },
  { id: 'horizontal-wrap', label: '横向折行', axis: 'horizontal', direction: 'RIGHT', engine: 'elk', wrap: true },
  { id: 'vertical', label: '纵向分层', axis: 'vertical', direction: 'DOWN', engine: 'elk' },
]

const KNOWN_IDS = new Set<string>(FLOW_LAYOUT_PRESETS.map((p) => p.id))

/** 把读到的排版方式收成四种之一。空值和不认识的值都落成默认排版 */
export function normalizeLayoutPresetId(id: string | null | undefined): FlowLayoutPresetId {
  if (id && KNOWN_IDS.has(id)) return id as FlowLayoutPresetId
  return 'default'
}

/** 按 id 取排版定义。无法识别时返回默认排版 */
export function presetById(id: string | null | undefined): FlowLayoutPreset {
  const normalized = normalizeLayoutPresetId(id)
  return FLOW_LAYOUT_PRESETS.find((p) => p.id === normalized) ?? FLOW_LAYOUT_PRESETS[0]
}

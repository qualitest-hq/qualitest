/**
 * 测试流画布自动排版。
 * <p>
 * 有边：按拓扑从左到右分层，同层节点上下排列；层数过多时折成多行带。
 * 无边：按固定列数网格换行。
 * 落位间距按每个节点的宽高计算（实测尺寸优先，否则按类型估算），避免卡片互相重叠。
 * <p>
 * 对外能力：
 * - computeLayeredPositions：整图重算全部节点坐标（一键排版）
 * - wrapPreferredPosition / findFreeLayoutPosition：给新增节点找不重叠空位（Staging 预览）
 * - resolveLayoutSize：解析单个节点占位宽高
 */
import {
  COND_NODE_W,
  COND_ROW_H,
  NODE_HEAD_H,
  NODE_MIN_H,
  NODE_W,
} from '../constants/flowConfig'

/** 画布排版原点横坐标 */
export const LAYOUT_ORIGIN_X = 40
/** 画布排版原点纵坐标 */
export const LAYOUT_ORIGIN_Y = 80
/** 相邻节点右缘到下一节点左缘的水平空隙 */
export const LAYOUT_GAP_X = 160
/** 相邻节点下缘到下一节点上缘的垂直空隙 */
export const LAYOUT_GAP_Y = 52
/**
 * 标准节点左缘到左缘的默认步距（NODE_W + LAYOUT_GAP_X）。
 * 仅用于无实测宽高时的网格候选起点；分层落位按实际宽高 + LAYOUT_GAP_X 累加。
 */
export const LAYOUT_LAYER_GAP_X = NODE_W + LAYOUT_GAP_X
/**
 * 标准节点上缘到上缘的默认步距（NODE_MIN_H + LAYOUT_GAP_Y）。
 * 仅用于无实测宽高时的网格候选起点；同层落位按实际高度 + LAYOUT_GAP_Y 累加。
 */
export const LAYOUT_NODE_GAP_Y = NODE_MIN_H + LAYOUT_GAP_Y
/** 互不连通的子图之间额外空出的垂直间距 */
export const LAYOUT_COMPONENT_GAP_Y = 80
/** 折行后的行带与行带之间的垂直间距 */
export const LAYOUT_BAND_GAP_Y = 80
/** 无边网格排版时，每一行最多放几个节点 */
export const LAYOUT_WRAP_COLUMNS = 4
/** 有边分层时，单行带至少保留的列数 */
export const LAYOUT_MIN_LAYER_COLUMNS = 3
/** 有边分层时，单行带最多列数 */
export const LAYOUT_MAX_LAYER_COLUMNS = 5

/** 找空位时横向/纵向最多试探次数，防止死循环 */
const MAX_SHIFT = 40

/** 条件节点内容区相对标题的额外内边距（与卡片样式一致） */
const COND_BODY_PAD = 12

/** 参与排版的节点信息 */
export interface LayoutNodeRef {
  id: string
  /** 节点类型；缺实测尺寸时用于估算宽高 */
  type?: string | null
  /** 实测或预估宽度；≤0 或缺省时按类型估算 */
  width?: number | null
  /** 实测或预估高度；≤0 或缺省时按类型估算 */
  height?: number | null
  /** 条件节点分支条数；无实测高度时估算条件卡高度 */
  branchCount?: number | null
}

/** 参与排版的边最小信息（source / target） */
export interface LayoutEdgeRef {
  source?: string | null
  target?: string | null
}

/** 二维画布坐标 */
export interface LayoutPosition {
  x: number
  y: number
}

/** 节点占位宽高 */
export interface LayoutSize {
  w: number
  h: number
}

/** 带可选宽高的矩形（障碍物）；缺宽高时按标准节点占位 */
export interface LayoutRect extends LayoutPosition {
  w?: number
  h?: number
}

/**
 * 解析节点占位宽高。
 * 优先用传入的 width/height；否则 condition 按分支数估算，其它类型用标准节点尺寸。
 */
export function resolveLayoutSize(node: LayoutNodeRef): LayoutSize {
  const type = (node.type ?? '').trim()
  const measuredW = node.width != null && node.width > 0 ? node.width : null
  const measuredH = node.height != null && node.height > 0 ? node.height : null

  if (type === 'condition') {
    const w = measuredW ?? COND_NODE_W
    if (measuredH != null) return { w, h: measuredH }
    const n = Math.max(1, node.branchCount ?? 1)
    const h = Math.max(
      NODE_HEAD_H + COND_ROW_H + COND_BODY_PAD,
      NODE_HEAD_H + n * COND_ROW_H + COND_BODY_PAD,
    )
    return { w, h }
  }

  return {
    w: measuredW ?? NODE_W,
    h: measuredH ?? NODE_MIN_H,
  }
}

/**
 * 按「已有障碍数量」在默认网格上算出候选落点（标准节点步距）。
 * 用于尚无上游、只能按序号落格的场景；最终空位仍由 findFreeLayoutPosition 按真实尺寸避让。
 */
export function wrapPreferredPosition(obstacleCount: number): LayoutPosition {
  const col = obstacleCount % LAYOUT_WRAP_COLUMNS
  const row = Math.floor(obstacleCount / LAYOUT_WRAP_COLUMNS)
  return {
    x: LAYOUT_ORIGIN_X + col * LAYOUT_LAYER_GAP_X,
    y: LAYOUT_ORIGIN_Y + row * LAYOUT_NODE_GAP_Y,
  }
}

/**
 * 从 preferred 出发找不与 obstacles 重叠的空位。
 * 试探步长 = 候选节点宽/高 + 边距；重叠判断使用各方实际（或默认）矩形。
 */
export function findFreeLayoutPosition(
  preferred: LayoutPosition,
  obstacles: LayoutRect[],
  candidateSize?: LayoutSize,
): LayoutPosition {
  const size = candidateSize ?? { w: NODE_W, h: NODE_MIN_H }
  const stepX = size.w + LAYOUT_GAP_X
  const stepY = size.h + LAYOUT_GAP_Y
  const baseX = preferred.x
  const baseY = preferred.y
  for (let row = 0; row < MAX_SHIFT; row++) {
    const y = baseY + row * stepY
    for (let i = 0; i < MAX_SHIFT; i++) {
      const x = baseX + i * stepX
      if (!overlapsAny(x, y, size, obstacles)) {
        return { x, y }
      }
    }
  }
  return {
    x: baseX + MAX_SHIFT * stepX,
    y: baseY + MAX_SHIFT * stepY,
  }
}

/** 候选矩形是否与任一障碍矩形相交（边贴合不算重叠） */
function overlapsAny(
  x: number,
  y: number,
  size: LayoutSize,
  obstacles: LayoutRect[],
): boolean {
  for (const o of obstacles) {
    const ow = o.w != null && o.w > 0 ? o.w : NODE_W
    const oh = o.h != null && o.h > 0 ? o.h : NODE_MIN_H
    if (!(
      x + size.w <= o.x
      || o.x + ow <= x
      || y + size.h <= o.y
      || o.y + oh <= y
    )) {
      return true
    }
  }
  return false
}

/**
 * 计算整图每个节点的坐标，返回 id → {x,y}。
 * - 无有效节点：空 Map
 * - 无有效边：按节点出现顺序做网格换行（按实际宽高累加）
 * - 有边：按连通分量分别分层落位，列距/行距按列内最大宽、节点实际高计算
 */
export function computeLayeredPositions(
  nodes: LayoutNodeRef[],
  edges: LayoutEdgeRef[],
): Map<string, LayoutPosition> {
  const result = new Map<string, LayoutPosition>()
  const ids: string[] = []
  const idSet = new Set<string>()
  const sizes = new Map<string, LayoutSize>()
  for (const n of nodes) {
    if (!n?.id || idSet.has(n.id)) continue
    idSet.add(n.id)
    ids.push(n.id)
    sizes.set(n.id, resolveLayoutSize(n))
  }
  if (ids.length === 0) return result

  const outs = new Map<string, string[]>()
  for (const id of ids) outs.set(id, [])
  let hasEdge = false
  for (const e of edges) {
    const s = e?.source
    const t = e?.target
    if (!s || !t || !idSet.has(s) || !idSet.has(t) || s === t) continue
    outs.get(s)!.push(t)
    hasEdge = true
  }

  if (!hasEdge) {
    layoutWrap(ids, sizes, result)
    return result
  }

  const components = connectedComponents(ids, outs)
  let cursorY = LAYOUT_ORIGIN_Y
  for (const component of components) {
    const used = layoutComponent(component, outs, cursorY, sizes, result)
    cursorY += used + LAYOUT_COMPONENT_GAP_Y
  }
  return result
}

/**
 * 无边时按行换列网格排布：同行内按节点宽度累加 x，行高取该行最高节点。
 */
function layoutWrap(
  ids: string[],
  sizes: Map<string, LayoutSize>,
  result: Map<string, LayoutPosition>,
): void {
  let rowY = LAYOUT_ORIGIN_Y
  for (let i = 0; i < ids.length; i += LAYOUT_WRAP_COLUMNS) {
    const row = ids.slice(i, i + LAYOUT_WRAP_COLUMNS)
    let x = LAYOUT_ORIGIN_X
    let maxH = NODE_MIN_H
    for (const id of row) {
      const s = sizes.get(id) ?? { w: NODE_W, h: NODE_MIN_H }
      result.set(id, { x, y: rowY })
      x += s.w + LAYOUT_GAP_X
      maxH = Math.max(maxH, s.h)
    }
    rowY += maxH + LAYOUT_GAP_Y
  }
}

/**
 * 对单个连通分量做分层落位，并把坐标写入 result。
 * 列的 x：上一列最大宽度 + 水平空隙。
 * 同层节点的 y：上一节点高度 + 垂直空隙。
 * 行带高度：该带内各列堆叠高度的最大值。
 */
function layoutComponent(
  component: string[],
  outs: Map<string, string[]>,
  originY: number,
  sizes: Map<string, LayoutSize>,
  result: Map<string, LayoutPosition>,
): number {
  const inComp = new Set(component)
  const layerOf = assignLayers(component, outs, inComp)

  let maxLayer = 0
  const byLayer = new Map<number, string[]>()
  const orderIndex = new Map<string, number>()
  component.forEach((id, i) => orderIndex.set(id, i))
  for (const id of component) {
    const layer = layerOf.get(id) ?? 0
    maxLayer = Math.max(maxLayer, layer)
    const list = byLayer.get(layer) ?? []
    list.push(id)
    byLayer.set(layer, list)
  }
  for (const list of byLayer.values()) {
    list.sort((a, b) => (orderIndex.get(a) ?? 0) - (orderIndex.get(b) ?? 0))
  }

  const layerSpan = maxLayer + 1
  const cols = resolveLayerColumns(component.length, layerSpan)
  const bandCount = Math.ceil(layerSpan / cols)

  const bandOriginYs: number[] = []
  // 每个行带内各列的左缘 x（按该带内各列最大宽度累加）
  const bandColXs: number[][] = []

  let cursorBandY = originY
  for (let band = 0; band < bandCount; band++) {
    const layerStart = band * cols
    const layerEnd = Math.min(layerStart + cols - 1, maxLayer)
    const colCount = layerEnd - layerStart + 1

    const colMaxW: number[] = []
    const colStackH: number[] = []
    for (let c = 0; c < colCount; c++) {
      const layerNodes = byLayer.get(layerStart + c) ?? []
      let maxW = NODE_W
      let stackH = 0
      layerNodes.forEach((id, row) => {
        const s = sizes.get(id) ?? { w: NODE_W, h: NODE_MIN_H }
        maxW = Math.max(maxW, s.w)
        stackH += s.h
        if (row < layerNodes.length - 1) stackH += LAYOUT_GAP_Y
      })
      if (layerNodes.length === 0) {
        stackH = NODE_MIN_H
      }
      colMaxW.push(maxW)
      colStackH.push(stackH)
    }

    const colXs: number[] = []
    let x = LAYOUT_ORIGIN_X
    for (let c = 0; c < colCount; c++) {
      colXs.push(x)
      x += colMaxW[c] + LAYOUT_GAP_X
    }
    bandColXs.push(colXs)

    const height = Math.max(NODE_MIN_H, ...colStackH)
    bandOriginYs.push(cursorBandY)
    cursorBandY += height + LAYOUT_BAND_GAP_Y
  }

  for (let layer = 0; layer <= maxLayer; layer++) {
    const layerNodes = byLayer.get(layer) ?? []
    const col = layer % cols
    const band = Math.floor(layer / cols)
    const bandOriginY = bandOriginYs[band]
    const colX = bandColXs[band][col] ?? LAYOUT_ORIGIN_X
    let y = bandOriginY
    for (const id of layerNodes) {
      const s = sizes.get(id) ?? { w: NODE_W, h: NODE_MIN_H }
      result.set(id, { x: colX, y })
      y += s.h + LAYOUT_GAP_Y
    }
  }

  return cursorBandY - originY - LAYOUT_BAND_GAP_Y
}

/**
 * 估算单行带应放几列，让整体更接近方正。
 * 层数 ≤ 1 时固定 1 列；否则取 ceil(√节点数)，夹在 MIN～MAX 之间，且不超过实际层数。
 */
function resolveLayerColumns(nodeCount: number, layerSpan: number): number {
  if (layerSpan <= 1) return 1
  const ideal = Math.ceil(Math.sqrt(Math.max(1, nodeCount)))
  const cols = Math.min(
    LAYOUT_MAX_LAYER_COLUMNS,
    Math.max(LAYOUT_MIN_LAYER_COLUMNS, ideal),
  )
  return Math.min(cols, layerSpan)
}

/**
 * 在连通分量内用 BFS 分层。
 * 无入边为第 0 层；沿出边层号 +1；多父时取较大层号。
 */
function assignLayers(
  component: string[],
  outs: Map<string, string[]>,
  inComp: Set<string>,
): Map<string, number> {
  const layerOf = new Map<string, number>()
  const roots: string[] = []
  for (const id of component) {
    if (countInEdges(id, component, outs) === 0) roots.push(id)
  }
  if (roots.length === 0 && component.length > 0) roots.push(component[0])

  const q: string[] = [...roots]
  for (const r of roots) layerOf.set(r, 0)
  while (q.length) {
    const u = q.shift()!
    const lu = layerOf.get(u) ?? 0
    for (const v of outs.get(u) ?? []) {
      if (!inComp.has(v)) continue
      const next = lu + 1
      const existing = layerOf.get(v)
      if (existing == null || next > existing) {
        layerOf.set(v, next)
        q.push(v)
      }
    }
  }
  for (const id of component) {
    if (!layerOf.has(id)) layerOf.set(id, 0)
  }
  return layerOf
}

/** 统计某节点在分量内有多少条入边 */
function countInEdges(id: string, component: string[], outs: Map<string, string[]>): number {
  let n = 0
  for (const u of component) {
    for (const v of outs.get(u) ?? []) {
      if (v === id) n++
    }
  }
  return n
}

/** 把有向图按无向连通划分成多个分量 */
function connectedComponents(ids: string[], outs: Map<string, string[]>): string[][] {
  const undirected = new Map<string, string[]>()
  for (const id of ids) undirected.set(id, [])
  for (const [u, vs] of outs) {
    for (const v of vs) {
      if (!undirected.has(v)) continue
      undirected.get(u)!.push(v)
      undirected.get(v)!.push(u)
    }
  }
  const seen = new Set<string>()
  const components: string[][] = []
  for (const start of ids) {
    if (seen.has(start)) continue
    seen.add(start)
    const comp: string[] = []
    const q = [start]
    while (q.length) {
      const u = q.shift()!
      comp.push(u)
      for (const v of undirected.get(u) ?? []) {
        if (seen.has(v)) continue
        seen.add(v)
        q.push(v)
      }
    }
    components.push(comp)
  }
  return components
}

/**
 * 测试流画布自动排版。
 * <p>
 * 有边：按拓扑从左到右分层；同层按父子重心排序，列内相对父节点纵向对齐；
 * 列数按节点规模折行，避免拉成一行。回边不参与抬层。
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
/** 有边分层时，单行带最多列数，超出则折到下一行带 */
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
 * 同层先按父节点重心排序（出边顺序作次序），再相对上一列父节点中心纵向对齐后紧凑堆叠。
 * 列的 x：上一列最大宽度 + 水平空隙。行带高度按对齐后的实际上下界计算。
 */
function layoutComponent(
  component: string[],
  outs: Map<string, string[]>,
  originY: number,
  sizes: Map<string, LayoutSize>,
  result: Map<string, LayoutPosition>,
): number {
  const inComp = new Set(component)
  const forward = forwardOuts(component, outs, inComp)
  const layerOf = assignLayers(component, forward, inComp)

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
  const ins = invertOuts(component, outs)
  orderLayersByBarycenter(byLayer, maxLayer, outs, ins, layerOf)

  const layerSpan = maxLayer + 1
  const cols = resolveLayerColumns(component.length, layerSpan)
  const bandCount = Math.ceil(layerSpan / cols)

  let cursorBandY = originY
  for (let band = 0; band < bandCount; band++) {
    const layerStart = band * cols
    const layerEnd = Math.min(layerStart + cols - 1, maxLayer)
    const colCount = layerEnd - layerStart + 1

    const colXs: number[] = []
    let x = LAYOUT_ORIGIN_X
    for (let c = 0; c < colCount; c++) {
      colXs.push(x)
      const layerNodes = byLayer.get(layerStart + c) ?? []
      let maxW = NODE_W
      for (const id of layerNodes) {
        maxW = Math.max(maxW, sizes.get(id)?.w ?? NODE_W)
      }
      x += maxW + LAYOUT_GAP_X
    }

    const placed: { id: string; y: number }[] = []
    const centerY = new Map<string, number>()
    let minY = Number.POSITIVE_INFINITY
    let maxBottom = Number.NEGATIVE_INFINITY

    for (let c = 0; c < colCount; c++) {
      const layer = layerStart + c
      const layerNodes = byLayer.get(layer) ?? []
      const colX = colXs[c] ?? LAYOUT_ORIGIN_X
      const stackH = columnStackHeight(layerNodes, sizes)
      let top = cursorBandY
      if (c > 0 && layerNodes.length > 0) {
        const ideals: number[] = []
        for (const id of layerNodes) {
          const ideal = parentCenterMedian(id, layer - 1, ins, layerOf, centerY)
          if (ideal != null) ideals.push(ideal)
        }
        if (ideals.length > 0) {
          top = median(ideals) - stackH / 2
        }
      }
      let y = top
      if (layerNodes.length === 0) {
        minY = Math.min(minY, cursorBandY)
        maxBottom = Math.max(maxBottom, cursorBandY + NODE_MIN_H)
      }
      for (const id of layerNodes) {
        const h = sizes.get(id)?.h ?? NODE_MIN_H
        result.set(id, { x: colX, y })
        centerY.set(id, y + h / 2)
        placed.push({ id, y })
        minY = Math.min(minY, y)
        maxBottom = Math.max(maxBottom, y + h)
        y += h + LAYOUT_GAP_Y
      }
    }

    if (Number.isFinite(minY) && minY < cursorBandY) {
      const shift = cursorBandY - minY
      for (const p of placed) {
        const pos = result.get(p.id)
        if (!pos) continue
        pos.y += shift
        const h = sizes.get(p.id)?.h ?? NODE_MIN_H
        centerY.set(p.id, pos.y + h / 2)
      }
      maxBottom += shift
    }

    const height = Math.max(NODE_MIN_H, (Number.isFinite(maxBottom) ? maxBottom : cursorBandY) - cursorBandY)
    cursorBandY += height + LAYOUT_BAND_GAP_Y
  }

  return cursorBandY - originY - LAYOUT_BAND_GAP_Y
}

/** 同层节点自上而下紧凑堆叠后的总高度（含节点间距） */
function columnStackHeight(ids: string[], sizes: Map<string, LayoutSize>): number {
  if (ids.length === 0) return NODE_MIN_H
  let h = 0
  ids.forEach((id, i) => {
    h += sizes.get(id)?.h ?? NODE_MIN_H
    if (i < ids.length - 1) h += LAYOUT_GAP_Y
  })
  return h
}

/**
 * 上一层父节点垂直中心的中位数。
 * 没有落在指定层的父节点时返回 null，调用方改为顶对齐。
 */
function parentCenterMedian(
  id: string,
  parentLayer: number,
  ins: Map<string, string[]>,
  layerOf: Map<string, number>,
  centerY: Map<string, number>,
): number | null {
  const centers: number[] = []
  for (const p of ins.get(id) ?? []) {
    if (layerOf.get(p) !== parentLayer) continue
    const c = centerY.get(p)
    if (c != null) centers.push(c)
  }
  if (centers.length === 0) return null
  return median(centers)
}

/** 数值中位数；偶数个取中间两数平均 */
function median(values: number[]): number {
  const s = [...values].sort((a, b) => a - b)
  const n = s.length
  if (n === 0) return 0
  if (n % 2 === 1) return s[(n - 1) / 2]
  return (s[n / 2 - 1] + s[n / 2]) / 2
}

/**
 * 同层重心排序：先按上一层父节点序号的中位数从左到右（这里是从上到下），
 * 重心相同时按父节点出边顺序；再按下一层子节点重心回扫一次。
 * 重心相同则保持当前相对顺序。
 */
function orderLayersByBarycenter(
  byLayer: Map<number, string[]>,
  maxLayer: number,
  outs: Map<string, string[]>,
  ins: Map<string, string[]>,
  layerOf: Map<string, number>,
): void {
  const rank = new Map<string, number>()
  const refresh = () => {
    for (let layer = 0; layer <= maxLayer; layer++) {
      const list = byLayer.get(layer) ?? []
      list.forEach((id, i) => rank.set(id, i))
    }
  }
  refresh()

  const bary = (ids: string[], self: string): number => {
    const rs: number[] = []
    for (const id of ids) {
      const r = rank.get(id)
      if (r != null) rs.push(r)
    }
    if (rs.length === 0) return rank.get(self) ?? 0
    return median(rs)
  }

  const onLayer = (pool: string[] | undefined, layer: number): string[] =>
    (pool ?? []).filter((n) => layerOf.get(n) === layer)

  for (let layer = 1; layer <= maxLayer; layer++) {
    const list = byLayer.get(layer)
    if (!list || list.length < 2) continue
    const cur = new Map(list.map((id, i) => [id, i]))
    list.sort((a, b) => {
      const pa = onLayer(ins.get(a), layer - 1)
      const pb = onLayer(ins.get(b), layer - 1)
      const ba = bary(pa, a)
      const bb = bary(pb, b)
      if (ba !== bb) return ba - bb
      const ea = edgeOrder(a, pa, outs)
      const eb = edgeOrder(b, pb, outs)
      if (ea !== eb) return ea - eb
      return (cur.get(a) ?? 0) - (cur.get(b) ?? 0)
    })
    refresh()
  }

  for (let layer = maxLayer - 1; layer >= 0; layer--) {
    const list = byLayer.get(layer)
    if (!list || list.length < 2) continue
    const cur = new Map(list.map((id, i) => [id, i]))
    list.sort((a, b) => {
      const ca = onLayer(outs.get(a), layer + 1)
      const cb = onLayer(outs.get(b), layer + 1)
      const ba = bary(ca, a)
      const bb = bary(cb, b)
      if (ba !== bb) return ba - bb
      return (cur.get(a) ?? 0) - (cur.get(b) ?? 0)
    })
    refresh()
  }
}

/** 节点在若干父节点出边列表中的最靠前序号；没有则视为最后 */
function edgeOrder(id: string, parents: string[], outs: Map<string, string[]>): number {
  let best = Number.POSITIVE_INFINITY
  for (const p of parents) {
    const idx = (outs.get(p) ?? []).indexOf(id)
    if (idx >= 0 && idx < best) best = idx
  }
  return best
}

/** 由出边表得到入边表（仅分量内节点） */
function invertOuts(component: string[], outs: Map<string, string[]>): Map<string, string[]> {
  const ins = new Map<string, string[]>()
  for (const id of component) ins.set(id, [])
  for (const [u, vs] of outs) {
    if (!ins.has(u)) continue
    for (const v of vs) {
      ins.get(v)?.push(u)
    }
  }
  return ins
}

/**
 * 估算单行带应放几列，让整图更接近方块，而不是拉成一行。
 * 层数 ≤ 1 时 1 列；否则取 ceil(√节点数)，夹在最小列数与最大列数之间，且不超过实际层数。
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
 * 去掉回边后的出边表。
 * 深度优先时指向仍在栈上的节点视为回边，该边不参与分层，避免环把节点层层拉开。
 */
function forwardOuts(
  component: string[],
  outs: Map<string, string[]>,
  inComp: Set<string>,
): Map<string, string[]> {
  const forward = new Map<string, string[]>()
  for (const id of component) forward.set(id, [])
  const color = new Map<string, number>()
  const dfs = (u: string) => {
    color.set(u, 1)
    for (const v of outs.get(u) ?? []) {
      if (!inComp.has(v)) continue
      const c = color.get(v) ?? 0
      if (c === 1) continue
      forward.get(u)!.push(v)
      if (c === 0) dfs(v)
    }
    color.set(u, 2)
  }
  for (const id of component) {
    if ((color.get(id) ?? 0) === 0) dfs(id)
  }
  return forward
}

/**
 * 在连通分量内用 BFS 分层（只沿非回边）。
 * 无入边为第 0 层；沿出边层号 +1；多父时取较大层号。
 * 层号达到节点数时不再抬高，防止残余环无限加深。
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

  const limit = component.length
  const q: string[] = [...roots]
  for (const r of roots) layerOf.set(r, 0)
  while (q.length) {
    const u = q.shift()!
    const lu = layerOf.get(u) ?? 0
    for (const v of outs.get(u) ?? []) {
      if (!inComp.has(v)) continue
      const next = lu + 1
      if (next >= limit) continue
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

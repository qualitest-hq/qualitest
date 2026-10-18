/**
 * 把 ELK 正交拐点画成带圆角的折线。
 * 拐点只存在内存里，刷新或拖动节点后失效，画布保存时不带上。
 */

export interface RoutePoint {
  x: number
  y: number
}

const RADIUS = 8

/**
 * 把折线两端贴到画布连接点上。
 * ELK 端口和句柄往往不在同一高度，只平移贴着端口的那一段，其余拐点不动。
 */
export function snapRouteEnds(points: RoutePoint[], source: RoutePoint, target: RoutePoint): RoutePoint[] {
  if (points.length < 2) return points.map((point) => ({ ...point }))
  const sourcePoint = { x: source.x, y: source.y }
  const targetPoint = { x: target.x, y: target.y }
  if (points.length === 2) return bridge(points[0], points[1], sourcePoint, targetPoint)

  const next = points[1]
  const prev = points[points.length - 2]
  const out = points.map((point) => ({ x: point.x, y: point.y }))
  out[0] = sourcePoint
  out[out.length - 1] = targetPoint
  if (isHorizontal(points[0], next)) out[1] = { x: next.x, y: sourcePoint.y }
  else out[1] = { x: sourcePoint.x, y: next.y }
  const corner = out.length - 2
  if (isHorizontal(prev, points[points.length - 1])) out[corner] = { x: out[corner].x, y: targetPoint.y }
  else out[corner] = { x: targetPoint.x, y: out[corner].y }
  return out
}

/** 只有起终点时，沿原方向先走到连接点的高度或横向位置，再接到终点 */
function bridge(from: RoutePoint, to: RoutePoint, source: RoutePoint, target: RoutePoint): RoutePoint[] {
  if (Math.abs(source.x - target.x) < 0.5 || Math.abs(source.y - target.y) < 0.5) return [source, target]
  if (isHorizontal(from, to)) return [source, { x: target.x, y: source.y }, target]
  return [source, { x: source.x, y: target.y }, target]
}

function isHorizontal(a: RoutePoint, b: RoutePoint): boolean {
  return Math.abs(a.y - b.y) <= Math.abs(a.x - b.x)
}

/** 返回值与 getSmoothStepPath 相同：[路径, 标签 x, 标签 y, 0, 0] */
export function routeToPath(points: RoutePoint[]): [string, number, number, number, number] | null {
  const simplified = simplify(points)
  if (simplified.length < 2) return null
  const [labelX, labelY] = midpoint(simplified)
  return [roundedOrthogonal(simplified, RADIUS), labelX, labelY, 0, 0]
}

function simplify(points: RoutePoint[]): RoutePoint[] {
  const kept: RoutePoint[] = []
  for (const point of points) {
    if (!Number.isFinite(point.x) || !Number.isFinite(point.y)) continue
    const prev = kept[kept.length - 1]
    if (prev && Math.abs(prev.x - point.x) < 0.5 && Math.abs(prev.y - point.y) < 0.5) continue
    kept.push(point)
  }
  if (kept.length < 3) return kept
  const out = [kept[0]]
  for (let i = 1; i < kept.length - 1; i++) {
    const a = out[out.length - 1]
    const b = kept[i]
    const c = kept[i + 1]
    const cross = (b.x - a.x) * (c.y - b.y) - (b.y - a.y) * (c.x - b.x)
    if (Math.abs(cross) < 0.5) continue
    out.push(b)
  }
  out.push(kept[kept.length - 1])
  return out
}

function midpoint(points: RoutePoint[]): [number, number] {
  let total = 0
  const lengths: number[] = []
  for (let i = 1; i < points.length; i++) {
    const len = Math.hypot(points[i].x - points[i - 1].x, points[i].y - points[i - 1].y)
    lengths.push(len)
    total += len
  }
  let remain = total / 2
  for (let i = 0; i < lengths.length; i++) {
    if (remain <= lengths[i]) {
      const t = lengths[i] === 0 ? 0 : remain / lengths[i]
      return [
        points[i].x + (points[i + 1].x - points[i].x) * t,
        points[i].y + (points[i + 1].y - points[i].y) * t,
      ]
    }
    remain -= lengths[i]
  }
  const last = points[points.length - 1]
  return [last.x, last.y]
}

function roundedOrthogonal(points: RoutePoint[], radius: number): string {
  let d = `M ${points[0].x} ${points[0].y}`
  for (let i = 1; i < points.length - 1; i++) {
    const prev = points[i - 1]
    const curr = points[i]
    const next = points[i + 1]
    const inLen = Math.hypot(curr.x - prev.x, curr.y - prev.y)
    const outLen = Math.hypot(next.x - curr.x, next.y - curr.y)
    const r = Math.min(radius, inLen / 2, outLen / 2)
    if (r < 1 || inLen < 1 || outLen < 1) {
      d += ` L ${curr.x} ${curr.y}`
      continue
    }
    const inX = (curr.x - prev.x) / inLen
    const inY = (curr.y - prev.y) / inLen
    const outX = (next.x - curr.x) / outLen
    const outY = (next.y - curr.y) / outLen
    d += ` L ${curr.x - inX * r} ${curr.y - inY * r} Q ${curr.x} ${curr.y} ${curr.x + outX * r} ${curr.y + outY * r}`
  }
  const last = points[points.length - 1]
  d += ` L ${last.x} ${last.y}`
  return d
}

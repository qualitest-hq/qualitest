/**
 * 判断 Vue Flow nodes-change 是否会改写可落盘的图内容。
 * dimensions / select 等仅 UI 态变更不应标脏，否则打开画布就会误标未保存。
 */
import type { NodeChange } from '@vue-flow/core'

/** 会改变节点坐标、增删节点的变更才算可落盘相关变更 */
export function nodeChangeAffectsPersist(changes: NodeChange[] | null | undefined): boolean {
  if (!changes?.length) return false
  return changes.some((c) => c.type === 'position' || c.type === 'remove' || c.type === 'add')
}

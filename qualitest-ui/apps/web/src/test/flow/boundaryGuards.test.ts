/**
 * 测业务边界护栏：禁止 FE 回流鉴权 pathPrefix / HTTP normalize 镜像。
 * 边界：读源文件字符串断言；不测运行行为。
 * 单跑：pnpm test boundaryGuards   （在 qualitest-ui 或 apps/web 下）
 */
import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { describe, expect, it } from 'vitest'

const utilsRoot = resolve(__dirname, '../../views/project/testFlow/utils')

function readUtil(name: string): string {
  return readFileSync(resolve(utilsRoot, name), 'utf8')
}

describe('流边界护栏', () => {
  it('nodeAuthScope 不再本地推演 pathPrefix', () => {
    // 前提：鉴权 conflict 应消费 canvas AUTH issues
    // 期望：源码不含 pathPrefix 匹配 / Profile 最长前缀算法
    const src = readUtil('nodeAuthScope.ts')
    expect(src).not.toMatch(/pathPrefixText/)
    expect(src).not.toMatch(/function resolveProfile\b/)
    expect(src).not.toMatch(/normalizePrefix\s*\(/)
    expect(src).toMatch(/authIssues/)
  })

  it('preparePatchForStaging 不镜像 BE HTTP Normalizer', () => {
    // 前提：Staging hydrate 只信服务端规范化结果
    // 期望：不导入 enrich / normalizeHttpNodeData
    const src = readUtil('preparePatchForStaging.ts')
    expect(src).not.toMatch(/enrichFlowDesignPatchHttpNodes/)
    expect(src).not.toMatch(/normalizeHttpNodeData/)
    expect(src).not.toMatch(/from ['\"]\.\/patchHttpNodeNormalize['\"]/)
  })

  it('patchHttpNodeNormalize 不再导出节点 data 权威规范化', () => {
    // 前提：HTTP data 形状权威在 BE
    // 期望：本文件仅保留 extracts 路径轻量工具
    const src = readUtil('patchHttpNodeNormalize.ts')
    expect(src).not.toMatch(/export function normalizeHttpNodeData/)
    expect(src).not.toMatch(/export async function enrichFlowDesignPatchHttpNodes/)
    expect(src).not.toMatch(/export function normalizeFlowDesignPatchHttpNodes/)
  })
})

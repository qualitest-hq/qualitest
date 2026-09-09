/**
 * 测谁：synthesizeTemplateApiTree（点号嵌套目录合成）
 * 边界：空分组、多级路径、catalog id 稳定、路径规范化与过滤
 * 单跑：pnpm test synthesizeTemplateApiTree
 */
import { describe, expect, it } from 'vitest'
import {
  DEFAULT_API_GROUP,
  apiGroupMatchesPath,
  collectApiGroupPaths,
  normalizeApiGroup,
  synthesizeTemplateApiCatalog,
} from '@/views/project/testProjectTemplate/utils/synthesizeTemplateApiTree'

describe('normalizeApiGroup / collectApiGroupPaths', () => {
  it('空与脏点号规整为默认分组或干净路径', () => {
    // 前提：空串、仅点号、带空格段
    // 期望：空 → 默认分组；有效段 trim 后用 . 拼接
    expect(normalizeApiGroup('')).toBe(DEFAULT_API_GROUP)
    expect(normalizeApiGroup('  ')).toBe(DEFAULT_API_GROUP)
    expect(normalizeApiGroup('...')).toBe(DEFAULT_API_GROUP)
    expect(normalizeApiGroup(' 管理端 . 系统 .登录 ')).toBe('管理端.系统.登录')
  })

  it('收集去重后的分组路径', () => {
    // 前提：两条同路径、一条不同、一条空
    const paths = collectApiGroupPaths([
      { apiGroup: '管理端.系统' },
      { apiGroup: '管理端.系统' },
      { apiGroup: '' },
      { apiGroup: '认证' },
    ])

    // 期望：去重且空归默认分组；顺序按首次出现
    expect(paths).toEqual(['管理端.系统', DEFAULT_API_GROUP, '认证'])
  })
})

describe('synthesizeTemplateApiCatalog 嵌套树', () => {
  it('按点号拆成嵌套分组且 catalog id 稳定', () => {
    // 前提：两条多级分组接口，带固定合成 id
    const { tree, catalog } = synthesizeTemplateApiCatalog([
      {
        testProjectApiId: '2100000000000004101',
        apiName: '登录',
        apiPath: '/login',
        apiGroup: '管理端.系统.登录',
        requestConfig: { method: 'POST' },
      },
      {
        testProjectApiId: '2100000000000004104',
        apiName: '获取用户信息',
        apiPath: '/getInfo',
        apiGroup: '管理端.系统',
        requestConfig: { method: 'GET' },
      },
    ])

    // 期望：catalog id 不漂移；树为 管理端 → 系统 → (登录分组 + getInfo api)
    expect(catalog.map((c) => c.syntheticId)).toEqual([
      '2100000000000004101',
      '2100000000000004104',
    ])
    expect(tree).toHaveLength(1)
    expect(tree[0].groupName).toBe('管理端')
    expect(tree[0].groupPath).toBe('管理端')
    const system = tree[0].children.find((c) => c.nodeType === 'group' && c.groupName === '系统')
    expect(system).toBeTruthy()
    expect(system.groupPath).toBe('管理端.系统')
    const loginGroup = system.children.find((c) => c.nodeType === 'group' && c.groupName === '登录')
    expect(loginGroup?.children?.[0]?.testProjectApiId).toBe('2100000000000004101')
    const getInfo = system.children.find((c) => c.nodeType === 'api' && c.apiPath === '/getInfo')
    expect(getInfo?.testProjectApiId).toBe('2100000000000004104')
    expect(getInfo?.groupPath).toBe('管理端.系统')
  })

  it('空 apiGroup 落入默认分组', () => {
    // 前提：无 apiGroup
    const { tree } = synthesizeTemplateApiCatalog([
      {
        testProjectApiId: '2100000000000004101',
        apiName: '登录',
        apiPath: '/login',
        requestConfig: { method: 'POST' },
      },
    ])

    // 期望：根为「默认分组」
    expect(tree[0].groupName).toBe(DEFAULT_API_GROUP)
    expect(tree[0].children[0].testProjectApiId).toBe('2100000000000004101')
  })
})

describe('apiGroupMatchesPath', () => {
  it('精确匹配与子路径命中', () => {
    // 前提：过滤路径 管理端.系统
    // 期望：自身与子路径命中，旁支不命中；空过滤不过滤
    expect(apiGroupMatchesPath('管理端.系统', '管理端.系统')).toBe(true)
    expect(apiGroupMatchesPath('管理端.系统.登录', '管理端.系统')).toBe(true)
    expect(apiGroupMatchesPath('管理端.其它', '管理端.系统')).toBe(false)
    expect(apiGroupMatchesPath('管理端.系统', '')).toBe(true)
  })
})

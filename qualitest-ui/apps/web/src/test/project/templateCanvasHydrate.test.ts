/**
 * 模板预制流画布灌入：catalog 绑定同步与探活再登录排版。
 */
import { describe, expect, it } from 'vitest'

import {
  applyLoginFlowLayoutIfPresent,
  hydrateTemplateFlowGraph,
  hydrateTemplateFlowsGraphs,
  migrateLoginFlowTerminalBranch,
  recoverLoginFlowEdgesIfMissing,
  syncTemplateHttpNodesFromCatalog,
} from '@/views/project/testProjectTemplate/utils/templateCanvasHydrate'

const catalog = [
  {
    syntheticId: '2100000000000004101',
    api: {
      testProjectApiId: '2100000000000004101',
      apiName: '登录',
      apiPath: '/login',
      httpMethod: 'POST',
      requestConfig: { method: 'POST' },
    },
  },
  {
    syntheticId: '2100000000000004104',
    api: {
      testProjectApiId: '2100000000000004104',
      apiName: '获取用户信息',
      apiPath: '/getInfo',
      httpMethod: 'GET',
      requestConfig: { method: 'GET' },
    },
  },
]

describe('recoverLoginFlowEdgesIfMissing', () => {
  it('骨架图无边时恢复四条默认边', () => {
    const nodes = [
      { id: 'cond_token' },
      { id: 'probe_http' },
      { id: 'login_http' },
    ]
    const edges = recoverLoginFlowEdgesIfMissing(nodes, [])
    expect(edges).toHaveLength(4)
    expect(edges.map((e) => e.id)).toEqual([
      'e_token_if',
      'e_token_else',
      'e_probe',
      'e_alive_else',
    ])
  })
})

describe('syncTemplateHttpNodesFromCatalog', () => {
  it('仅有 method+path 时按 catalog 回填合成 id 与 apiName', () => {
    const nodes = [
      {
        id: 'login_http',
        type: 'http',
        data: {
          name: '登录',
          callMode: 'project',
          httpMethod: 'POST',
          apiPath: '/login',
        },
      },
    ]

    const changed = syncTemplateHttpNodesFromCatalog(nodes, catalog)

    expect(changed).toBe(true)
    expect(nodes[0].data.testProjectApiId).toBe('2100000000000004101')
    expect(nodes[0].data.apiName).toBe('登录')
    expect(nodes[0].data.summary).toContain('POST')
  })
})

describe('applyLoginFlowLayoutIfPresent', () => {
  it('识别骨架节点 id 时应用加宽坐标', () => {
    const nodes = [
      { id: 'probe_http', type: 'http', position: { x: 280, y: 40 }, data: {} },
      { id: 'login_http', type: 'http', position: { x: 520, y: 220 }, data: {} },
    ]

    const changed = applyLoginFlowLayoutIfPresent(nodes)

    expect(changed).toBe(true)
    expect(nodes[0].position).toEqual({ x: 480, y: 60 })
    expect(nodes[1].position).toEqual({ x: 680, y: 440 })
  })
})

describe('hydrateTemplateFlowsGraphs', () => {
  it('对 templateFlows 内 graphJson 字符串做绑定与排版', () => {
    const flows = [
      {
        flowName: '管理端 Bearer 登录',
        graphJson: JSON.stringify({
          nodes: [
            {
              id: 'probe_http',
              type: 'http',
              position: { x: 280, y: 40 },
              data: {
                name: '探活',
                callMode: 'project',
                httpMethod: 'GET',
                apiPath: '/getInfo',
              },
            },
            {
              id: 'login_http',
              type: 'http',
              position: { x: 520, y: 220 },
              data: {
                name: '登录',
                callMode: 'project',
                httpMethod: 'POST',
                apiPath: '/login',
              },
            },
          ],
          edges: [],
        }),
      },
    ]

    const { flows: next, changed } = hydrateTemplateFlowsGraphs(flows, catalog)

    expect(changed).toBe(true)
    const graph = next[0].graphJson as { nodes: Array<{ id: string; data: Record<string, unknown>; position: { x: number; y: number } }> }
    const probe = graph.nodes.find((n) => n.id === 'probe_http')
    const login = graph.nodes.find((n) => n.id === 'login_http')
    expect(probe?.data.testProjectApiId).toBe('2100000000000004104')
    expect(login?.data.testProjectApiId).toBe('2100000000000004101')
    expect(probe?.position).toEqual({ x: 480, y: 60 })
    expect(login?.position).toEqual({ x: 680, y: 440 })
  })
})

describe('migrateLoginFlowTerminalBranch', () => {
  it('移除 reuse_end 并将 b_alive_if 改为 terminal', () => {
    const graph = {
      nodes: [
        {
          id: 'cond_alive',
          type: 'condition',
          data: {
            branches: [
              {
                id: 'b_alive_if',
                kind: 'if',
                target: 'reuse_end',
                conditions: [{ left: 'http.status', operator: 'eq', right: '200' }],
              },
              { id: 'b_alive_else', kind: 'else', target: 'login_http', conditions: [] },
            ],
          },
        },
        {
          id: 'reuse_end',
          type: 'delay',
          data: { name: '复用凭证', ms: 0 },
        },
      ],
      edges: [
        { id: 'e_alive_if', source: 'cond_alive', target: 'reuse_end' },
        { id: 'e_alive_else', source: 'cond_alive', target: 'login_http' },
      ],
    }

    expect(migrateLoginFlowTerminalBranch(graph)).toBe(true)
    expect(graph.nodes.some((n) => n.id === 'reuse_end')).toBe(false)
    expect(graph.edges.some((e) => e.id === 'e_alive_if')).toBe(false)
    const aliveIf = graph.nodes[0].data.branches.find((b) => b.id === 'b_alive_if')
    expect(aliveIf?.terminal).toBe(true)
    expect(aliveIf?.target).toBeUndefined()
  })
})

describe('hydrateTemplateFlowGraph', () => {
  it('已绑定 id 时不重复改写', () => {
    const graph = {
      nodes: [
        {
          id: 'login_http',
          type: 'http',
          position: { x: 680, y: 440 },
          data: {
            name: '登录',
            callMode: 'project',
            httpMethod: 'POST',
            apiPath: '/login',
            apiName: '登录',
            testProjectApiId: '2100000000000004101',
            summary: 'POST 登录',
          },
        },
      ],
      edges: [],
    }

    expect(hydrateTemplateFlowGraph(graph, catalog)).toBe(false)
  })
})

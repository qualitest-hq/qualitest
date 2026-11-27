/**
 * 测 normalizeFlowDesignPatchIds：patch id 规范化与边端点 remap。
 * 边界：纯函数，无 store 依赖。
 * 单跑：pnpm test patchIdNormalize   （在 qualitest-ui 或 apps/web 下）
 */
import { describe, expect, it } from 'vitest';

import type { FlowDesignPatch } from '@/views/project/testFlow/types/aiDesignTypes';
import { normalizeFlowDesignPatchIds } from '@/views/project/testFlow/utils/patchIdNormalize';

describe('normalizeFlowDesignPatchIds', () => {
  /** 非数字节点 id 被替换后，连线 source/target 应指向新节点 id。 */
  it('节点 id 被替换时同步 remap addEdge 端点', () => {
    // 前提：addNodes 含非数字 id，addEdge 引用旧 id
    // 期望：节点与边 id 均为数字，边端点指向新节点 id
    const patch: FlowDesignPatch = {
      addNodes: [
        { id: 'bad-a', type: 'http', data: { name: '登录' } },
        { id: 'bad-b', type: 'http', data: { name: '资料' } },
      ],
      addEdges: [{ id: 'bad-e', source: 'bad-a', target: 'bad-b' }],
    };

    const normalized = normalizeFlowDesignPatchIds(patch);
    const nodeIds = normalized.addNodes!.map((n) => n.id);
    const edge = normalized.addEdges![0];

    expect(nodeIds.every((id) => /^\d+$/.test(id))).toBe(true);
    expect(edge.id).toMatch(/^\d+$/);
    expect(edge.source).toBe(nodeIds[0]);
    expect(edge.target).toBe(nodeIds[1]);
  });

  it('传入 clientIdMap 时复用已映射雪花，不重新发号', () => {
    // 前提：clientIdMap 已有 n_login
    // 期望：复用映射 id，不重新发号
    const patch: FlowDesignPatch = {
      addNodes: [{ id: 'n_login', type: 'http', data: { name: '登录' } }],
      addEdges: [],
    };
    const normalized = normalizeFlowDesignPatchIds(patch, { n_login: '2097000000000000001' });
    expect(normalized.addNodes![0].id).toBe('2097000000000000001');
  });

  it('未 remap 的边端点保持原样', () => {
    // 前提：节点 id 有效，边端点指向未知 id
    // 期望：端点不被改写
    const patch: FlowDesignPatch = {
      addNodes: [
        { id: '9001', type: 'http', data: { name: 'A' } },
        { id: '9002', type: 'http', data: { name: 'B' } },
      ],
      addEdges: [{ id: '8001', source: 'x1', target: 'x2' }],
    };
    const normalized = normalizeFlowDesignPatchIds(patch);
    expect(normalized.addEdges![0]).toMatchObject({ source: 'x1', target: 'x2' });
  });
});

/**
 * 测 normalizeFlowDesignPatchIds：patch id 规范化与边端点重连。
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

  /** 端点 id 全部无效且为 3 节点 3 边时，应重连为 A→B、B→C、A→C。 */
  it('3 节点 3 边且端点全无效时按流水线拓扑重连', () => {
    // 前提：3 节点 id 有效，3 边端点均无效
    // 期望：边重连为 9001→9002、9002→9003、9001→9003
    const patch: FlowDesignPatch = {
      addNodes: [
        { id: '9001', type: 'http', data: { name: 'A' } },
        { id: '9002', type: 'http', data: { name: 'B' } },
        { id: '9003', type: 'assert', data: { name: 'C' } },
      ],
      addEdges: [
        { id: '8001', source: 'x1', target: 'x2' },
        { id: '8002', source: 'x2', target: 'x3' },
        { id: '8003', source: 'x1', target: 'x3' },
      ],
    };

    const normalized = normalizeFlowDesignPatchIds(patch);
    expect(normalized.addEdges![0]).toMatchObject({ source: '9001', target: '9002' });
    expect(normalized.addEdges![1]).toMatchObject({ source: '9002', target: '9003' });
    expect(normalized.addEdges![2]).toMatchObject({ source: '9001', target: '9003' });
  });
});

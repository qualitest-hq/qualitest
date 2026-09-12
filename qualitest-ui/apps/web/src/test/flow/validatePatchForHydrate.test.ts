/**
 * 测 validatePatchForHydrate：灌入前轻量 schema 闸。
 * 单跑：pnpm test validatePatchForHydrate   （在 qualitest-ui 或 apps/web 下）
 */
import { describe, expect, it } from 'vitest';

import type { FlowDesignPatch } from '@/views/project/testFlow/types/aiDesignTypes';
import { validatePatchForHydrate } from '@/views/project/testFlow/utils/validatePatchForHydrate';

describe('validatePatchForHydrate', () => {
  it('缺 type 的 addNode 被跳过且不再默认 http', () => {
    const patch: FlowDesignPatch = {
      addNodes: [{ id: '9001', data: { name: '无类型' } } as never],
    };
    const result = validatePatchForHydrate(patch);
    expect(result.patch.addNodes).toBeUndefined();
    expect(result.warnings.some((w) => w.includes('缺 type'))).toBe(true);
  });

  it('update 指向不存在的节点时跳过', () => {
    const patch: FlowDesignPatch = {
      updateNodes: [{ id: '9999', type: 'http', data: { name: '幽灵' } }],
    };
    const result = validatePatchForHydrate(patch, {
      existingNodeIds: new Set(['9001']),
    });
    expect(result.patch.updateNodes).toBeUndefined();
    expect(result.warnings.some((w) => w.includes('updateNode:9999'))).toBe(true);
  });

  it('合法 addNode 保留', () => {
    const patch: FlowDesignPatch = {
      addNodes: [{ id: '9001', type: 'http', data: { name: '登录', callMode: 'project' } }],
    };
    const result = validatePatchForHydrate(patch);
    expect(result.patch.addNodes).toHaveLength(1);
    expect(result.warnings).toHaveLength(0);
  });
});

/**
 * 测 nextSnowflakeId / generateNodeId：流程节点雪花 ID 生成。
 * 边界：纯函数，无持久化。
 * 单跑：pnpm test snowflakeId   （在 qualitest-ui 或 apps/web 下）
 */
import { describe, expect, it } from 'vitest';

import { nextSnowflakeId } from '@/utils/flow/snowflakeId';

import { generateNodeId } from '@/views/project/testFlow/utils/nodeDataUtils';

describe('nextSnowflakeId', () => {
  /** 连续两次调用应返回不同纯数字字符串 */
  it('返回纯数字字符串且连续调用不重复', () => {
    // 前提：连续两次调用 nextSnowflakeId
    // 期望：均为纯数字且互不相同
    const a = nextSnowflakeId();
    const b = nextSnowflakeId();
    expect(a).toMatch(/^\d+$/);
    expect(b).toMatch(/^\d+$/);
    expect(a).not.toBe(b);
  });

  /** generateNodeId 应产出雪花数字 id，不含 n_ 前缀 */
  it('generateNodeId 使用雪花 id', () => {
    // 前提：为 http 节点生成 id
    // 期望：纯数字 id，无 n_ 前缀
    const id = generateNodeId('http');
    expect(id).toMatch(/^\d+$/);
    expect(id).not.toMatch(/^n_/);
  });
});

/**
 * nextSnowflakeId / generateNodeId 单元测试：验证流程节点雪花 ID 生成。
 *
 * nextSnowflakeId：返回纯数字字符串，连续调用不重复。
 * generateNodeId：新建节点时使用雪花 id（非旧版 n_ 前缀）。
 *
 * 运行（apps/web 目录）：yarn test snowflakeId
 */
import { describe, expect, it } from 'vitest';

import { nextSnowflakeId } from '@/utils/flow/snowflakeId';

import { generateNodeId } from '@/views/project/testFlow/utils/nodeDataUtils';

describe('nextSnowflakeId', () => {
  /** 连续两次调用应返回不同纯数字字符串 */
  it('返回纯数字字符串且连续调用不重复', () => {
    const a = nextSnowflakeId();
    const b = nextSnowflakeId();
    expect(a).toMatch(/^\d+$/);
    expect(b).toMatch(/^\d+$/);
    expect(a).not.toBe(b);
  });

  /** generateNodeId 应产出雪花数字 id，不含 n_ 前缀 */
  it('generateNodeId 使用雪花 id', () => {
    const id = generateNodeId('http');
    expect(id).toMatch(/^\d+$/);
    expect(id).not.toMatch(/^n_/);
  });
});

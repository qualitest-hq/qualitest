/**
 * 测 stagingConfirmErrorHints：确认失败错误的分类、分组、格式化，以及鉴权 CODE 前缀剥离。
 * 边界：纯函数，无 API。
 * 单跑：yarn test stagingConfirmErrorHints   （在 qualitest-ui 或 apps/web 下）
 */
import { describe, expect, it } from 'vitest';

import {
  classifyStagingConfirmErrors,
  formatStagingConfirmErrorLine,
  groupStagingConfirmErrors,
} from '@/views/project/testFlow/utils/stagingConfirmErrorHints';

describe('classifyStagingConfirmErrors', () => {
  it('dependencyHints 标记为 dependency', () => {
    // 前提：错误来自 dependencyHints
    // 期望：source 为 dependency
    const lines = classifyStagingConfirmErrors(
      ['确认 addEdge:8001 需要先确认 addNode:9001'],
      [],
    );
    expect(lines[0].source).toBe('dependency');
  });

  it('开始节点错误标记为 canvas', () => {
    // 前提：warnings 含多个开始节点
    // 期望：source 为 canvas
    const lines = classifyStagingConfirmErrors([], ['流程只能有一个开始节点，当前有 2 个：A、B']);
    expect(lines[0].source).toBe('canvas');
  });

  it('HTTP 字段错误标记为 unit', () => {
    // 前提：errors 含 HTTP 节点字段缺失
    // 期望：source 为 unit
    const lines = classifyStagingConfirmErrors([], ['nodes[0] HTTP 节点「登录」外联模式缺少 externalUrl']);
    expect(lines[0].source).toBe('unit');
  });

  it('AUTH_TOKEN_MISSING 剥掉 CODE 后归为本单元校验', () => {
    // 前提：errors 含 AUTH_TOKEN_MISSING: 文案
    // 期望：展示无 CODE 前缀，source 为 unit
    const lines = classifyStagingConfirmErrors([], [
      'AUTH_TOKEN_MISSING: 图中使用了客户端 Bearer（flow.token），但未找到该变量来源',
    ]);
    expect(lines).toHaveLength(1);
    expect(lines[0].source).toBe('unit');
    expect(lines[0].message).toBe('图中使用了客户端 Bearer（flow.token），但未找到该变量来源');
  });
});

describe('groupStagingConfirmErrors', () => {
  it('按 dependency → canvas → unit 顺序分组', () => {
    // 前提：三类 source 乱序输入
    // 期望：输出按 dependency、canvas、unit 排序
    const grouped = groupStagingConfirmErrors([
      { source: 'unit', message: '缺少 url' },
      { source: 'dependency', message: '先 confirm 节点' },
      { source: 'canvas', message: '无开始节点' },
    ]);
    expect(grouped.map((g) => g.source)).toEqual(['dependency', 'canvas', 'unit']);
  });
});

describe('formatStagingConfirmErrorLine', () => {
  it('带中文来源前缀', () => {
    // 前提：dependency 类错误行
    // 期望：格式化文本含「依赖未满足」
    expect(
      formatStagingConfirmErrorLine({ source: 'dependency', message: '需要先确认 addNode' }),
    ).toContain('依赖未满足');
  });
});

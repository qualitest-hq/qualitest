/**
 * 测 snapshotPreRunValidate / resolveResetBaseUrl：快照前置节点重置端点静态校验。
 * 边界：纯函数，fixture 图与环境 URL。
 * 单跑：pnpm test snapshotPreRunValidate   （在 qualitest-ui 或 apps/web 下）
 */
import { describe, expect, it } from 'vitest';

import {
  hasSnapshotBeforeNodes,
  isSnapshotBeforeNode,
  SNAPSHOT_RESET_ENDPOINT_ERROR,
  validateSnapshotResetEndpointStatic,
} from '@/utils/flow/snapshotPreRunValidate';
import {
  DEFAULT_ENV_MODULE_NAME,
  resolveResetBaseUrl,
} from '@/views/project/testProject/utils/envConfigUtils';

import type { GraphJson } from '@/utils/flow/graphTypes';

function graphWithSnapshotBefore(enabled: boolean): Pick<GraphJson, 'nodes'> {
  return {
    nodes: [
      {
        id: 'n1',
        type: 'http',
        position: { x: 0, y: 0 },
        data: { snapshotBefore: enabled },
      },
    ],
  };
}

describe('resolveResetBaseUrl', () => {
  it('普通 http 基址会自动补上 /test-support', () => {
    // 前提：基址为 http://host:8801
    // 期望：末尾补上 /test-support
    expect(resolveResetBaseUrl('http://host:8801')).toBe('http://host:8801/test-support');
  });

  it('无协议时补 http 并挂上 /test-support', () => {
    // 前提：基址为 localhost:8801（无协议）
    // 期望：补 http 并挂 /test-support
    expect(resolveResetBaseUrl('localhost:8801')).toBe('http://localhost:8801/test-support');
  });

  it('基址末尾斜杠会被规范化后再拼路径', () => {
    // 前提：基址末尾有斜杠
    // 期望：规范化后拼 /test-support
    expect(resolveResetBaseUrl('http://host:8801/')).toBe('http://host:8801/test-support');
  });

  it('空环境 URL 时还原基址为空串', () => {
    // 前提：环境 URL 为空或仅空白
    // 期望：还原基址为空串
    expect(resolveResetBaseUrl('')).toBe('');
    expect(resolveResetBaseUrl('   ')).toBe('');
  });

  it('非法 JSON 环境 URL 时还原为空串', () => {
    // 前提：环境 URL 为非法 JSON
    // 期望：还原基址为空串
    expect(resolveResetBaseUrl('{bad')).toBe('');
  });

  it('多模块 JSON 取默认模块 URL', () => {
    // 前提：环境 URL 为多模块 JSON
    // 期望：取默认模块 URL 并拼 /test-support
    const envUrl = JSON.stringify({ [DEFAULT_ENV_MODULE_NAME]: 'http://demo:8801' });
    expect(resolveResetBaseUrl(envUrl)).toBe('http://demo:8801/test-support');
  });
});

describe('isSnapshotBeforeNode', () => {
  it('snapshotBefore 为真值时判定为快照节点', () => {
    // 前提：snapshotBefore 为 true/'true'/1/'1'
    // 期望：均判定为快照节点
    expect(isSnapshotBeforeNode({ snapshotBefore: true })).toBe(true);
    expect(isSnapshotBeforeNode({ snapshotBefore: 'true' })).toBe(true);
    expect(isSnapshotBeforeNode({ snapshotBefore: 1 })).toBe(true);
    expect(isSnapshotBeforeNode({ snapshotBefore: '1' })).toBe(true);
  });

  it('缺省或 false 时不视为快照节点', () => {
    // 前提：无 snapshotBefore 或值为 false
    // 期望：均不为快照节点
    expect(isSnapshotBeforeNode({})).toBe(false);
    expect(isSnapshotBeforeNode({ snapshotBefore: false })).toBe(false);
    expect(isSnapshotBeforeNode(undefined)).toBe(false);
  });
});

describe('validateSnapshotResetEndpointStatic', () => {
  it('无快照节点时跳过重置端点校验', () => {
    // 前提：图中无 snapshotBefore 节点
    // 期望：校验通过
    const result = validateSnapshotResetEndpointStatic({ nodes: [] }, {
      allowDestructiveReset: 1,
      envUrl: '',
    });
    expect(result.ok).toBe(true);
  });

  it('有快照但未开破坏性重置时跳过校验', () => {
    // 前提：有快照节点但 allowDestructiveReset=0
    // 期望：校验通过
    const result = validateSnapshotResetEndpointStatic(graphWithSnapshotBefore(true), {
      allowDestructiveReset: 0,
      envUrl: '',
    });
    expect(result.ok).toBe(true);
  });

  it('开启重置且 URL 合法时校验通过', () => {
    // 前提：有快照、开启重置、envUrl 合法
    // 期望：校验通过
    const result = validateSnapshotResetEndpointStatic(graphWithSnapshotBefore(true), {
      allowDestructiveReset: 1,
      envUrl: 'http://host:8801',
    });
    expect(result.ok).toBe(true);
  });

  it('存在 snapshotBefore 节点时 hasSnapshotBeforeNodes 返回 true', () => {
    // 前提：图含 snapshotBefore=true 节点
    // 期望：hasSnapshotBeforeNodes 为 true
    expect(hasSnapshotBeforeNodes(graphWithSnapshotBefore(true))).toBe(true);
  });

  it('开启重置但 URL 为空时失败并给出端点错误', () => {
    // 前提：有快照且开启重置，envUrl 为空
    // 期望：ok 为 false，message 为端点错误文案
    const result = validateSnapshotResetEndpointStatic(graphWithSnapshotBefore(true), {
      allowDestructiveReset: 1,
      envUrl: '',
    });
    expect(result.ok).toBe(false);
    expect(result.message).toBe(SNAPSHOT_RESET_ENDPOINT_ERROR);
  });

  it('开启重置但环境 URL 非法 JSON 时失败', () => {
    // 前提：有快照且开启重置，envUrl 非法 JSON
    // 期望：校验失败
    const result = validateSnapshotResetEndpointStatic(graphWithSnapshotBefore(true), {
      allowDestructiveReset: 1,
      envUrl: '{not-json',
    });
    expect(result.ok).toBe(false);
  });

  it('多模块环境 URL 解析成功时校验通过', () => {
    // 前提：多模块 JSON envUrl 含默认模块
    // 期望：校验通过
    const envUrl = JSON.stringify({ [DEFAULT_ENV_MODULE_NAME]: 'http://demo:8801' });
    const result = validateSnapshotResetEndpointStatic(graphWithSnapshotBefore(true), {
      allowDestructiveReset: 1,
      envUrl,
    });
    expect(result.ok).toBe(true);
  });
});

/**
 * 测 snapshotPreRunValidate：快照前置节点与存档点插件启用校验。
 * 含 isSnapshotBeforeNode 只认布尔 true（字符串 / 数字不算开启）。
 * 边界：纯函数，fixture 图与环境字段。
 * 单跑：pnpm test snapshotPreRunValidate   （在 qualitest-ui 或 apps/web 下）
 */
import { describe, expect, it } from 'vitest';

import {
  hasSnapshotBeforeNodes,
  isSnapshotBeforeNode,
  SNAPSHOT_PLUGIN_REQUIRED_ERROR,
  validateSnapshotResetEndpointStatic,
} from '@/utils/flow/snapshotPreRunValidate';

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

describe('isSnapshotBeforeNode', () => {
  it('仅 snapshotBefore===true 判定为快照节点', () => {
    // 前提：snapshotBefore 为 true / 'true' / 1 / '1'
    // 期望：仅布尔 true 为快照节点
    expect(isSnapshotBeforeNode({ snapshotBefore: true })).toBe(true);
    expect(isSnapshotBeforeNode({ snapshotBefore: 'true' })).toBe(false);
    expect(isSnapshotBeforeNode({ snapshotBefore: 1 })).toBe(false);
    expect(isSnapshotBeforeNode({ snapshotBefore: '1' })).toBe(false);
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
  it('无快照节点时跳过校验', () => {
    // 前提：图中无 snapshotBefore 节点
    // 期望：校验通过
    const result = validateSnapshotResetEndpointStatic({ nodes: [] }, {
      allowDestructiveReset: 1,
      savePointPluginEnabled: false,
    });
    expect(result.ok).toBe(true);
  });

  it('有快照但未开破坏性重置时跳过校验', () => {
    // 前提：有快照节点但 allowDestructiveReset=0
    // 期望：校验通过
    const result = validateSnapshotResetEndpointStatic(graphWithSnapshotBefore(true), {
      allowDestructiveReset: 0,
      savePointPluginEnabled: false,
    });
    expect(result.ok).toBe(true);
  });

  it('开启重置且已启用 onCheckpoint 插件时校验通过', () => {
    // 前提：有快照、开启重置、checkpoint 插件已启用
    // 期望：校验通过
    const result = validateSnapshotResetEndpointStatic(graphWithSnapshotBefore(true), {
      allowDestructiveReset: 1,
      checkpointPluginEnabled: true,
    });
    expect(result.ok).toBe(true);
  });

  it('存在 snapshotBefore 节点时 hasSnapshotBeforeNodes 返回 true', () => {
    // 前提：图含 snapshotBefore=true 节点
    // 期望：hasSnapshotBeforeNodes 为 true
    expect(hasSnapshotBeforeNodes(graphWithSnapshotBefore(true))).toBe(true);
  });

  it('开启重置但未启用插件时失败', () => {
    // 前提：有快照且开启重置，未启用存档点插件
    // 期望：ok 为 false，message 为插件必填文案
    const result = validateSnapshotResetEndpointStatic(graphWithSnapshotBefore(true), {
      allowDestructiveReset: 1,
      savePointPluginEnabled: false,
    });
    expect(result.ok).toBe(false);
    expect(result.message).toBe(SNAPSHOT_PLUGIN_REQUIRED_ERROR);
  });
});

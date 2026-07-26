/**
 * snapshotPreRunValidate / resolveResetBaseUrl 单元测试。
 *
 * 运行（apps/web 目录）：yarn test snapshotPreRunValidate
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
  it('plainHttpUrl_appendsTestSupport', () => {
    expect(resolveResetBaseUrl('http://host:8081')).toBe('http://host:8081/test-support');
  });

  it('noScheme_addsHttpAndTestSupport', () => {
    expect(resolveResetBaseUrl('localhost:8081')).toBe('http://localhost:8081/test-support');
  });

  it('trailingSlashOnBase_normalized', () => {
    expect(resolveResetBaseUrl('http://host:8081/')).toBe('http://host:8081/test-support');
  });

  it('emptyEnvUrl_returnsEmpty', () => {
    expect(resolveResetBaseUrl('')).toBe('');
    expect(resolveResetBaseUrl('   ')).toBe('');
  });

  it('invalidJson_returnsEmpty', () => {
    expect(resolveResetBaseUrl('{bad')).toBe('');
  });

  it('multiModuleJson_usesDefaultModule', () => {
    const envUrl = JSON.stringify({ [DEFAULT_ENV_MODULE_NAME]: 'http://demo:8081' });
    expect(resolveResetBaseUrl(envUrl)).toBe('http://demo:8081/test-support');
  });
});

describe('isSnapshotBeforeNode', () => {
  it('acceptsBooleanAndStringTrue', () => {
    expect(isSnapshotBeforeNode({ snapshotBefore: true })).toBe(true);
    expect(isSnapshotBeforeNode({ snapshotBefore: 'true' })).toBe(true);
    expect(isSnapshotBeforeNode({ snapshotBefore: 1 })).toBe(true);
    expect(isSnapshotBeforeNode({ snapshotBefore: '1' })).toBe(true);
  });

  it('defaultsToFalse', () => {
    expect(isSnapshotBeforeNode({})).toBe(false);
    expect(isSnapshotBeforeNode({ snapshotBefore: false })).toBe(false);
    expect(isSnapshotBeforeNode(undefined)).toBe(false);
  });
});

describe('validateSnapshotResetEndpointStatic', () => {
  it('noSnapshotNodes_skips', () => {
    const result = validateSnapshotResetEndpointStatic({ nodes: [] }, {
      allowDestructiveReset: 1,
      envUrl: '',
    });
    expect(result.ok).toBe(true);
  });

  it('snapshotButResetDisabled_skips', () => {
    const result = validateSnapshotResetEndpointStatic(graphWithSnapshotBefore(true), {
      allowDestructiveReset: 0,
      envUrl: '',
    });
    expect(result.ok).toBe(true);
  });

  it('snapshotAndResetEnabled_validUrl_passes', () => {
    const result = validateSnapshotResetEndpointStatic(graphWithSnapshotBefore(true), {
      allowDestructiveReset: 1,
      envUrl: 'http://host:8081',
    });
    expect(result.ok).toBe(true);
    expect(hasSnapshotBeforeNodes(graphWithSnapshotBefore(true))).toBe(true);
  });

  it('snapshotAndResetEnabled_emptyUrl_fails', () => {
    const result = validateSnapshotResetEndpointStatic(graphWithSnapshotBefore(true), {
      allowDestructiveReset: 1,
      envUrl: '',
    });
    expect(result.ok).toBe(false);
    expect(result.message).toBe(SNAPSHOT_RESET_ENDPOINT_ERROR);
  });

  it('snapshotAndResetEnabled_badJson_fails', () => {
    const result = validateSnapshotResetEndpointStatic(graphWithSnapshotBefore(true), {
      allowDestructiveReset: 1,
      envUrl: '{not-json',
    });
    expect(result.ok).toBe(false);
  });

  it('multiModuleEnvUrl_passes', () => {
    const envUrl = JSON.stringify({ [DEFAULT_ENV_MODULE_NAME]: 'http://demo:8081' });
    const result = validateSnapshotResetEndpointStatic(graphWithSnapshotBefore(true), {
      allowDestructiveReset: 1,
      envUrl,
    });
    expect(result.ok).toBe(true);
  });
});

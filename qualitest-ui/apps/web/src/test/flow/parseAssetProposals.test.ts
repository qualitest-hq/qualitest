/**
 * 测 parseAssetProposalsFromMeta：从消息元数据还原素材库写入提案。
 * 边界：缺 key、仅有字段名、含明文 fields。
 * 单跑：pnpm test parseAssetProposals
 */
import { describe, expect, it } from 'vitest';

import { parseAssetProposalsFromMeta } from '@/views/project/testFlow/types/aiDesignTypes';

describe('parseAssetProposalsFromMeta', () => {
  it('解析含 fields 的完整提案', () => {
    // 前提：meta.assetProposals 含 key 与 fields
    // 期望：返回 key/status/fields，fieldNames 由 fields 推导
    const list = parseAssetProposalsFromMeta({
      assetProposals: [
        {
          key: 'clientAuth',
          action: 'created',
          status: 'pending',
          fields: { mobile: '13800000001', password: 'x' },
        },
      ],
    });
    expect(list).toHaveLength(1);
    expect(list[0].key).toBe('clientAuth');
    expect(list[0].fields?.password).toBe('x');
    expect(list[0].fieldNames).toEqual(['mobile', 'password']);
  });

  it('摘要模式仅有 fieldNames 时仍可展示', () => {
    // 前提：无 fields，仅有 fieldNames
    // 期望：保留 fieldNames，fields 为空
    const list = parseAssetProposalsFromMeta({
      assetProposals: [
        {
          key: 'clientAuth',
          action: 'updated',
          status: 'pending',
          fieldNames: ['password'],
        },
      ],
    });
    expect(list[0].fieldNames).toEqual(['password']);
    expect(list[0].fields).toBeUndefined();
  });

  it('缺 key 的项跳过', () => {
    // 前提：一条无 key、一条合法
    // 期望：只保留合法项
    const list = parseAssetProposalsFromMeta({
      assetProposals: [{ action: 'created' }, { key: 'ok', status: 'rejected' }],
    });
    expect(list).toHaveLength(1);
    expect(list[0].key).toBe('ok');
    expect(list[0].status).toBe('rejected');
  });
});

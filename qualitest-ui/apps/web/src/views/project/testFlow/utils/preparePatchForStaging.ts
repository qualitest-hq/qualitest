/**
 * AI patch 灌入 Staging 前的 HTTP 节点预处理：
 * 拉取接口详情后做测值差分规范化。
 */
import type { FlowDesignPatch } from '../types/aiDesignTypes';
import {
  enrichFlowDesignPatchHttpNodes,
} from './patchHttpNodeNormalize';
import { resolveCanvasApiDetail } from './resolveCanvasApiDetail';

type JsonRecord = Record<string, unknown>;

function parseJsonField(raw: unknown): JsonRecord | null {
  if (raw && typeof raw === 'object') {
    return raw as JsonRecord;
  }
  if (typeof raw === 'string' && raw.trim()) {
    try {
      return JSON.parse(raw) as JsonRecord;
    } catch {
      return null;
    }
  }
  return null;
}

function normalizeApiDetail(detail: JsonRecord): JsonRecord {
  const next = { ...detail };
  const requestConfig = parseJsonField(detail.requestConfig);
  if (requestConfig) {
    next.requestConfig = requestConfig;
  }
  return next;
}

export async function preparePatchForStaging(patch: FlowDesignPatch): Promise<FlowDesignPatch> {
  return enrichFlowDesignPatchHttpNodes(patch, async (apiId) => {
    const detail = await resolveCanvasApiDetail(apiId);
    return detail ? normalizeApiDetail(detail) : null;
  });
}

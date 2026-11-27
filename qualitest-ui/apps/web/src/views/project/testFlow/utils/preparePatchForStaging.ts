/**
 * AI patch 灌入 Staging 前预处理。
 * HTTP data 形状以服务端 FlowDesignHttpNodeNormalizer 为准；此处仅深拷贝，不再做 FE 镜像差分。
 */
import type { FlowDesignPatch } from '../types/aiDesignTypes';

export async function preparePatchForStaging(patch: FlowDesignPatch): Promise<FlowDesignPatch> {
  return JSON.parse(JSON.stringify(patch)) as FlowDesignPatch;
}

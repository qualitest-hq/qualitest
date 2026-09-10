/**
 * AI 平台通用 API：模型列表、画布多轮会话。
 */
import request from '@/utils/request';

/** 测试流 AI 设计场景标识 */
export const AI_SCENE_TEST_FLOW_DESIGN = 'test_flow_design';

/** AI API 助手场景标识 */
export const AI_SCENE_TEST_API_DESIGN = 'test_api_design';

/** 单个模型下拉选项 */
export interface AiModelOption {
  aiLlmModelId: string;
  modelName: string;
  sortNum?: number;
  thinkingCapable?: boolean;
  thinkingDefault?: boolean;
}

/** 厂商分组（对应 el-option-group） */
export interface AiModelVendorGroup {
  aiLlmVendorId: string;
  vendorName: string;
  sortNum?: number;
  models: AiModelOption[];
}

/** GET /ai/models 响应体；defaultModelId 由后端按 sort_num 计算 */
export interface AiModelsListResult {
  defaultModelId?: string;
  vendors: AiModelVendorGroup[];
}

/** 会话列表项 */
export interface AiChatSessionItem {
  aiChatSessionId: string;
  testProjectId?: string;
  sessionScene?: string;
  bizRefJson?: string;
  currentModelId?: string;
  /** 0 关、1 开、null/undefined 跟随模型默认 */
  thinkingEnabled?: number | null;
  sessionTitle?: string;
  createTime?: string;
}

/** 会话消息加载模式：summary 不含 patchJson，full 含完整 meta */
export type AiChatMessageDetailMode = 'summary' | 'full';

/** 服务端会话消息 */
export interface AiChatMessageItem {
  aiChatMessageId: string;
  aiChatSessionId?: string;
  messageRole: 'user' | 'assistant' | 'system';
  aiLlmModelId?: string;
  messageContent?: string;
  thinkingContent?: string;
  resultMetaJson?: string;
  createTime?: string;
}

/** 会话详情 */
export interface AiChatSessionDetail {
  session: AiChatSessionItem;
  messages: AiChatMessageItem[];
  /** 会话消息总数（分页时返回） */
  totalMessageCount?: number;
  /** 是否还有更早消息（分页时返回） */
  hasMoreOlder?: boolean;
}

/** 会话消息分页查询 */
export interface AiChatSessionPageQuery {
  limit?: number;
  beforeMessageId?: string;
}

/** 拉取按厂商分组的启用模型列表，用于设计面板模型下拉 */
export function listAiModels() {
  return request({
    url: '/ai/models',
    method: 'get',
  });
}

/** 构建测试流设计场景的 bizRef JSON */
export function buildTestFlowDesignBizRef(testFlowId: string): string {
  return JSON.stringify({ testFlowId });
}

/** 列出当前用户在指定 API 下的 AI API 助手会话 */
export function listApiDesignChatSessions(testProjectId: string, testProjectApiId: string) {
  return request({
    url: '/ai/chat/session/list',
    method: 'get',
    params: {
      scene: AI_SCENE_TEST_API_DESIGN,
      testProjectId,
      testProjectApiId,
    },
  });
}

/** 列出当前用户在指定 testFlow 下的设计会话 */
export function listAiChatSessions(testProjectId: string, testFlowId: string) {
  return request({
    url: '/ai/chat/session/list',
    method: 'get',
    params: {
      scene: AI_SCENE_TEST_FLOW_DESIGN,
      testProjectId,
      testFlowId,
    },
  });
}

/** 获取会话详情及消息；默认 summary 模式（Lazy Patch，不含 patchJson） */
export function getAiChatSession(
  sessionId: string,
  messageDetail: AiChatMessageDetailMode = 'summary',
  page?: AiChatSessionPageQuery,
) {
  return request({
    url: `/ai/chat/session/${sessionId}`,
    method: 'get',
    params: {
      messageDetail,
      limit: page?.limit,
      beforeMessageId: page?.beforeMessageId,
    },
  });
}

/** 获取单条消息完整 meta（含 patchJson） */
export function getAiChatMessageMeta(messageId: string) {
  return request({
    url: `/ai/chat/message/${messageId}/meta`,
    method: 'get',
  });
}

/** 新建空会话 */
export function createAiChatSession(data: {
  sessionScene: string;
  testProjectId: string;
  bizRefJson: string;
  currentModelId?: string;
  sessionTitle?: string;
}) {
  return request({
    url: '/ai/chat/session',
    method: 'post',
    data,
  });
}

/** 更新会话绑定的模型 */
export function updateAiChatSessionModel(sessionId: string, currentModelId: string) {
  return request({
    url: `/ai/chat/session/${sessionId}/model`,
    method: 'put',
    data: { currentModelId },
  });
}

/** 更新会话思考开关 */
export function updateAiChatSessionThinking(sessionId: string, thinkingEnabled: boolean) {
  return request({
    url: `/ai/chat/session/${sessionId}/thinking`,
    method: 'put',
    data: { thinkingEnabled },
  });
}

/** 软删除会话 */
export function deleteAiChatSession(sessionId: string) {
  return request({
    url: `/ai/chat/session/${sessionId}`,
    method: 'delete',
  });
}

/** Staging 丢弃时摘除会话 clientId→雪花映射 */
export function pruneFlowDesignClientIdMap(sessionId: string, snowflakeIds: string[]) {
  return request({
    url: `/ai/chat/session/${sessionId}/flow-design-id-map/prune`,
    method: 'post',
    data: { snowflakeIds },
  });
}

/**
 * 从锚点消息起截断会话后续消息。
 * @param inclusive true 时连同锚点消息一并删除
 */
export function truncateAiChatMessagesAfter(
  sessionId: string,
  anchorMessageId: string,
  inclusive = false,
) {
  return request({
    url: `/ai/chat/session/${sessionId}/messages/truncate-after/${anchorMessageId}`,
    method: 'delete',
    params: { inclusive },
  });
}

/**
 * API 配置协议版本常量。
 * requestConfig、responseConfig 及导入包根对象的 configVersion 字段均使用此处取值（当前为 1）。
 */
export const CONFIG_VERSION = 1 as const;

/** 写入 requestConfig.configVersion */
export const REQUEST_CONFIG_VERSION = CONFIG_VERSION;

/** 写入 responseConfig.configVersion */
export const RESPONSE_CONFIG_VERSION = CONFIG_VERSION;

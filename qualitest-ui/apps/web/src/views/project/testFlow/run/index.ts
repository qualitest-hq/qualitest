/**
 * 跑流域出口：会话互斥控制、运行库回放、路径模拟。
 * Esc 停止与方向键步进统一走 runSessionController，避免回放与模拟模块互相引用。
 */
export {
  registerRunSession,
  stopRunSession,
  stopOtherRunSessions,
  abortActiveRunSession,
  stepActiveRunSession,
} from './runSessionController';
export { usePlayback, startRunReplay, endRunReplay } from './usePlayback';
export { useFlowSimulate, endSimulate, pathSimulateActive } from './useFlowSimulate';

import {ipcMain} from 'electron'
import {IpcChannel} from '@qualitest/transport-types'
import {executeGovernanceHttp, executeDebugHttp} from '../http/outbound.js'
import {getRuntimeBackendBaseUrl} from '../config/backend-runtime.js'

const CHANNEL_GOVERNANCE = IpcChannel.GOVERNANCE
const CHANNEL_DEBUG_HTTP = IpcChannel.DEBUG_HTTP

/**
 * @param {object} opts
 * @param {string} opts.devApiPrefix
 */
export function registerIpc(opts) {
  ipcMain.removeHandler(CHANNEL_GOVERNANCE)
  ipcMain.removeHandler(CHANNEL_DEBUG_HTTP)
  ipcMain.removeHandler(IpcChannel.BACKEND_GET_CONFIG)

  const runtimeOpts = () => ({
    backendBaseUrl: getRuntimeBackendBaseUrl(),
    devApiPrefix: opts.devApiPrefix
  })

  ipcMain.handle(CHANNEL_GOVERNANCE, async (event, payload) => {
    return executeGovernanceHttp(runtimeOpts(), payload)
  })

  ipcMain.handle(CHANNEL_DEBUG_HTTP, async (event, payload) => {
    return executeDebugHttp(payload)
  })

  ipcMain.handle(IpcChannel.BACKEND_GET_CONFIG, async () => ({
    backendBaseUrl: getRuntimeBackendBaseUrl()
  }))
}

export {CHANNEL_GOVERNANCE, CHANNEL_DEBUG_HTTP}

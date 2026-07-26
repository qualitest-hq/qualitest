import {contextBridge, ipcRenderer} from 'electron'
import {IpcChannel} from '@qualitest/transport-types'

const CHANNEL_GOVERNANCE = IpcChannel.GOVERNANCE
const CHANNEL_DEBUG_HTTP = IpcChannel.DEBUG_HTTP

contextBridge.exposeInMainWorld('__QUALITEST_ELECTRON__', {
  transport: 'electron-main',
  getBackendBaseUrl: () =>
    ipcRenderer.invoke(IpcChannel.BACKEND_GET_CONFIG).then((r) => r?.backendBaseUrl || ''),
  governanceRequest: (payload) => ipcRenderer.invoke(CHANNEL_GOVERNANCE, payload),
  debugHttpRequest: (payload) => ipcRenderer.invoke(CHANNEL_DEBUG_HTTP, payload)
})

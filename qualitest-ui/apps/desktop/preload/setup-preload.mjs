import {contextBridge, ipcRenderer} from 'electron'
import {IpcChannel} from '@qualitest/transport-types'

contextBridge.exposeInMainWorld('__QUALITEST_BACKEND_SETUP__', {
  getInitial: () => ipcRenderer.invoke(IpcChannel.BACKEND_SETUP_INITIAL),
  submit: (url) => ipcRenderer.invoke(IpcChannel.BACKEND_SETUP_SUBMIT, url),
  cancel: () => ipcRenderer.invoke(IpcChannel.BACKEND_SETUP_CANCEL)
})

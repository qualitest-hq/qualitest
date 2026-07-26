import path from 'node:path'
import {fileURLToPath} from 'node:url'
import {BrowserWindow, ipcMain, nativeImage} from 'electron'
import {resolveAppIconPath} from './app-branding.js'
import {IpcChannel} from '@qualitest/transport-types'
import {normalizeBackendBaseUrl} from './backend-config.js'
import {probeBackend} from '../http/probe.js'
import {BACKEND_SETUP_I18N} from './backend-setup-i18n.js'

const __dirname = path.dirname(fileURLToPath(import.meta.url))

/**
 * 弹出后端地址配置窗；成功 resolve URL，取消 resolve null
 * @param {string} initialUrl
 * @param {string} [lastError]
 * @returns {Promise<string|null>}
 */
export function promptBackendBaseUrl(initialUrl, lastError = '') {
  return new Promise((resolve) => {
    let settled = false
    const finish = (value) => {
      if (settled) {
        return
      }
      settled = true
      cleanup()
      try {
        if (!win.isDestroyed()) {
          win.close()
        }
      } catch {
        /* ignore */
      }
      resolve(value)
    }

    let winIcon
    try {
      const image = nativeImage.createFromPath(resolveAppIconPath())
      if (!image.isEmpty()) {
        winIcon = image
      }
    } catch {
      /* ignore */
    }

    const win = new BrowserWindow({
      width: 500,
      height: 340,
      useContentSize: true,
      icon: winIcon,
      resizable: false,
      minimizable: false,
      maximizable: false,
      fullscreenable: false,
      show: false,
      autoHideMenuBar: true,
      title: BACKEND_SETUP_I18N.windowTitle,
      webPreferences: {
        preload: path.join(__dirname, '../../preload/setup-preload.mjs'),
        contextIsolation: true,
        nodeIntegration: false,
        sandbox: false
      }
    })

    const handlers = {
      initial: () => ({
        url: initialUrl,
        lastError: lastError || '',
        texts: {...BACKEND_SETUP_I18N}
      }),
      submit: async (_event, rawUrl) => {
        let normalized
        try {
          normalized = normalizeBackendBaseUrl(rawUrl)
        } catch (e) {
          return {ok: false, error: e && e.message ? String(e.message) : BACKEND_SETUP_I18N.invalidUrl}
        }
        const ok = await probeBackend(normalized)
        if (!ok) {
          return {
            ok: false,
            error: BACKEND_SETUP_I18N.stillCannotConnect,
            probeError: `${BACKEND_SETUP_I18N.probeErrorPrefix}${normalized}`
          }
        }
        finish(normalized)
        return {ok: true}
      },
      cancel: () => {
        finish(null)
        return {ok: true}
      }
    }

    function cleanup() {
      ipcMain.removeHandler(IpcChannel.BACKEND_SETUP_INITIAL)
      ipcMain.removeHandler(IpcChannel.BACKEND_SETUP_SUBMIT)
      ipcMain.removeHandler(IpcChannel.BACKEND_SETUP_CANCEL)
    }

    ipcMain.handle(IpcChannel.BACKEND_SETUP_INITIAL, handlers.initial)
    ipcMain.handle(IpcChannel.BACKEND_SETUP_SUBMIT, handlers.submit)
    ipcMain.handle(IpcChannel.BACKEND_SETUP_CANCEL, handlers.cancel)

    win.on('closed', () => {
      if (!settled) {
        finish(null)
      }
    })

    win.once('ready-to-show', () => win.show())
    win.loadFile(path.join(__dirname, '../setup/backend-config.html'))
  })
}

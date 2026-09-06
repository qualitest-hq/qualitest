import path from 'node:path'
import {fileURLToPath} from 'node:url'
import {app, BrowserWindow, Menu, nativeImage} from 'electron'
import {registerIpc} from './ipc/register.js'
import {ensureBackendAvailable} from './config/ensure-backend.js'
import {APP_DISPLAY_TITLE, APP_PRODUCT_NAME, resolveAppIconPath} from './config/app-branding.js'

const __dirname = path.dirname(fileURLToPath(import.meta.url))

const isDev = process.env.NODE_ENV === 'development' || !app.isPackaged

app.setName(APP_PRODUCT_NAME)
if (process.platform === 'win32') {
  app.setAppUserModelId('com.qualitest.desktop')
}

// Windows/Linux 打包后默认会挂 Electron 英文菜单栏，产品应用应去掉
if (process.platform !== 'darwin') {
  Menu.setApplicationMenu(null)
}

/** 后端配置窗关闭时尚无主窗口，须避免 window-all-closed 误触发退出 */
let quitWhenAllWindowsClosed = false

function getAppIcon() {
  try {
    const image = nativeImage.createFromPath(resolveAppIconPath())
    if (!image.isEmpty()) {
      return image
    }
  } catch {
    /* ignore */
  }
  return undefined
}

function getWebDevServerUrl() {
  return process.env.QUALITEST_WEB_DEV_URL || 'http://127.0.0.1:5180'
}

function createWindow() {
  const win = new BrowserWindow({
    width: 1280,
    height: 800,
    title: APP_DISPLAY_TITLE,
    icon: getAppIcon(),
    autoHideMenuBar: true,
    webPreferences: {
      preload: path.join(__dirname, '../preload/index.mjs'),
      contextIsolation: true,
      nodeIntegration: false,
      sandbox: false,
      webSecurity: true
    }
  })

  if (isDev) {
    const devUrl = getWebDevServerUrl()
    console.log('[qualitest-desktop] 开发模式加载页面:', devUrl)
    win.webContents.on('did-fail-load', (event, errorCode, errorDescription, validatedURL, isMainFrame) => {
      if (!isMainFrame) {
        return
      }
      const target = String(validatedURL || '')
      if (!target.startsWith('http://') && !target.startsWith('https://')) {
        return
      }
      const html = `<!DOCTYPE html><html><head><meta charset="utf-8"><title>无法加载开发页</title></head><body style="font-family:system-ui,sans-serif;padding:24px;line-height:1.6;max-width:640px">
<h1 style="margin-top:0">无法连接到 Web 开发服务</h1>
<p>请求地址：<code style="word-break:break-all">${devUrl}</code></p>
<p><strong>推荐：</strong>在 <code>qualitest-ui</code> 根目录一条命令同时启动 Vite 与桌面壳：</p>
<pre style="background:#f5f5f5;padding:12px;border-radius:8px">pnpm dev:desktop</pre>
<p>若你只用了「仅 Electron」命令，请<strong>另开终端</strong>先执行：</p>
<pre style="background:#f5f5f5;padding:12px;border-radius:8px">pnpm dev:web</pre>
<p>再运行 <code>pnpm dev:desktop:only</code>。</p>
<p>若 Vite 实际监听在其它端口，请设置环境变量 <code>QUALITEST_WEB_DEV_URL</code>（与 Vite 终端里 Local 地址一致）后重新运行 <code>pnpm dev:desktop</code>。</p>
<p style="color:#666;font-size:14px">错误码 ${errorCode}：${String(errorDescription || '')}</p>
</body></html>`
      win.loadURL('data:text/html;charset=utf-8,' + encodeURIComponent(html))
    })
    win.loadURL(devUrl)
    win.webContents.openDevTools({mode: 'detach'})
  } else {
    const indexHtml = path.join(process.resourcesPath, 'web-dist', 'index.html')
    win.loadFile(indexHtml)
  }

  return win
}

app.whenReady().then(async () => {
  const backendBaseUrl = await ensureBackendAvailable()
  if (!backendBaseUrl) {
    app.quit()
    return
  }

  registerIpc({
    devApiPrefix: process.env.QUALITEST_DEV_API_PREFIX || '/dev-api'
  })
  createWindow()
  quitWhenAllWindowsClosed = true

  app.on('activate', () => {
    if (BrowserWindow.getAllWindows().length === 0) {
      createWindow()
    }
  })
})

app.on('window-all-closed', () => {
  if (!quitWhenAllWindowsClosed) {
    return
  }
  if (process.platform !== 'darwin') {
    app.quit()
  }
})

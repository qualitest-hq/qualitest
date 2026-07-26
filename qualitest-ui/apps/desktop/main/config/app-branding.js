import fs from 'node:fs'
import path from 'node:path'
import {fileURLToPath} from 'node:url'
import {app} from 'electron'

const __dirname = path.dirname(fileURLToPath(import.meta.url))

/** 任务管理器 / 进程名 / 安装目录名（英文） */
export const APP_PRODUCT_NAME = 'Qualitest'

/** 窗口标题栏与页面标题（中文） */
export const APP_DISPLAY_TITLE = '质衡'

/**
 * 运行时图标路径（开发 / 打包）
 * @returns {string}
 */
export function resolveAppIconPath() {
  if (app.isPackaged) {
    const resources = process.resourcesPath
    if (process.platform === 'win32') {
      const ico = path.join(resources, 'app-icon.ico')
      if (fs.existsSync(ico)) {
        return ico
      }
    }
    return path.join(resources, 'app-icon.png')
  }
  const appRoot = app.getAppPath()
  const buildDir = path.join(appRoot, 'build')
  // 开发态标题栏：优先 ICO（与 DevTools 用的 /favicon.ico 同源），避免 PNG 被系统缩到 16px 时发糊/裁切
  const devCandidates = [
    path.join(buildDir, 'icon.ico'),
    path.resolve(appRoot, '../../web/public/favicon.ico'),
    path.join(buildDir, 'icon.png')
  ]
  for (const candidate of devCandidates) {
    if (fs.existsSync(candidate)) {
      return candidate
    }
  }
  return path.join(buildDir, 'icon.png')
}

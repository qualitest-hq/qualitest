/**
 * 桌面端构建/开发辅助
 *
 * brand — 生成 icon.ico，rcedit 写入 electron.exe（供 build 打包）
 * build — brand 后执行 electron-builder --dir
 * dev   — 直接启动 electron（窗口图标由 main 读取 build/icon.png）
 */
import {spawn} from 'node:child_process'
import fs from 'node:fs'
import path from 'node:path'
import {fileURLToPath} from 'node:url'
import {createRequire} from 'node:module'
import sharp from 'sharp'
import pngToIco from 'png-to-ico'
import rcedit from 'rcedit'

const __dirname = path.dirname(fileURLToPath(import.meta.url))
const desktopRoot = path.resolve(__dirname, '..')
const require = createRequire(import.meta.url)
const electronExe = require('electron')
const legacyDevExe = path.join(path.dirname(electronExe), 'Qualitest-dev.exe')
const iconPng = path.join(desktopRoot, 'build', 'icon.png')
const iconIco = path.join(desktopRoot, 'build', 'icon.ico')
const VERSION_STRINGS = {
  ProductName: 'Qualitest',
  FileDescription: 'Qualitest',
  CompanyName: 'Qualitest',
  InternalName: 'Qualitest',
  OriginalFilename: 'Qualitest.exe',
  ProductVersion: '0.1.0.0',
  FileVersion: '0.1.0.0'
}

const command = process.argv[2] || 'brand'

async function buildIco() {
  if (!fs.existsSync(iconPng)) {
    throw new Error(`缺少图标文件: ${iconPng}`)
  }
  const sizes = [256, 48, 32, 16]
  const pngBuffers = await Promise.all(
    sizes.map((size) =>
      sharp(iconPng)
        .resize(size, size, {fit: 'contain', background: {r: 0, g: 0, b: 0, alpha: 0}})
        .png()
        .toBuffer()
    )
  )
  fs.writeFileSync(iconIco, await pngToIco(pngBuffers))
  console.log('[qualitest-desktop] 已生成 build/icon.ico')
}

async function patchExe(exePath) {
  await rcedit(exePath, {icon: iconIco, 'version-string': VERSION_STRINGS})
  console.log('[qualitest-desktop] 已写入图标与版本信息:', exePath)
}

function removeLegacyDevExeInDist() {
  if (!fs.existsSync(legacyDevExe)) return
  try {
    fs.unlinkSync(legacyDevExe)
    console.log('[qualitest-desktop] 已移除 electron/dist 中的旧开发 exe')
  } catch (err) {
    console.warn('[qualitest-desktop] 无法删除旧开发 exe:', err?.message || err)
  }
}

async function brand() {
  await buildIco()
  removeLegacyDevExeInDist()
  await patchExe(electronExe)
}

function runElectronBuilder() {
  const builderCli = path.resolve(desktopRoot, '../../node_modules/electron-builder/cli.js')
  const child = spawn(process.execPath, [builderCli, '--dir'], {
    cwd: desktopRoot,
    stdio: 'inherit',
    env: {
      ...process.env,
      CSC_IDENTITY_AUTO_DISCOVERY: 'false',
      ELECTRON_BUILDER_OFFLINE: 'true'
    }
  })
  child.on('close', (code) => process.exit(code ?? 1))
}

function runDev() {
  const child = spawn(electronExe, ['.'], {
    cwd: desktopRoot,
    stdio: 'inherit',
    windowsHide: false,
    env: {...process.env, NODE_ENV: 'development'}
  })
  child.on('close', (code, signal) => {
    if (code === null) {
      console.error(electronExe, 'exited with signal', signal)
      process.exit(1)
    }
    process.exit(code ?? 0)
  })
  for (const sig of ['SIGINT', 'SIGTERM']) {
    process.on(sig, () => {
      if (!child.killed) child.kill(sig)
    })
  }
}

async function main() {
  if (command === 'build') {
    await brand()
    runElectronBuilder()
    return
  }
  if (command === 'dev') {
    runDev()
    return
  }
  if (command === 'brand') {
    await brand()
    return
  }
  console.error(`未知命令: ${command}（可用: brand | build | dev）`)
  process.exit(1)
}

main().catch((err) => {
  console.error('[qualitest-desktop]', err)
  process.exit(1)
})

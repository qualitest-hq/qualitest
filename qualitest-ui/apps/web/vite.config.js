import { defineConfig, loadEnv } from 'vite'
import path from 'path'
import { fileURLToPath } from 'node:url'
import createVitePlugins from './vite/plugins'

const __dirname = path.dirname(fileURLToPath(import.meta.url))
const baseUrl = 'http://127.0.0.1:8080' // 后端接口（避免 localhost 解析为 ::1 导致 ECONNREFUSED）

// https://vitejs.dev/config/
export default defineConfig(({ mode, command }) => {
  const env = loadEnv(mode, __dirname)
  const { VITE_APP_ENV } = env
  const isDesktopBuild = mode === 'desktop' || VITE_APP_ENV === 'desktop'
  return {
    // 部署生产环境和开发环境下的URL。
    // 默认情况下，vite 会假设你的应用是被部署在一个域名的根路径上
    // 例如 https://www.ruoyi.vip/。如果应用被部署在一个子路径上，你就需要用这个选项指定这个子路径。例如，如果你的应用被部署在 https://www.ruoyi.vip/admin/，则设置 baseUrl 为 /admin/。
    // Electron loadFile(file://) 必须使用相对 base，否则 /static/* 会解析到磁盘根目录导致白屏/一直转圈
    base: isDesktopBuild ? './' : '/',
    envDir: __dirname,
    plugins: createVitePlugins(env, command === 'build'),
    resolve: {
      // https://cn.vitejs.dev/config/#resolve-alias
      alias: {
        // 设置路径
        '~': path.resolve(__dirname, './'),
        // 设置别名
        '@': path.resolve(__dirname, './src')
      },
      // https://cn.vitejs.dev/config/#resolve-extensions
      extensions: ['.mjs', '.ts', '.tsx', '.js', '.jsx', '.json', '.vue']
    },
    // 打包配置
    build: {
      // https://vite.dev/config/build-options.html
      sourcemap: command === 'build' ? false : 'inline',
      outDir: 'dist',
      assetsDir: 'assets',
      chunkSizeWarningLimit: 2000,
      rollupOptions: {
        output: {
          chunkFileNames: 'static/js/[name]-[hash].js',
          entryFileNames: 'static/js/[name]-[hash].js',
          assetFileNames: 'static/[ext]/[name]-[hash].[ext]'
        }
      }
    },
    // vite 相关配置
    server: {
      // 避免 Windows 上占用 80 需管理员权限导致 dev 起不来，与 apps/desktop 默认 QUALITEST_WEB_DEV_URL 一致
      port: Number(process.env.VITE_DEV_SERVER_PORT) || 5173,
      strictPort: true,
      host: true,
      open: false,
      proxy: {
        // https://cn.vitejs.dev/config/#server-proxy
        '/dev-api': {
          target: baseUrl,
          changeOrigin: true,
          rewrite: (p) => p.replace(/^\/dev-api/, '')
        },
         // springdoc proxy
         '^/v3/api-docs/(.*)': {
          target: baseUrl,
          changeOrigin: true,
        }
      }
    },
    css: {
      postcss: {
        plugins: [
          {
            postcssPlugin: 'internal:charset-removal',
            AtRule: {
              charset: (atRule) => {
                if (atRule.name === 'charset') {
                  atRule.remove()
                }
              }
            }
          }
        ]
      }
    },
    test: {
      environment: 'node',
      include: ['src/test/**/*.test.ts', 'src/test/**/*.spec.ts'],
      setupFiles: ['./src/test/vitest-setup.ts'],
      reporters: ['verbose'],
    }
  }
})

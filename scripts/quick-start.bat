@echo off
REM 质衡一键启动：Docker Compose 全栈
REM 用法：
REM   scripts\quick-start.bat           优先拉 GHCR，失败则本地构建
REM   scripts\quick-start.bat -h        显示本说明
REM 说明：从任意目录调用即可（脚本会切到仓库根）；依赖 Docker Desktop + Compose V2
REM 靶场 / RustFS：独立仓 qualitest-demo 另起 Compose（本仓无 --profile demo）
chcp 65001 >nul
setlocal
cd /d "%~dp0.."

if /I "%~1"=="-h" goto :usage
if /I "%~1"=="--help" goto :usage
if /I "%~1"=="/?" goto :usage
if /I "%~1"=="help" goto :usage
if not "%~1"=="" (
  echo [error] 未知参数: %~1
  call :usage
  exit /b 1
)

where docker >nul 2>&1
if errorlevel 1 (
  echo [error] 未找到 docker，请先安装 Docker Desktop
  exit /b 1
)

docker compose version >nul 2>&1
if errorlevel 1 (
  echo [error] 需要 Docker Compose V2（docker compose）
  exit /b 1
)

if not exist ".env" (
  if exist ".env.example" (
    copy /Y ".env.example" ".env" >nul
    echo [info] 已从 .env.example 生成 .env
  )
)

set "WEB_PORT=80"
if exist ".env" (
  for /f "usebackq tokens=1,* delims==" %%A in (`findstr /b /c:"WEB_PORT=" ".env"`) do set "WEB_PORT=%%B"
)

echo [info] 拉取 GHCR 预构建镜像（ghcr.io/qualitest-hq/qualitest-app^|web）...
docker compose pull app web
if errorlevel 1 (
  echo [warn] pull 失败（镜像未发布 / 网络），改为本地构建 ...
  docker compose up -d --build
  if errorlevel 1 exit /b 1
) else (
  echo [info] 启动 MySQL + Redis + 后端 + Nginx ...
  docker compose up -d
  if errorlevel 1 exit /b 1
)

echo.
echo ==============================================
echo  质衡已启动（请稍等后端健康 / Flyway 完成后再登录）
if "%WEB_PORT%"=="80" (
  echo  浏览器打开: http://localhost
) else (
  echo  浏览器打开: http://localhost:%WEB_PORT%
)
echo  默认账号:   admin / admin123
echo  停止:       docker compose down
echo  仅依赖:     scripts\dev-deps-up.bat
echo  靶场/RustFS: 见 qualitest-demo（另起 Compose；本仓无 --profile demo）
echo ==============================================
endlocal
exit /b 0

:usage
echo 用法:
echo   scripts\quick-start.bat           启动全栈（优先 GHCR；失败则 --build）
echo   scripts\quick-start.bat -h        显示本说明
echo.
echo 仅依赖:     scripts\dev-deps-up.bat （停：scripts\dev-deps-down.bat）
echo 停止全栈:   docker compose down
echo 本地重建:   docker compose up -d --build
echo 靶场/RustFS: 独立仓 qualitest-demo（另起 Compose；本仓无 --profile demo）
exit /b 0

@echo off
REM 质衡一键启动：Docker Compose 全栈
REM 用法：
REM   scripts\quick-start.bat           启动 MySQL + Redis + 后端 + Nginx
REM   scripts\quick-start.bat -h        显示本说明
REM 说明：从任意目录调用即可（脚本会切到仓库根）；依赖 Docker Desktop + Compose V2
REM 靶场 / RustFS：见独立仓 qualitest-demo
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

echo [info] 构建并启动 MySQL + Redis + 后端 + Nginx ...
docker compose up -d --build
if errorlevel 1 exit /b 1

echo.
echo ==============================================
echo  质衡已启动
echo  浏览器打开: http://localhost
echo  默认账号:   admin / admin123
echo  停止:       docker compose down
echo  仅依赖:     docker compose up -d mysql redis
echo  靶场/RustFS: 见 qualitest-demo（scripts\quick-start.bat）
echo ==============================================
endlocal
exit /b 0

:usage
echo 用法:
echo   scripts\quick-start.bat           启动全栈（MySQL + Redis + 后端 + Nginx）
echo   scripts\quick-start.bat -h        显示本说明
echo.
echo 仅依赖:     docker compose up -d mysql redis
echo 停止:       docker compose down
echo 靶场/RustFS: 独立仓 qualitest-demo
exit /b 0

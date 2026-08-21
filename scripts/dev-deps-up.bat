@echo off
REM 质衡开发依赖：仅启动 MySQL + Redis（本机 mvn / pnpm 热更）
REM 用法：
REM   scripts\dev-deps-up.bat           启动并等待 healthy
REM   scripts\dev-deps-up.bat -h        说明
REM 停止：scripts\dev-deps-down.bat
chcp 65001 >nul
setlocal EnableExtensions
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

docker compose ps --status running --services 2>nul | findstr /R /C:"^app$" /C:"^web$" >nul 2>&1
if not errorlevel 1 (
  echo [warn] 检测到 app/web 正在运行；本脚本只确保 mysql/redis up
)

echo [info] 启动 MySQL + Redis ...
docker compose up -d mysql redis
if errorlevel 1 exit /b 1

echo [info] 等待 mysql / redis healthy ...
set /a _tries=0
:wait_loop
set /a _tries+=1
if %_tries% GTR 60 (
  echo [error] 等待超时。请执行: docker compose logs mysql redis
  exit /b 1
)
for /f "usebackq delims=" %%A in (`docker inspect -f "{{.State.Health.Status}}" qualitest-mysql 2^>nul`) do set MYSQL_H=%%A
for /f "usebackq delims=" %%A in (`docker inspect -f "{{.State.Health.Status}}" qualitest-redis 2^>nul`) do set REDIS_H=%%A
if /I "%MYSQL_H%"=="healthy" if /I "%REDIS_H%"=="healthy" goto :ready
timeout /t 2 /nobreak >nul
goto :wait_loop

:ready
echo.
echo ==============================================
echo  开发依赖已就绪（MySQL + Redis）
echo  MySQL:  localhost:3306  库名 qualitest
echo  Redis:  localhost:6379
echo.
echo  本机后端（profile=dev，Flyway 会自动迁库）:
echo    mvn -pl qualitest-admin -am -DskipTests package
echo    再 qualitest.bat / qualitest.sh 或 spring-boot:run
echo  本机前端:
echo    cd qualitest-ui ^&^& pnpm install ^&^& pnpm dev
echo    浏览器 http://localhost:5173
echo.
echo  默认账号: admin / admin123（首启后端迁库后）
echo  停止依赖: scripts\dev-deps-down.bat
echo  注意:    用 JRebel 时请关掉 spring-boot-devtools restart
echo ==============================================
endlocal
exit /b 0

:usage
echo 用法:
echo   scripts\dev-deps-up.bat           启动 MySQL + Redis（不启 app / web）
echo   scripts\dev-deps-up.bat -h        显示本说明
echo.
echo 停止依赖:   scripts\dev-deps-down.bat
echo 全栈一键:   scripts\quick-start.bat
echo 说明:       docs\deploy.md「仅依赖（本机开发）」
exit /b 0

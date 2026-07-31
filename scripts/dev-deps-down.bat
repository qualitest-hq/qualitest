@echo off
REM 质衡开发依赖：停止 MySQL + Redis（保留数据卷）
REM 用法：
REM   scripts\dev-deps-down.bat           stop mysql redis
REM   scripts\dev-deps-down.bat -v        stop 并删除容器（仍保留卷）
REM   scripts\dev-deps-down.bat -h
chcp 65001 >nul
setlocal EnableExtensions
cd /d "%~dp0.."

set REMOVE_CONTAINERS=0
if /I "%~1"=="-h" goto :usage
if /I "%~1"=="--help" goto :usage
if /I "%~1"=="/?" goto :usage
if /I "%~1"=="help" goto :usage
if /I "%~1"=="-v" set REMOVE_CONTAINERS=1
if /I "%~1"=="--rm" set REMOVE_CONTAINERS=1
if /I "%~1"=="--remove" set REMOVE_CONTAINERS=1
if not "%~1"=="" if %REMOVE_CONTAINERS%==0 (
  echo [error] 未知参数: %~1
  call :usage
  exit /b 1
)

where docker >nul 2>&1
if errorlevel 1 (
  echo [error] 未找到 docker
  exit /b 1
)

docker compose version >nul 2>&1
if errorlevel 1 (
  echo [error] 需要 Docker Compose V2（docker compose）
  exit /b 1
)

docker compose ps --status running --services 2>nul | findstr /R /C:"^app$" /C:"^web$" >nul 2>&1
if not errorlevel 1 (
  echo [warn] app/web 仍在运行；停掉 mysql/redis 后全栈将不可用。建议先: docker compose stop app web
)

if %REMOVE_CONTAINERS%==1 (
  echo [info] 停止并删除 mysql / redis 容器（数据卷保留）...
  docker compose rm -sf mysql redis
) else (
  echo [info] 停止 mysql / redis ...
  docker compose stop mysql redis
)
if errorlevel 1 exit /b 1

echo [info] 完成。数据在卷 mysql_data / redis_data 中；再次启动: scripts\dev-deps-up.bat
endlocal
exit /b 0

:usage
echo 用法:
echo   scripts\dev-deps-down.bat           停止 MySQL + Redis（保留容器与数据卷）
echo   scripts\dev-deps-down.bat -v        停止并删除 mysql/redis 容器（仍保留数据卷）
echo   scripts\dev-deps-down.bat -h        显示本说明
echo.
echo 启动依赖:   scripts\dev-deps-up.bat
echo 清空全部卷: docker compose down -v
exit /b 0

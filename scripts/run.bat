@echo off
setlocal

set "ROOT=%~dp0.."

docker compose -f "%ROOT%\docker-compose.yml" up -d
if errorlevel 1 (
    echo Falha ao iniciar os servicos Docker.
    exit /b 1
)

start "VanRoute Backend" /D "%ROOT%\backend" cmd /k "mvnw.cmd spring-boot:run"
if errorlevel 1 (
    echo Falha ao abrir o backend.
    exit /b 1
)

start "VanRoute Frontend" /D "%ROOT%\frontend" cmd /k "npm run start"
if errorlevel 1 (
    echo Falha ao abrir o frontend.
    exit /b 1
)

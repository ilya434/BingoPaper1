@echo off
chcp 65001 >nul
where java >nul 2>nul || (echo Не найден Java. Установи JDK 21 и добавь его в PATH.& pause & exit /b 1)
where mvn >nul 2>nul || (echo Не найден Maven. Установи Apache Maven и добавь его в PATH.& pause & exit /b 1)
mvn clean package
if errorlevel 1 (
  echo Сборка завершилась с ошибкой.
  pause
  exit /b 1
)
echo.
echo Готовый плагин: target\Bingo-1.0.0.jar
pause

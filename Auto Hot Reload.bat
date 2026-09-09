@echo off
echo [%time%] Detectando cambio, aplicando...
cd /d "%~dp0"
call gradlew.bat installDebug --offline
adb shell am force-stop com.deavidig.sketchprojectpro
adb shell am start -n com.deavidig.sketchprojectpro/com.deavidig.sketch.project.main.activities.MainActivity
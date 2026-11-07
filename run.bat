@echo off
cd /d "%~dp0"
if not exist out mkdir out
(for /r src\main\java %%f in (*.java) do @echo %%f) > out\sources.txt
javac --release 17 -d out @out\sources.txt
if errorlevel 1 exit /b 1
java -cp out com.codetrace.Main %*

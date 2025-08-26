@echo off
echo Compilation du framework MySpring...

REM Répertoires
set TEMP_SRC=temp_src
set MY_CLASSES=classes

REM Suppression des répertoires existants
if exist "%TEMP_SRC%" (
    echo Suppression du répertoire temporaire existant...
    rmdir /s /q "%TEMP_SRC%"
)

if exist "%MY_CLASSES%" (
    echo Suppression du répertoire classes existant...
    rmdir /s /q "%MY_CLASSES%"
)

REM Création des répertoires
mkdir "%TEMP_SRC%"
mkdir "%MY_CLASSES%"
echo Répertoires créés avec succès.

REM Copie des fichiers Java en préservant la structure des packages
echo Copie des fichiers Java...
for /r "src" %%f in (*.java) do (
    echo Copie de %%f
    set "source=%%f"
    set "dest=%TEMP_SRC%\%%~nxf"
    copy "%%f" "%TEMP_SRC%\%%~nxf"
)

REM Compilation avec la structure de packages préservée
echo Compilation en cours...
javac -cp "lib\*" -d "%MY_CLASSES%" "%TEMP_SRC%\*.java"
if %errorlevel% neq 0 (
    echo Erreur lors de la compilation!
    echo Vérifiez que tous les fichiers Java sont dans le bon répertoire.
    pause
    exit /b 1
)

REM Création du JAR
echo Création du fichier JAR...
jar cf "lib\front-controller.jar" -C "%MY_CLASSES%" .

REM Nettoyage
rmdir /s /q "%TEMP_SRC%"
echo Compilation terminée avec succès!
pause

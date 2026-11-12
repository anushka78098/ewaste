@echo off
rem Downloads the JavaFX 21 modules for Windows from Maven Central into lib-fx\ and verifies their SHA-1.
cd /d "%~dp0\.."
if not exist lib-fx mkdir lib-fx
powershell -NoProfile -Command "$ErrorActionPreference='Stop'; $v='21.0.5'; foreach ($m in 'base','graphics','controls') { $f='javafx-'+$m+'-'+$v+'-win.jar'; $u='https://repo.maven.apache.org/maven2/org/openjfx/javafx-'+$m+'/'+$v+'/'+$f; Invoke-WebRequest -UseBasicParsing $u -OutFile ('lib-fx\'+$f); $exp=(Invoke-WebRequest -UseBasicParsing ($u+'.sha1')).Content.Trim().Split(' ')[0]; $act=(Get-FileHash ('lib-fx\'+$f) -Algorithm SHA1).Hash; if ($exp -ne $act) { Remove-Item ('lib-fx\'+$f); Write-Host ('Checksum mismatch for '+$f); exit 1 } else { Write-Host ('ok  '+$f) } }"
if errorlevel 1 exit /b 1
echo JavaFX installed in lib-fx\. Next: build-fx.bat then run-client-fx.bat

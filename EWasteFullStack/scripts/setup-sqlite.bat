@echo off
rem Downloads the official SQLite JDBC driver from Maven Central into lib\ (PowerShell is built into Windows).
cd /d "%~dp0\.."
if not exist lib mkdir lib
powershell -NoProfile -Command "$ErrorActionPreference='Stop'; $v='3.36.0.3'; $b='https://repo.maven.apache.org/maven2/org/xerial/sqlite-jdbc/'+$v; $f='lib\sqlite-jdbc-'+$v+'.jar'; $u=$b+'/sqlite-jdbc-'+$v+'.jar'; $exp=(Invoke-WebRequest -UseBasicParsing ($u+'.sha1')).Content.Trim().Split(' ')[0]; if (Test-Path $f) { $act=(Get-FileHash $f -Algorithm SHA1).Hash; if ($exp -eq $act) { Write-Host 'SQLite driver already installed and checksum verified.'; exit 0 } }; Invoke-WebRequest -UseBasicParsing $u -OutFile $f; $act=(Get-FileHash $f -Algorithm SHA1).Hash; if ($exp -ne $act) { Remove-Item $f; Write-Host 'Checksum mismatch - download deleted'; exit 1 } else { Write-Host 'SQLite driver installed (checksum verified). Restart the server.' }"
if errorlevel 1 exit /b 1

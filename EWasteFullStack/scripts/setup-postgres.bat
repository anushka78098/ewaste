@echo off
rem Downloads the PostgreSQL JDBC driver into lib\ and verifies its SHA-256 checksum.
cd /d "%~dp0\.."
if not exist lib mkdir lib
powershell -NoProfile -Command "$ErrorActionPreference='Stop'; $v='42.7.14'; $expected='73914527305A40CCE504B0D3D90B23CAF565136912D607AE8AE7C5895512332C'; $f='lib\postgresql-'+$v+'.jar'; $u='https://repo.maven.apache.org/maven2/org/postgresql/postgresql/'+$v+'/postgresql-'+$v+'.jar'; if (Test-Path $f) { $actual=(Get-FileHash -LiteralPath $f -Algorithm SHA256).Hash; if ($expected -eq $actual) { Write-Host 'PostgreSQL JDBC driver already installed and checksum verified.'; exit 0 } }; Invoke-WebRequest -UseBasicParsing $u -OutFile $f; $actual=(Get-FileHash -LiteralPath $f -Algorithm SHA256).Hash; if ($expected -ne $actual) { Remove-Item -LiteralPath $f; throw 'Checksum mismatch - downloaded driver deleted' } else { Write-Host 'PostgreSQL JDBC driver installed (checksum verified). Restart the server.' }"
if errorlevel 1 exit /b 1

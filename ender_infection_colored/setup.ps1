$ErrorActionPreference = 'Stop'
Set-Location -LiteralPath $PSScriptRoot
if (Test-Path -LiteralPath 'gradle/wrapper/gradle-wrapper.jar') { Write-Host 'Wrapper already present.'; exit 0 }
$bootstrapPath = Join-Path $PSScriptRoot '.bootstrap'
New-Item -ItemType Directory -Path $bootstrapPath -Force | Out-Null
$archivePath = Join-Path $bootstrapPath 'forge-mdk.zip'
Invoke-WebRequest -Uri 'https://maven.minecraftforge.net/net/minecraftforge/forge/1.20.1-47.3.12/forge-1.20.1-47.3.12-mdk.zip' -OutFile $archivePath
$actualHash = (Get-FileHash -LiteralPath $archivePath -Algorithm SHA1).Hash.ToLowerInvariant()
if ($actualHash -ne '445df7c2cb37086102293f946463df53013fc4c7') { throw 'Official MDK checksum mismatch.' }
$extractPath = Join-Path $bootstrapPath 'mdk'
Expand-Archive -LiteralPath $archivePath -DestinationPath $extractPath -Force
foreach ($entryName in @('gradlew','gradlew.bat','gradle')) {
    Copy-Item -LiteralPath (Join-Path $extractPath $entryName) -Destination $PSScriptRoot -Recurse -Force
}
Write-Host 'Forge Gradle wrapper installed. With JDK 17 installed, run: .\gradlew.bat build'

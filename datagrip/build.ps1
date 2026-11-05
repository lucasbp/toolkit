param(
    [string]$LocalIdePath,
    [string]$JdkPath = $env:JAVA_HOME,
    [string]$ArtifactDirectory
)

$ErrorActionPreference = 'Stop'
$previousJava = $env:JAVA_HOME
Push-Location $PSScriptRoot
try {
    if ($JdkPath) { $env:JAVA_HOME = $JdkPath }
    if (-not $ArtifactDirectory) {
        $commonGit = & git rev-parse --path-format=absolute --git-common-dir
        if ($LASTEXITCODE -ne 0) { throw 'Não foi possível localizar a pasta do projeto para os artefatos.' }
        $projectRoot = Split-Path -Parent $commonGit.Trim()
        $ArtifactDirectory = Join-Path $projectRoot 'artifacts/datagrip'
    }
    $gradleArgs = @('--no-daemon', 'test', 'verifyPluginStructure', 'buildPlugin')
    if ($LocalIdePath) { $gradleArgs += "-PlocalIdePath=$LocalIdePath" }
    & .\gradlew.bat @gradleArgs
    if ($LASTEXITCODE -ne 0) { throw "Build falhou: $LASTEXITCODE" }
    New-Item -ItemType Directory -Path $ArtifactDirectory -Force | Out-Null
    $versionMatch = [regex]::Match([IO.File]::ReadAllText((Join-Path $PSScriptRoot 'build.gradle.kts')), '(?m)^version\s*=\s*"([^"]+)"')
    if (-not $versionMatch.Success) { throw 'Versão do plugin não encontrada.' }
    $pluginVersion = $versionMatch.Groups[1].Value
    Get-Item -LiteralPath "./build/distributions/server-management-$pluginVersion.zip" | ForEach-Object {
        $hash = (Get-FileHash -LiteralPath $_.FullName -Algorithm SHA256).Hash.ToLowerInvariant()
        [IO.File]::WriteAllText("$($_.FullName).sha256", "$hash  $($_.Name)`n")
        Copy-Item -LiteralPath $_.FullName, "$($_.FullName).sha256" -Destination $ArtifactDirectory -Force
        Write-Output (Join-Path $ArtifactDirectory $_.Name)
    }
}
finally {
    $env:JAVA_HOME = $previousJava
    Pop-Location
}

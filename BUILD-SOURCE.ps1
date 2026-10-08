param(
    [string]$Java = 'java',
    [string]$Libraries = "$env:USERPROFILE/.gradle/caches",
    [Parameter(Mandatory = $true)][string]$NeoForgeJar
)

$project = Split-Path -Parent $MyInvocation.MyCommand.Path
& $Java "$project/tools/release/BuildRelease.java" `
    --repo $project `
    --base "$project/release-base/base-formaworks.jar.part1" `
    --libs $Libraries `
    --neoforge $NeoForgeJar `
    --out "$project/build-release"
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

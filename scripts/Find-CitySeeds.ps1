<#
.SYNOPSIS
Find Minecraft overworld seeds with selected Cities Arise plan elements, without a game client.
.EXAMPLE
.\scripts\Find-CitySeeds.ps1 -Elements bridge -Datapack .\build\distributions\composition_fixture.zip
.EXAMPLE
.\scripts\Find-CitySeeds.ps1 -Elements bridge,road_step -StartSeed 1000 -SeedCount 100 -TimeoutSeconds 1800
#>
[CmdletBinding()]
param(
    [ValidateSet('settlement','bridge','road_step','access_step','retaining_wall','earthworks','props','modular_building')]
    [string[]]$Elements = @('bridge'),
    [long]$StartSeed = 0,
    [ValidateRange(1,100000)][int]$SeedCount = 100,
    [ValidateRange(0,64)][int]$RadiusRegions = 8,
    [ValidateRange(-29000000,29000000)][int]$CenterX = 0,
    [ValidateRange(-29000000,29000000)][int]$CenterZ = 0,
    [ValidateRange(1,256)][int]$CandidatesPerSeed = 16,
    [ValidateRange(1,1000)][int]$MaxResults = 3,
    [ValidateRange(1,7200)][int]$TimeoutSeconds = 600,
    [ValidatePattern('^[a-z0-9_.-]+:[a-z0-9_./-]+$')][Alias('Profile')][string]$SettlementProfile = 'cities_arise:suburb',
    [ValidateRange(1,1024)][int]$CandidateRegionModulo = 16,
    [string[]]$Datapack = @()
)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$jobRoot = Join-Path $projectRoot ('build/seed-search/' + [guid]::NewGuid().ToString('N'))
$null = New-Item -ItemType Directory -Path $jobRoot
$runtime = Join-Path $jobRoot 'runtime'
$defaults = Join-Path $runtime 'defaultconfigs'
$null = New-Item -ItemType Directory -Path $defaults
[IO.File]::WriteAllText((Join-Path $runtime 'server.properties'), "level-name=seed-search`n", [Text.UTF8Encoding]::new($false))
$serverConfig = "[worldgen]`nenabled=true`nsettlementProfileId=`"$SettlementProfile`"`ncandidateRegionModulo=$CandidateRegionModulo`nlocateSearchRadiusRegions=64`nlocateMaxCandidateAttempts=256`nlocateImprovementCandidateAttempts=16`n"
[IO.File]::WriteAllText((Join-Path $defaults 'cities_arise-server.toml'), $serverConfig, [Text.UTF8Encoding]::new($false))
$packCopies = @()
$manifest = @()
foreach ($inputPack in $Datapack) {
    $source = Get-Item -LiteralPath $inputPack
    $destination = Join-Path $jobRoot ('pack-' + $packCopies.Count + $(if ($source.PSIsContainer) { '' } else { '.zip' }))
    Copy-Item -LiteralPath $source.FullName -Destination $destination -Recurse
    $packCopies += $destination
    $manifest += @{ source = $source.FullName; copy = $destination; files = @(
        if ($source.PSIsContainer) {
            Get-ChildItem -LiteralPath $destination -Recurse -File | Sort-Object FullName | ForEach-Object {
                @{ path = $_.FullName.Substring($destination.Length + 1); sha256 = (Get-FileHash -LiteralPath $_.FullName -Algorithm SHA256).Hash }
            }
        } else { @{ path = $source.Name; sha256 = (Get-FileHash -LiteralPath $destination -Algorithm SHA256).Hash } }
    ) }
}
$options = @{
    startSeed = $StartSeed.ToString([Globalization.CultureInfo]::InvariantCulture)
    seedCount = $SeedCount; radiusRegions = $RadiusRegions; candidatesPerSeed = $CandidatesPerSeed
    maxResults = $MaxResults; timeoutSeconds = $TimeoutSeconds; elements = @($Elements)
    datapacks = @($packCopies); packManifest = @($manifest)
    outputDirectory = $jobRoot
    profile = $SettlementProfile; candidateRegionModulo = $CandidateRegionModulo
    centerX = $CenterX; centerZ = $CenterZ
}
$sourceManifest = @(Get-ChildItem -LiteralPath (Join-Path $projectRoot 'src/main'), (Join-Path $projectRoot 'src/gameTest') -File -Recurse | Sort-Object FullName | ForEach-Object {
    $_.FullName.Substring($projectRoot.Length) + ' ' + (Get-FileHash -LiteralPath $_.FullName -Algorithm SHA256).Hash
})
$fingerprintAlgorithm = [Security.Cryptography.SHA256]::Create()
try { $options.sourceFingerprint = [BitConverter]::ToString($fingerprintAlgorithm.ComputeHash([Text.Encoding]::UTF8.GetBytes(($sourceManifest -join "`n")))).Replace('-','') }
finally { $fingerprintAlgorithm.Dispose() }
$optionsPath = Join-Path $jobRoot 'options.json'
[IO.File]::WriteAllText($optionsPath, ($options | ConvertTo-Json -Depth 12), [Text.UTF8Encoding]::new($false))
Write-Host "Results and progress: $(Join-Path $jobRoot 'results.json')"
Write-Host "Graceful cancellation: create an empty file $(Join-Path $jobRoot 'stop.request')"
Push-Location $projectRoot
try {
    & .\gradlew.bat runSeedSearch "-PseedSearchOptions=$optionsPath" "-PseedSearchWorkDir=$(Join-Path $jobRoot 'runtime')" --console=plain
    if ($LASTEXITCODE -ne 0) { throw "Seed search exited with code $LASTEXITCODE. See $jobRoot and runtime/logs/latest.log." }
    $reportPath = Join-Path $jobRoot 'results.json'
    if (-not (Test-Path -LiteralPath $reportPath)) { throw "Search did not start. See $runtime/logs/latest.log." }
    $reportText = Get-Content -LiteralPath $reportPath -Raw
    $report = $reportText | ConvertFrom-Json
    if ($report.status -notin @('RESULT_LIMIT','EXHAUSTED','TIME_LIMIT','CANCELLED')) { throw "Search did not complete: $($report.status). See $reportPath." }
    Write-Host "Search status: $($report.status). Results: $(@($report.results).Count). Report: $reportPath"
    Write-Host "Checked candidates: $($report.checkedCandidates). Slowest candidate: $($report.maxCandidateSeconds) seconds."
    if (@($report.results).Count -eq 0) {
        Write-Host 'No match in the checked subset. TIME_LIMIT is a completed search budget, not a crash.'
        foreach ($reason in $report.rejections.PSObject.Properties) { Write-Host "  $($reason.Name): $($reason.Value)" }
        Write-Host "Last seed: $($report.currentSeed). Increase the budget or change StartSeed/search center to check other locations."
    }
    foreach ($hit in $report.results) { Write-Host "Seed $($hit.seed) at X=$($hit.x), Z=$($hit.z). $($hit.teleport)" }
} finally { Pop-Location }

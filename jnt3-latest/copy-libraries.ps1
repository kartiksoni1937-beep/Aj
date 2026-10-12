param(
    [string]$TargetDir = (Join-Path $PSScriptRoot "profiles\echoWauth\libs")
)

$classpathFile = "C:\Users\Lund\Documents\GitHub\Echo\Echo\versions\1.21.11\.gradle\loom-cache\remapClasspath.txt"

$classpath = Get-Content $classpathFile -Raw

$jars = $classpath -split ';'

Write-Host "Found $($jars.Count) JAR files in classpath"
Write-Host "Copying to: $TargetDir"

if (-not (Test-Path $TargetDir)) {
    New-Item -ItemType Directory -Path $TargetDir -Force | Out-Null
}

$copiedCount = 0
$skippedCount = 0

foreach ($jar in $jars) {
    $jar = $jar.Trim()
    if ($jar -and (Test-Path $jar)) {
        $fileName = Split-Path $jar -Leaf
        $destination = Join-Path $TargetDir $fileName
        
        if (Test-Path $destination) {
            $sourceSize = (Get-Item $jar).Length
            $destSize = (Get-Item $destination).Length
            if ($sourceSize -eq $destSize) {
                $skippedCount++
                continue
            }
        }
        
        try {
            Copy-Item $jar $destination -Force
            $copiedCount++
            Write-Host "  Copied: $fileName"
        } catch {
            Write-Host "  Failed to copy: $fileName - $_" -ForegroundColor Red
        }
    }
}

Write-Host "`nCompleted!"
Write-Host "  Copied: $copiedCount files"
Write-Host "  Skipped: $skippedCount files (already up-to-date)"
Write-Host "  Total: $($copiedCount + $skippedCount) files"

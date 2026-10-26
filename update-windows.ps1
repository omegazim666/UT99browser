param(
    [Parameter(Mandatory = $true)][int]$ProcessId,
    [Parameter(Mandatory = $true)][string]$Archive,
    [Parameter(Mandatory = $true)][string]$InstallRoot
)

$ErrorActionPreference = 'Stop'
$Parent = Split-Path -Parent $InstallRoot
$Stage = Join-Path $Parent ".ut99-update-stage-$ProcessId"
$Backup = Join-Path $Parent ".ut99-update-backup-$ProcessId"

try {
    while (Get-Process -Id $ProcessId -ErrorAction SilentlyContinue) {
        Start-Sleep -Milliseconds 250
    }
    if (Test-Path -LiteralPath $Stage) {
        Remove-Item -LiteralPath $Stage -Recurse -Force
    }
    New-Item -ItemType Directory -Path $Stage | Out-Null
    Expand-Archive -LiteralPath $Archive -DestinationPath $Stage -Force

    $Java = Join-Path $Stage 'runtime\bin\javaw.exe'
    $Classes = Join-Path $Stage 'app\ut99\ServerBrowserApp.class'
    $Launcher = Join-Path $Stage 'UT99 Browser.bat'
    if (!(Test-Path -LiteralPath $Java) -or !(Test-Path -LiteralPath $Classes) -or
        !(Test-Path -LiteralPath $Launcher)) {
        throw 'The downloaded archive is not a valid UT99 Browser package.'
    }

    Move-Item -LiteralPath $InstallRoot -Destination $Backup
    try {
        Move-Item -LiteralPath $Stage -Destination $InstallRoot
    } catch {
        Move-Item -LiteralPath $Backup -Destination $InstallRoot
        throw
    }

    $Java = Join-Path $InstallRoot 'runtime\bin\javaw.exe'
    Start-Process -FilePath $Java `
        -ArgumentList ('-cp "{0}\app" ut99.ServerBrowserApp' -f $InstallRoot) `
        -WorkingDirectory $InstallRoot
    Remove-Item -LiteralPath $Backup -Recurse -Force
    Remove-Item -LiteralPath $Archive -Force
} catch {
    if (!(Test-Path -LiteralPath $InstallRoot) -and (Test-Path -LiteralPath $Backup)) {
        Move-Item -LiteralPath $Backup -Destination $InstallRoot
    }
    if (Test-Path -LiteralPath $Stage) {
        Remove-Item -LiteralPath $Stage -Recurse -Force
    }
    Add-Type -AssemblyName System.Windows.Forms
    [System.Windows.Forms.MessageBox]::Show(
        "The update could not be installed. Your existing application should still be available.`r`n`r`n$($_.Exception.Message)",
        'UT99 Browser update failed',
        [System.Windows.Forms.MessageBoxButtons]::OK,
        [System.Windows.Forms.MessageBoxIcon]::Error
    ) | Out-Null
}

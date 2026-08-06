[CmdletBinding()]
param(
    [ValidateSet('dev', 'stage', 'prod')]
    [string]$Environment = 'dev',

    [string]$ConfigRepository
)

if ([string]::IsNullOrWhiteSpace($ConfigRepository)) {
    $ConfigRepository = Join-Path $PSScriptRoot '..\..\ecommerce-config-repo'
}

$environmentFile = Join-Path (Join-Path $ConfigRepository $Environment) '.env'

# Support the previous flat-file layout during a repository migration.
if (-not (Test-Path -LiteralPath $environmentFile -PathType Leaf)) {
    $environmentFile = Join-Path $ConfigRepository ".env.$Environment"
}

if (-not (Test-Path -LiteralPath $environmentFile -PathType Leaf)) {
    throw "Environment file not found: $environmentFile"
}

$loaded = 0
Get-Content -LiteralPath $environmentFile | ForEach-Object {
    $line = $_.Trim()

    if ([string]::IsNullOrWhiteSpace($line) -or $line.StartsWith('#')) {
        return
    }

    $separator = $line.IndexOf('=')
    if ($separator -lt 1) {
        throw "Invalid environment entry in ${environmentFile}: $line"
    }

    $name = $line.Substring(0, $separator).Trim()
    $value = $line.Substring($separator + 1)

    if ($name -notmatch '^[A-Za-z_][A-Za-z0-9_]*$') {
        throw "Invalid environment variable name in ${environmentFile}: $name"
    }

    [Environment]::SetEnvironmentVariable($name, $value, 'Process')
    $loaded++
}

Write-Host "Loaded $loaded variables from $environmentFile into this PowerShell session."

param (
    [Parameter(Mandatory = $true)][string]$arch
)

$files = $(Get-ChildItem -Path "out\installer\build\anki\windows\visualstudio\$arch\Release" -Recurse -Include *.exe, *.dll, *.pyd)
if (-not $files) {
    Write-Error "No binaries found for $arch"
}

# Files already signed by their vendor (Qt, Python, Microsoft) are left alone.
$unsigned = $files | Where-Object { (Get-AuthenticodeSignature $_.FullName).Status -eq "NotSigned" }
if (-not $unsigned) {
    Write-Error "No unsigned binaries found for $arch"
}

Write-Host "Found $($unsigned.Count) unsigned of $($files.Count) binaries:"
$unsigned | ForEach-Object { Write-Host "  $($_.FullName)" }

"files=$(($unsigned | ForEach-Object FullName) -join ',')" | Out-File -FilePath $env:GITHUB_OUTPUT -Append

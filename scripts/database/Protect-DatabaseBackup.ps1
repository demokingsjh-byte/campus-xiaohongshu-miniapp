param(
    [Parameter(Mandatory = $true)]
    [string] $InputPath,

    [Parameter(Mandatory = $true)]
    [string] $OutputPath,

    [Parameter(Mandatory = $true)]
    [string] $KeyPath
)

$ErrorActionPreference = 'Stop'

$inputFile = [System.IO.Path]::GetFullPath($InputPath)
$outputFile = [System.IO.Path]::GetFullPath($OutputPath)
$keyFile = [System.IO.Path]::GetFullPath($KeyPath)

if (-not [System.IO.File]::Exists($inputFile)) {
    throw "Backup input does not exist: $inputFile"
}

[System.IO.Directory]::CreateDirectory([System.IO.Path]::GetDirectoryName($outputFile)) | Out-Null
[System.IO.Directory]::CreateDirectory([System.IO.Path]::GetDirectoryName($keyFile)) | Out-Null

if ([System.IO.File]::Exists($keyFile)) {
    [byte[]] $keyMaterial = [Convert]::FromBase64String([System.IO.File]::ReadAllText($keyFile).Trim())
} else {
    [byte[]] $keyMaterial = New-Object byte[] 64
    $random = [System.Security.Cryptography.RandomNumberGenerator]::Create()
    try {
        $random.GetBytes($keyMaterial)
    } finally {
        $random.Dispose()
    }
    [System.IO.File]::WriteAllText($keyFile, [Convert]::ToBase64String($keyMaterial))
    if ($env:OS -eq 'Windows_NT') {
        $identity = [System.Security.Principal.WindowsIdentity]::GetCurrent().Name
        & icacls.exe $keyFile /inheritance:r /grant:r "${identity}:(R,W)" | Out-Null
        if ($LASTEXITCODE -ne 0) {
            throw "Unable to restrict backup-key permissions: $keyFile"
        }
    }
}

if ($keyMaterial.Length -ne 64) {
    throw 'Backup key must decode to exactly 64 bytes.'
}

[byte[]] $encryptionKey = $keyMaterial[0..31]
[byte[]] $authenticationKey = $keyMaterial[32..63]
[byte[]] $plainBytes = [System.IO.File]::ReadAllBytes($inputFile)
[byte[]] $magic = [System.Text.Encoding]::ASCII.GetBytes('CDBAK001')

$aes = [System.Security.Cryptography.Aes]::Create()
try {
    $aes.KeySize = 256
    $aes.BlockSize = 128
    $aes.Mode = [System.Security.Cryptography.CipherMode]::CBC
    $aes.Padding = [System.Security.Cryptography.PaddingMode]::PKCS7
    $aes.Key = $encryptionKey
    $aes.GenerateIV()
    [byte[]] $iv = $aes.IV
    $encryptor = $aes.CreateEncryptor()
    try {
        [byte[]] $cipherBytes = $encryptor.TransformFinalBlock($plainBytes, 0, $plainBytes.Length)
    } finally {
        $encryptor.Dispose()
    }
} finally {
    $aes.Dispose()
}

[byte[]] $authenticatedBytes = New-Object byte[] ($magic.Length + $iv.Length + $cipherBytes.Length)
[Array]::Copy($magic, 0, $authenticatedBytes, 0, $magic.Length)
[Array]::Copy($iv, 0, $authenticatedBytes, $magic.Length, $iv.Length)
[Array]::Copy($cipherBytes, 0, $authenticatedBytes, $magic.Length + $iv.Length, $cipherBytes.Length)

$hmac = New-Object System.Security.Cryptography.HMACSHA256 (,$authenticationKey)
try {
    [byte[]] $tag = $hmac.ComputeHash($authenticatedBytes)
} finally {
    $hmac.Dispose()
}

[byte[]] $outputBytes = New-Object byte[] ($authenticatedBytes.Length + $tag.Length)
[Array]::Copy($authenticatedBytes, 0, $outputBytes, 0, $authenticatedBytes.Length)
[Array]::Copy($tag, 0, $outputBytes, $authenticatedBytes.Length, $tag.Length)
[System.IO.File]::WriteAllBytes($outputFile, $outputBytes)

Write-Output "Encrypted backup: $outputFile"
Write-Output "Recovery key: $keyFile"

param(
    [Parameter(Mandatory = $true)]
    [string] $InputPath,

    [Parameter(Mandatory = $true)]
    [string] $OutputPath,

    [Parameter(Mandatory = $true)]
    [string] $KeyPath
)

$ErrorActionPreference = 'Stop'

[byte[]] $inputBytes = [System.IO.File]::ReadAllBytes([System.IO.Path]::GetFullPath($InputPath))
[byte[]] $keyMaterial = [Convert]::FromBase64String(
    [System.IO.File]::ReadAllText([System.IO.Path]::GetFullPath($KeyPath)).Trim()
)
if ($keyMaterial.Length -ne 64 -or $inputBytes.Length -lt 72) {
    throw 'Invalid encrypted backup or recovery key.'
}

[byte[]] $expectedMagic = [System.Text.Encoding]::ASCII.GetBytes('CDBAK001')
for ($index = 0; $index -lt $expectedMagic.Length; $index++) {
    if ($inputBytes[$index] -ne $expectedMagic[$index]) {
        throw 'Unsupported encrypted backup format.'
    }
}

$authenticatedLength = $inputBytes.Length - 32
[byte[]] $authenticatedBytes = New-Object byte[] $authenticatedLength
[byte[]] $storedTag = New-Object byte[] 32
[Array]::Copy($inputBytes, 0, $authenticatedBytes, 0, $authenticatedLength)
[Array]::Copy($inputBytes, $authenticatedLength, $storedTag, 0, 32)

[byte[]] $authenticationKey = $keyMaterial[32..63]
$hmac = New-Object System.Security.Cryptography.HMACSHA256 (,$authenticationKey)
try {
    [byte[]] $calculatedTag = $hmac.ComputeHash($authenticatedBytes)
} finally {
    $hmac.Dispose()
}

$difference = 0
for ($index = 0; $index -lt 32; $index++) {
    $difference = $difference -bor ($storedTag[$index] -bxor $calculatedTag[$index])
}
if ($difference -ne 0) {
    throw 'Backup integrity check failed. The file or key is incorrect.'
}

[byte[]] $iv = New-Object byte[] 16
[Array]::Copy($authenticatedBytes, 8, $iv, 0, 16)
$cipherLength = $authenticatedLength - 24
[byte[]] $cipherBytes = New-Object byte[] $cipherLength
[Array]::Copy($authenticatedBytes, 24, $cipherBytes, 0, $cipherLength)

$aes = [System.Security.Cryptography.Aes]::Create()
try {
    $aes.KeySize = 256
    $aes.BlockSize = 128
    $aes.Mode = [System.Security.Cryptography.CipherMode]::CBC
    $aes.Padding = [System.Security.Cryptography.PaddingMode]::PKCS7
    $aes.Key = [byte[]] $keyMaterial[0..31]
    $aes.IV = $iv
    $decryptor = $aes.CreateDecryptor()
    try {
        [byte[]] $plainBytes = $decryptor.TransformFinalBlock($cipherBytes, 0, $cipherBytes.Length)
    } finally {
        $decryptor.Dispose()
    }
} finally {
    $aes.Dispose()
}

$outputFile = [System.IO.Path]::GetFullPath($OutputPath)
[System.IO.Directory]::CreateDirectory([System.IO.Path]::GetDirectoryName($outputFile)) | Out-Null
[System.IO.File]::WriteAllBytes($outputFile, $plainBytes)
Write-Output "Restored compressed SQL backup: $outputFile"

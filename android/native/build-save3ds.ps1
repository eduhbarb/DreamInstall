# Builds the bundled save3ds binary (no FUSE) for Android.
#
# Requirements:
#   - Rust (rustup) with the aarch64-linux-android target
#   - Android NDK 27 or newer, path in ANDROID_NDK_HOME or passed as -Ndk
#   - a save3ds checkout at the commit in save3ds-commit.txt
#
# Usage:
#   ./build-save3ds.ps1 -Source "C:\src\save3ds" -Ndk "C:\Android\Sdk\ndk\28.2.13676358"

param(
    [Parameter(Mandatory = $true)][string]$Source,
    [string]$Ndk = $env:ANDROID_NDK_HOME,
    [string]$Output = "$PSScriptRoot\..\app\src\main\jniLibs\arm64-v8a"
)

$ErrorActionPreference = "Stop"
if (-not (Test-Path $Ndk)) { throw "NDK not found: $Ndk" }

$toolchain = Join-Path $Ndk "toolchains\llvm\prebuilt\windows-x86_64\bin"
$linker = Join-Path $toolchain "aarch64-linux-android30-clang.cmd"
if (-not (Test-Path $linker)) { throw "linker not found: $linker" }

$env:CARGO_TARGET_AARCH64_LINUX_ANDROID_LINKER = $linker
$env:RUSTFLAGS = "-C target-feature=+aes"

Push-Location (Join-Path $Source "save3ds_fuse")
try {
    cargo build --release --no-default-features --target aarch64-linux-android
} finally {
    Pop-Location
}

New-Item -ItemType Directory -Force $Output | Out-Null
Copy-Item (Join-Path $Source "target\aarch64-linux-android\release\save3ds_fuse") `
    (Join-Path $Output "libsave3ds.so") -Force
Write-Host "built $(Join-Path $Output 'libsave3ds.so')"

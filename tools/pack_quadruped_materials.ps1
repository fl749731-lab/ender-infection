# Deterministic conversion of the built-in imagegen master into the existing UV atlas.
param([string]$Pack = (Join-Path $PSScriptRoot '../colored_units/creature_rework_v2'))
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing
$materialFolder = Join-Path $Pack 'materials'
$masterImage = [System.Drawing.Bitmap]::FromFile((Join-Path $materialFolder 'generated-master-v2.png'))
$originalImage = [System.Drawing.Bitmap]::FromFile((Join-Path $materialFolder 'quadruped-atlas-v1.png'))
$packedImage = [System.Drawing.Bitmap]::new(128,128,[System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
try {
    $packingGraphics = [System.Drawing.Graphics]::FromImage($packedImage)
    try {
        $packingGraphics.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::NearestNeighbor
        $packingGraphics.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::Half
        $packingGraphics.DrawImage($masterImage,[System.Drawing.Rectangle]::new(0,0,128,128),0,0,$masterImage.Width,$masterImage.Height,[System.Drawing.GraphicsUnit]::Pixel)
    } finally { $packingGraphics.Dispose() }
    # Only six semantic swatches change. Eye/nose/nostril pixels and unused alpha are exact originals.
    for ($materialY=0; $materialY -lt 128; $materialY++) {
        for ($materialX=0; $materialX -lt 128; $materialX++) {
            if (($materialY -ge 32 -and $materialX -ge 64) -or $materialY -ge 64) {
                $packedImage.SetPixel($materialX,$materialY,$originalImage.GetPixel($materialX,$materialY))
            }
        }
    }
    $packedImage.Save((Join-Path $materialFolder 'quadruped-atlas-v2.png'),[System.Drawing.Imaging.ImageFormat]::Png)
} finally { $originalImage.Dispose(); $masterImage.Dispose(); $packedImage.Dispose() }

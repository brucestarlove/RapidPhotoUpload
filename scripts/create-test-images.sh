#!/bin/bash

# Create test images for Phase 2 testing
# This script creates sample JPEG and PNG images using ImageMagick or sips (macOS)

OUTPUT_DIR="./test-images"
mkdir -p "$OUTPUT_DIR"

echo "Creating test images in $OUTPUT_DIR..."

# Check if ImageMagick is available
if command -v convert &> /dev/null; then
    echo "Using ImageMagick..."
    
    # Create a small JPEG (1MB)
    convert -size 1024x768 xc:skyblue \
            -fill blue -draw "circle 512,384 512,200" \
            -pointsize 72 -fill white -gravity center -annotate +0+0 "Test 1MB" \
            "$OUTPUT_DIR/photo1.jpg"
    
    # Create a medium PNG (2MB)
    convert -size 1920x1080 xc:lightgreen \
            -fill green -draw "rectangle 100,100 1820,980" \
            -pointsize 96 -fill black -gravity center -annotate +0+0 "Test 2MB" \
            "$OUTPUT_DIR/photo2.png"
    
    # Create a large JPEG (10MB) for multipart testing
    convert -size 4000x3000 xc:lightcoral \
            -fill red -draw "circle 2000,1500 2000,500" \
            -pointsize 144 -fill white -gravity center -annotate +0+0 "Large 10MB" \
            "$OUTPUT_DIR/large-photo.jpg"
    
elif command -v sips &> /dev/null; then
    echo "Using macOS sips..."
    
    # Create a small JPEG (1MB) - sips doesn't support text, so we'll create a gradient
    sips --setProperty format jpeg \
         --setProperty formatOptions 80 \
         -z 1024 768 \
         --newColor sRGB \
         --pixelHeight 1024 --pixelWidth 768 \
         "$OUTPUT_DIR/photo1.jpg" 2>/dev/null || \
    python3 -c "
from PIL import Image, ImageDraw, ImageFont
img = Image.new('RGB', (1024, 768), color='skyblue')
draw = ImageDraw.Draw(img)
draw.ellipse([256, 128, 768, 640], fill='blue')
try:
    font = ImageFont.truetype('/System/Library/Fonts/Helvetica.ttc', 72)
except:
    font = ImageFont.load_default()
draw.text((512, 384), 'Test 1MB', fill='white', font=font, anchor='mm')
img.save('$OUTPUT_DIR/photo1.jpg', 'JPEG', quality=85)
" 2>/dev/null || echo "Warning: Could not create photo1.jpg"
    
    # Create a medium PNG (2MB)
    python3 -c "
from PIL import Image, ImageDraw, ImageFont
img = Image.new('RGB', (1920, 1080), color='lightgreen')
draw = ImageDraw.Draw(img)
draw.rectangle([100, 100, 1820, 980], fill='green', outline='black', width=5)
try:
    font = ImageFont.truetype('/System/Library/Fonts/Helvetica.ttc', 96)
except:
    font = ImageFont.load_default()
draw.text((960, 540), 'Test 2MB', fill='black', font=font, anchor='mm')
img.save('$OUTPUT_DIR/photo2.png', 'PNG')
" 2>/dev/null || echo "Warning: Could not create photo2.png"
    
    # Create a large JPEG (10MB) for multipart testing
    python3 -c "
from PIL import Image, ImageDraw, ImageFont
img = Image.new('RGB', (4000, 3000), color='lightcoral')
draw = ImageDraw.Draw(img)
draw.ellipse([1000, 500, 3000, 2500], fill='red', outline='darkred', width=10)
try:
    font = ImageFont.truetype('/System/Library/Fonts/Helvetica.ttc', 144)
except:
    font = ImageFont.load_default()
draw.text((2000, 1500), 'Large 10MB', fill='white', font=font, anchor='mm')
img.save('$OUTPUT_DIR/large-photo.jpg', 'JPEG', quality=90)
" 2>/dev/null || echo "Warning: Could not create large-photo.jpg"
    
else
    echo "Neither ImageMagick nor sips found. Trying Python PIL..."
    
    # Try Python PIL
    python3 -c "
from PIL import Image, ImageDraw, ImageFont
import os

os.makedirs('$OUTPUT_DIR', exist_ok=True)

# Small JPEG (1MB)
img = Image.new('RGB', (1024, 768), color='skyblue')
draw = ImageDraw.Draw(img)
draw.ellipse([256, 128, 768, 640], fill='blue')
try:
    font = ImageFont.truetype('/System/Library/Fonts/Helvetica.ttc', 72)
except:
    try:
        font = ImageFont.truetype('Arial.ttf', 72)
    except:
        font = ImageFont.load_default()
draw.text((512, 384), 'Test 1MB', fill='white', font=font, anchor='mm')
img.save('$OUTPUT_DIR/photo1.jpg', 'JPEG', quality=85)
print('Created photo1.jpg')

# Medium PNG (2MB)
img = Image.new('RGB', (1920, 1080), color='lightgreen')
draw = ImageDraw.Draw(img)
draw.rectangle([100, 100, 1820, 980], fill='green', outline='black', width=5)
try:
    font = ImageFont.truetype('/System/Library/Fonts/Helvetica.ttc', 96)
except:
    try:
        font = ImageFont.truetype('Arial.ttf', 96)
    except:
        font = ImageFont.load_default()
draw.text((960, 540), 'Test 2MB', fill='black', font=font, anchor='mm')
img.save('$OUTPUT_DIR/photo2.png', 'PNG')
print('Created photo2.png')

# Large JPEG (10MB) for multipart
img = Image.new('RGB', (4000, 3000), color='lightcoral')
draw = ImageDraw.Draw(img)
draw.ellipse([1000, 500, 3000, 2500], fill='red', outline='darkred', width=10)
try:
    font = ImageFont.truetype('/System/Library/Fonts/Helvetica.ttc', 144)
except:
    try:
        font = ImageFont.truetype('Arial.ttf', 144)
    except:
        font = ImageFont.load_default()
draw.text((2000, 1500), 'Large 10MB', fill='white', font=font, anchor='mm')
img.save('$OUTPUT_DIR/large-photo.jpg', 'JPEG', quality=90)
print('Created large-photo.jpg')
" 2>/dev/null || {
    echo "Error: Could not create test images. Please install one of:"
    echo "  - ImageMagick: brew install imagemagick"
    echo "  - Python PIL: pip3 install Pillow"
    echo ""
    echo "Or manually create test images:"
    echo "  - photo1.jpg (~1MB)"
    echo "  - photo2.png (~2MB)"
    echo "  - large-photo.jpg (~10MB)"
    exit 1
}
fi

# Verify files were created
if [ -f "$OUTPUT_DIR/photo1.jpg" ]; then
    SIZE1=$(stat -f%z "$OUTPUT_DIR/photo1.jpg" 2>/dev/null || stat -c%s "$OUTPUT_DIR/photo1.jpg" 2>/dev/null)
    echo "✓ Created photo1.jpg ($(numfmt --to=iec-i --suffix=B $SIZE1 2>/dev/null || echo "${SIZE1} bytes"))"
fi

if [ -f "$OUTPUT_DIR/photo2.png" ]; then
    SIZE2=$(stat -f%z "$OUTPUT_DIR/photo2.png" 2>/dev/null || stat -c%s "$OUTPUT_DIR/photo2.png" 2>/dev/null)
    echo "✓ Created photo2.png ($(numfmt --to=iec-i --suffix=B $SIZE2 2>/dev/null || echo "${SIZE2} bytes"))"
fi

if [ -f "$OUTPUT_DIR/large-photo.jpg" ]; then
    SIZE3=$(stat -f%z "$OUTPUT_DIR/large-photo.jpg" 2>/dev/null || stat -c%s "$OUTPUT_DIR/large-photo.jpg" 2>/dev/null)
    echo "✓ Created large-photo.jpg ($(numfmt --to=iec-i --suffix=B $SIZE3 2>/dev/null || echo "${SIZE3} bytes"))"
fi

echo ""
echo "Test images created in $OUTPUT_DIR/"
echo "You can now use these with test-phase2.sh"


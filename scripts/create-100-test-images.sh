#!/bin/bash

# Create 100 test images similar to photo1.jpg
# This script creates sample JPEG images using ImageMagick

OUTPUT_DIR="./test-images"
mkdir -p "$OUTPUT_DIR"

echo "Creating 100 test images in $OUTPUT_DIR..."

# Check if ImageMagick is available (prefer magick command for IMv7, fallback to convert)
if command -v magick &> /dev/null; then
    CONVERT_CMD="magick"
elif command -v convert &> /dev/null; then
    CONVERT_CMD="convert"
fi

if [ -n "$CONVERT_CMD" ]; then
    echo "Using ImageMagick..."
    
    # Create 100 JPEG images similar to photo1.jpg
    for i in $(seq 1 100); do
        # Format number with zero padding (photo001.jpg, photo002.jpg, etc.)
        filename=$(printf "photo%03d.jpg" $i)
        
        # Create image similar to photo1.jpg: 1024x768, skyblue background, blue circle, white text
        $CONVERT_CMD -size 1024x768 xc:skyblue \
                -fill blue -draw "circle 512,384 512,200" \
                -pointsize 72 -fill white -gravity center -annotate +0+0 "Test $i" \
                "$OUTPUT_DIR/$filename"
        
        # Show progress every 10 images
        if [ $((i % 10)) -eq 0 ]; then
            echo "Created $i/100 images..."
        fi
    done
    
    echo ""
    echo "✓ Created 100 test images in $OUTPUT_DIR/"
    
    # Show file count and total size
    FILE_COUNT=$(ls -1 "$OUTPUT_DIR"/photo*.jpg 2>/dev/null | wc -l | tr -d ' ')
    if [ "$FILE_COUNT" -gt 0 ]; then
        TOTAL_SIZE=$(du -sh "$OUTPUT_DIR" 2>/dev/null | cut -f1)
        echo "Total files: $FILE_COUNT"
        echo "Total size: $TOTAL_SIZE"
        
        # Show first and last file sizes
        FIRST_SIZE=$(stat -f%z "$OUTPUT_DIR/photo001.jpg" 2>/dev/null || stat -c%s "$OUTPUT_DIR/photo001.jpg" 2>/dev/null)
        LAST_SIZE=$(stat -f%z "$OUTPUT_DIR/photo100.jpg" 2>/dev/null || stat -c%s "$OUTPUT_DIR/photo100.jpg" 2>/dev/null)
        echo "Sample: photo001.jpg ($(numfmt --to=iec-i --suffix=B $FIRST_SIZE 2>/dev/null || echo "${FIRST_SIZE} bytes"))"
        echo "Sample: photo100.jpg ($(numfmt --to=iec-i --suffix=B $LAST_SIZE 2>/dev/null || echo "${LAST_SIZE} bytes"))"
    fi
    
elif command -v python3 &> /dev/null; then
    echo "ImageMagick not found. Using Python PIL..."
    
    python3 << 'PYTHON_SCRIPT'
from PIL import Image, ImageDraw, ImageFont
import os

OUTPUT_DIR = "./test-images"
os.makedirs(OUTPUT_DIR, exist_ok=True)

# Try to load a font, fallback to default if not available
try:
    font = ImageFont.truetype('/System/Library/Fonts/Helvetica.ttc', 72)
except:
    try:
        font = ImageFont.truetype('Arial.ttf', 72)
    except:
        font = ImageFont.load_default()

print("Creating 100 test images...")

for i in range(1, 101):
    # Create image similar to photo1.jpg: 1024x768, skyblue background, blue circle, white text
    img = Image.new('RGB', (1024, 768), color='skyblue')
    draw = ImageDraw.Draw(img)
    
    # Draw blue circle
    draw.ellipse([256, 128, 768, 640], fill='blue')
    
    # Draw text with image number
    text = f"Test {i}"
    draw.text((512, 384), text, fill='white', font=font, anchor='mm')
    
    # Save as JPEG
    filename = f"{OUTPUT_DIR}/photo{i:03d}.jpg"
    img.save(filename, 'JPEG', quality=85)
    
    # Show progress every 10 images
    if i % 10 == 0:
        print(f"Created {i}/100 images...")

print(f"\n✓ Created 100 test images in {OUTPUT_DIR}/")
PYTHON_SCRIPT
    
    if [ $? -eq 0 ]; then
        FILE_COUNT=$(ls -1 "$OUTPUT_DIR"/photo*.jpg 2>/dev/null | wc -l | tr -d ' ')
        if [ "$FILE_COUNT" -gt 0 ]; then
            TOTAL_SIZE=$(du -sh "$OUTPUT_DIR" 2>/dev/null | cut -f1)
            echo "Total files: $FILE_COUNT"
            echo "Total size: $TOTAL_SIZE"
        fi
    else
        echo "Error: Could not create test images. Please install one of:"
        echo "  - ImageMagick: brew install imagemagick"
        echo "  - Python PIL: pip3 install Pillow"
        exit 1
    fi
    
else
    echo "Error: Neither ImageMagick nor Python3 found. Please install one of:"
    echo "  - ImageMagick: brew install imagemagick"
    echo "  - Python PIL: pip3 install Pillow"
    exit 1
fi

echo ""
echo "Test images created successfully!"


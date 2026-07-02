#!/bin/bash

# The purpose of this script is to take a directory of prepared[1] svg files
#  and some configuration data (names of themes, color codes, image geometries)
#  and create a directory tree of generated png files.  
# This script requires a version of inkscape that runs in command line mode.
#  A mac user has reported difficulty using the homebrew version of inkscape for this.
# The generated files will have the same file basenames as the original svg.
#  the color code used to create them will be encoded in the file name, easily 
#  removed or changed with 'rename'
#
#[1] 'Prepared' svg files have a recognizable string within them
# as a placeholder for the color hex code and opacity float

# After reviewing the generated images and insuring they are as desired, they can be moved to the
#  target directories using something like this:
#   for dpi in mdpi hdpi xhdpi xxhdpi
#      do cp themename/$dpi/* /path/to/AnkiDroid/src/main/res/drawable-$dpi/
#   done


# For now, using:  333333 Enabled: 60% opacity Disabled: 30% opacity 
#    and #FFFFFF Enabled: 80% opacity Disabled: 30% opacity
declare themeName=('holo-light' 'holo-dark')
declare themeColor=('333333' 'FFFFFF')
declare themeOpacity=('0.6' '0.8')
declare themeDisabledOpacity=('0.3' '0.3')

# Icon sizes and corresponding directory names
declare iconSizeValue=('32' '48' '64' '96')
declare iconSizeDirectory=('mdpi' 'hdpi' 'xhdpi' 'xxhdpi')

# There are a few icons which require special handling for the creation of 'disabled' versions 
#  of them.  Fortunately, they were named as ic_X_disabled.png (where ic_X.png is the 
#  original), making this easy to automate for now.  This will need to be adapted when we change
#  icon names
#declare disabledIconList=('ic_action_whiteboard_enable_light' 'ic_menu_revert')
disabledIcon1='ic_action_whiteboard_enable_light'
disabledIcon2='ic_menu_revert'

# This is the string which will be found in the prepared svg files, and which will be replaced by this script
colorPlaceHolder="COLOR_PLACE_HOLDER"
opacityPlaceHolder="OPACITY_PLACE_HOLDER"

# For systems which have a non-standard way of launching inkscape, modify this line
# This may not work with the homebrew version of inkscape
inkscapeCommand="inkscape"  

# The source directory for the prepared files.  Svg files must have placeholders for color and opacity
svgDir=./svg-files

# A temporary directory to store intermediate svg files.  It can be useful to keep these when diagnosing problems
tmpDir=tmp-svg-files-delete-me

# array for svg filenames
fileBasenames=()

# for each file in svgDir, add to new array
for file in "$svgDir"/*; do
 if [[ $file == *.svg ]]; then
   # get file name
   filename="${file##*/}"

   # chop off '.svg' file format
   filenameWithoutExtension="${filename%.*}"

   # add file basename to array
   fileBasenames+=($filenameWithoutExtension)
 fi
done

# This assumes that the source file exists, and the output directories exist
function generateSvgFile {
		sed "s/$colorPlaceHolder/${themeColor[$themeIndex]}/" < $svgDir/$basename.svg  \
			| sed "s/$opacityPlaceHolder/${themeOpacity[$themeIndex]}/"  \
			> $tmpDir/${themeName[$themeIndex]}-svg/$basename.svg
}

# Similar to generateSvgFile, but creates the svg file for the 'disabled' (greyed out) version
function generateDisabledSvgFile {
		sed "s/$colorPlaceHolder/${themeColor[$themeIndex]}/" < $svgDir/$basename.svg  \
			| sed "s/$opacityPlaceHolder/${themeDisabledOpacity[$themeIndex]}/"  \
			> $tmpDir/${themeName[$themeIndex]}-svg/${basename}_disabled.svg
}

# Check: 
# for i in "${!fileBasenames[@]}"; do echo "${fileBasenames[$i]}"; done;

for themeIndex in 0 1; do
	echo "Preparing icons for theme: ${themeName[$themeIndex]}"
	# For each color code, create a (temporary) directory where we will place svg files which have the correct color and transparency values
	mkdir -p "$tmpDir/${themeName[$themeIndex]}-svg"
	for basename in "${fileBasenames[@]}"; do 
	#for basename in $fileBasenames; do 
		echo "Icon: $basename"
		#  Generate temporary svg files
		generateSvgFile
		for iconSizeIndex in 0 1 2 3; do
			# For each file size, create ouput directories (ldpi, hdpi, xhdpi, xxhdpi)
			mkdir -p  ${themeName[$themeIndex]}-png/${iconSizeDirectory[$iconSizeIndex]}
			size=${iconSizeValue[$iconSizeIndex]}
			# Use inkscape to create the pngs in the desired target directory
			$inkscapeCommand -zD -w $size -h $size -y 0 -f $tmpDir/${themeName[$themeIndex]}-svg/$basename.svg  \
				-e ${themeName[$themeIndex]}-png/${iconSizeDirectory[$iconSizeIndex]}/$basename.png
			# Handle the special cases of disabled icons
			if [ "$basename" = "${disabledIcon1}" ] || [ "$basename" = "${disabledIcon2}" ];  then
				echo "Disabled icon: $basename"
				generateDisabledSvgFile
				$inkscapeCommand -zD -w $size -h $size -y 0 -f $tmpDir/${themeName[$themeIndex]}-svg/${basename}_disabled.svg  \
					-e ${themeName[$themeIndex]}-png/${iconSizeDirectory[$iconSizeIndex]}/${basename}_disabled.png

			fi
		done
	done
done

# Some might prefer to uncomment this line to remove the temporary directory and all of its children. I prefer deleting manually
# rm -fr $tmpDir
echo "You may remove the temporary directory:  $tmpDir"

echo "To move these files into the project, you might use something like:"
echo "for dpi in mdpi hdpi xhdpi xxhdpi; do cp ${themeName[$themeIndex]}-png/\$dpi/* /path/to/AnkiDroid/src/main/res/drawable-\$dpi/; done;"

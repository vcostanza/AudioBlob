#!/bin/sh

# Builds the SBSMS JNI and optionally SBSMS itself

shared_dir="shared"
sbsms_zip="2.3.0.zip"
sbsms_release="https://github.com/claytonotey/libsbsms/archive/refs/tags/$sbsms_zip"
sbsms_shared="$shared_dir/libsbsms.so"
sbsms_dir="libsbsms-2.3.0"
sbsms_build_output="$sbsms_dir/src/.libs/libsbsms.so.0.0.0"
sbsms_jni="libsbsmsjni.so"

jdk_finder="update-java-alternatives -l"

# Check if SBSMS shared library exists
if [ ! -f "$sbsms_shared" ]; then

	# Download SBSMS source
	if [ ! -d "$sbsms_dir" ]; then
		wget "$sbsms_release"
		unzip "$sbsms_zip"
	fi

	# Build SBSMS shared library
	cd $sbsms_dir
	make clean
	./configure --enable-shared=yes --enable-static=no
	make
	cd ..

	# Build failed if the file doesn't exist
	if [ ! -f "$sbsms_build_output" ]; then
		echo "Failed to build SBSMS shared library"
		exit 1	
	fi

	# Move the shared library to our jni/shared directory
	if [ ! -d "$shared_dir" ]; then
		mkdir "$shared_dir"
	fi
	mv "$sbsms_build_output" "$sbsms_shared"

	# Cleanup
	rm -rf "$sbsms_dir"
	rm "$sbsms_zip"
fi

# Find JDK 8
if [ -z "${JDK_HOME}" ]; then
	command -v $jdk_finder >/dev/null && continue || { echo "$i command not found. Please set JDK_HOME to a JDK 8 directory."; exit 1; }
	for line in $($jdk_finder); do
		echo "$line"
	done
fi

# Build the JNI
g++ -shared -fpic -g -std=c++14 -I$JDK_HOME/include -I$JDK_HOME/include/linux -L$shared_dir jsbsms.cpp -o $sbsms_jni -lsbsms

if [ -f "$sbsms_jni" ]; then
	echo "Built SBSMS JNI successfully!"
else
	echo "Failed to build SBSMS JNI"
fi


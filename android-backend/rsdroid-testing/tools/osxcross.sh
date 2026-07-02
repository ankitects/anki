git clone https://github.com/tpoechtrager/osxcross
cd osxcross
wget -nc -nv https://s3.dockerproject.org/darwin/v2/MacOSX10.10.sdk.tar.xz
mv MacOSX10.10.sdk.tar.xz tarballs/
UNATTENDED=yes OSX_VERSION_MIN=10.7 ./build.sh
# https://wapl.es/rust/2019/02/17/rust-cross-compile-linux-to-macos.html
# also needs dependencies - see tools/get_dependencies.sh

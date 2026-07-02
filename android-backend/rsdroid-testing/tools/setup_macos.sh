# Install build dependencies
apt install \
    clang \
    gcc \
    g++ \
    zlib1g-dev \
    libmpc-dev \
    libmpfr-dev \
    libgmp-dev \
	cmake \
	libxml2-dev \
	llvm-dev \
	cpio \
	libbz2-dev

# Removed as they didn't work& weren't required for CI	
#	uuid-dev \
#	libssl-dev \

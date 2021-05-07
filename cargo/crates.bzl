"""
@generated
cargo-raze generated Bazel file.

DO NOT EDIT! Replaced on runs of cargo-raze
"""

load("@bazel_tools//tools/build_defs/repo:git.bzl", "new_git_repository")  # buildifier: disable=load
load("@bazel_tools//tools/build_defs/repo:http.bzl", "http_archive")  # buildifier: disable=load
load("@bazel_tools//tools/build_defs/repo:utils.bzl", "maybe")  # buildifier: disable=load

def raze_fetch_remote_crates():
    """This function defines a collection of repos and should be called in a WORKSPACE file"""
    maybe(
        http_archive,
        name = "raze__Inflector__0_11_4",
        url = "https://crates.io/api/v1/crates/Inflector/0.11.4/download",
        type = "tar.gz",
        sha256 = "fe438c63458706e03479442743baae6c88256498e6431708f6dfc520a26515d3",
        strip_prefix = "Inflector-0.11.4",
        build_file = Label("//cargo/remote:BUILD.Inflector-0.11.4.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__adler__1_0_2",
        url = "https://crates.io/api/v1/crates/adler/1.0.2/download",
        type = "tar.gz",
        sha256 = "f26201604c87b1e01bd3d98f8d5d9a8fcbb815e8cedb41ffccbeb4bf593a35fe",
        strip_prefix = "adler-1.0.2",
        build_file = Label("//cargo/remote:BUILD.adler-1.0.2.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__ahash__0_4_7",
        url = "https://crates.io/api/v1/crates/ahash/0.4.7/download",
        type = "tar.gz",
        sha256 = "739f4a8db6605981345c5654f3a85b056ce52f37a39d34da03f25bf2151ea16e",
        strip_prefix = "ahash-0.4.7",
        build_file = Label("//cargo/remote:BUILD.ahash-0.4.7.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__aho_corasick__0_7_18",
        url = "https://crates.io/api/v1/crates/aho-corasick/0.7.18/download",
        type = "tar.gz",
        sha256 = "1e37cfd5e7657ada45f742d6e99ca5788580b5c529dc78faf11ece6dc702656f",
        strip_prefix = "aho-corasick-0.7.18",
        build_file = Label("//cargo/remote:BUILD.aho-corasick-0.7.18.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__ammonia__3_1_1",
        url = "https://crates.io/api/v1/crates/ammonia/3.1.1/download",
        type = "tar.gz",
        sha256 = "1ee7d6eb157f337c5cedc95ddf17f0cbc36d36eb7763c8e0d1c1aeb3722f6279",
        strip_prefix = "ammonia-3.1.1",
        build_file = Label("//cargo/remote:BUILD.ammonia-3.1.1.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__anyhow__1_0_40",
        url = "https://crates.io/api/v1/crates/anyhow/1.0.40/download",
        type = "tar.gz",
        sha256 = "28b2cd92db5cbd74e8e5028f7e27dd7aa3090e89e4f2a197cc7c8dfb69c7063b",
        strip_prefix = "anyhow-1.0.40",
        build_file = Label("//cargo/remote:BUILD.anyhow-1.0.40.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__arc_swap__1_2_0",
        url = "https://crates.io/api/v1/crates/arc-swap/1.2.0/download",
        type = "tar.gz",
        sha256 = "d4d7d63395147b81a9e570bcc6243aaf71c017bd666d4909cfef0085bdda8d73",
        strip_prefix = "arc-swap-1.2.0",
        build_file = Label("//cargo/remote:BUILD.arc-swap-1.2.0.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__arrayref__0_3_6",
        url = "https://crates.io/api/v1/crates/arrayref/0.3.6/download",
        type = "tar.gz",
        sha256 = "a4c527152e37cf757a3f78aae5a06fbeefdb07ccc535c980a3208ee3060dd544",
        strip_prefix = "arrayref-0.3.6",
        build_file = Label("//cargo/remote:BUILD.arrayref-0.3.6.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__arrayvec__0_4_12",
        url = "https://crates.io/api/v1/crates/arrayvec/0.4.12/download",
        type = "tar.gz",
        sha256 = "cd9fd44efafa8690358b7408d253adf110036b88f55672a933f01d616ad9b1b9",
        strip_prefix = "arrayvec-0.4.12",
        build_file = Label("//cargo/remote:BUILD.arrayvec-0.4.12.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__arrayvec__0_5_2",
        url = "https://crates.io/api/v1/crates/arrayvec/0.5.2/download",
        type = "tar.gz",
        sha256 = "23b62fc65de8e4e7f52534fb52b0f3ed04746ae267519eef2a83941e8085068b",
        strip_prefix = "arrayvec-0.5.2",
        build_file = Label("//cargo/remote:BUILD.arrayvec-0.5.2.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__askama__0_10_5",
        url = "https://crates.io/api/v1/crates/askama/0.10.5/download",
        type = "tar.gz",
        sha256 = "d298738b6e47e1034e560e5afe63aa488fea34e25ec11b855a76f0d7b8e73134",
        strip_prefix = "askama-0.10.5",
        build_file = Label("//cargo/remote:BUILD.askama-0.10.5.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__askama_derive__0_10_5",
        url = "https://crates.io/api/v1/crates/askama_derive/0.10.5/download",
        type = "tar.gz",
        sha256 = "ca2925c4c290382f9d2fa3d1c1b6a63fa1427099721ecca4749b154cc9c25522",
        strip_prefix = "askama_derive-0.10.5",
        build_file = Label("//cargo/remote:BUILD.askama_derive-0.10.5.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__askama_escape__0_10_1",
        url = "https://crates.io/api/v1/crates/askama_escape/0.10.1/download",
        type = "tar.gz",
        sha256 = "90c108c1a94380c89d2215d0ac54ce09796823cca0fd91b299cfff3b33e346fb",
        strip_prefix = "askama_escape-0.10.1",
        build_file = Label("//cargo/remote:BUILD.askama_escape-0.10.1.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__askama_shared__0_11_1",
        url = "https://crates.io/api/v1/crates/askama_shared/0.11.1/download",
        type = "tar.gz",
        sha256 = "2582b77e0f3c506ec4838a25fa8a5f97b9bed72bb6d3d272ea1c031d8bd373bc",
        strip_prefix = "askama_shared-0.11.1",
        build_file = Label("//cargo/remote:BUILD.askama_shared-0.11.1.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__async_compression__0_3_8",
        url = "https://crates.io/api/v1/crates/async-compression/0.3.8/download",
        type = "tar.gz",
        sha256 = "5443ccbb270374a2b1055fc72da40e1f237809cd6bb0e97e66d264cd138473a6",
        strip_prefix = "async-compression-0.3.8",
        build_file = Label("//cargo/remote:BUILD.async-compression-0.3.8.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__async_trait__0_1_50",
        url = "https://crates.io/api/v1/crates/async-trait/0.1.50/download",
        type = "tar.gz",
        sha256 = "0b98e84bbb4cbcdd97da190ba0c58a1bb0de2c1fdf67d159e192ed766aeca722",
        strip_prefix = "async-trait-0.1.50",
        build_file = Label("//cargo/remote:BUILD.async-trait-0.1.50.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__atty__0_2_14",
        url = "https://crates.io/api/v1/crates/atty/0.2.14/download",
        type = "tar.gz",
        sha256 = "d9b39be18770d11421cdb1b9947a45dd3f37e93092cbf377614828a319d5fee8",
        strip_prefix = "atty-0.2.14",
        build_file = Label("//cargo/remote:BUILD.atty-0.2.14.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__autocfg__1_0_1",
        url = "https://crates.io/api/v1/crates/autocfg/1.0.1/download",
        type = "tar.gz",
        sha256 = "cdb031dd78e28731d87d56cc8ffef4a8f36ca26c38fe2de700543e627f8a464a",
        strip_prefix = "autocfg-1.0.1",
        build_file = Label("//cargo/remote:BUILD.autocfg-1.0.1.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__base64__0_13_0",
        url = "https://crates.io/api/v1/crates/base64/0.13.0/download",
        type = "tar.gz",
        sha256 = "904dfeac50f3cdaba28fc6f57fdcddb75f49ed61346676a78c4ffe55877802fd",
        strip_prefix = "base64-0.13.0",
        build_file = Label("//cargo/remote:BUILD.base64-0.13.0.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__bitflags__1_2_1",
        url = "https://crates.io/api/v1/crates/bitflags/1.2.1/download",
        type = "tar.gz",
        sha256 = "cf1de2fe8c75bc145a2f577add951f8134889b4795d47466a54a5c846d691693",
        strip_prefix = "bitflags-1.2.1",
        build_file = Label("//cargo/remote:BUILD.bitflags-1.2.1.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__bitvec__0_19_5",
        url = "https://crates.io/api/v1/crates/bitvec/0.19.5/download",
        type = "tar.gz",
        sha256 = "8942c8d352ae1838c9dda0b0ca2ab657696ef2232a20147cf1b30ae1a9cb4321",
        strip_prefix = "bitvec-0.19.5",
        build_file = Label("//cargo/remote:BUILD.bitvec-0.19.5.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__blake3__0_3_7",
        url = "https://crates.io/api/v1/crates/blake3/0.3.7/download",
        type = "tar.gz",
        sha256 = "e9ff35b701f3914bdb8fad3368d822c766ef2858b2583198e41639b936f09d3f",
        strip_prefix = "blake3-0.3.7",
        build_file = Label("//cargo/remote:BUILD.blake3-0.3.7.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__bumpalo__3_6_1",
        url = "https://crates.io/api/v1/crates/bumpalo/3.6.1/download",
        type = "tar.gz",
        sha256 = "63396b8a4b9de3f4fdfb320ab6080762242f66a8ef174c49d8e19b674db4cdbe",
        strip_prefix = "bumpalo-3.6.1",
        build_file = Label("//cargo/remote:BUILD.bumpalo-3.6.1.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__byteorder__1_4_3",
        url = "https://crates.io/api/v1/crates/byteorder/1.4.3/download",
        type = "tar.gz",
        sha256 = "14c189c53d098945499cdfa7ecc63567cf3886b3332b312a5b4585d8d3a6a610",
        strip_prefix = "byteorder-1.4.3",
        build_file = Label("//cargo/remote:BUILD.byteorder-1.4.3.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__bytes__1_0_1",
        url = "https://crates.io/api/v1/crates/bytes/1.0.1/download",
        type = "tar.gz",
        sha256 = "b700ce4376041dcd0a327fd0097c41095743c4c8af8887265942faf1100bd040",
        strip_prefix = "bytes-1.0.1",
        build_file = Label("//cargo/remote:BUILD.bytes-1.0.1.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__cc__1_0_67",
        url = "https://crates.io/api/v1/crates/cc/1.0.67/download",
        type = "tar.gz",
        sha256 = "e3c69b077ad434294d3ce9f1f6143a2a4b89a8a2d54ef813d85003a4fd1137fd",
        strip_prefix = "cc-1.0.67",
        build_file = Label("//cargo/remote:BUILD.cc-1.0.67.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__cfg_if__0_1_10",
        url = "https://crates.io/api/v1/crates/cfg-if/0.1.10/download",
        type = "tar.gz",
        sha256 = "4785bdd1c96b2a846b2bd7cc02e86b6b3dbf14e7e53446c4f54c92a361040822",
        strip_prefix = "cfg-if-0.1.10",
        build_file = Label("//cargo/remote:BUILD.cfg-if-0.1.10.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__cfg_if__1_0_0",
        url = "https://crates.io/api/v1/crates/cfg-if/1.0.0/download",
        type = "tar.gz",
        sha256 = "baf1de4339761588bc0619e3cbc0120ee582ebb74b53b4efbf79117bd2da40fd",
        strip_prefix = "cfg-if-1.0.0",
        build_file = Label("//cargo/remote:BUILD.cfg-if-1.0.0.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__chrono__0_4_19",
        url = "https://crates.io/api/v1/crates/chrono/0.4.19/download",
        type = "tar.gz",
        sha256 = "670ad68c9088c2a963aaa298cb369688cf3f9465ce5e2d4ca10e6e0098a1ce73",
        strip_prefix = "chrono-0.4.19",
        build_file = Label("//cargo/remote:BUILD.chrono-0.4.19.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__coarsetime__0_1_19",
        url = "https://crates.io/api/v1/crates/coarsetime/0.1.19/download",
        type = "tar.gz",
        sha256 = "2918e2ffa91a49dabbba4965fe38a37a1ba0b6953a29e32cc250a8d59cd42232",
        strip_prefix = "coarsetime-0.1.19",
        build_file = Label("//cargo/remote:BUILD.coarsetime-0.1.19.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__constant_time_eq__0_1_5",
        url = "https://crates.io/api/v1/crates/constant_time_eq/0.1.5/download",
        type = "tar.gz",
        sha256 = "245097e9a4535ee1e3e3931fcfcd55a796a44c643e8596ff6566d68f09b87bbc",
        strip_prefix = "constant_time_eq-0.1.5",
        build_file = Label("//cargo/remote:BUILD.constant_time_eq-0.1.5.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__core_foundation__0_9_1",
        url = "https://crates.io/api/v1/crates/core-foundation/0.9.1/download",
        type = "tar.gz",
        sha256 = "0a89e2ae426ea83155dccf10c0fa6b1463ef6d5fcb44cee0b224a408fa640a62",
        strip_prefix = "core-foundation-0.9.1",
        build_file = Label("//cargo/remote:BUILD.core-foundation-0.9.1.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__core_foundation_sys__0_8_2",
        url = "https://crates.io/api/v1/crates/core-foundation-sys/0.8.2/download",
        type = "tar.gz",
        sha256 = "ea221b5284a47e40033bf9b66f35f984ec0ea2931eb03505246cd27a963f981b",
        strip_prefix = "core-foundation-sys-0.8.2",
        build_file = Label("//cargo/remote:BUILD.core-foundation-sys-0.8.2.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__crc32fast__1_2_1",
        url = "https://crates.io/api/v1/crates/crc32fast/1.2.1/download",
        type = "tar.gz",
        sha256 = "81156fece84ab6a9f2afdb109ce3ae577e42b1228441eded99bd77f627953b1a",
        strip_prefix = "crc32fast-1.2.1",
        build_file = Label("//cargo/remote:BUILD.crc32fast-1.2.1.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__crossbeam_channel__0_5_1",
        url = "https://crates.io/api/v1/crates/crossbeam-channel/0.5.1/download",
        type = "tar.gz",
        sha256 = "06ed27e177f16d65f0f0c22a213e17c696ace5dd64b14258b52f9417ccb52db4",
        strip_prefix = "crossbeam-channel-0.5.1",
        build_file = Label("//cargo/remote:BUILD.crossbeam-channel-0.5.1.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__crossbeam_utils__0_8_4",
        url = "https://crates.io/api/v1/crates/crossbeam-utils/0.8.4/download",
        type = "tar.gz",
        sha256 = "4feb231f0d4d6af81aed15928e58ecf5816aa62a2393e2c82f46973e92a9a278",
        strip_prefix = "crossbeam-utils-0.8.4",
        build_file = Label("//cargo/remote:BUILD.crossbeam-utils-0.8.4.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__crypto_mac__0_8_0",
        url = "https://crates.io/api/v1/crates/crypto-mac/0.8.0/download",
        type = "tar.gz",
        sha256 = "b584a330336237c1eecd3e94266efb216c56ed91225d634cb2991c5f3fd1aeab",
        strip_prefix = "crypto-mac-0.8.0",
        build_file = Label("//cargo/remote:BUILD.crypto-mac-0.8.0.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__ctor__0_1_20",
        url = "https://crates.io/api/v1/crates/ctor/0.1.20/download",
        type = "tar.gz",
        sha256 = "5e98e2ad1a782e33928b96fc3948e7c355e5af34ba4de7670fe8bac2a3b2006d",
        strip_prefix = "ctor-0.1.20",
        build_file = Label("//cargo/remote:BUILD.ctor-0.1.20.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__derivative__2_2_0",
        url = "https://crates.io/api/v1/crates/derivative/2.2.0/download",
        type = "tar.gz",
        sha256 = "fcc3dd5e9e9c0b295d6e1e4d811fb6f157d5ffd784b8d202fc62eac8035a770b",
        strip_prefix = "derivative-2.2.0",
        build_file = Label("//cargo/remote:BUILD.derivative-2.2.0.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__digest__0_9_0",
        url = "https://crates.io/api/v1/crates/digest/0.9.0/download",
        type = "tar.gz",
        sha256 = "d3dd60d1080a57a05ab032377049e0591415d2b31afd7028356dbf3cc6dcb066",
        strip_prefix = "digest-0.9.0",
        build_file = Label("//cargo/remote:BUILD.digest-0.9.0.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__dirs__2_0_2",
        url = "https://crates.io/api/v1/crates/dirs/2.0.2/download",
        type = "tar.gz",
        sha256 = "13aea89a5c93364a98e9b37b2fa237effbb694d5cfe01c5b70941f7eb087d5e3",
        strip_prefix = "dirs-2.0.2",
        build_file = Label("//cargo/remote:BUILD.dirs-2.0.2.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__dirs_sys__0_3_6",
        url = "https://crates.io/api/v1/crates/dirs-sys/0.3.6/download",
        type = "tar.gz",
        sha256 = "03d86534ed367a67548dc68113a0f5db55432fdfbb6e6f9d77704397d95d5780",
        strip_prefix = "dirs-sys-0.3.6",
        build_file = Label("//cargo/remote:BUILD.dirs-sys-0.3.6.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__either__1_6_1",
        url = "https://crates.io/api/v1/crates/either/1.6.1/download",
        type = "tar.gz",
        sha256 = "e78d4f1cc4ae33bbfc157ed5d5a5ef3bc29227303d595861deb238fcec4e9457",
        strip_prefix = "either-1.6.1",
        build_file = Label("//cargo/remote:BUILD.either-1.6.1.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__encoding_rs__0_8_28",
        url = "https://crates.io/api/v1/crates/encoding_rs/0.8.28/download",
        type = "tar.gz",
        sha256 = "80df024fbc5ac80f87dfef0d9f5209a252f2a497f7f42944cff24d8253cac065",
        strip_prefix = "encoding_rs-0.8.28",
        build_file = Label("//cargo/remote:BUILD.encoding_rs-0.8.28.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__env_logger__0_8_3",
        url = "https://crates.io/api/v1/crates/env_logger/0.8.3/download",
        type = "tar.gz",
        sha256 = "17392a012ea30ef05a610aa97dfb49496e71c9f676b27879922ea5bdf60d9d3f",
        strip_prefix = "env_logger-0.8.3",
        build_file = Label("//cargo/remote:BUILD.env_logger-0.8.3.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__fallible_iterator__0_2_0",
        url = "https://crates.io/api/v1/crates/fallible-iterator/0.2.0/download",
        type = "tar.gz",
        sha256 = "4443176a9f2c162692bd3d352d745ef9413eec5782a80d8fd6f8a1ac692a07f7",
        strip_prefix = "fallible-iterator-0.2.0",
        build_file = Label("//cargo/remote:BUILD.fallible-iterator-0.2.0.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__fallible_streaming_iterator__0_1_9",
        url = "https://crates.io/api/v1/crates/fallible-streaming-iterator/0.1.9/download",
        type = "tar.gz",
        sha256 = "7360491ce676a36bf9bb3c56c1aa791658183a54d2744120f27285738d90465a",
        strip_prefix = "fallible-streaming-iterator-0.1.9",
        build_file = Label("//cargo/remote:BUILD.fallible-streaming-iterator-0.1.9.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__fixedbitset__0_2_0",
        url = "https://crates.io/api/v1/crates/fixedbitset/0.2.0/download",
        type = "tar.gz",
        sha256 = "37ab347416e802de484e4d03c7316c48f1ecb56574dfd4a46a80f173ce1de04d",
        strip_prefix = "fixedbitset-0.2.0",
        build_file = Label("//cargo/remote:BUILD.fixedbitset-0.2.0.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__flate2__1_0_20",
        url = "https://crates.io/api/v1/crates/flate2/1.0.20/download",
        type = "tar.gz",
        sha256 = "cd3aec53de10fe96d7d8c565eb17f2c687bb5518a2ec453b5b1252964526abe0",
        strip_prefix = "flate2-1.0.20",
        build_file = Label("//cargo/remote:BUILD.flate2-1.0.20.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__fluent__0_15_0",
        url = "https://crates.io/api/v1/crates/fluent/0.15.0/download",
        type = "tar.gz",
        sha256 = "bc4d7142005e2066e4844caf9f271b93fc79836ee96ec85057b8c109687e629a",
        strip_prefix = "fluent-0.15.0",
        build_file = Label("//cargo/remote:BUILD.fluent-0.15.0.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__fluent_bundle__0_15_1",
        url = "https://crates.io/api/v1/crates/fluent-bundle/0.15.1/download",
        type = "tar.gz",
        sha256 = "8acf044eeb4872d9dbf2667541fbf461f5965c57e343878ad0fb24b5793fa007",
        strip_prefix = "fluent-bundle-0.15.1",
        build_file = Label("//cargo/remote:BUILD.fluent-bundle-0.15.1.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__fluent_langneg__0_13_0",
        url = "https://crates.io/api/v1/crates/fluent-langneg/0.13.0/download",
        type = "tar.gz",
        sha256 = "2c4ad0989667548f06ccd0e306ed56b61bd4d35458d54df5ec7587c0e8ed5e94",
        strip_prefix = "fluent-langneg-0.13.0",
        build_file = Label("//cargo/remote:BUILD.fluent-langneg-0.13.0.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__fluent_syntax__0_11_0",
        url = "https://crates.io/api/v1/crates/fluent-syntax/0.11.0/download",
        type = "tar.gz",
        sha256 = "c0abed97648395c902868fee9026de96483933faa54ea3b40d652f7dfe61ca78",
        strip_prefix = "fluent-syntax-0.11.0",
        build_file = Label("//cargo/remote:BUILD.fluent-syntax-0.11.0.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__fnv__1_0_7",
        url = "https://crates.io/api/v1/crates/fnv/1.0.7/download",
        type = "tar.gz",
        sha256 = "3f9eec918d3f24069decb9af1554cad7c880e2da24a9afd88aca000531ab82c1",
        strip_prefix = "fnv-1.0.7",
        build_file = Label("//cargo/remote:BUILD.fnv-1.0.7.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__foreign_types__0_3_2",
        url = "https://crates.io/api/v1/crates/foreign-types/0.3.2/download",
        type = "tar.gz",
        sha256 = "f6f339eb8adc052cd2ca78910fda869aefa38d22d5cb648e6485e4d3fc06f3b1",
        strip_prefix = "foreign-types-0.3.2",
        build_file = Label("//cargo/remote:BUILD.foreign-types-0.3.2.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__foreign_types_shared__0_1_1",
        url = "https://crates.io/api/v1/crates/foreign-types-shared/0.1.1/download",
        type = "tar.gz",
        sha256 = "00b0228411908ca8685dba7fc2cdd70ec9990a6e753e89b6ac91a84c40fbaf4b",
        strip_prefix = "foreign-types-shared-0.1.1",
        build_file = Label("//cargo/remote:BUILD.foreign-types-shared-0.1.1.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__form_urlencoded__1_0_1",
        url = "https://crates.io/api/v1/crates/form_urlencoded/1.0.1/download",
        type = "tar.gz",
        sha256 = "5fc25a87fa4fd2094bffb06925852034d90a17f0d1e05197d4956d3555752191",
        strip_prefix = "form_urlencoded-1.0.1",
        build_file = Label("//cargo/remote:BUILD.form_urlencoded-1.0.1.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__funty__1_1_0",
        url = "https://crates.io/api/v1/crates/funty/1.1.0/download",
        type = "tar.gz",
        sha256 = "fed34cd105917e91daa4da6b3728c47b068749d6a62c59811f06ed2ac71d9da7",
        strip_prefix = "funty-1.1.0",
        build_file = Label("//cargo/remote:BUILD.funty-1.1.0.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__futf__0_1_4",
        url = "https://crates.io/api/v1/crates/futf/0.1.4/download",
        type = "tar.gz",
        sha256 = "7c9c1ce3fa9336301af935ab852c437817d14cd33690446569392e65170aac3b",
        strip_prefix = "futf-0.1.4",
        build_file = Label("//cargo/remote:BUILD.futf-0.1.4.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__futures__0_3_14",
        url = "https://crates.io/api/v1/crates/futures/0.3.14/download",
        type = "tar.gz",
        sha256 = "a9d5813545e459ad3ca1bff9915e9ad7f1a47dc6a91b627ce321d5863b7dd253",
        strip_prefix = "futures-0.3.14",
        build_file = Label("//cargo/remote:BUILD.futures-0.3.14.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__futures_channel__0_3_14",
        url = "https://crates.io/api/v1/crates/futures-channel/0.3.14/download",
        type = "tar.gz",
        sha256 = "ce79c6a52a299137a6013061e0cf0e688fce5d7f1bc60125f520912fdb29ec25",
        strip_prefix = "futures-channel-0.3.14",
        build_file = Label("//cargo/remote:BUILD.futures-channel-0.3.14.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__futures_core__0_3_14",
        url = "https://crates.io/api/v1/crates/futures-core/0.3.14/download",
        type = "tar.gz",
        sha256 = "098cd1c6dda6ca01650f1a37a794245eb73181d0d4d4e955e2f3c37db7af1815",
        strip_prefix = "futures-core-0.3.14",
        build_file = Label("//cargo/remote:BUILD.futures-core-0.3.14.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__futures_executor__0_3_14",
        url = "https://crates.io/api/v1/crates/futures-executor/0.3.14/download",
        type = "tar.gz",
        sha256 = "10f6cb7042eda00f0049b1d2080aa4b93442997ee507eb3828e8bd7577f94c9d",
        strip_prefix = "futures-executor-0.3.14",
        build_file = Label("//cargo/remote:BUILD.futures-executor-0.3.14.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__futures_io__0_3_14",
        url = "https://crates.io/api/v1/crates/futures-io/0.3.14/download",
        type = "tar.gz",
        sha256 = "365a1a1fb30ea1c03a830fdb2158f5236833ac81fa0ad12fe35b29cddc35cb04",
        strip_prefix = "futures-io-0.3.14",
        build_file = Label("//cargo/remote:BUILD.futures-io-0.3.14.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__futures_macro__0_3_14",
        url = "https://crates.io/api/v1/crates/futures-macro/0.3.14/download",
        type = "tar.gz",
        sha256 = "668c6733a182cd7deb4f1de7ba3bf2120823835b3bcfbeacf7d2c4a773c1bb8b",
        strip_prefix = "futures-macro-0.3.14",
        build_file = Label("//cargo/remote:BUILD.futures-macro-0.3.14.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__futures_sink__0_3_14",
        url = "https://crates.io/api/v1/crates/futures-sink/0.3.14/download",
        type = "tar.gz",
        sha256 = "5c5629433c555de3d82861a7a4e3794a4c40040390907cfbfd7143a92a426c23",
        strip_prefix = "futures-sink-0.3.14",
        build_file = Label("//cargo/remote:BUILD.futures-sink-0.3.14.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__futures_task__0_3_14",
        url = "https://crates.io/api/v1/crates/futures-task/0.3.14/download",
        type = "tar.gz",
        sha256 = "ba7aa51095076f3ba6d9a1f702f74bd05ec65f555d70d2033d55ba8d69f581bc",
        strip_prefix = "futures-task-0.3.14",
        build_file = Label("//cargo/remote:BUILD.futures-task-0.3.14.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__futures_util__0_3_14",
        url = "https://crates.io/api/v1/crates/futures-util/0.3.14/download",
        type = "tar.gz",
        sha256 = "3c144ad54d60f23927f0a6b6d816e4271278b64f005ad65e4e35291d2de9c025",
        strip_prefix = "futures-util-0.3.14",
        build_file = Label("//cargo/remote:BUILD.futures-util-0.3.14.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__generic_array__0_14_4",
        url = "https://crates.io/api/v1/crates/generic-array/0.14.4/download",
        type = "tar.gz",
        sha256 = "501466ecc8a30d1d3b7fc9229b122b2ce8ed6e9d9223f1138d4babb253e51817",
        strip_prefix = "generic-array-0.14.4",
        build_file = Label("//cargo/remote:BUILD.generic-array-0.14.4.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__getopts__0_2_21",
        url = "https://crates.io/api/v1/crates/getopts/0.2.21/download",
        type = "tar.gz",
        sha256 = "14dbbfd5c71d70241ecf9e6f13737f7b5ce823821063188d7e46c41d371eebd5",
        strip_prefix = "getopts-0.2.21",
        build_file = Label("//cargo/remote:BUILD.getopts-0.2.21.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__getrandom__0_1_16",
        url = "https://crates.io/api/v1/crates/getrandom/0.1.16/download",
        type = "tar.gz",
        sha256 = "8fc3cb4d91f53b50155bdcfd23f6a4c39ae1969c2ae85982b135750cccaf5fce",
        strip_prefix = "getrandom-0.1.16",
        build_file = Label("//cargo/remote:BUILD.getrandom-0.1.16.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__getrandom__0_2_2",
        url = "https://crates.io/api/v1/crates/getrandom/0.2.2/download",
        type = "tar.gz",
        sha256 = "c9495705279e7140bf035dde1f6e750c162df8b625267cd52cc44e0b156732c8",
        strip_prefix = "getrandom-0.2.2",
        build_file = Label("//cargo/remote:BUILD.getrandom-0.2.2.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__ghost__0_1_2",
        url = "https://crates.io/api/v1/crates/ghost/0.1.2/download",
        type = "tar.gz",
        sha256 = "1a5bcf1bbeab73aa4cf2fde60a846858dc036163c7c33bec309f8d17de785479",
        strip_prefix = "ghost-0.1.2",
        build_file = Label("//cargo/remote:BUILD.ghost-0.1.2.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__h2__0_3_3",
        url = "https://crates.io/api/v1/crates/h2/0.3.3/download",
        type = "tar.gz",
        sha256 = "825343c4eef0b63f541f8903f395dc5beb362a979b5799a84062527ef1e37726",
        strip_prefix = "h2-0.3.3",
        build_file = Label("//cargo/remote:BUILD.h2-0.3.3.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__hashbrown__0_9_1",
        url = "https://crates.io/api/v1/crates/hashbrown/0.9.1/download",
        type = "tar.gz",
        sha256 = "d7afe4a420e3fe79967a00898cc1f4db7c8a49a9333a29f8a4bd76a253d5cd04",
        strip_prefix = "hashbrown-0.9.1",
        build_file = Label("//cargo/remote:BUILD.hashbrown-0.9.1.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__hashlink__0_6_0",
        url = "https://crates.io/api/v1/crates/hashlink/0.6.0/download",
        type = "tar.gz",
        sha256 = "d99cf782f0dc4372d26846bec3de7804ceb5df083c2d4462c0b8d2330e894fa8",
        strip_prefix = "hashlink-0.6.0",
        build_file = Label("//cargo/remote:BUILD.hashlink-0.6.0.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__heck__0_3_2",
        url = "https://crates.io/api/v1/crates/heck/0.3.2/download",
        type = "tar.gz",
        sha256 = "87cbf45460356b7deeb5e3415b5563308c0a9b057c85e12b06ad551f98d0a6ac",
        strip_prefix = "heck-0.3.2",
        build_file = Label("//cargo/remote:BUILD.heck-0.3.2.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__hermit_abi__0_1_18",
        url = "https://crates.io/api/v1/crates/hermit-abi/0.1.18/download",
        type = "tar.gz",
        sha256 = "322f4de77956e22ed0e5032c359a0f1273f1f7f0d79bfa3b8ffbc730d7fbcc5c",
        strip_prefix = "hermit-abi-0.1.18",
        build_file = Label("//cargo/remote:BUILD.hermit-abi-0.1.18.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__hex__0_4_3",
        url = "https://crates.io/api/v1/crates/hex/0.4.3/download",
        type = "tar.gz",
        sha256 = "7f24254aa9a54b5c858eaee2f5bccdb46aaf0e486a595ed5fd8f86ba55232a70",
        strip_prefix = "hex-0.4.3",
        build_file = Label("//cargo/remote:BUILD.hex-0.4.3.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__html5ever__0_25_1",
        url = "https://crates.io/api/v1/crates/html5ever/0.25.1/download",
        type = "tar.gz",
        sha256 = "aafcf38a1a36118242d29b92e1b08ef84e67e4a5ed06e0a80be20e6a32bfed6b",
        strip_prefix = "html5ever-0.25.1",
        build_file = Label("//cargo/remote:BUILD.html5ever-0.25.1.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__htmlescape__0_3_1",
        url = "https://crates.io/api/v1/crates/htmlescape/0.3.1/download",
        type = "tar.gz",
        sha256 = "e9025058dae765dee5070ec375f591e2ba14638c63feff74f13805a72e523163",
        strip_prefix = "htmlescape-0.3.1",
        build_file = Label("//cargo/remote:BUILD.htmlescape-0.3.1.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__http__0_2_4",
        url = "https://crates.io/api/v1/crates/http/0.2.4/download",
        type = "tar.gz",
        sha256 = "527e8c9ac747e28542699a951517aa9a6945af506cd1f2e1b53a576c17b6cc11",
        strip_prefix = "http-0.2.4",
        build_file = Label("//cargo/remote:BUILD.http-0.2.4.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__http_body__0_4_1",
        url = "https://crates.io/api/v1/crates/http-body/0.4.1/download",
        type = "tar.gz",
        sha256 = "5dfb77c123b4e2f72a2069aeae0b4b4949cc7e966df277813fc16347e7549737",
        strip_prefix = "http-body-0.4.1",
        build_file = Label("//cargo/remote:BUILD.http-body-0.4.1.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__httparse__1_4_0",
        url = "https://crates.io/api/v1/crates/httparse/1.4.0/download",
        type = "tar.gz",
        sha256 = "4a1ce40d6fc9764887c2fdc7305c3dcc429ba11ff981c1509416afd5697e4437",
        strip_prefix = "httparse-1.4.0",
        build_file = Label("//cargo/remote:BUILD.httparse-1.4.0.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__httpdate__1_0_0",
        url = "https://crates.io/api/v1/crates/httpdate/1.0.0/download",
        type = "tar.gz",
        sha256 = "05842d0d43232b23ccb7060ecb0f0626922c21f30012e97b767b30afd4a5d4b9",
        strip_prefix = "httpdate-1.0.0",
        build_file = Label("//cargo/remote:BUILD.httpdate-1.0.0.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__humansize__1_1_0",
        url = "https://crates.io/api/v1/crates/humansize/1.1.0/download",
        type = "tar.gz",
        sha256 = "b6cab2627acfc432780848602f3f558f7e9dd427352224b0d9324025796d2a5e",
        strip_prefix = "humansize-1.1.0",
        build_file = Label("//cargo/remote:BUILD.humansize-1.1.0.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__humantime__2_1_0",
        url = "https://crates.io/api/v1/crates/humantime/2.1.0/download",
        type = "tar.gz",
        sha256 = "9a3a5bfb195931eeb336b2a7b4d761daec841b97f947d34394601737a7bba5e4",
        strip_prefix = "humantime-2.1.0",
        build_file = Label("//cargo/remote:BUILD.humantime-2.1.0.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__hyper__0_14_7",
        url = "https://crates.io/api/v1/crates/hyper/0.14.7/download",
        type = "tar.gz",
        sha256 = "1e5f105c494081baa3bf9e200b279e27ec1623895cd504c7dbef8d0b080fcf54",
        strip_prefix = "hyper-0.14.7",
        build_file = Label("//cargo/remote:BUILD.hyper-0.14.7.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__hyper_rustls__0_22_1",
        url = "https://crates.io/api/v1/crates/hyper-rustls/0.22.1/download",
        type = "tar.gz",
        sha256 = "5f9f7a97316d44c0af9b0301e65010573a853a9fc97046d7331d7f6bc0fd5a64",
        strip_prefix = "hyper-rustls-0.22.1",
        build_file = Label("//cargo/remote:BUILD.hyper-rustls-0.22.1.bazel"),
    )

    maybe(
        new_git_repository,
        name = "raze__hyper_timeout__0_4_1",
        remote = "https://github.com/ankitects/hyper-timeout.git",
        shallow_since = "1619519657 +1000",
        commit = "0cb6f7d14c62819e37cd221736f8b0555e823712",
        build_file = Label("//cargo/remote:BUILD.hyper-timeout-0.4.1.bazel"),
        init_submodules = True,
    )

    maybe(
        http_archive,
        name = "raze__hyper_tls__0_5_0",
        url = "https://crates.io/api/v1/crates/hyper-tls/0.5.0/download",
        type = "tar.gz",
        sha256 = "d6183ddfa99b85da61a140bea0efc93fdf56ceaa041b37d553518030827f9905",
        strip_prefix = "hyper-tls-0.5.0",
        build_file = Label("//cargo/remote:BUILD.hyper-tls-0.5.0.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__idna__0_2_3",
        url = "https://crates.io/api/v1/crates/idna/0.2.3/download",
        type = "tar.gz",
        sha256 = "418a0a6fab821475f634efe3ccc45c013f742efe03d853e8d3355d5cb850ecf8",
        strip_prefix = "idna-0.2.3",
        build_file = Label("//cargo/remote:BUILD.idna-0.2.3.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__indexmap__1_6_2",
        url = "https://crates.io/api/v1/crates/indexmap/1.6.2/download",
        type = "tar.gz",
        sha256 = "824845a0bf897a9042383849b02c1bc219c2383772efcd5c6f9766fa4b81aef3",
        strip_prefix = "indexmap-1.6.2",
        build_file = Label("//cargo/remote:BUILD.indexmap-1.6.2.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__indoc__0_3_6",
        url = "https://crates.io/api/v1/crates/indoc/0.3.6/download",
        type = "tar.gz",
        sha256 = "47741a8bc60fb26eb8d6e0238bbb26d8575ff623fdc97b1a2c00c050b9684ed8",
        strip_prefix = "indoc-0.3.6",
        build_file = Label("//cargo/remote:BUILD.indoc-0.3.6.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__indoc_impl__0_3_6",
        url = "https://crates.io/api/v1/crates/indoc-impl/0.3.6/download",
        type = "tar.gz",
        sha256 = "ce046d161f000fffde5f432a0d034d0341dc152643b2598ed5bfce44c4f3a8f0",
        strip_prefix = "indoc-impl-0.3.6",
        build_file = Label("//cargo/remote:BUILD.indoc-impl-0.3.6.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__inflections__1_1_1",
        url = "https://crates.io/api/v1/crates/inflections/1.1.1/download",
        type = "tar.gz",
        sha256 = "a257582fdcde896fd96463bf2d40eefea0580021c0712a0e2b028b60b47a837a",
        strip_prefix = "inflections-1.1.1",
        build_file = Label("//cargo/remote:BUILD.inflections-1.1.1.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__instant__0_1_9",
        url = "https://crates.io/api/v1/crates/instant/0.1.9/download",
        type = "tar.gz",
        sha256 = "61124eeebbd69b8190558df225adf7e4caafce0d743919e5d6b19652314ec5ec",
        strip_prefix = "instant-0.1.9",
        build_file = Label("//cargo/remote:BUILD.instant-0.1.9.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__intl_memoizer__0_5_1",
        url = "https://crates.io/api/v1/crates/intl-memoizer/0.5.1/download",
        type = "tar.gz",
        sha256 = "c310433e4a310918d6ed9243542a6b83ec1183df95dff8f23f87bb88a264a66f",
        strip_prefix = "intl-memoizer-0.5.1",
        build_file = Label("//cargo/remote:BUILD.intl-memoizer-0.5.1.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__intl_pluralrules__7_0_1",
        url = "https://crates.io/api/v1/crates/intl_pluralrules/7.0.1/download",
        type = "tar.gz",
        sha256 = "b18f988384267d7066cc2be425e6faf352900652c046b6971d2e228d3b1c5ecf",
        strip_prefix = "intl_pluralrules-7.0.1",
        build_file = Label("//cargo/remote:BUILD.intl_pluralrules-7.0.1.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__inventory__0_1_10",
        url = "https://crates.io/api/v1/crates/inventory/0.1.10/download",
        type = "tar.gz",
        sha256 = "0f0f7efb804ec95e33db9ad49e4252f049e37e8b0a4652e3cd61f7999f2eff7f",
        strip_prefix = "inventory-0.1.10",
        build_file = Label("//cargo/remote:BUILD.inventory-0.1.10.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__inventory_impl__0_1_10",
        url = "https://crates.io/api/v1/crates/inventory-impl/0.1.10/download",
        type = "tar.gz",
        sha256 = "75c094e94816723ab936484666968f5b58060492e880f3c8d00489a1e244fa51",
        strip_prefix = "inventory-impl-0.1.10",
        build_file = Label("//cargo/remote:BUILD.inventory-impl-0.1.10.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__ipnet__2_3_0",
        url = "https://crates.io/api/v1/crates/ipnet/2.3.0/download",
        type = "tar.gz",
        sha256 = "47be2f14c678be2fdcab04ab1171db51b2762ce6f0a8ee87c8dd4a04ed216135",
        strip_prefix = "ipnet-2.3.0",
        build_file = Label("//cargo/remote:BUILD.ipnet-2.3.0.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__itertools__0_9_0",
        url = "https://crates.io/api/v1/crates/itertools/0.9.0/download",
        type = "tar.gz",
        sha256 = "284f18f85651fe11e8a991b2adb42cb078325c996ed026d994719efcfca1d54b",
        strip_prefix = "itertools-0.9.0",
        build_file = Label("//cargo/remote:BUILD.itertools-0.9.0.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__itoa__0_4_7",
        url = "https://crates.io/api/v1/crates/itoa/0.4.7/download",
        type = "tar.gz",
        sha256 = "dd25036021b0de88a0aff6b850051563c6516d0bf53f8638938edbb9de732736",
        strip_prefix = "itoa-0.4.7",
        build_file = Label("//cargo/remote:BUILD.itoa-0.4.7.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__js_sys__0_3_50",
        url = "https://crates.io/api/v1/crates/js-sys/0.3.50/download",
        type = "tar.gz",
        sha256 = "2d99f9e3e84b8f67f846ef5b4cbbc3b1c29f6c759fcbce6f01aa0e73d932a24c",
        strip_prefix = "js-sys-0.3.50",
        build_file = Label("//cargo/remote:BUILD.js-sys-0.3.50.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__lazy_static__1_4_0",
        url = "https://crates.io/api/v1/crates/lazy_static/1.4.0/download",
        type = "tar.gz",
        sha256 = "e2abad23fbc42b3700f2f279844dc832adb2b2eb069b2df918f455c4e18cc646",
        strip_prefix = "lazy_static-1.4.0",
        build_file = Label("//cargo/remote:BUILD.lazy_static-1.4.0.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__lexical_core__0_7_6",
        url = "https://crates.io/api/v1/crates/lexical-core/0.7.6/download",
        type = "tar.gz",
        sha256 = "6607c62aa161d23d17a9072cc5da0be67cdfc89d3afb1e8d9c842bebc2525ffe",
        strip_prefix = "lexical-core-0.7.6",
        build_file = Label("//cargo/remote:BUILD.lexical-core-0.7.6.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__libc__0_2_94",
        url = "https://crates.io/api/v1/crates/libc/0.2.94/download",
        type = "tar.gz",
        sha256 = "18794a8ad5b29321f790b55d93dfba91e125cb1a9edbd4f8e3150acc771c1a5e",
        strip_prefix = "libc-0.2.94",
        build_file = Label("//cargo/remote:BUILD.libc-0.2.94.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__libsqlite3_sys__0_20_1",
        url = "https://crates.io/api/v1/crates/libsqlite3-sys/0.20.1/download",
        type = "tar.gz",
        sha256 = "64d31059f22935e6c31830db5249ba2b7ecd54fd73a9909286f0a67aa55c2fbd",
        strip_prefix = "libsqlite3-sys-0.20.1",
        build_file = Label("//cargo/remote:BUILD.libsqlite3-sys-0.20.1.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__lock_api__0_4_4",
        url = "https://crates.io/api/v1/crates/lock_api/0.4.4/download",
        type = "tar.gz",
        sha256 = "0382880606dff6d15c9476c416d18690b72742aa7b605bb6dd6ec9030fbf07eb",
        strip_prefix = "lock_api-0.4.4",
        build_file = Label("//cargo/remote:BUILD.lock_api-0.4.4.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__log__0_4_14",
        url = "https://crates.io/api/v1/crates/log/0.4.14/download",
        type = "tar.gz",
        sha256 = "51b9bbe6c47d51fc3e1a9b945965946b4c44142ab8792c50835a980d362c2710",
        strip_prefix = "log-0.4.14",
        build_file = Label("//cargo/remote:BUILD.log-0.4.14.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__mac__0_1_1",
        url = "https://crates.io/api/v1/crates/mac/0.1.1/download",
        type = "tar.gz",
        sha256 = "c41e0c4fef86961ac6d6f8a82609f55f31b05e4fce149ac5710e439df7619ba4",
        strip_prefix = "mac-0.1.1",
        build_file = Label("//cargo/remote:BUILD.mac-0.1.1.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__maplit__1_0_2",
        url = "https://crates.io/api/v1/crates/maplit/1.0.2/download",
        type = "tar.gz",
        sha256 = "3e2e65a1a2e43cfcb47a895c4c8b10d1f4a61097f9f254f183aee60cad9c651d",
        strip_prefix = "maplit-1.0.2",
        build_file = Label("//cargo/remote:BUILD.maplit-1.0.2.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__markup5ever__0_10_1",
        url = "https://crates.io/api/v1/crates/markup5ever/0.10.1/download",
        type = "tar.gz",
        sha256 = "a24f40fb03852d1cdd84330cddcaf98e9ec08a7b7768e952fad3b4cf048ec8fd",
        strip_prefix = "markup5ever-0.10.1",
        build_file = Label("//cargo/remote:BUILD.markup5ever-0.10.1.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__markup5ever_rcdom__0_1_0",
        url = "https://crates.io/api/v1/crates/markup5ever_rcdom/0.1.0/download",
        type = "tar.gz",
        sha256 = "f015da43bcd8d4f144559a3423f4591d69b8ce0652c905374da7205df336ae2b",
        strip_prefix = "markup5ever_rcdom-0.1.0",
        build_file = Label("//cargo/remote:BUILD.markup5ever_rcdom-0.1.0.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__matches__0_1_8",
        url = "https://crates.io/api/v1/crates/matches/0.1.8/download",
        type = "tar.gz",
        sha256 = "7ffc5c5338469d4d3ea17d269fa8ea3512ad247247c30bd2df69e68309ed0a08",
        strip_prefix = "matches-0.1.8",
        build_file = Label("//cargo/remote:BUILD.matches-0.1.8.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__memchr__2_4_0",
        url = "https://crates.io/api/v1/crates/memchr/2.4.0/download",
        type = "tar.gz",
        sha256 = "b16bd47d9e329435e309c58469fe0791c2d0d1ba96ec0954152a5ae2b04387dc",
        strip_prefix = "memchr-2.4.0",
        build_file = Label("//cargo/remote:BUILD.memchr-2.4.0.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__mime__0_3_16",
        url = "https://crates.io/api/v1/crates/mime/0.3.16/download",
        type = "tar.gz",
        sha256 = "2a60c7ce501c71e03a9c9c0d35b861413ae925bd979cc7a4e30d060069aaac8d",
        strip_prefix = "mime-0.3.16",
        build_file = Label("//cargo/remote:BUILD.mime-0.3.16.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__mime_guess__2_0_3",
        url = "https://crates.io/api/v1/crates/mime_guess/2.0.3/download",
        type = "tar.gz",
        sha256 = "2684d4c2e97d99848d30b324b00c8fcc7e5c897b7cbb5819b09e7c90e8baf212",
        strip_prefix = "mime_guess-2.0.3",
        build_file = Label("//cargo/remote:BUILD.mime_guess-2.0.3.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__miniz_oxide__0_4_4",
        url = "https://crates.io/api/v1/crates/miniz_oxide/0.4.4/download",
        type = "tar.gz",
        sha256 = "a92518e98c078586bc6c934028adcca4c92a53d6a958196de835170a01d84e4b",
        strip_prefix = "miniz_oxide-0.4.4",
        build_file = Label("//cargo/remote:BUILD.miniz_oxide-0.4.4.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__mio__0_7_11",
        url = "https://crates.io/api/v1/crates/mio/0.7.11/download",
        type = "tar.gz",
        sha256 = "cf80d3e903b34e0bd7282b218398aec54e082c840d9baf8339e0080a0c542956",
        strip_prefix = "mio-0.7.11",
        build_file = Label("//cargo/remote:BUILD.mio-0.7.11.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__miow__0_3_7",
        url = "https://crates.io/api/v1/crates/miow/0.3.7/download",
        type = "tar.gz",
        sha256 = "b9f1c5b025cda876f66ef43a113f91ebc9f4ccef34843000e0adf6ebbab84e21",
        strip_prefix = "miow-0.3.7",
        build_file = Label("//cargo/remote:BUILD.miow-0.3.7.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__multimap__0_8_3",
        url = "https://crates.io/api/v1/crates/multimap/0.8.3/download",
        type = "tar.gz",
        sha256 = "e5ce46fe64a9d73be07dcbe690a38ce1b293be448fd8ce1e6c1b8062c9f72c6a",
        strip_prefix = "multimap-0.8.3",
        build_file = Label("//cargo/remote:BUILD.multimap-0.8.3.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__native_tls__0_2_7",
        url = "https://crates.io/api/v1/crates/native-tls/0.2.7/download",
        type = "tar.gz",
        sha256 = "b8d96b2e1c8da3957d58100b09f102c6d9cfdfced01b7ec5a8974044bb09dbd4",
        strip_prefix = "native-tls-0.2.7",
        build_file = Label("//cargo/remote:BUILD.native-tls-0.2.7.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__new_debug_unreachable__1_0_4",
        url = "https://crates.io/api/v1/crates/new_debug_unreachable/1.0.4/download",
        type = "tar.gz",
        sha256 = "e4a24736216ec316047a1fc4252e27dabb04218aa4a3f37c6e7ddbf1f9782b54",
        strip_prefix = "new_debug_unreachable-1.0.4",
        build_file = Label("//cargo/remote:BUILD.new_debug_unreachable-1.0.4.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__nodrop__0_1_14",
        url = "https://crates.io/api/v1/crates/nodrop/0.1.14/download",
        type = "tar.gz",
        sha256 = "72ef4a56884ca558e5ddb05a1d1e7e1bfd9a68d9ed024c21704cc98872dae1bb",
        strip_prefix = "nodrop-0.1.14",
        build_file = Label("//cargo/remote:BUILD.nodrop-0.1.14.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__nom__6_1_2",
        url = "https://crates.io/api/v1/crates/nom/6.1.2/download",
        type = "tar.gz",
        sha256 = "e7413f999671bd4745a7b624bd370a569fb6bc574b23c83a3c5ed2e453f3d5e2",
        strip_prefix = "nom-6.1.2",
        build_file = Label("//cargo/remote:BUILD.nom-6.1.2.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__ntapi__0_3_6",
        url = "https://crates.io/api/v1/crates/ntapi/0.3.6/download",
        type = "tar.gz",
        sha256 = "3f6bb902e437b6d86e03cce10a7e2af662292c5dfef23b65899ea3ac9354ad44",
        strip_prefix = "ntapi-0.3.6",
        build_file = Label("//cargo/remote:BUILD.ntapi-0.3.6.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__num_format__0_4_0",
        url = "https://crates.io/api/v1/crates/num-format/0.4.0/download",
        type = "tar.gz",
        sha256 = "bafe4179722c2894288ee77a9f044f02811c86af699344c498b0840c698a2465",
        strip_prefix = "num-format-0.4.0",
        build_file = Label("//cargo/remote:BUILD.num-format-0.4.0.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__num_integer__0_1_44",
        url = "https://crates.io/api/v1/crates/num-integer/0.1.44/download",
        type = "tar.gz",
        sha256 = "d2cc698a63b549a70bc047073d2949cce27cd1c7b0a4a862d08a8031bc2801db",
        strip_prefix = "num-integer-0.1.44",
        build_file = Label("//cargo/remote:BUILD.num-integer-0.1.44.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__num_traits__0_2_14",
        url = "https://crates.io/api/v1/crates/num-traits/0.2.14/download",
        type = "tar.gz",
        sha256 = "9a64b1ec5cda2586e284722486d802acf1f7dbdc623e2bfc57e65ca1cd099290",
        strip_prefix = "num-traits-0.2.14",
        build_file = Label("//cargo/remote:BUILD.num-traits-0.2.14.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__num_cpus__1_13_0",
        url = "https://crates.io/api/v1/crates/num_cpus/1.13.0/download",
        type = "tar.gz",
        sha256 = "05499f3756671c15885fee9034446956fff3f243d6077b91e5767df161f766b3",
        strip_prefix = "num_cpus-1.13.0",
        build_file = Label("//cargo/remote:BUILD.num_cpus-1.13.0.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__num_enum__0_5_1",
        url = "https://crates.io/api/v1/crates/num_enum/0.5.1/download",
        type = "tar.gz",
        sha256 = "226b45a5c2ac4dd696ed30fa6b94b057ad909c7b7fc2e0d0808192bced894066",
        strip_prefix = "num_enum-0.5.1",
        build_file = Label("//cargo/remote:BUILD.num_enum-0.5.1.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__num_enum_derive__0_5_1",
        url = "https://crates.io/api/v1/crates/num_enum_derive/0.5.1/download",
        type = "tar.gz",
        sha256 = "1c0fd9eba1d5db0994a239e09c1be402d35622277e35468ba891aa5e3188ce7e",
        strip_prefix = "num_enum_derive-0.5.1",
        build_file = Label("//cargo/remote:BUILD.num_enum_derive-0.5.1.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__once_cell__1_7_2",
        url = "https://crates.io/api/v1/crates/once_cell/1.7.2/download",
        type = "tar.gz",
        sha256 = "af8b08b04175473088b46763e51ee54da5f9a164bc162f615b91bc179dbf15a3",
        strip_prefix = "once_cell-1.7.2",
        build_file = Label("//cargo/remote:BUILD.once_cell-1.7.2.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__openssl__0_10_34",
        url = "https://crates.io/api/v1/crates/openssl/0.10.34/download",
        type = "tar.gz",
        sha256 = "6d7830286ad6a3973c0f1d9b73738f69c76b739301d0229c4b96501695cbe4c8",
        strip_prefix = "openssl-0.10.34",
        build_file = Label("//cargo/remote:BUILD.openssl-0.10.34.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__openssl_probe__0_1_2",
        url = "https://crates.io/api/v1/crates/openssl-probe/0.1.2/download",
        type = "tar.gz",
        sha256 = "77af24da69f9d9341038eba93a073b1fdaaa1b788221b00a69bce9e762cb32de",
        strip_prefix = "openssl-probe-0.1.2",
        build_file = Label("//cargo/remote:BUILD.openssl-probe-0.1.2.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__openssl_sys__0_9_63",
        url = "https://crates.io/api/v1/crates/openssl-sys/0.9.63/download",
        type = "tar.gz",
        sha256 = "b6b0d6fb7d80f877617dfcb014e605e2b5ab2fb0afdf27935219bb6bd984cb98",
        strip_prefix = "openssl-sys-0.9.63",
        build_file = Label("//cargo/remote:BUILD.openssl-sys-0.9.63.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__ouroboros__0_9_2",
        url = "https://crates.io/api/v1/crates/ouroboros/0.9.2/download",
        type = "tar.gz",
        sha256 = "c8234affc3c31a8b744cc236fd3dc7443f57c6370cbb7d61f41f9c7dcc6c2530",
        strip_prefix = "ouroboros-0.9.2",
        build_file = Label("//cargo/remote:BUILD.ouroboros-0.9.2.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__ouroboros_macro__0_9_2",
        url = "https://crates.io/api/v1/crates/ouroboros_macro/0.9.2/download",
        type = "tar.gz",
        sha256 = "3633332cd8c0b6a865e2e0e705fad9cde25fe458cd0c693629b58a7b15e4d852",
        strip_prefix = "ouroboros_macro-0.9.2",
        build_file = Label("//cargo/remote:BUILD.ouroboros_macro-0.9.2.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__parking_lot__0_11_1",
        url = "https://crates.io/api/v1/crates/parking_lot/0.11.1/download",
        type = "tar.gz",
        sha256 = "6d7744ac029df22dca6284efe4e898991d28e3085c706c972bcd7da4a27a15eb",
        strip_prefix = "parking_lot-0.11.1",
        build_file = Label("//cargo/remote:BUILD.parking_lot-0.11.1.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__parking_lot_core__0_8_3",
        url = "https://crates.io/api/v1/crates/parking_lot_core/0.8.3/download",
        type = "tar.gz",
        sha256 = "fa7a782938e745763fe6907fc6ba86946d72f49fe7e21de074e08128a99fb018",
        strip_prefix = "parking_lot_core-0.8.3",
        build_file = Label("//cargo/remote:BUILD.parking_lot_core-0.8.3.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__paste__0_1_18",
        url = "https://crates.io/api/v1/crates/paste/0.1.18/download",
        type = "tar.gz",
        sha256 = "45ca20c77d80be666aef2b45486da86238fabe33e38306bd3118fe4af33fa880",
        strip_prefix = "paste-0.1.18",
        build_file = Label("//cargo/remote:BUILD.paste-0.1.18.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__paste_impl__0_1_18",
        url = "https://crates.io/api/v1/crates/paste-impl/0.1.18/download",
        type = "tar.gz",
        sha256 = "d95a7db200b97ef370c8e6de0088252f7e0dfff7d047a28528e47456c0fc98b6",
        strip_prefix = "paste-impl-0.1.18",
        build_file = Label("//cargo/remote:BUILD.paste-impl-0.1.18.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__percent_encoding__2_1_0",
        url = "https://crates.io/api/v1/crates/percent-encoding/2.1.0/download",
        type = "tar.gz",
        sha256 = "d4fd5641d01c8f18a23da7b6fe29298ff4b55afcccdf78973b24cf3175fee32e",
        strip_prefix = "percent-encoding-2.1.0",
        build_file = Label("//cargo/remote:BUILD.percent-encoding-2.1.0.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__petgraph__0_5_1",
        url = "https://crates.io/api/v1/crates/petgraph/0.5.1/download",
        type = "tar.gz",
        sha256 = "467d164a6de56270bd7c4d070df81d07beace25012d5103ced4e9ff08d6afdb7",
        strip_prefix = "petgraph-0.5.1",
        build_file = Label("//cargo/remote:BUILD.petgraph-0.5.1.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__phf__0_8_0",
        url = "https://crates.io/api/v1/crates/phf/0.8.0/download",
        type = "tar.gz",
        sha256 = "3dfb61232e34fcb633f43d12c58f83c1df82962dcdfa565a4e866ffc17dafe12",
        strip_prefix = "phf-0.8.0",
        build_file = Label("//cargo/remote:BUILD.phf-0.8.0.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__phf_codegen__0_8_0",
        url = "https://crates.io/api/v1/crates/phf_codegen/0.8.0/download",
        type = "tar.gz",
        sha256 = "cbffee61585b0411840d3ece935cce9cb6321f01c45477d30066498cd5e1a815",
        strip_prefix = "phf_codegen-0.8.0",
        build_file = Label("//cargo/remote:BUILD.phf_codegen-0.8.0.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__phf_generator__0_8_0",
        url = "https://crates.io/api/v1/crates/phf_generator/0.8.0/download",
        type = "tar.gz",
        sha256 = "17367f0cc86f2d25802b2c26ee58a7b23faeccf78a396094c13dced0d0182526",
        strip_prefix = "phf_generator-0.8.0",
        build_file = Label("//cargo/remote:BUILD.phf_generator-0.8.0.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__phf_macros__0_8_0",
        url = "https://crates.io/api/v1/crates/phf_macros/0.8.0/download",
        type = "tar.gz",
        sha256 = "7f6fde18ff429ffc8fe78e2bf7f8b7a5a5a6e2a8b58bc5a9ac69198bbda9189c",
        strip_prefix = "phf_macros-0.8.0",
        build_file = Label("//cargo/remote:BUILD.phf_macros-0.8.0.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__phf_shared__0_8_0",
        url = "https://crates.io/api/v1/crates/phf_shared/0.8.0/download",
        type = "tar.gz",
        sha256 = "c00cf8b9eafe68dde5e9eaa2cef8ee84a9336a47d566ec55ca16589633b65af7",
        strip_prefix = "phf_shared-0.8.0",
        build_file = Label("//cargo/remote:BUILD.phf_shared-0.8.0.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__pin_project__1_0_7",
        url = "https://crates.io/api/v1/crates/pin-project/1.0.7/download",
        type = "tar.gz",
        sha256 = "c7509cc106041c40a4518d2af7a61530e1eed0e6285296a3d8c5472806ccc4a4",
        strip_prefix = "pin-project-1.0.7",
        build_file = Label("//cargo/remote:BUILD.pin-project-1.0.7.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__pin_project_internal__1_0_7",
        url = "https://crates.io/api/v1/crates/pin-project-internal/1.0.7/download",
        type = "tar.gz",
        sha256 = "48c950132583b500556b1efd71d45b319029f2b71518d979fcc208e16b42426f",
        strip_prefix = "pin-project-internal-1.0.7",
        build_file = Label("//cargo/remote:BUILD.pin-project-internal-1.0.7.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__pin_project_lite__0_2_6",
        url = "https://crates.io/api/v1/crates/pin-project-lite/0.2.6/download",
        type = "tar.gz",
        sha256 = "dc0e1f259c92177c30a4c9d177246edd0a3568b25756a977d0632cf8fa37e905",
        strip_prefix = "pin-project-lite-0.2.6",
        build_file = Label("//cargo/remote:BUILD.pin-project-lite-0.2.6.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__pin_utils__0_1_0",
        url = "https://crates.io/api/v1/crates/pin-utils/0.1.0/download",
        type = "tar.gz",
        sha256 = "8b870d8c151b6f2fb93e84a13146138f05d02ed11c7e7c54f8826aaaf7c9f184",
        strip_prefix = "pin-utils-0.1.0",
        build_file = Label("//cargo/remote:BUILD.pin-utils-0.1.0.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__pkg_config__0_3_19",
        url = "https://crates.io/api/v1/crates/pkg-config/0.3.19/download",
        type = "tar.gz",
        sha256 = "3831453b3449ceb48b6d9c7ad7c96d5ea673e9b470a1dc578c2ce6521230884c",
        strip_prefix = "pkg-config-0.3.19",
        build_file = Label("//cargo/remote:BUILD.pkg-config-0.3.19.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__ppv_lite86__0_2_10",
        url = "https://crates.io/api/v1/crates/ppv-lite86/0.2.10/download",
        type = "tar.gz",
        sha256 = "ac74c624d6b2d21f425f752262f42188365d7b8ff1aff74c82e45136510a4857",
        strip_prefix = "ppv-lite86-0.2.10",
        build_file = Label("//cargo/remote:BUILD.ppv-lite86-0.2.10.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__precomputed_hash__0_1_1",
        url = "https://crates.io/api/v1/crates/precomputed-hash/0.1.1/download",
        type = "tar.gz",
        sha256 = "925383efa346730478fb4838dbe9137d2a47675ad789c546d150a6e1dd4ab31c",
        strip_prefix = "precomputed-hash-0.1.1",
        build_file = Label("//cargo/remote:BUILD.precomputed-hash-0.1.1.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__proc_macro_crate__0_1_5",
        url = "https://crates.io/api/v1/crates/proc-macro-crate/0.1.5/download",
        type = "tar.gz",
        sha256 = "1d6ea3c4595b96363c13943497db34af4460fb474a95c43f4446ad341b8c9785",
        strip_prefix = "proc-macro-crate-0.1.5",
        build_file = Label("//cargo/remote:BUILD.proc-macro-crate-0.1.5.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__proc_macro_error__1_0_4",
        url = "https://crates.io/api/v1/crates/proc-macro-error/1.0.4/download",
        type = "tar.gz",
        sha256 = "da25490ff9892aab3fcf7c36f08cfb902dd3e71ca0f9f9517bea02a73a5ce38c",
        strip_prefix = "proc-macro-error-1.0.4",
        build_file = Label("//cargo/remote:BUILD.proc-macro-error-1.0.4.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__proc_macro_error_attr__1_0_4",
        url = "https://crates.io/api/v1/crates/proc-macro-error-attr/1.0.4/download",
        type = "tar.gz",
        sha256 = "a1be40180e52ecc98ad80b184934baf3d0d29f979574e439af5a55274b35f869",
        strip_prefix = "proc-macro-error-attr-1.0.4",
        build_file = Label("//cargo/remote:BUILD.proc-macro-error-attr-1.0.4.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__proc_macro_hack__0_5_19",
        url = "https://crates.io/api/v1/crates/proc-macro-hack/0.5.19/download",
        type = "tar.gz",
        sha256 = "dbf0c48bc1d91375ae5c3cd81e3722dff1abcf81a30960240640d223f59fe0e5",
        strip_prefix = "proc-macro-hack-0.5.19",
        build_file = Label("//cargo/remote:BUILD.proc-macro-hack-0.5.19.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__proc_macro_nested__0_1_6",
        url = "https://crates.io/api/v1/crates/proc-macro-nested/0.1.6/download",
        type = "tar.gz",
        sha256 = "eba180dafb9038b050a4c280019bbedf9f2467b61e5d892dcad585bb57aadc5a",
        strip_prefix = "proc-macro-nested-0.1.6",
        build_file = Label("//cargo/remote:BUILD.proc-macro-nested-0.1.6.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__proc_macro2__1_0_26",
        url = "https://crates.io/api/v1/crates/proc-macro2/1.0.26/download",
        type = "tar.gz",
        sha256 = "a152013215dca273577e18d2bf00fa862b89b24169fb78c4c95aeb07992c9cec",
        strip_prefix = "proc-macro2-1.0.26",
        build_file = Label("//cargo/remote:BUILD.proc-macro2-1.0.26.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__prost__0_7_0",
        url = "https://crates.io/api/v1/crates/prost/0.7.0/download",
        type = "tar.gz",
        sha256 = "9e6984d2f1a23009bd270b8bb56d0926810a3d483f59c987d77969e9d8e840b2",
        strip_prefix = "prost-0.7.0",
        build_file = Label("//cargo/remote:BUILD.prost-0.7.0.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__prost_build__0_7_0",
        url = "https://crates.io/api/v1/crates/prost-build/0.7.0/download",
        type = "tar.gz",
        sha256 = "32d3ebd75ac2679c2af3a92246639f9fcc8a442ee420719cc4fe195b98dd5fa3",
        strip_prefix = "prost-build-0.7.0",
        build_file = Label("//cargo/remote:BUILD.prost-build-0.7.0.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__prost_derive__0_7_0",
        url = "https://crates.io/api/v1/crates/prost-derive/0.7.0/download",
        type = "tar.gz",
        sha256 = "169a15f3008ecb5160cba7d37bcd690a7601b6d30cfb87a117d45e59d52af5d4",
        strip_prefix = "prost-derive-0.7.0",
        build_file = Label("//cargo/remote:BUILD.prost-derive-0.7.0.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__prost_types__0_7_0",
        url = "https://crates.io/api/v1/crates/prost-types/0.7.0/download",
        type = "tar.gz",
        sha256 = "b518d7cdd93dab1d1122cf07fa9a60771836c668dde9d9e2a139f957f0d9f1bb",
        strip_prefix = "prost-types-0.7.0",
        build_file = Label("//cargo/remote:BUILD.prost-types-0.7.0.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__pulldown_cmark__0_8_0",
        url = "https://crates.io/api/v1/crates/pulldown-cmark/0.8.0/download",
        type = "tar.gz",
        sha256 = "ffade02495f22453cd593159ea2f59827aae7f53fa8323f756799b670881dcf8",
        strip_prefix = "pulldown-cmark-0.8.0",
        build_file = Label("//cargo/remote:BUILD.pulldown-cmark-0.8.0.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__pyo3__0_13_2",
        url = "https://crates.io/api/v1/crates/pyo3/0.13.2/download",
        type = "tar.gz",
        sha256 = "4837b8e8e18a102c23f79d1e9a110b597ea3b684c95e874eb1ad88f8683109c3",
        strip_prefix = "pyo3-0.13.2",
        build_file = Label("//cargo/remote:BUILD.pyo3-0.13.2.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__pyo3_macros__0_13_2",
        url = "https://crates.io/api/v1/crates/pyo3-macros/0.13.2/download",
        type = "tar.gz",
        sha256 = "a47f2c300ceec3e58064fd5f8f5b61230f2ffd64bde4970c81fdd0563a2db1bb",
        strip_prefix = "pyo3-macros-0.13.2",
        build_file = Label("//cargo/remote:BUILD.pyo3-macros-0.13.2.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__pyo3_macros_backend__0_13_2",
        url = "https://crates.io/api/v1/crates/pyo3-macros-backend/0.13.2/download",
        type = "tar.gz",
        sha256 = "87b097e5d84fcbe3e167f400fbedd657820a375b034c78bd852050749a575d66",
        strip_prefix = "pyo3-macros-backend-0.13.2",
        build_file = Label("//cargo/remote:BUILD.pyo3-macros-backend-0.13.2.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__quote__1_0_9",
        url = "https://crates.io/api/v1/crates/quote/1.0.9/download",
        type = "tar.gz",
        sha256 = "c3d0b9745dc2debf507c8422de05d7226cc1f0644216dfdfead988f9b1ab32a7",
        strip_prefix = "quote-1.0.9",
        build_file = Label("//cargo/remote:BUILD.quote-1.0.9.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__radium__0_5_3",
        url = "https://crates.io/api/v1/crates/radium/0.5.3/download",
        type = "tar.gz",
        sha256 = "941ba9d78d8e2f7ce474c015eea4d9c6d25b6a3327f9832ee29a4de27f91bbb8",
        strip_prefix = "radium-0.5.3",
        build_file = Label("//cargo/remote:BUILD.radium-0.5.3.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__rand__0_7_3",
        url = "https://crates.io/api/v1/crates/rand/0.7.3/download",
        type = "tar.gz",
        sha256 = "6a6b1679d49b24bbfe0c803429aa1874472f50d9b363131f0e89fc356b544d03",
        strip_prefix = "rand-0.7.3",
        build_file = Label("//cargo/remote:BUILD.rand-0.7.3.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__rand__0_8_3",
        url = "https://crates.io/api/v1/crates/rand/0.8.3/download",
        type = "tar.gz",
        sha256 = "0ef9e7e66b4468674bfcb0c81af8b7fa0bb154fa9f28eb840da5c447baeb8d7e",
        strip_prefix = "rand-0.8.3",
        build_file = Label("//cargo/remote:BUILD.rand-0.8.3.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__rand_chacha__0_2_2",
        url = "https://crates.io/api/v1/crates/rand_chacha/0.2.2/download",
        type = "tar.gz",
        sha256 = "f4c8ed856279c9737206bf725bf36935d8666ead7aa69b52be55af369d193402",
        strip_prefix = "rand_chacha-0.2.2",
        build_file = Label("//cargo/remote:BUILD.rand_chacha-0.2.2.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__rand_chacha__0_3_0",
        url = "https://crates.io/api/v1/crates/rand_chacha/0.3.0/download",
        type = "tar.gz",
        sha256 = "e12735cf05c9e10bf21534da50a147b924d555dc7a547c42e6bb2d5b6017ae0d",
        strip_prefix = "rand_chacha-0.3.0",
        build_file = Label("//cargo/remote:BUILD.rand_chacha-0.3.0.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__rand_core__0_5_1",
        url = "https://crates.io/api/v1/crates/rand_core/0.5.1/download",
        type = "tar.gz",
        sha256 = "90bde5296fc891b0cef12a6d03ddccc162ce7b2aff54160af9338f8d40df6d19",
        strip_prefix = "rand_core-0.5.1",
        build_file = Label("//cargo/remote:BUILD.rand_core-0.5.1.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__rand_core__0_6_2",
        url = "https://crates.io/api/v1/crates/rand_core/0.6.2/download",
        type = "tar.gz",
        sha256 = "34cf66eb183df1c5876e2dcf6b13d57340741e8dc255b48e40a26de954d06ae7",
        strip_prefix = "rand_core-0.6.2",
        build_file = Label("//cargo/remote:BUILD.rand_core-0.6.2.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__rand_hc__0_2_0",
        url = "https://crates.io/api/v1/crates/rand_hc/0.2.0/download",
        type = "tar.gz",
        sha256 = "ca3129af7b92a17112d59ad498c6f81eaf463253766b90396d39ea7a39d6613c",
        strip_prefix = "rand_hc-0.2.0",
        build_file = Label("//cargo/remote:BUILD.rand_hc-0.2.0.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__rand_hc__0_3_0",
        url = "https://crates.io/api/v1/crates/rand_hc/0.3.0/download",
        type = "tar.gz",
        sha256 = "3190ef7066a446f2e7f42e239d161e905420ccab01eb967c9eb27d21b2322a73",
        strip_prefix = "rand_hc-0.3.0",
        build_file = Label("//cargo/remote:BUILD.rand_hc-0.3.0.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__rand_pcg__0_2_1",
        url = "https://crates.io/api/v1/crates/rand_pcg/0.2.1/download",
        type = "tar.gz",
        sha256 = "16abd0c1b639e9eb4d7c50c0b8100b0d0f849be2349829c740fe8e6eb4816429",
        strip_prefix = "rand_pcg-0.2.1",
        build_file = Label("//cargo/remote:BUILD.rand_pcg-0.2.1.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__redox_syscall__0_2_8",
        url = "https://crates.io/api/v1/crates/redox_syscall/0.2.8/download",
        type = "tar.gz",
        sha256 = "742739e41cd49414de871ea5e549afb7e2a3ac77b589bcbebe8c82fab37147fc",
        strip_prefix = "redox_syscall-0.2.8",
        build_file = Label("//cargo/remote:BUILD.redox_syscall-0.2.8.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__redox_users__0_4_0",
        url = "https://crates.io/api/v1/crates/redox_users/0.4.0/download",
        type = "tar.gz",
        sha256 = "528532f3d801c87aec9def2add9ca802fe569e44a544afe633765267840abe64",
        strip_prefix = "redox_users-0.4.0",
        build_file = Label("//cargo/remote:BUILD.redox_users-0.4.0.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__regex__1_5_4",
        url = "https://crates.io/api/v1/crates/regex/1.5.4/download",
        type = "tar.gz",
        sha256 = "d07a8629359eb56f1e2fb1652bb04212c072a87ba68546a04065d525673ac461",
        strip_prefix = "regex-1.5.4",
        build_file = Label("//cargo/remote:BUILD.regex-1.5.4.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__regex_syntax__0_6_25",
        url = "https://crates.io/api/v1/crates/regex-syntax/0.6.25/download",
        type = "tar.gz",
        sha256 = "f497285884f3fcff424ffc933e56d7cbca511def0c9831a7f9b5f6153e3cc89b",
        strip_prefix = "regex-syntax-0.6.25",
        build_file = Label("//cargo/remote:BUILD.regex-syntax-0.6.25.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__remove_dir_all__0_5_3",
        url = "https://crates.io/api/v1/crates/remove_dir_all/0.5.3/download",
        type = "tar.gz",
        sha256 = "3acd125665422973a33ac9d3dd2df85edad0f4ae9b00dafb1a05e43a9f5ef8e7",
        strip_prefix = "remove_dir_all-0.5.3",
        build_file = Label("//cargo/remote:BUILD.remove_dir_all-0.5.3.bazel"),
    )

    maybe(
        new_git_repository,
        name = "raze__reqwest__0_11_3",
        remote = "https://github.com/ankitects/reqwest.git",
        shallow_since = "1619519742 +1000",
        commit = "7591444614de02b658ddab125efba7b2bb4e2335",
        build_file = Label("//cargo:BUILD.reqwest.native.bazel"),
        init_submodules = True,
    )

    maybe(
        http_archive,
        name = "raze__ring__0_16_20",
        url = "https://crates.io/api/v1/crates/ring/0.16.20/download",
        type = "tar.gz",
        sha256 = "3053cf52e236a3ed746dfc745aa9cacf1b791d846bdaf412f60a8d7d6e17c8fc",
        strip_prefix = "ring-0.16.20",
        build_file = Label("//cargo/remote:BUILD.ring-0.16.20.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__rusqlite__0_24_2",
        url = "https://crates.io/api/v1/crates/rusqlite/0.24.2/download",
        type = "tar.gz",
        sha256 = "d5f38ee71cbab2c827ec0ac24e76f82eca723cee92c509a65f67dee393c25112",
        strip_prefix = "rusqlite-0.24.2",
        build_file = Label("//cargo/remote:BUILD.rusqlite-0.24.2.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__rustc_hash__1_1_0",
        url = "https://crates.io/api/v1/crates/rustc-hash/1.1.0/download",
        type = "tar.gz",
        sha256 = "08d43f7aa6b08d49f382cde6a7982047c3426db949b1424bc4b7ec9ae12c6ce2",
        strip_prefix = "rustc-hash-1.1.0",
        build_file = Label("//cargo/remote:BUILD.rustc-hash-1.1.0.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__rustls__0_19_1",
        url = "https://crates.io/api/v1/crates/rustls/0.19.1/download",
        type = "tar.gz",
        sha256 = "35edb675feee39aec9c99fa5ff985081995a06d594114ae14cbe797ad7b7a6d7",
        strip_prefix = "rustls-0.19.1",
        build_file = Label("//cargo/remote:BUILD.rustls-0.19.1.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__ryu__1_0_5",
        url = "https://crates.io/api/v1/crates/ryu/1.0.5/download",
        type = "tar.gz",
        sha256 = "71d301d4193d031abdd79ff7e3dd721168a9572ef3fe51a1517aba235bd8f86e",
        strip_prefix = "ryu-1.0.5",
        build_file = Label("//cargo/remote:BUILD.ryu-1.0.5.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__schannel__0_1_19",
        url = "https://crates.io/api/v1/crates/schannel/0.1.19/download",
        type = "tar.gz",
        sha256 = "8f05ba609c234e60bee0d547fe94a4c7e9da733d1c962cf6e59efa4cd9c8bc75",
        strip_prefix = "schannel-0.1.19",
        build_file = Label("//cargo/remote:BUILD.schannel-0.1.19.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__scopeguard__1_1_0",
        url = "https://crates.io/api/v1/crates/scopeguard/1.1.0/download",
        type = "tar.gz",
        sha256 = "d29ab0c6d3fc0ee92fe66e2d99f700eab17a8d57d1c1d3b748380fb20baa78cd",
        strip_prefix = "scopeguard-1.1.0",
        build_file = Label("//cargo/remote:BUILD.scopeguard-1.1.0.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__sct__0_6_1",
        url = "https://crates.io/api/v1/crates/sct/0.6.1/download",
        type = "tar.gz",
        sha256 = "b362b83898e0e69f38515b82ee15aa80636befe47c3b6d3d89a911e78fc228ce",
        strip_prefix = "sct-0.6.1",
        build_file = Label("//cargo/remote:BUILD.sct-0.6.1.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__security_framework__2_2_0",
        url = "https://crates.io/api/v1/crates/security-framework/2.2.0/download",
        type = "tar.gz",
        sha256 = "3670b1d2fdf6084d192bc71ead7aabe6c06aa2ea3fbd9cc3ac111fa5c2b1bd84",
        strip_prefix = "security-framework-2.2.0",
        build_file = Label("//cargo/remote:BUILD.security-framework-2.2.0.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__security_framework_sys__2_2_0",
        url = "https://crates.io/api/v1/crates/security-framework-sys/2.2.0/download",
        type = "tar.gz",
        sha256 = "3676258fd3cfe2c9a0ec99ce3038798d847ce3e4bb17746373eb9f0f1ac16339",
        strip_prefix = "security-framework-sys-2.2.0",
        build_file = Label("//cargo/remote:BUILD.security-framework-sys-2.2.0.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__serde__1_0_125",
        url = "https://crates.io/api/v1/crates/serde/1.0.125/download",
        type = "tar.gz",
        sha256 = "558dc50e1a5a5fa7112ca2ce4effcb321b0300c0d4ccf0776a9f60cd89031171",
        strip_prefix = "serde-1.0.125",
        build_file = Label("//cargo/remote:BUILD.serde-1.0.125.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__serde_aux__0_6_1",
        url = "https://crates.io/api/v1/crates/serde-aux/0.6.1/download",
        type = "tar.gz",
        sha256 = "ae50f53d4b01e854319c1f5b854cd59471f054ea7e554988850d3f36ca1dc852",
        strip_prefix = "serde-aux-0.6.1",
        build_file = Label("//cargo/remote:BUILD.serde-aux-0.6.1.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__serde_derive__1_0_125",
        url = "https://crates.io/api/v1/crates/serde_derive/1.0.125/download",
        type = "tar.gz",
        sha256 = "b093b7a2bb58203b5da3056c05b4ec1fed827dcfdb37347a8841695263b3d06d",
        strip_prefix = "serde_derive-1.0.125",
        build_file = Label("//cargo/remote:BUILD.serde_derive-1.0.125.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__serde_json__1_0_64",
        url = "https://crates.io/api/v1/crates/serde_json/1.0.64/download",
        type = "tar.gz",
        sha256 = "799e97dc9fdae36a5c8b8f2cae9ce2ee9fdce2058c57a93e6099d919fd982f79",
        strip_prefix = "serde_json-1.0.64",
        build_file = Label("//cargo/remote:BUILD.serde_json-1.0.64.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__serde_repr__0_1_6",
        url = "https://crates.io/api/v1/crates/serde_repr/0.1.6/download",
        type = "tar.gz",
        sha256 = "2dc6b7951b17b051f3210b063f12cc17320e2fe30ae05b0fe2a3abb068551c76",
        strip_prefix = "serde_repr-0.1.6",
        build_file = Label("//cargo/remote:BUILD.serde_repr-0.1.6.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__serde_tuple__0_5_0",
        url = "https://crates.io/api/v1/crates/serde_tuple/0.5.0/download",
        type = "tar.gz",
        sha256 = "f4f025b91216f15a2a32aa39669329a475733590a015835d1783549a56d09427",
        strip_prefix = "serde_tuple-0.5.0",
        build_file = Label("//cargo/remote:BUILD.serde_tuple-0.5.0.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__serde_tuple_macros__0_5_0",
        url = "https://crates.io/api/v1/crates/serde_tuple_macros/0.5.0/download",
        type = "tar.gz",
        sha256 = "4076151d1a2b688e25aaf236997933c66e18b870d0369f8b248b8ab2be630d7e",
        strip_prefix = "serde_tuple_macros-0.5.0",
        build_file = Label("//cargo/remote:BUILD.serde_tuple_macros-0.5.0.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__serde_urlencoded__0_7_0",
        url = "https://crates.io/api/v1/crates/serde_urlencoded/0.7.0/download",
        type = "tar.gz",
        sha256 = "edfa57a7f8d9c1d260a549e7224100f6c43d43f9103e06dd8b4095a9b2b43ce9",
        strip_prefix = "serde_urlencoded-0.7.0",
        build_file = Label("//cargo/remote:BUILD.serde_urlencoded-0.7.0.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__sha1__0_6_0",
        url = "https://crates.io/api/v1/crates/sha1/0.6.0/download",
        type = "tar.gz",
        sha256 = "2579985fda508104f7587689507983eadd6a6e84dd35d6d115361f530916fa0d",
        strip_prefix = "sha1-0.6.0",
        build_file = Label("//cargo/remote:BUILD.sha1-0.6.0.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__siphasher__0_3_5",
        url = "https://crates.io/api/v1/crates/siphasher/0.3.5/download",
        type = "tar.gz",
        sha256 = "cbce6d4507c7e4a3962091436e56e95290cb71fa302d0d270e32130b75fbff27",
        strip_prefix = "siphasher-0.3.5",
        build_file = Label("//cargo/remote:BUILD.siphasher-0.3.5.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__slab__0_4_3",
        url = "https://crates.io/api/v1/crates/slab/0.4.3/download",
        type = "tar.gz",
        sha256 = "f173ac3d1a7e3b28003f40de0b5ce7fe2710f9b9dc3fc38664cebee46b3b6527",
        strip_prefix = "slab-0.4.3",
        build_file = Label("//cargo/remote:BUILD.slab-0.4.3.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__slog__2_7_0",
        url = "https://crates.io/api/v1/crates/slog/2.7.0/download",
        type = "tar.gz",
        sha256 = "8347046d4ebd943127157b94d63abb990fcf729dc4e9978927fdf4ac3c998d06",
        strip_prefix = "slog-2.7.0",
        build_file = Label("//cargo/remote:BUILD.slog-2.7.0.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__slog_async__2_6_0",
        url = "https://crates.io/api/v1/crates/slog-async/2.6.0/download",
        type = "tar.gz",
        sha256 = "c60813879f820c85dbc4eabf3269befe374591289019775898d56a81a804fbdc",
        strip_prefix = "slog-async-2.6.0",
        build_file = Label("//cargo/remote:BUILD.slog-async-2.6.0.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__slog_envlogger__2_2_0",
        url = "https://crates.io/api/v1/crates/slog-envlogger/2.2.0/download",
        type = "tar.gz",
        sha256 = "906a1a0bc43fed692df4b82a5e2fbfc3733db8dad8bb514ab27a4f23ad04f5c0",
        strip_prefix = "slog-envlogger-2.2.0",
        build_file = Label("//cargo/remote:BUILD.slog-envlogger-2.2.0.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__slog_scope__4_4_0",
        url = "https://crates.io/api/v1/crates/slog-scope/4.4.0/download",
        type = "tar.gz",
        sha256 = "2f95a4b4c3274cd2869549da82b57ccc930859bdbf5bcea0424bc5f140b3c786",
        strip_prefix = "slog-scope-4.4.0",
        build_file = Label("//cargo/remote:BUILD.slog-scope-4.4.0.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__slog_stdlog__4_1_0",
        url = "https://crates.io/api/v1/crates/slog-stdlog/4.1.0/download",
        type = "tar.gz",
        sha256 = "8228ab7302adbf4fcb37e66f3cda78003feb521e7fd9e3847ec117a7784d0f5a",
        strip_prefix = "slog-stdlog-4.1.0",
        build_file = Label("//cargo/remote:BUILD.slog-stdlog-4.1.0.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__slog_term__2_6_0",
        url = "https://crates.io/api/v1/crates/slog-term/2.6.0/download",
        type = "tar.gz",
        sha256 = "bab1d807cf71129b05ce36914e1dbb6fbfbdecaf686301cb457f4fa967f9f5b6",
        strip_prefix = "slog-term-2.6.0",
        build_file = Label("//cargo/remote:BUILD.slog-term-2.6.0.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__smallvec__1_6_1",
        url = "https://crates.io/api/v1/crates/smallvec/1.6.1/download",
        type = "tar.gz",
        sha256 = "fe0f37c9e8f3c5a4a66ad655a93c74daac4ad00c441533bf5c6e7990bb42604e",
        strip_prefix = "smallvec-1.6.1",
        build_file = Label("//cargo/remote:BUILD.smallvec-1.6.1.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__socket2__0_4_0",
        url = "https://crates.io/api/v1/crates/socket2/0.4.0/download",
        type = "tar.gz",
        sha256 = "9e3dfc207c526015c632472a77be09cf1b6e46866581aecae5cc38fb4235dea2",
        strip_prefix = "socket2-0.4.0",
        build_file = Label("//cargo/remote:BUILD.socket2-0.4.0.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__spin__0_5_2",
        url = "https://crates.io/api/v1/crates/spin/0.5.2/download",
        type = "tar.gz",
        sha256 = "6e63cff320ae2c57904679ba7cb63280a3dc4613885beafb148ee7bf9aa9042d",
        strip_prefix = "spin-0.5.2",
        build_file = Label("//cargo/remote:BUILD.spin-0.5.2.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__stable_deref_trait__1_2_0",
        url = "https://crates.io/api/v1/crates/stable_deref_trait/1.2.0/download",
        type = "tar.gz",
        sha256 = "a8f112729512f8e442d81f95a8a7ddf2b7c6b8a1a6f509a95864142b30cab2d3",
        strip_prefix = "stable_deref_trait-1.2.0",
        build_file = Label("//cargo/remote:BUILD.stable_deref_trait-1.2.0.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__static_assertions__1_1_0",
        url = "https://crates.io/api/v1/crates/static_assertions/1.1.0/download",
        type = "tar.gz",
        sha256 = "a2eb9349b6444b326872e140eb1cf5e7c522154d69e7a0ffb0fb81c06b37543f",
        strip_prefix = "static_assertions-1.1.0",
        build_file = Label("//cargo/remote:BUILD.static_assertions-1.1.0.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__string_cache__0_8_1",
        url = "https://crates.io/api/v1/crates/string_cache/0.8.1/download",
        type = "tar.gz",
        sha256 = "8ddb1139b5353f96e429e1a5e19fbaf663bddedaa06d1dbd49f82e352601209a",
        strip_prefix = "string_cache-0.8.1",
        build_file = Label("//cargo/remote:BUILD.string_cache-0.8.1.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__string_cache_codegen__0_5_1",
        url = "https://crates.io/api/v1/crates/string_cache_codegen/0.5.1/download",
        type = "tar.gz",
        sha256 = "f24c8e5e19d22a726626f1a5e16fe15b132dcf21d10177fa5a45ce7962996b97",
        strip_prefix = "string_cache_codegen-0.5.1",
        build_file = Label("//cargo/remote:BUILD.string_cache_codegen-0.5.1.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__strum__0_20_0",
        url = "https://crates.io/api/v1/crates/strum/0.20.0/download",
        type = "tar.gz",
        sha256 = "7318c509b5ba57f18533982607f24070a55d353e90d4cae30c467cdb2ad5ac5c",
        strip_prefix = "strum-0.20.0",
        build_file = Label("//cargo/remote:BUILD.strum-0.20.0.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__strum_macros__0_20_1",
        url = "https://crates.io/api/v1/crates/strum_macros/0.20.1/download",
        type = "tar.gz",
        sha256 = "ee8bc6b87a5112aeeab1f4a9f7ab634fe6cbefc4850006df31267f4cfb9e3149",
        strip_prefix = "strum_macros-0.20.1",
        build_file = Label("//cargo/remote:BUILD.strum_macros-0.20.1.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__subtle__2_4_0",
        url = "https://crates.io/api/v1/crates/subtle/2.4.0/download",
        type = "tar.gz",
        sha256 = "1e81da0851ada1f3e9d4312c704aa4f8806f0f9d69faaf8df2f3464b4a9437c2",
        strip_prefix = "subtle-2.4.0",
        build_file = Label("//cargo/remote:BUILD.subtle-2.4.0.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__syn__1_0_72",
        url = "https://crates.io/api/v1/crates/syn/1.0.72/download",
        type = "tar.gz",
        sha256 = "a1e8cdbefb79a9a5a65e0db8b47b723ee907b7c7f8496c76a1770b5c310bab82",
        strip_prefix = "syn-1.0.72",
        build_file = Label("//cargo/remote:BUILD.syn-1.0.72.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__take_mut__0_2_2",
        url = "https://crates.io/api/v1/crates/take_mut/0.2.2/download",
        type = "tar.gz",
        sha256 = "f764005d11ee5f36500a149ace24e00e3da98b0158b3e2d53a7495660d3f4d60",
        strip_prefix = "take_mut-0.2.2",
        build_file = Label("//cargo/remote:BUILD.take_mut-0.2.2.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__tap__1_0_1",
        url = "https://crates.io/api/v1/crates/tap/1.0.1/download",
        type = "tar.gz",
        sha256 = "55937e1799185b12863d447f42597ed69d9928686b8d88a1df17376a097d8369",
        strip_prefix = "tap-1.0.1",
        build_file = Label("//cargo/remote:BUILD.tap-1.0.1.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__tempfile__3_2_0",
        url = "https://crates.io/api/v1/crates/tempfile/3.2.0/download",
        type = "tar.gz",
        sha256 = "dac1c663cfc93810f88aed9b8941d48cabf856a1b111c29a40439018d870eb22",
        strip_prefix = "tempfile-3.2.0",
        build_file = Label("//cargo/remote:BUILD.tempfile-3.2.0.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__tendril__0_4_2",
        url = "https://crates.io/api/v1/crates/tendril/0.4.2/download",
        type = "tar.gz",
        sha256 = "a9ef557cb397a4f0a5a3a628f06515f78563f2209e64d47055d9dc6052bf5e33",
        strip_prefix = "tendril-0.4.2",
        build_file = Label("//cargo/remote:BUILD.tendril-0.4.2.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__term__0_6_1",
        url = "https://crates.io/api/v1/crates/term/0.6.1/download",
        type = "tar.gz",
        sha256 = "c0863a3345e70f61d613eab32ee046ccd1bcc5f9105fe402c61fcd0c13eeb8b5",
        strip_prefix = "term-0.6.1",
        build_file = Label("//cargo/remote:BUILD.term-0.6.1.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__termcolor__1_1_2",
        url = "https://crates.io/api/v1/crates/termcolor/1.1.2/download",
        type = "tar.gz",
        sha256 = "2dfed899f0eb03f32ee8c6a0aabdb8a7949659e3466561fc0adf54e26d88c5f4",
        strip_prefix = "termcolor-1.1.2",
        build_file = Label("//cargo/remote:BUILD.termcolor-1.1.2.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__thiserror__1_0_24",
        url = "https://crates.io/api/v1/crates/thiserror/1.0.24/download",
        type = "tar.gz",
        sha256 = "e0f4a65597094d4483ddaed134f409b2cb7c1beccf25201a9f73c719254fa98e",
        strip_prefix = "thiserror-1.0.24",
        build_file = Label("//cargo/remote:BUILD.thiserror-1.0.24.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__thiserror_impl__1_0_24",
        url = "https://crates.io/api/v1/crates/thiserror-impl/1.0.24/download",
        type = "tar.gz",
        sha256 = "7765189610d8241a44529806d6fd1f2e0a08734313a35d5b3a556f92b381f3c0",
        strip_prefix = "thiserror-impl-1.0.24",
        build_file = Label("//cargo/remote:BUILD.thiserror-impl-1.0.24.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__thread_local__1_1_3",
        url = "https://crates.io/api/v1/crates/thread_local/1.1.3/download",
        type = "tar.gz",
        sha256 = "8018d24e04c95ac8790716a5987d0fec4f8b27249ffa0f7d33f1369bdfb88cbd",
        strip_prefix = "thread_local-1.1.3",
        build_file = Label("//cargo/remote:BUILD.thread_local-1.1.3.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__time__0_1_43",
        url = "https://crates.io/api/v1/crates/time/0.1.43/download",
        type = "tar.gz",
        sha256 = "ca8a50ef2360fbd1eeb0ecd46795a87a19024eb4b53c5dc916ca1fd95fe62438",
        strip_prefix = "time-0.1.43",
        build_file = Label("//cargo/remote:BUILD.time-0.1.43.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__tinystr__0_3_4",
        url = "https://crates.io/api/v1/crates/tinystr/0.3.4/download",
        type = "tar.gz",
        sha256 = "29738eedb4388d9ea620eeab9384884fc3f06f586a2eddb56bedc5885126c7c1",
        strip_prefix = "tinystr-0.3.4",
        build_file = Label("//cargo/remote:BUILD.tinystr-0.3.4.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__tinyvec__1_2_0",
        url = "https://crates.io/api/v1/crates/tinyvec/1.2.0/download",
        type = "tar.gz",
        sha256 = "5b5220f05bb7de7f3f53c7c065e1199b3172696fe2db9f9c4d8ad9b4ee74c342",
        strip_prefix = "tinyvec-1.2.0",
        build_file = Label("//cargo/remote:BUILD.tinyvec-1.2.0.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__tinyvec_macros__0_1_0",
        url = "https://crates.io/api/v1/crates/tinyvec_macros/0.1.0/download",
        type = "tar.gz",
        sha256 = "cda74da7e1a664f795bb1f8a87ec406fb89a02522cf6e50620d016add6dbbf5c",
        strip_prefix = "tinyvec_macros-0.1.0",
        build_file = Label("//cargo/remote:BUILD.tinyvec_macros-0.1.0.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__tokio__1_5_0",
        url = "https://crates.io/api/v1/crates/tokio/1.5.0/download",
        type = "tar.gz",
        sha256 = "83f0c8e7c0addab50b663055baf787d0af7f413a46e6e7fb9559a4e4db7137a5",
        strip_prefix = "tokio-1.5.0",
        build_file = Label("//cargo/remote:BUILD.tokio-1.5.0.bazel"),
    )

    maybe(
        new_git_repository,
        name = "raze__tokio_io_timeout__1_1_1",
        remote = "https://github.com/ankitects/tokio-io-timeout.git",
        shallow_since = "1619517354 +1000",
        commit = "1ee0892217e9a76bba4bb369ec5fab8854935a3c",
        build_file = Label("//cargo/remote:BUILD.tokio-io-timeout-1.1.1.bazel"),
        init_submodules = True,
    )

    maybe(
        http_archive,
        name = "raze__tokio_native_tls__0_3_0",
        url = "https://crates.io/api/v1/crates/tokio-native-tls/0.3.0/download",
        type = "tar.gz",
        sha256 = "f7d995660bd2b7f8c1568414c1126076c13fbb725c40112dc0120b78eb9b717b",
        strip_prefix = "tokio-native-tls-0.3.0",
        build_file = Label("//cargo/remote:BUILD.tokio-native-tls-0.3.0.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__tokio_rustls__0_22_0",
        url = "https://crates.io/api/v1/crates/tokio-rustls/0.22.0/download",
        type = "tar.gz",
        sha256 = "bc6844de72e57df1980054b38be3a9f4702aba4858be64dd700181a8a6d0e1b6",
        strip_prefix = "tokio-rustls-0.22.0",
        build_file = Label("//cargo/remote:BUILD.tokio-rustls-0.22.0.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__tokio_socks__0_5_1",
        url = "https://crates.io/api/v1/crates/tokio-socks/0.5.1/download",
        type = "tar.gz",
        sha256 = "51165dfa029d2a65969413a6cc96f354b86b464498702f174a4efa13608fd8c0",
        strip_prefix = "tokio-socks-0.5.1",
        build_file = Label("//cargo/remote:BUILD.tokio-socks-0.5.1.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__tokio_util__0_6_6",
        url = "https://crates.io/api/v1/crates/tokio-util/0.6.6/download",
        type = "tar.gz",
        sha256 = "940a12c99365c31ea8dd9ba04ec1be183ffe4920102bb7122c2f515437601e8e",
        strip_prefix = "tokio-util-0.6.6",
        build_file = Label("//cargo/remote:BUILD.tokio-util-0.6.6.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__toml__0_5_8",
        url = "https://crates.io/api/v1/crates/toml/0.5.8/download",
        type = "tar.gz",
        sha256 = "a31142970826733df8241ef35dc040ef98c679ab14d7c3e54d827099b3acecaa",
        strip_prefix = "toml-0.5.8",
        build_file = Label("//cargo/remote:BUILD.toml-0.5.8.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__tower_service__0_3_1",
        url = "https://crates.io/api/v1/crates/tower-service/0.3.1/download",
        type = "tar.gz",
        sha256 = "360dfd1d6d30e05fda32ace2c8c70e9c0a9da713275777f5a4dbb8a1893930c6",
        strip_prefix = "tower-service-0.3.1",
        build_file = Label("//cargo/remote:BUILD.tower-service-0.3.1.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__tracing__0_1_26",
        url = "https://crates.io/api/v1/crates/tracing/0.1.26/download",
        type = "tar.gz",
        sha256 = "09adeb8c97449311ccd28a427f96fb563e7fd31aabf994189879d9da2394b89d",
        strip_prefix = "tracing-0.1.26",
        build_file = Label("//cargo/remote:BUILD.tracing-0.1.26.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__tracing_core__0_1_18",
        url = "https://crates.io/api/v1/crates/tracing-core/0.1.18/download",
        type = "tar.gz",
        sha256 = "a9ff14f98b1a4b289c6248a023c1c2fa1491062964e9fed67ab29c4e4da4a052",
        strip_prefix = "tracing-core-0.1.18",
        build_file = Label("//cargo/remote:BUILD.tracing-core-0.1.18.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__try_lock__0_2_3",
        url = "https://crates.io/api/v1/crates/try-lock/0.2.3/download",
        type = "tar.gz",
        sha256 = "59547bce71d9c38b83d9c0e92b6066c4253371f15005def0c30d9657f50c7642",
        strip_prefix = "try-lock-0.2.3",
        build_file = Label("//cargo/remote:BUILD.try-lock-0.2.3.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__type_map__0_4_0",
        url = "https://crates.io/api/v1/crates/type-map/0.4.0/download",
        type = "tar.gz",
        sha256 = "b6d3364c5e96cb2ad1603037ab253ddd34d7fb72a58bdddf4b7350760fc69a46",
        strip_prefix = "type-map-0.4.0",
        build_file = Label("//cargo/remote:BUILD.type-map-0.4.0.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__typenum__1_13_0",
        url = "https://crates.io/api/v1/crates/typenum/1.13.0/download",
        type = "tar.gz",
        sha256 = "879f6906492a7cd215bfa4cf595b600146ccfac0c79bcbd1f3000162af5e8b06",
        strip_prefix = "typenum-1.13.0",
        build_file = Label("//cargo/remote:BUILD.typenum-1.13.0.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__unic_langid__0_9_0",
        url = "https://crates.io/api/v1/crates/unic-langid/0.9.0/download",
        type = "tar.gz",
        sha256 = "73328fcd730a030bdb19ddf23e192187a6b01cd98be6d3140622a89129459ce5",
        strip_prefix = "unic-langid-0.9.0",
        build_file = Label("//cargo/remote:BUILD.unic-langid-0.9.0.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__unic_langid_impl__0_9_0",
        url = "https://crates.io/api/v1/crates/unic-langid-impl/0.9.0/download",
        type = "tar.gz",
        sha256 = "1a4a8eeaf0494862c1404c95ec2f4c33a2acff5076f64314b465e3ddae1b934d",
        strip_prefix = "unic-langid-impl-0.9.0",
        build_file = Label("//cargo/remote:BUILD.unic-langid-impl-0.9.0.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__unic_langid_macros__0_9_0",
        url = "https://crates.io/api/v1/crates/unic-langid-macros/0.9.0/download",
        type = "tar.gz",
        sha256 = "18f980d6d87e8805f2836d64b4138cc95aa7986fa63b1f51f67d5fbff64dd6e5",
        strip_prefix = "unic-langid-macros-0.9.0",
        build_file = Label("//cargo/remote:BUILD.unic-langid-macros-0.9.0.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__unic_langid_macros_impl__0_9_0",
        url = "https://crates.io/api/v1/crates/unic-langid-macros-impl/0.9.0/download",
        type = "tar.gz",
        sha256 = "29396ffd97e27574c3e01368b1a64267d3064969e4848e2e130ff668be9daa9f",
        strip_prefix = "unic-langid-macros-impl-0.9.0",
        build_file = Label("//cargo/remote:BUILD.unic-langid-macros-impl-0.9.0.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__unicase__2_6_0",
        url = "https://crates.io/api/v1/crates/unicase/2.6.0/download",
        type = "tar.gz",
        sha256 = "50f37be617794602aabbeee0be4f259dc1778fabe05e2d67ee8f79326d5cb4f6",
        strip_prefix = "unicase-2.6.0",
        build_file = Label("//cargo/remote:BUILD.unicase-2.6.0.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__unicode_bidi__0_3_5",
        url = "https://crates.io/api/v1/crates/unicode-bidi/0.3.5/download",
        type = "tar.gz",
        sha256 = "eeb8be209bb1c96b7c177c7420d26e04eccacb0eeae6b980e35fcb74678107e0",
        strip_prefix = "unicode-bidi-0.3.5",
        build_file = Label("//cargo/remote:BUILD.unicode-bidi-0.3.5.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__unicode_normalization__0_1_17",
        url = "https://crates.io/api/v1/crates/unicode-normalization/0.1.17/download",
        type = "tar.gz",
        sha256 = "07fbfce1c8a97d547e8b5334978438d9d6ec8c20e38f56d4a4374d181493eaef",
        strip_prefix = "unicode-normalization-0.1.17",
        build_file = Label("//cargo/remote:BUILD.unicode-normalization-0.1.17.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__unicode_segmentation__1_7_1",
        url = "https://crates.io/api/v1/crates/unicode-segmentation/1.7.1/download",
        type = "tar.gz",
        sha256 = "bb0d2e7be6ae3a5fa87eed5fb451aff96f2573d2694942e40543ae0bbe19c796",
        strip_prefix = "unicode-segmentation-1.7.1",
        build_file = Label("//cargo/remote:BUILD.unicode-segmentation-1.7.1.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__unicode_width__0_1_8",
        url = "https://crates.io/api/v1/crates/unicode-width/0.1.8/download",
        type = "tar.gz",
        sha256 = "9337591893a19b88d8d87f2cec1e73fad5cdfd10e5a6f349f498ad6ea2ffb1e3",
        strip_prefix = "unicode-width-0.1.8",
        build_file = Label("//cargo/remote:BUILD.unicode-width-0.1.8.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__unicode_xid__0_2_2",
        url = "https://crates.io/api/v1/crates/unicode-xid/0.2.2/download",
        type = "tar.gz",
        sha256 = "8ccb82d61f80a663efe1f787a51b16b5a51e3314d6ac365b08639f52387b33f3",
        strip_prefix = "unicode-xid-0.2.2",
        build_file = Label("//cargo/remote:BUILD.unicode-xid-0.2.2.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__unindent__0_1_7",
        url = "https://crates.io/api/v1/crates/unindent/0.1.7/download",
        type = "tar.gz",
        sha256 = "f14ee04d9415b52b3aeab06258a3f07093182b88ba0f9b8d203f211a7a7d41c7",
        strip_prefix = "unindent-0.1.7",
        build_file = Label("//cargo/remote:BUILD.unindent-0.1.7.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__untrusted__0_7_1",
        url = "https://crates.io/api/v1/crates/untrusted/0.7.1/download",
        type = "tar.gz",
        sha256 = "a156c684c91ea7d62626509bce3cb4e1d9ed5c4d978f7b4352658f96a4c26b4a",
        strip_prefix = "untrusted-0.7.1",
        build_file = Label("//cargo/remote:BUILD.untrusted-0.7.1.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__url__2_2_1",
        url = "https://crates.io/api/v1/crates/url/2.2.1/download",
        type = "tar.gz",
        sha256 = "9ccd964113622c8e9322cfac19eb1004a07e636c545f325da085d5cdde6f1f8b",
        strip_prefix = "url-2.2.1",
        build_file = Label("//cargo/remote:BUILD.url-2.2.1.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__utf_8__0_7_6",
        url = "https://crates.io/api/v1/crates/utf-8/0.7.6/download",
        type = "tar.gz",
        sha256 = "09cc8ee72d2a9becf2f2febe0205bbed8fc6615b7cb429ad062dc7b7ddd036a9",
        strip_prefix = "utf-8-0.7.6",
        build_file = Label("//cargo/remote:BUILD.utf-8-0.7.6.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__utime__0_3_1",
        url = "https://crates.io/api/v1/crates/utime/0.3.1/download",
        type = "tar.gz",
        sha256 = "91baa0c65eabd12fcbdac8cc35ff16159cab95cae96d0222d6d0271db6193cef",
        strip_prefix = "utime-0.3.1",
        build_file = Label("//cargo/remote:BUILD.utime-0.3.1.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__vcpkg__0_2_12",
        url = "https://crates.io/api/v1/crates/vcpkg/0.2.12/download",
        type = "tar.gz",
        sha256 = "cbdbff6266a24120518560b5dc983096efb98462e51d0d68169895b237be3e5d",
        strip_prefix = "vcpkg-0.2.12",
        build_file = Label("//cargo/remote:BUILD.vcpkg-0.2.12.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__version_check__0_9_3",
        url = "https://crates.io/api/v1/crates/version_check/0.9.3/download",
        type = "tar.gz",
        sha256 = "5fecdca9a5291cc2b8dcf7dc02453fee791a280f3743cb0905f8822ae463b3fe",
        strip_prefix = "version_check-0.9.3",
        build_file = Label("//cargo/remote:BUILD.version_check-0.9.3.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__want__0_3_0",
        url = "https://crates.io/api/v1/crates/want/0.3.0/download",
        type = "tar.gz",
        sha256 = "1ce8a968cb1cd110d136ff8b819a556d6fb6d919363c61534f6860c7eb172ba0",
        strip_prefix = "want-0.3.0",
        build_file = Label("//cargo/remote:BUILD.want-0.3.0.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__wasi__0_10_2_wasi_snapshot_preview1",
        url = "https://crates.io/api/v1/crates/wasi/0.10.2+wasi-snapshot-preview1/download",
        type = "tar.gz",
        sha256 = "fd6fbd9a79829dd1ad0cc20627bf1ed606756a7f77edff7b66b7064f9cb327c6",
        strip_prefix = "wasi-0.10.2+wasi-snapshot-preview1",
        build_file = Label("//cargo/remote:BUILD.wasi-0.10.2+wasi-snapshot-preview1.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__wasi__0_9_0_wasi_snapshot_preview1",
        url = "https://crates.io/api/v1/crates/wasi/0.9.0+wasi-snapshot-preview1/download",
        type = "tar.gz",
        sha256 = "cccddf32554fecc6acb585f82a32a72e28b48f8c4c1883ddfeeeaa96f7d8e519",
        strip_prefix = "wasi-0.9.0+wasi-snapshot-preview1",
        build_file = Label("//cargo/remote:BUILD.wasi-0.9.0+wasi-snapshot-preview1.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__wasm_bindgen__0_2_73",
        url = "https://crates.io/api/v1/crates/wasm-bindgen/0.2.73/download",
        type = "tar.gz",
        sha256 = "83240549659d187488f91f33c0f8547cbfef0b2088bc470c116d1d260ef623d9",
        strip_prefix = "wasm-bindgen-0.2.73",
        build_file = Label("//cargo/remote:BUILD.wasm-bindgen-0.2.73.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__wasm_bindgen_backend__0_2_73",
        url = "https://crates.io/api/v1/crates/wasm-bindgen-backend/0.2.73/download",
        type = "tar.gz",
        sha256 = "ae70622411ca953215ca6d06d3ebeb1e915f0f6613e3b495122878d7ebec7dae",
        strip_prefix = "wasm-bindgen-backend-0.2.73",
        build_file = Label("//cargo/remote:BUILD.wasm-bindgen-backend-0.2.73.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__wasm_bindgen_futures__0_4_23",
        url = "https://crates.io/api/v1/crates/wasm-bindgen-futures/0.4.23/download",
        type = "tar.gz",
        sha256 = "81b8b767af23de6ac18bf2168b690bed2902743ddf0fb39252e36f9e2bfc63ea",
        strip_prefix = "wasm-bindgen-futures-0.4.23",
        build_file = Label("//cargo/remote:BUILD.wasm-bindgen-futures-0.4.23.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__wasm_bindgen_macro__0_2_73",
        url = "https://crates.io/api/v1/crates/wasm-bindgen-macro/0.2.73/download",
        type = "tar.gz",
        sha256 = "3e734d91443f177bfdb41969de821e15c516931c3c3db3d318fa1b68975d0f6f",
        strip_prefix = "wasm-bindgen-macro-0.2.73",
        build_file = Label("//cargo/remote:BUILD.wasm-bindgen-macro-0.2.73.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__wasm_bindgen_macro_support__0_2_73",
        url = "https://crates.io/api/v1/crates/wasm-bindgen-macro-support/0.2.73/download",
        type = "tar.gz",
        sha256 = "d53739ff08c8a68b0fdbcd54c372b8ab800b1449ab3c9d706503bc7dd1621b2c",
        strip_prefix = "wasm-bindgen-macro-support-0.2.73",
        build_file = Label("//cargo/remote:BUILD.wasm-bindgen-macro-support-0.2.73.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__wasm_bindgen_shared__0_2_73",
        url = "https://crates.io/api/v1/crates/wasm-bindgen-shared/0.2.73/download",
        type = "tar.gz",
        sha256 = "d9a543ae66aa233d14bb765ed9af4a33e81b8b58d1584cf1b47ff8cd0b9e4489",
        strip_prefix = "wasm-bindgen-shared-0.2.73",
        build_file = Label("//cargo/remote:BUILD.wasm-bindgen-shared-0.2.73.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__web_sys__0_3_50",
        url = "https://crates.io/api/v1/crates/web-sys/0.3.50/download",
        type = "tar.gz",
        sha256 = "a905d57e488fec8861446d3393670fb50d27a262344013181c2cdf9fff5481be",
        strip_prefix = "web-sys-0.3.50",
        build_file = Label("//cargo/remote:BUILD.web-sys-0.3.50.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__webpki__0_21_4",
        url = "https://crates.io/api/v1/crates/webpki/0.21.4/download",
        type = "tar.gz",
        sha256 = "b8e38c0608262c46d4a56202ebabdeb094cef7e560ca7a226c6bf055188aa4ea",
        strip_prefix = "webpki-0.21.4",
        build_file = Label("//cargo/remote:BUILD.webpki-0.21.4.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__webpki_roots__0_21_1",
        url = "https://crates.io/api/v1/crates/webpki-roots/0.21.1/download",
        type = "tar.gz",
        sha256 = "aabe153544e473b775453675851ecc86863d2a81d786d741f6b76778f2a48940",
        strip_prefix = "webpki-roots-0.21.1",
        build_file = Label("//cargo/remote:BUILD.webpki-roots-0.21.1.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__which__4_1_0",
        url = "https://crates.io/api/v1/crates/which/4.1.0/download",
        type = "tar.gz",
        sha256 = "b55551e42cbdf2ce2bedd2203d0cc08dba002c27510f86dab6d0ce304cba3dfe",
        strip_prefix = "which-4.1.0",
        build_file = Label("//cargo/remote:BUILD.which-4.1.0.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__winapi__0_3_9",
        url = "https://crates.io/api/v1/crates/winapi/0.3.9/download",
        type = "tar.gz",
        sha256 = "5c839a674fcd7a98952e593242ea400abe93992746761e38641405d28b00f419",
        strip_prefix = "winapi-0.3.9",
        build_file = Label("//cargo/remote:BUILD.winapi-0.3.9.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__winapi_i686_pc_windows_gnu__0_4_0",
        url = "https://crates.io/api/v1/crates/winapi-i686-pc-windows-gnu/0.4.0/download",
        type = "tar.gz",
        sha256 = "ac3b87c63620426dd9b991e5ce0329eff545bccbbb34f3be09ff6fb6ab51b7b6",
        strip_prefix = "winapi-i686-pc-windows-gnu-0.4.0",
        build_file = Label("//cargo/remote:BUILD.winapi-i686-pc-windows-gnu-0.4.0.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__winapi_util__0_1_5",
        url = "https://crates.io/api/v1/crates/winapi-util/0.1.5/download",
        type = "tar.gz",
        sha256 = "70ec6ce85bb158151cae5e5c87f95a8e97d2c0c4b001223f33a334e3ce5de178",
        strip_prefix = "winapi-util-0.1.5",
        build_file = Label("//cargo/remote:BUILD.winapi-util-0.1.5.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__winapi_x86_64_pc_windows_gnu__0_4_0",
        url = "https://crates.io/api/v1/crates/winapi-x86_64-pc-windows-gnu/0.4.0/download",
        type = "tar.gz",
        sha256 = "712e227841d057c1ee1cd2fb22fa7e5a5461ae8e48fa2ca79ec42cfc1931183f",
        strip_prefix = "winapi-x86_64-pc-windows-gnu-0.4.0",
        build_file = Label("//cargo/remote:BUILD.winapi-x86_64-pc-windows-gnu-0.4.0.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__winreg__0_7_0",
        url = "https://crates.io/api/v1/crates/winreg/0.7.0/download",
        type = "tar.gz",
        sha256 = "0120db82e8a1e0b9fb3345a539c478767c0048d842860994d96113d5b667bd69",
        strip_prefix = "winreg-0.7.0",
        build_file = Label("//cargo/remote:BUILD.winreg-0.7.0.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__wyz__0_2_0",
        url = "https://crates.io/api/v1/crates/wyz/0.2.0/download",
        type = "tar.gz",
        sha256 = "85e60b0d1b5f99db2556934e21937020776a5d31520bf169e851ac44e6420214",
        strip_prefix = "wyz-0.2.0",
        build_file = Label("//cargo/remote:BUILD.wyz-0.2.0.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__xml5ever__0_16_1",
        url = "https://crates.io/api/v1/crates/xml5ever/0.16.1/download",
        type = "tar.gz",
        sha256 = "0b1b52e6e8614d4a58b8e70cf51ec0cc21b256ad8206708bcff8139b5bbd6a59",
        strip_prefix = "xml5ever-0.16.1",
        build_file = Label("//cargo/remote:BUILD.xml5ever-0.16.1.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__zip__0_5_12",
        url = "https://crates.io/api/v1/crates/zip/0.5.12/download",
        type = "tar.gz",
        sha256 = "9c83dc9b784d252127720168abd71ea82bf8c3d96b17dc565b5e2a02854f2b27",
        strip_prefix = "zip-0.5.12",
        build_file = Label("//cargo/remote:BUILD.zip-0.5.12.bazel"),
    )
    
    maybe(
        new_git_repository,
        name = "reqwest_rustls",
        remote = "https://github.com/ankitects/reqwest.git",
        shallow_since = "1619519742 +1000",
        commit = "7591444614de02b658ddab125efba7b2bb4e2335",
        build_file = Label("//cargo:BUILD.reqwest.rustls.bazel"),
        init_submodules = True,
    )
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
        name = "raze__adler__1_0_2",
        url = "https://crates.io/api/v1/crates/adler/1.0.2/download",
        type = "tar.gz",
        sha256 = "f26201604c87b1e01bd3d98f8d5d9a8fcbb815e8cedb41ffccbeb4bf593a35fe",
        strip_prefix = "adler-1.0.2",
        build_file = Label("//cargo/remote:BUILD.adler-1.0.2.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__ahash__0_7_6",
        url = "https://crates.io/api/v1/crates/ahash/0.7.6/download",
        type = "tar.gz",
        sha256 = "fcb51a0695d8f838b1ee009b3fbf66bda078cd64590202a864a8f3e8c4315c47",
        strip_prefix = "ahash-0.7.6",
        build_file = Label("//cargo/remote:BUILD.ahash-0.7.6.bazel"),
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
        name = "raze__ammonia__3_1_2",
        url = "https://crates.io/api/v1/crates/ammonia/3.1.2/download",
        type = "tar.gz",
        sha256 = "2e445c26125ff80316eaea16e812d717b147b82a68682bd4730f74d4845c8b35",
        strip_prefix = "ammonia-3.1.2",
        build_file = Label("//cargo/remote:BUILD.ammonia-3.1.2.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__anyhow__1_0_45",
        url = "https://crates.io/api/v1/crates/anyhow/1.0.45/download",
        type = "tar.gz",
        sha256 = "ee10e43ae4a853c0a3591d4e2ada1719e553be18199d9da9d4a83f5927c2f5c7",
        strip_prefix = "anyhow-1.0.45",
        build_file = Label("//cargo/remote:BUILD.anyhow-1.0.45.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__arc_swap__1_5_0",
        url = "https://crates.io/api/v1/crates/arc-swap/1.5.0/download",
        type = "tar.gz",
        sha256 = "c5d78ce20460b82d3fa150275ed9d55e21064fc7951177baacf86a145c4a4b1f",
        strip_prefix = "arc-swap-1.5.0",
        build_file = Label("//cargo/remote:BUILD.arc-swap-1.5.0.bazel"),
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
        name = "raze__arrayvec__0_7_2",
        url = "https://crates.io/api/v1/crates/arrayvec/0.7.2/download",
        type = "tar.gz",
        sha256 = "8da52d66c7071e2e3fa2a1e5c6d088fec47b593032b254f5e980de8ea54454d6",
        strip_prefix = "arrayvec-0.7.2",
        build_file = Label("//cargo/remote:BUILD.arrayvec-0.7.2.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__async_trait__0_1_51",
        url = "https://crates.io/api/v1/crates/async-trait/0.1.51/download",
        type = "tar.gz",
        sha256 = "44318e776df68115a881de9a8fd1b9e53368d7a4a5ce4cc48517da3393233a5e",
        strip_prefix = "async-trait-0.1.51",
        build_file = Label("//cargo/remote:BUILD.async-trait-0.1.51.bazel"),
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
        name = "raze__bitflags__1_3_2",
        url = "https://crates.io/api/v1/crates/bitflags/1.3.2/download",
        type = "tar.gz",
        sha256 = "bef38d45163c2f1dde094a7dfd33ccf595c92905c8f8f4fdc18d06fb1037718a",
        strip_prefix = "bitflags-1.3.2",
        build_file = Label("//cargo/remote:BUILD.bitflags-1.3.2.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__blake3__1_2_0",
        url = "https://crates.io/api/v1/crates/blake3/1.2.0/download",
        type = "tar.gz",
        sha256 = "526c210b4520e416420759af363083471656e819a75e831b8d2c9d5a584f2413",
        strip_prefix = "blake3-1.2.0",
        build_file = Label("//cargo/remote:BUILD.blake3-1.2.0.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__bumpalo__3_8_0",
        url = "https://crates.io/api/v1/crates/bumpalo/3.8.0/download",
        type = "tar.gz",
        sha256 = "8f1e260c3a9040a7c19a12468758f4c16f31a81a1fe087482be9570ec864bb6c",
        strip_prefix = "bumpalo-3.8.0",
        build_file = Label("//cargo/remote:BUILD.bumpalo-3.8.0.bazel"),
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
        name = "raze__bytes__1_1_0",
        url = "https://crates.io/api/v1/crates/bytes/1.1.0/download",
        type = "tar.gz",
        sha256 = "c4872d67bab6358e59559027aa3b9157c53d9358c51423c17554809a8858e0f8",
        strip_prefix = "bytes-1.1.0",
        build_file = Label("//cargo/remote:BUILD.bytes-1.1.0.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__cc__1_0_72",
        url = "https://crates.io/api/v1/crates/cc/1.0.72/download",
        type = "tar.gz",
        sha256 = "22a9137b95ea06864e018375b72adfb7db6e6f68cfc8df5a04d00288050485ee",
        strip_prefix = "cc-1.0.72",
        build_file = Label("//cargo/remote:BUILD.cc-1.0.72.bazel"),
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
        name = "raze__coarsetime__0_1_20",
        url = "https://crates.io/api/v1/crates/coarsetime/0.1.20/download",
        type = "tar.gz",
        sha256 = "5b6ec6f6e80e839eb22bd61b18f19a8f2ae3f8bda9cf0fdce9dd96c9c5df8393",
        strip_prefix = "coarsetime-0.1.20",
        build_file = Label("//cargo/remote:BUILD.coarsetime-0.1.20.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__codespan__0_11_1",
        url = "https://crates.io/api/v1/crates/codespan/0.11.1/download",
        type = "tar.gz",
        sha256 = "3362992a0d9f1dd7c3d0e89e0ab2bb540b7a95fea8cd798090e758fda2899b5e",
        strip_prefix = "codespan-0.11.1",
        build_file = Label("//cargo/remote:BUILD.codespan-0.11.1.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__codespan_reporting__0_11_1",
        url = "https://crates.io/api/v1/crates/codespan-reporting/0.11.1/download",
        type = "tar.gz",
        sha256 = "3538270d33cc669650c4b093848450d380def10c331d38c768e34cac80576e6e",
        strip_prefix = "codespan-reporting-0.11.1",
        build_file = Label("//cargo/remote:BUILD.codespan-reporting-0.11.1.bazel"),
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
        name = "raze__convert_case__0_4_0",
        url = "https://crates.io/api/v1/crates/convert_case/0.4.0/download",
        type = "tar.gz",
        sha256 = "6245d59a3e82a7fc217c5828a6692dbc6dfb63a0c8c90495621f7b9d79704a0e",
        strip_prefix = "convert_case-0.4.0",
        build_file = Label("//cargo/remote:BUILD.convert_case-0.4.0.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__core_foundation__0_9_2",
        url = "https://crates.io/api/v1/crates/core-foundation/0.9.2/download",
        type = "tar.gz",
        sha256 = "6888e10551bb93e424d8df1d07f1a8b4fceb0001a3a4b048bfc47554946f47b3",
        strip_prefix = "core-foundation-0.9.2",
        build_file = Label("//cargo/remote:BUILD.core-foundation-0.9.2.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__core_foundation_sys__0_8_3",
        url = "https://crates.io/api/v1/crates/core-foundation-sys/0.8.3/download",
        type = "tar.gz",
        sha256 = "5827cebf4670468b8772dd191856768aedcb1b0278a04f989f7766351917b9dc",
        strip_prefix = "core-foundation-sys-0.8.3",
        build_file = Label("//cargo/remote:BUILD.core-foundation-sys-0.8.3.bazel"),
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
        name = "raze__crossbeam_utils__0_8_5",
        url = "https://crates.io/api/v1/crates/crossbeam-utils/0.8.5/download",
        type = "tar.gz",
        sha256 = "d82cfc11ce7f2c3faef78d8a684447b40d503d9681acebed6cb728d45940c4db",
        strip_prefix = "crossbeam-utils-0.8.5",
        build_file = Label("//cargo/remote:BUILD.crossbeam-utils-0.8.5.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__cssparser__0_27_2",
        url = "https://crates.io/api/v1/crates/cssparser/0.27.2/download",
        type = "tar.gz",
        sha256 = "754b69d351cdc2d8ee09ae203db831e005560fc6030da058f86ad60c92a9cb0a",
        strip_prefix = "cssparser-0.27.2",
        build_file = Label("//cargo/remote:BUILD.cssparser-0.27.2.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__cssparser_macros__0_6_0",
        url = "https://crates.io/api/v1/crates/cssparser-macros/0.6.0/download",
        type = "tar.gz",
        sha256 = "dfae75de57f2b2e85e8768c3ea840fd159c8f33e2b6522c7835b7abac81be16e",
        strip_prefix = "cssparser-macros-0.6.0",
        build_file = Label("//cargo/remote:BUILD.cssparser-macros-0.6.0.bazel"),
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
        name = "raze__derive_more__0_99_16",
        url = "https://crates.io/api/v1/crates/derive_more/0.99.16/download",
        type = "tar.gz",
        sha256 = "40eebddd2156ce1bb37b20bbe5151340a31828b1f2d22ba4141f3531710e38df",
        strip_prefix = "derive_more-0.99.16",
        build_file = Label("//cargo/remote:BUILD.derive_more-0.99.16.bazel"),
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
        name = "raze__dirs_next__2_0_0",
        url = "https://crates.io/api/v1/crates/dirs-next/2.0.0/download",
        type = "tar.gz",
        sha256 = "b98cf8ebf19c3d1b223e151f99a4f9f0690dca41414773390fc824184ac833e1",
        strip_prefix = "dirs-next-2.0.0",
        build_file = Label("//cargo/remote:BUILD.dirs-next-2.0.0.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__dirs_sys_next__0_1_2",
        url = "https://crates.io/api/v1/crates/dirs-sys-next/0.1.2/download",
        type = "tar.gz",
        sha256 = "4ebda144c4fe02d1f7ea1a7d9641b6fc6b580adcfa024ae48797ecdeb6825b4d",
        strip_prefix = "dirs-sys-next-0.1.2",
        build_file = Label("//cargo/remote:BUILD.dirs-sys-next-0.1.2.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__dtoa__0_4_8",
        url = "https://crates.io/api/v1/crates/dtoa/0.4.8/download",
        type = "tar.gz",
        sha256 = "56899898ce76aaf4a0f24d914c97ea6ed976d42fec6ad33fcbb0a1103e07b2b0",
        strip_prefix = "dtoa-0.4.8",
        build_file = Label("//cargo/remote:BUILD.dtoa-0.4.8.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__dtoa_short__0_3_3",
        url = "https://crates.io/api/v1/crates/dtoa-short/0.3.3/download",
        type = "tar.gz",
        sha256 = "bde03329ae10e79ede66c9ce4dc930aa8599043b0743008548680f25b91502d6",
        strip_prefix = "dtoa-short-0.3.3",
        build_file = Label("//cargo/remote:BUILD.dtoa-short-0.3.3.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__dunce__1_0_2",
        url = "https://crates.io/api/v1/crates/dunce/1.0.2/download",
        type = "tar.gz",
        sha256 = "453440c271cf5577fd2a40e4942540cb7d0d2f85e27c8d07dd0023c925a67541",
        strip_prefix = "dunce-1.0.2",
        build_file = Label("//cargo/remote:BUILD.dunce-1.0.2.bazel"),
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
        name = "raze__encoding_rs__0_8_29",
        url = "https://crates.io/api/v1/crates/encoding_rs/0.8.29/download",
        type = "tar.gz",
        sha256 = "a74ea89a0a1b98f6332de42c95baff457ada66d1cb4030f9ff151b2041a1c746",
        strip_prefix = "encoding_rs-0.8.29",
        build_file = Label("//cargo/remote:BUILD.encoding_rs-0.8.29.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__env_logger__0_9_0",
        url = "https://crates.io/api/v1/crates/env_logger/0.9.0/download",
        type = "tar.gz",
        sha256 = "0b2cf0344971ee6c64c31be0d530793fba457d322dfec2810c453d0ef228f9c3",
        strip_prefix = "env_logger-0.9.0",
        build_file = Label("//cargo/remote:BUILD.env_logger-0.9.0.bazel"),
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
        name = "raze__fixedbitset__0_4_0",
        url = "https://crates.io/api/v1/crates/fixedbitset/0.4.0/download",
        type = "tar.gz",
        sha256 = "398ea4fabe40b9b0d885340a2a991a44c8a645624075ad966d21f88688e2b69e",
        strip_prefix = "fixedbitset-0.4.0",
        build_file = Label("//cargo/remote:BUILD.fixedbitset-0.4.0.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__flate2__1_0_22",
        url = "https://crates.io/api/v1/crates/flate2/1.0.22/download",
        type = "tar.gz",
        sha256 = "1e6988e897c1c9c485f43b47a529cef42fde0547f9d8d41a7062518f1d8fc53f",
        strip_prefix = "flate2-1.0.22",
        build_file = Label("//cargo/remote:BUILD.flate2-1.0.22.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__fluent__0_16_0",
        url = "https://crates.io/api/v1/crates/fluent/0.16.0/download",
        type = "tar.gz",
        sha256 = "61f69378194459db76abd2ce3952b790db103ceb003008d3d50d97c41ff847a7",
        strip_prefix = "fluent-0.16.0",
        build_file = Label("//cargo/remote:BUILD.fluent-0.16.0.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__fluent_bundle__0_15_2",
        url = "https://crates.io/api/v1/crates/fluent-bundle/0.15.2/download",
        type = "tar.gz",
        sha256 = "e242c601dec9711505f6d5bbff5bedd4b61b2469f2e8bb8e57ee7c9747a87ffd",
        strip_prefix = "fluent-bundle-0.15.2",
        build_file = Label("//cargo/remote:BUILD.fluent-bundle-0.15.2.bazel"),
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
        name = "raze__futf__0_1_4",
        url = "https://crates.io/api/v1/crates/futf/0.1.4/download",
        type = "tar.gz",
        sha256 = "7c9c1ce3fa9336301af935ab852c437817d14cd33690446569392e65170aac3b",
        strip_prefix = "futf-0.1.4",
        build_file = Label("//cargo/remote:BUILD.futf-0.1.4.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__futures__0_3_17",
        url = "https://crates.io/api/v1/crates/futures/0.3.17/download",
        type = "tar.gz",
        sha256 = "a12aa0eb539080d55c3f2d45a67c3b58b6b0773c1a3ca2dfec66d58c97fd66ca",
        strip_prefix = "futures-0.3.17",
        build_file = Label("//cargo/remote:BUILD.futures-0.3.17.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__futures_channel__0_3_17",
        url = "https://crates.io/api/v1/crates/futures-channel/0.3.17/download",
        type = "tar.gz",
        sha256 = "5da6ba8c3bb3c165d3c7319fc1cc8304facf1fb8db99c5de877183c08a273888",
        strip_prefix = "futures-channel-0.3.17",
        build_file = Label("//cargo/remote:BUILD.futures-channel-0.3.17.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__futures_core__0_3_17",
        url = "https://crates.io/api/v1/crates/futures-core/0.3.17/download",
        type = "tar.gz",
        sha256 = "88d1c26957f23603395cd326b0ffe64124b818f4449552f960d815cfba83a53d",
        strip_prefix = "futures-core-0.3.17",
        build_file = Label("//cargo/remote:BUILD.futures-core-0.3.17.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__futures_executor__0_3_17",
        url = "https://crates.io/api/v1/crates/futures-executor/0.3.17/download",
        type = "tar.gz",
        sha256 = "45025be030969d763025784f7f355043dc6bc74093e4ecc5000ca4dc50d8745c",
        strip_prefix = "futures-executor-0.3.17",
        build_file = Label("//cargo/remote:BUILD.futures-executor-0.3.17.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__futures_io__0_3_17",
        url = "https://crates.io/api/v1/crates/futures-io/0.3.17/download",
        type = "tar.gz",
        sha256 = "522de2a0fe3e380f1bc577ba0474108faf3f6b18321dbf60b3b9c39a75073377",
        strip_prefix = "futures-io-0.3.17",
        build_file = Label("//cargo/remote:BUILD.futures-io-0.3.17.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__futures_macro__0_3_17",
        url = "https://crates.io/api/v1/crates/futures-macro/0.3.17/download",
        type = "tar.gz",
        sha256 = "18e4a4b95cea4b4ccbcf1c5675ca7c4ee4e9e75eb79944d07defde18068f79bb",
        strip_prefix = "futures-macro-0.3.17",
        build_file = Label("//cargo/remote:BUILD.futures-macro-0.3.17.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__futures_sink__0_3_17",
        url = "https://crates.io/api/v1/crates/futures-sink/0.3.17/download",
        type = "tar.gz",
        sha256 = "36ea153c13024fe480590b3e3d4cad89a0cfacecc24577b68f86c6ced9c2bc11",
        strip_prefix = "futures-sink-0.3.17",
        build_file = Label("//cargo/remote:BUILD.futures-sink-0.3.17.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__futures_task__0_3_17",
        url = "https://crates.io/api/v1/crates/futures-task/0.3.17/download",
        type = "tar.gz",
        sha256 = "1d3d00f4eddb73e498a54394f228cd55853bdf059259e8e7bc6e69d408892e99",
        strip_prefix = "futures-task-0.3.17",
        build_file = Label("//cargo/remote:BUILD.futures-task-0.3.17.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__futures_util__0_3_17",
        url = "https://crates.io/api/v1/crates/futures-util/0.3.17/download",
        type = "tar.gz",
        sha256 = "36568465210a3a6ee45e1f165136d68671471a501e632e9a98d96872222b5481",
        strip_prefix = "futures-util-0.3.17",
        build_file = Label("//cargo/remote:BUILD.futures-util-0.3.17.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__fxhash__0_2_1",
        url = "https://crates.io/api/v1/crates/fxhash/0.2.1/download",
        type = "tar.gz",
        sha256 = "c31b6d751ae2c7f11320402d34e41349dd1016f8d5d45e48c4312bc8625af50c",
        strip_prefix = "fxhash-0.2.1",
        build_file = Label("//cargo/remote:BUILD.fxhash-0.2.1.bazel"),
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
        name = "raze__getrandom__0_2_3",
        url = "https://crates.io/api/v1/crates/getrandom/0.2.3/download",
        type = "tar.gz",
        sha256 = "7fcd999463524c52659517fe2cea98493cfe485d10565e7b0fb07dbba7ad2753",
        strip_prefix = "getrandom-0.2.3",
        build_file = Label("//cargo/remote:BUILD.getrandom-0.2.3.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__h2__0_3_7",
        url = "https://crates.io/api/v1/crates/h2/0.3.7/download",
        type = "tar.gz",
        sha256 = "7fd819562fcebdac5afc5c113c3ec36f902840b70fd4fc458799c8ce4607ae55",
        strip_prefix = "h2-0.3.7",
        build_file = Label("//cargo/remote:BUILD.h2-0.3.7.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__hashbrown__0_11_2",
        url = "https://crates.io/api/v1/crates/hashbrown/0.11.2/download",
        type = "tar.gz",
        sha256 = "ab5ef0d4909ef3724cc8cce6ccc8572c5c817592e9285f5464f8e86f8bd3726e",
        strip_prefix = "hashbrown-0.11.2",
        build_file = Label("//cargo/remote:BUILD.hashbrown-0.11.2.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__hashlink__0_7_0",
        url = "https://crates.io/api/v1/crates/hashlink/0.7.0/download",
        type = "tar.gz",
        sha256 = "7249a3129cbc1ffccd74857f81464a323a152173cdb134e0fd81bc803b29facf",
        strip_prefix = "hashlink-0.7.0",
        build_file = Label("//cargo/remote:BUILD.hashlink-0.7.0.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__heck__0_3_3",
        url = "https://crates.io/api/v1/crates/heck/0.3.3/download",
        type = "tar.gz",
        sha256 = "6d621efb26863f0e9924c6ac577e8275e5e6b77455db64ffa6c65c904e9e132c",
        strip_prefix = "heck-0.3.3",
        build_file = Label("//cargo/remote:BUILD.heck-0.3.3.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__hermit_abi__0_1_19",
        url = "https://crates.io/api/v1/crates/hermit-abi/0.1.19/download",
        type = "tar.gz",
        sha256 = "62b467343b94ba476dcb2500d242dadbb39557df889310ac77c5d99100aaac33",
        strip_prefix = "hermit-abi-0.1.19",
        build_file = Label("//cargo/remote:BUILD.hermit-abi-0.1.19.bazel"),
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
        name = "raze__http__0_2_5",
        url = "https://crates.io/api/v1/crates/http/0.2.5/download",
        type = "tar.gz",
        sha256 = "1323096b05d41827dadeaee54c9981958c0f94e670bc94ed80037d1a7b8b186b",
        strip_prefix = "http-0.2.5",
        build_file = Label("//cargo/remote:BUILD.http-0.2.5.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__http_body__0_4_4",
        url = "https://crates.io/api/v1/crates/http-body/0.4.4/download",
        type = "tar.gz",
        sha256 = "1ff4f84919677303da5f147645dbea6b1881f368d03ac84e1dc09031ebd7b2c6",
        strip_prefix = "http-body-0.4.4",
        build_file = Label("//cargo/remote:BUILD.http-body-0.4.4.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__httparse__1_5_1",
        url = "https://crates.io/api/v1/crates/httparse/1.5.1/download",
        type = "tar.gz",
        sha256 = "acd94fdbe1d4ff688b67b04eee2e17bd50995534a61539e45adfefb45e5e5503",
        strip_prefix = "httparse-1.5.1",
        build_file = Label("//cargo/remote:BUILD.httparse-1.5.1.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__httpdate__1_0_2",
        url = "https://crates.io/api/v1/crates/httpdate/1.0.2/download",
        type = "tar.gz",
        sha256 = "c4a1e36c821dbe04574f602848a19f742f4fb3c98d40449f11bcad18d6b17421",
        strip_prefix = "httpdate-1.0.2",
        build_file = Label("//cargo/remote:BUILD.httpdate-1.0.2.bazel"),
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
        name = "raze__hyper__0_14_15",
        url = "https://crates.io/api/v1/crates/hyper/0.14.15/download",
        type = "tar.gz",
        sha256 = "436ec0091e4f20e655156a30a0df3770fe2900aa301e548e08446ec794b6953c",
        strip_prefix = "hyper-0.14.15",
        build_file = Label("//cargo/remote:BUILD.hyper-0.14.15.bazel"),
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
        name = "raze__indexmap__1_7_0",
        url = "https://crates.io/api/v1/crates/indexmap/1.7.0/download",
        type = "tar.gz",
        sha256 = "bc633605454125dec4b66843673f01c7df2b89479b32e0ed634e43a91cff62a5",
        strip_prefix = "indexmap-1.7.0",
        build_file = Label("//cargo/remote:BUILD.indexmap-1.7.0.bazel"),
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
        name = "raze__instant__0_1_12",
        url = "https://crates.io/api/v1/crates/instant/0.1.12/download",
        type = "tar.gz",
        sha256 = "7a5bbe824c507c5da5956355e86a746d82e0e1464f65d862cc5e71da70e94b2c",
        strip_prefix = "instant-0.1.12",
        build_file = Label("//cargo/remote:BUILD.instant-0.1.12.bazel"),
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
        name = "raze__ipnet__2_3_1",
        url = "https://crates.io/api/v1/crates/ipnet/2.3.1/download",
        type = "tar.gz",
        sha256 = "68f2d64f2edebec4ce84ad108148e67e1064789bee435edc5b60ad398714a3a9",
        strip_prefix = "ipnet-2.3.1",
        build_file = Label("//cargo/remote:BUILD.ipnet-2.3.1.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__itertools__0_10_1",
        url = "https://crates.io/api/v1/crates/itertools/0.10.1/download",
        type = "tar.gz",
        sha256 = "69ddb889f9d0d08a67338271fa9b62996bc788c7796a5c18cf057420aaed5eaf",
        strip_prefix = "itertools-0.10.1",
        build_file = Label("//cargo/remote:BUILD.itertools-0.10.1.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__itoa__0_4_8",
        url = "https://crates.io/api/v1/crates/itoa/0.4.8/download",
        type = "tar.gz",
        sha256 = "b71991ff56294aa922b450139ee08b3bfc70982c6b2c7562771375cf73542dd4",
        strip_prefix = "itoa-0.4.8",
        build_file = Label("//cargo/remote:BUILD.itoa-0.4.8.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__js_sys__0_3_55",
        url = "https://crates.io/api/v1/crates/js-sys/0.3.55/download",
        type = "tar.gz",
        sha256 = "7cc9ffccd38c451a86bf13657df244e9c3f37493cce8e5e21e940963777acc84",
        strip_prefix = "js-sys-0.3.55",
        build_file = Label("//cargo/remote:BUILD.js-sys-0.3.55.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__kuchiki__0_8_1",
        url = "https://crates.io/api/v1/crates/kuchiki/0.8.1/download",
        type = "tar.gz",
        sha256 = "1ea8e9c6e031377cff82ee3001dc8026cdf431ed4e2e6b51f98ab8c73484a358",
        strip_prefix = "kuchiki-0.8.1",
        build_file = Label("//cargo/remote:BUILD.kuchiki-0.8.1.bazel"),
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
        name = "raze__libc__0_2_107",
        url = "https://crates.io/api/v1/crates/libc/0.2.107/download",
        type = "tar.gz",
        sha256 = "fbe5e23404da5b4f555ef85ebed98fb4083e55a00c317800bc2a50ede9f3d219",
        strip_prefix = "libc-0.2.107",
        build_file = Label("//cargo/remote:BUILD.libc-0.2.107.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__libsqlite3_sys__0_23_1",
        url = "https://crates.io/api/v1/crates/libsqlite3-sys/0.23.1/download",
        type = "tar.gz",
        sha256 = "abd5850c449b40bacb498b2bbdfaff648b1b055630073ba8db499caf2d0ea9f2",
        strip_prefix = "libsqlite3-sys-0.23.1",
        build_file = Label("//cargo/remote:BUILD.libsqlite3-sys-0.23.1.bazel"),
    )

    maybe(
        new_git_repository,
        name = "raze__linkcheck__0_4_1_alpha_0",
        remote = "https://github.com/ankitects/linkcheck.git",
        shallow_since = "1626729019 +0200",
        commit = "2f20798ce521cc594d510d4e417e76d5eac04d4b",
        build_file = Label("//cargo/remote:BUILD.linkcheck-0.4.1-alpha.0.bazel"),
        init_submodules = True,
    )

    maybe(
        http_archive,
        name = "raze__linkify__0_5_0",
        url = "https://crates.io/api/v1/crates/linkify/0.5.0/download",
        type = "tar.gz",
        sha256 = "78d59d732ba6d7eeefc418aab8057dc8e3da4374bd5802ffa95bebc04b4d1dfb",
        strip_prefix = "linkify-0.5.0",
        build_file = Label("//cargo/remote:BUILD.linkify-0.5.0.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__lock_api__0_4_5",
        url = "https://crates.io/api/v1/crates/lock_api/0.4.5/download",
        type = "tar.gz",
        sha256 = "712a4d093c9976e24e7dbca41db895dabcbac38eb5f4045393d17a95bdfb1109",
        strip_prefix = "lock_api-0.4.5",
        build_file = Label("//cargo/remote:BUILD.lock_api-0.4.5.bazel"),
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
        name = "raze__matches__0_1_9",
        url = "https://crates.io/api/v1/crates/matches/0.1.9/download",
        type = "tar.gz",
        sha256 = "a3e378b66a060d48947b590737b30a1be76706c8dd7b8ba0f2fe3989c68a853f",
        strip_prefix = "matches-0.1.9",
        build_file = Label("//cargo/remote:BUILD.matches-0.1.9.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__memchr__2_4_1",
        url = "https://crates.io/api/v1/crates/memchr/2.4.1/download",
        type = "tar.gz",
        sha256 = "308cc39be01b73d0d18f82a0e7b2a3df85245f84af96fdddc5d202d27e47b86a",
        strip_prefix = "memchr-2.4.1",
        build_file = Label("//cargo/remote:BUILD.memchr-2.4.1.bazel"),
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
        name = "raze__minimal_lexical__0_2_1",
        url = "https://crates.io/api/v1/crates/minimal-lexical/0.2.1/download",
        type = "tar.gz",
        sha256 = "68354c5c6bd36d73ff3feceb05efa59b6acb7626617f4962be322a825e61f79a",
        strip_prefix = "minimal-lexical-0.2.1",
        build_file = Label("//cargo/remote:BUILD.minimal-lexical-0.2.1.bazel"),
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
        name = "raze__mio__0_7_14",
        url = "https://crates.io/api/v1/crates/mio/0.7.14/download",
        type = "tar.gz",
        sha256 = "8067b404fe97c70829f082dec8bcf4f71225d7eaea1d8645349cb76fa06205cc",
        strip_prefix = "mio-0.7.14",
        build_file = Label("//cargo/remote:BUILD.mio-0.7.14.bazel"),
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
        name = "raze__native_tls__0_2_8",
        url = "https://crates.io/api/v1/crates/native-tls/0.2.8/download",
        type = "tar.gz",
        sha256 = "48ba9f7719b5a0f42f338907614285fb5fd70e53858141f69898a1fb7203b24d",
        strip_prefix = "native-tls-0.2.8",
        build_file = Label("//cargo/remote:BUILD.native-tls-0.2.8.bazel"),
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
        name = "raze__nom__7_1_0",
        url = "https://crates.io/api/v1/crates/nom/7.1.0/download",
        type = "tar.gz",
        sha256 = "1b1d11e1ef389c76fe5b81bcaf2ea32cf88b62bc494e19f493d0b30e7a930109",
        strip_prefix = "nom-7.1.0",
        build_file = Label("//cargo/remote:BUILD.nom-7.1.0.bazel"),
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
        name = "raze__num_enum__0_5_4",
        url = "https://crates.io/api/v1/crates/num_enum/0.5.4/download",
        type = "tar.gz",
        sha256 = "3f9bd055fb730c4f8f4f57d45d35cd6b3f0980535b056dc7ff119cee6a66ed6f",
        strip_prefix = "num_enum-0.5.4",
        build_file = Label("//cargo/remote:BUILD.num_enum-0.5.4.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__num_enum_derive__0_5_4",
        url = "https://crates.io/api/v1/crates/num_enum_derive/0.5.4/download",
        type = "tar.gz",
        sha256 = "486ea01961c4a818096de679a8b740b26d9033146ac5291b1c98557658f8cdd9",
        strip_prefix = "num_enum_derive-0.5.4",
        build_file = Label("//cargo/remote:BUILD.num_enum_derive-0.5.4.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__once_cell__1_8_0",
        url = "https://crates.io/api/v1/crates/once_cell/1.8.0/download",
        type = "tar.gz",
        sha256 = "692fcb63b64b1758029e0a96ee63e049ce8c5948587f2f7208df04625e5f6b56",
        strip_prefix = "once_cell-1.8.0",
        build_file = Label("//cargo/remote:BUILD.once_cell-1.8.0.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__openssl__0_10_38",
        url = "https://crates.io/api/v1/crates/openssl/0.10.38/download",
        type = "tar.gz",
        sha256 = "0c7ae222234c30df141154f159066c5093ff73b63204dcda7121eb082fc56a95",
        strip_prefix = "openssl-0.10.38",
        build_file = Label("//cargo/remote:BUILD.openssl-0.10.38.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__openssl_probe__0_1_4",
        url = "https://crates.io/api/v1/crates/openssl-probe/0.1.4/download",
        type = "tar.gz",
        sha256 = "28988d872ab76095a6e6ac88d99b54fd267702734fd7ffe610ca27f533ddb95a",
        strip_prefix = "openssl-probe-0.1.4",
        build_file = Label("//cargo/remote:BUILD.openssl-probe-0.1.4.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__openssl_sys__0_9_71",
        url = "https://crates.io/api/v1/crates/openssl-sys/0.9.71/download",
        type = "tar.gz",
        sha256 = "7df13d165e607909b363a4757a6f133f8a818a74e9d3a98d09c6128e15fa4c73",
        strip_prefix = "openssl-sys-0.9.71",
        build_file = Label("//cargo/remote:BUILD.openssl-sys-0.9.71.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__parking_lot__0_11_2",
        url = "https://crates.io/api/v1/crates/parking_lot/0.11.2/download",
        type = "tar.gz",
        sha256 = "7d17b78036a60663b797adeaee46f5c9dfebb86948d1255007a1d6be0271ff99",
        strip_prefix = "parking_lot-0.11.2",
        build_file = Label("//cargo/remote:BUILD.parking_lot-0.11.2.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__parking_lot_core__0_8_5",
        url = "https://crates.io/api/v1/crates/parking_lot_core/0.8.5/download",
        type = "tar.gz",
        sha256 = "d76e8e1493bcac0d2766c42737f34458f1c8c50c0d23bcb24ea953affb273216",
        strip_prefix = "parking_lot_core-0.8.5",
        build_file = Label("//cargo/remote:BUILD.parking_lot_core-0.8.5.bazel"),
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
        new_git_repository,
        name = "raze__pct_str__1_1_0",
        remote = "https://github.com/timothee-haudebourg/pct-str.git",
        shallow_since = "1605376517 +0100",
        commit = "4adccd8d4a222ab2672350a102f06ae832a0572d",
        build_file = Label("//cargo/remote:BUILD.pct-str-1.1.0.bazel"),
        init_submodules = True,
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
        name = "raze__pest__2_1_3",
        url = "https://crates.io/api/v1/crates/pest/2.1.3/download",
        type = "tar.gz",
        sha256 = "10f4872ae94d7b90ae48754df22fd42ad52ce740b8f370b03da4835417403e53",
        strip_prefix = "pest-2.1.3",
        build_file = Label("//cargo/remote:BUILD.pest-2.1.3.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__petgraph__0_6_0",
        url = "https://crates.io/api/v1/crates/petgraph/0.6.0/download",
        type = "tar.gz",
        sha256 = "4a13a2fa9d0b63e5f22328828741e523766fff0ee9e779316902290dff3f824f",
        strip_prefix = "petgraph-0.6.0",
        build_file = Label("//cargo/remote:BUILD.petgraph-0.6.0.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__phf__0_10_0",
        url = "https://crates.io/api/v1/crates/phf/0.10.0/download",
        type = "tar.gz",
        sha256 = "b9fc3db1018c4b59d7d582a739436478b6035138b6aecbce989fc91c3e98409f",
        strip_prefix = "phf-0.10.0",
        build_file = Label("//cargo/remote:BUILD.phf-0.10.0.bazel"),
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
        name = "raze__phf_generator__0_10_0",
        url = "https://crates.io/api/v1/crates/phf_generator/0.10.0/download",
        type = "tar.gz",
        sha256 = "5d5285893bb5eb82e6aaf5d59ee909a06a16737a8970984dd7746ba9283498d6",
        strip_prefix = "phf_generator-0.10.0",
        build_file = Label("//cargo/remote:BUILD.phf_generator-0.10.0.bazel"),
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
        name = "raze__phf_macros__0_10_0",
        url = "https://crates.io/api/v1/crates/phf_macros/0.10.0/download",
        type = "tar.gz",
        sha256 = "58fdf3184dd560f160dd73922bea2d5cd6e8f064bf4b13110abd81b03697b4e0",
        strip_prefix = "phf_macros-0.10.0",
        build_file = Label("//cargo/remote:BUILD.phf_macros-0.10.0.bazel"),
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
        name = "raze__phf_shared__0_10_0",
        url = "https://crates.io/api/v1/crates/phf_shared/0.10.0/download",
        type = "tar.gz",
        sha256 = "b6796ad771acdc0123d2a88dc428b5e38ef24456743ddb1744ed628f9815c096",
        strip_prefix = "phf_shared-0.10.0",
        build_file = Label("//cargo/remote:BUILD.phf_shared-0.10.0.bazel"),
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
        name = "raze__pin_project__1_0_8",
        url = "https://crates.io/api/v1/crates/pin-project/1.0.8/download",
        type = "tar.gz",
        sha256 = "576bc800220cc65dac09e99e97b08b358cfab6e17078de8dc5fee223bd2d0c08",
        strip_prefix = "pin-project-1.0.8",
        build_file = Label("//cargo/remote:BUILD.pin-project-1.0.8.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__pin_project_internal__1_0_8",
        url = "https://crates.io/api/v1/crates/pin-project-internal/1.0.8/download",
        type = "tar.gz",
        sha256 = "6e8fe8163d14ce7f0cdac2e040116f22eac817edabff0be91e8aff7e9accf389",
        strip_prefix = "pin-project-internal-1.0.8",
        build_file = Label("//cargo/remote:BUILD.pin-project-internal-1.0.8.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__pin_project_lite__0_2_7",
        url = "https://crates.io/api/v1/crates/pin-project-lite/0.2.7/download",
        type = "tar.gz",
        sha256 = "8d31d11c69a6b52a174b42bdc0c30e5e11670f90788b2c471c31c1d17d449443",
        strip_prefix = "pin-project-lite-0.2.7",
        build_file = Label("//cargo/remote:BUILD.pin-project-lite-0.2.7.bazel"),
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
        name = "raze__pkg_config__0_3_22",
        url = "https://crates.io/api/v1/crates/pkg-config/0.3.22/download",
        type = "tar.gz",
        sha256 = "12295df4f294471248581bc09bef3c38a5e46f1e36d6a37353621a0c6c357e1f",
        strip_prefix = "pkg-config-0.3.22",
        build_file = Label("//cargo/remote:BUILD.pkg-config-0.3.22.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__ppv_lite86__0_2_15",
        url = "https://crates.io/api/v1/crates/ppv-lite86/0.2.15/download",
        type = "tar.gz",
        sha256 = "ed0cfbc8191465bed66e1718596ee0b0b35d5ee1f41c5df2189d0fe8bde535ba",
        strip_prefix = "ppv-lite86-0.2.15",
        build_file = Label("//cargo/remote:BUILD.ppv-lite86-0.2.15.bazel"),
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
        name = "raze__proc_macro_crate__1_1_0",
        url = "https://crates.io/api/v1/crates/proc-macro-crate/1.1.0/download",
        type = "tar.gz",
        sha256 = "1ebace6889caf889b4d3f76becee12e90353f2b8c7d875534a71e5742f8f6f83",
        strip_prefix = "proc-macro-crate-1.1.0",
        build_file = Label("//cargo/remote:BUILD.proc-macro-crate-1.1.0.bazel"),
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
        name = "raze__proc_macro_nested__0_1_7",
        url = "https://crates.io/api/v1/crates/proc-macro-nested/0.1.7/download",
        type = "tar.gz",
        sha256 = "bc881b2c22681370c6a780e47af9840ef841837bc98118431d4e1868bd0c1086",
        strip_prefix = "proc-macro-nested-0.1.7",
        build_file = Label("//cargo/remote:BUILD.proc-macro-nested-0.1.7.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__proc_macro2__1_0_32",
        url = "https://crates.io/api/v1/crates/proc-macro2/1.0.32/download",
        type = "tar.gz",
        sha256 = "ba508cc11742c0dc5c1659771673afbab7a0efab23aa17e854cbab0837ed0b43",
        strip_prefix = "proc-macro2-1.0.32",
        build_file = Label("//cargo/remote:BUILD.proc-macro2-1.0.32.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__prost__0_9_0",
        url = "https://crates.io/api/v1/crates/prost/0.9.0/download",
        type = "tar.gz",
        sha256 = "444879275cb4fd84958b1a1d5420d15e6fcf7c235fe47f053c9c2a80aceb6001",
        strip_prefix = "prost-0.9.0",
        build_file = Label("//cargo/remote:BUILD.prost-0.9.0.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__prost_build__0_9_0",
        url = "https://crates.io/api/v1/crates/prost-build/0.9.0/download",
        type = "tar.gz",
        sha256 = "62941722fb675d463659e49c4f3fe1fe792ff24fe5bbaa9c08cd3b98a1c354f5",
        strip_prefix = "prost-build-0.9.0",
        build_file = Label("//cargo/remote:BUILD.prost-build-0.9.0.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__prost_derive__0_9_0",
        url = "https://crates.io/api/v1/crates/prost-derive/0.9.0/download",
        type = "tar.gz",
        sha256 = "f9cc1a3263e07e0bf68e96268f37665207b49560d98739662cdfaae215c720fe",
        strip_prefix = "prost-derive-0.9.0",
        build_file = Label("//cargo/remote:BUILD.prost-derive-0.9.0.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__prost_types__0_9_0",
        url = "https://crates.io/api/v1/crates/prost-types/0.9.0/download",
        type = "tar.gz",
        sha256 = "534b7a0e836e3c482d2693070f982e39e7611da9695d4d1f5a4b186b51faef0a",
        strip_prefix = "prost-types-0.9.0",
        build_file = Label("//cargo/remote:BUILD.prost-types-0.9.0.bazel"),
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
        name = "raze__pyo3__0_15_0",
        url = "https://crates.io/api/v1/crates/pyo3/0.15.0/download",
        type = "tar.gz",
        sha256 = "64664505ce285a59b8b7e940fbe54ad65b1758a0810eddc5bc26df6f6ec8c557",
        strip_prefix = "pyo3-0.15.0",
        build_file = Label("//cargo/remote:BUILD.pyo3-0.15.0.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__pyo3_build_config__0_15_0",
        url = "https://crates.io/api/v1/crates/pyo3-build-config/0.15.0/download",
        type = "tar.gz",
        sha256 = "5f1e4a72de84cdcd69f62211b62f62753d0c11b7b5715f3467f3754dab22a7ca",
        strip_prefix = "pyo3-build-config-0.15.0",
        build_file = Label("//cargo/remote:BUILD.pyo3-build-config-0.15.0.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__pyo3_macros__0_15_0",
        url = "https://crates.io/api/v1/crates/pyo3-macros/0.15.0/download",
        type = "tar.gz",
        sha256 = "244f21d0a3887a9c02018b94e3b78d693dc7eca5c56839b7796a499cc364deb4",
        strip_prefix = "pyo3-macros-0.15.0",
        build_file = Label("//cargo/remote:BUILD.pyo3-macros-0.15.0.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__pyo3_macros_backend__0_15_0",
        url = "https://crates.io/api/v1/crates/pyo3-macros-backend/0.15.0/download",
        type = "tar.gz",
        sha256 = "b3d3d18ac41d05199bb82645d56e39f8c8b4909a0137c6f2640f03685b29f672",
        strip_prefix = "pyo3-macros-backend-0.15.0",
        build_file = Label("//cargo/remote:BUILD.pyo3-macros-backend-0.15.0.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__quote__1_0_10",
        url = "https://crates.io/api/v1/crates/quote/1.0.10/download",
        type = "tar.gz",
        sha256 = "38bc8cc6a5f2e3655e0899c1b848643b2562f853f114bfec7be120678e3ace05",
        strip_prefix = "quote-1.0.10",
        build_file = Label("//cargo/remote:BUILD.quote-1.0.10.bazel"),
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
        name = "raze__rand__0_8_4",
        url = "https://crates.io/api/v1/crates/rand/0.8.4/download",
        type = "tar.gz",
        sha256 = "2e7573632e6454cf6b99d7aac4ccca54be06da05aca2ef7423d22d27d4d4bcd8",
        strip_prefix = "rand-0.8.4",
        build_file = Label("//cargo/remote:BUILD.rand-0.8.4.bazel"),
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
        name = "raze__rand_chacha__0_3_1",
        url = "https://crates.io/api/v1/crates/rand_chacha/0.3.1/download",
        type = "tar.gz",
        sha256 = "e6c10a63a0fa32252be49d21e7709d4d4baf8d231c2dbce1eaa8141b9b127d88",
        strip_prefix = "rand_chacha-0.3.1",
        build_file = Label("//cargo/remote:BUILD.rand_chacha-0.3.1.bazel"),
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
        name = "raze__rand_core__0_6_3",
        url = "https://crates.io/api/v1/crates/rand_core/0.6.3/download",
        type = "tar.gz",
        sha256 = "d34f1408f55294453790c48b2f1ebbb1c5b4b7563eb1f418bcfcfdbb06ebb4e7",
        strip_prefix = "rand_core-0.6.3",
        build_file = Label("//cargo/remote:BUILD.rand_core-0.6.3.bazel"),
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
        name = "raze__rand_hc__0_3_1",
        url = "https://crates.io/api/v1/crates/rand_hc/0.3.1/download",
        type = "tar.gz",
        sha256 = "d51e9f596de227fda2ea6c84607f5558e196eeaf43c986b724ba4fb8fdf497e7",
        strip_prefix = "rand_hc-0.3.1",
        build_file = Label("//cargo/remote:BUILD.rand_hc-0.3.1.bazel"),
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
        name = "raze__redox_syscall__0_2_10",
        url = "https://crates.io/api/v1/crates/redox_syscall/0.2.10/download",
        type = "tar.gz",
        sha256 = "8383f39639269cde97d255a32bdb68c047337295414940c68bdd30c2e13203ff",
        strip_prefix = "redox_syscall-0.2.10",
        build_file = Label("//cargo/remote:BUILD.redox_syscall-0.2.10.bazel"),
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
        name = "raze__rusqlite__0_26_1",
        url = "https://crates.io/api/v1/crates/rusqlite/0.26.1/download",
        type = "tar.gz",
        sha256 = "8a82b0b91fad72160c56bf8da7a549b25d7c31109f52cc1437eac4c0ad2550a7",
        strip_prefix = "rusqlite-0.26.1",
        build_file = Label("//cargo/remote:BUILD.rusqlite-0.26.1.bazel"),
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
        name = "raze__rustc_version__0_3_3",
        url = "https://crates.io/api/v1/crates/rustc_version/0.3.3/download",
        type = "tar.gz",
        sha256 = "f0dfe2087c51c460008730de8b57e6a320782fbfb312e1f4d520e6c6fae155ee",
        strip_prefix = "rustc_version-0.3.3",
        build_file = Label("//cargo/remote:BUILD.rustc_version-0.3.3.bazel"),
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
        name = "raze__rustls_native_certs__0_5_0",
        url = "https://crates.io/api/v1/crates/rustls-native-certs/0.5.0/download",
        type = "tar.gz",
        sha256 = "5a07b7c1885bd8ed3831c289b7870b13ef46fe0e856d288c30d9cc17d75a2092",
        strip_prefix = "rustls-native-certs-0.5.0",
        build_file = Label("//cargo/remote:BUILD.rustls-native-certs-0.5.0.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__rustversion__1_0_5",
        url = "https://crates.io/api/v1/crates/rustversion/1.0.5/download",
        type = "tar.gz",
        sha256 = "61b3909d758bb75c79f23d4736fac9433868679d3ad2ea7a61e3c25cfda9a088",
        strip_prefix = "rustversion-1.0.5",
        build_file = Label("//cargo/remote:BUILD.rustversion-1.0.5.bazel"),
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
        name = "raze__same_file__1_0_6",
        url = "https://crates.io/api/v1/crates/same-file/1.0.6/download",
        type = "tar.gz",
        sha256 = "93fc1dc3aaa9bfed95e02e6eadabb4baf7e3078b0bd1b4d7b6b0b68378900502",
        strip_prefix = "same-file-1.0.6",
        build_file = Label("//cargo/remote:BUILD.same-file-1.0.6.bazel"),
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
        name = "raze__security_framework__2_4_2",
        url = "https://crates.io/api/v1/crates/security-framework/2.4.2/download",
        type = "tar.gz",
        sha256 = "525bc1abfda2e1998d152c45cf13e696f76d0a4972310b22fac1658b05df7c87",
        strip_prefix = "security-framework-2.4.2",
        build_file = Label("//cargo/remote:BUILD.security-framework-2.4.2.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__security_framework_sys__2_4_2",
        url = "https://crates.io/api/v1/crates/security-framework-sys/2.4.2/download",
        type = "tar.gz",
        sha256 = "a9dd14d83160b528b7bfd66439110573efcfbe281b17fc2ca9f39f550d619c7e",
        strip_prefix = "security-framework-sys-2.4.2",
        build_file = Label("//cargo/remote:BUILD.security-framework-sys-2.4.2.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__selectors__0_22_0",
        url = "https://crates.io/api/v1/crates/selectors/0.22.0/download",
        type = "tar.gz",
        sha256 = "df320f1889ac4ba6bc0cdc9c9af7af4bd64bb927bccdf32d81140dc1f9be12fe",
        strip_prefix = "selectors-0.22.0",
        build_file = Label("//cargo/remote:BUILD.selectors-0.22.0.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__self_cell__0_10_1",
        url = "https://crates.io/api/v1/crates/self_cell/0.10.1/download",
        type = "tar.gz",
        sha256 = "7ac753e6625df1711012c6469644ddd9b6338be1e4abf235c3a8817191411ae1",
        strip_prefix = "self_cell-0.10.1",
        build_file = Label("//cargo/remote:BUILD.self_cell-0.10.1.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__semver__0_11_0",
        url = "https://crates.io/api/v1/crates/semver/0.11.0/download",
        type = "tar.gz",
        sha256 = "f301af10236f6df4160f7c3f04eec6dbc70ace82d23326abad5edee88801c6b6",
        strip_prefix = "semver-0.11.0",
        build_file = Label("//cargo/remote:BUILD.semver-0.11.0.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__semver_parser__0_10_2",
        url = "https://crates.io/api/v1/crates/semver-parser/0.10.2/download",
        type = "tar.gz",
        sha256 = "00b0bef5b7f9e0df16536d3961cfb6e84331c065b4066afb39768d0e319411f7",
        strip_prefix = "semver-parser-0.10.2",
        build_file = Label("//cargo/remote:BUILD.semver-parser-0.10.2.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__serde__1_0_130",
        url = "https://crates.io/api/v1/crates/serde/1.0.130/download",
        type = "tar.gz",
        sha256 = "f12d06de37cf59146fbdecab66aa99f9fe4f78722e3607577a5375d66bd0c913",
        strip_prefix = "serde-1.0.130",
        build_file = Label("//cargo/remote:BUILD.serde-1.0.130.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__serde_aux__3_0_1",
        url = "https://crates.io/api/v1/crates/serde-aux/3.0.1/download",
        type = "tar.gz",
        sha256 = "93abf9799c576f004252b2a05168d58527fb7c54de12e94b4d12fe3475ffad24",
        strip_prefix = "serde-aux-3.0.1",
        build_file = Label("//cargo/remote:BUILD.serde-aux-3.0.1.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__serde_derive__1_0_130",
        url = "https://crates.io/api/v1/crates/serde_derive/1.0.130/download",
        type = "tar.gz",
        sha256 = "d7bc1a1ab1961464eae040d96713baa5a724a8152c1222492465b54322ec508b",
        strip_prefix = "serde_derive-1.0.130",
        build_file = Label("//cargo/remote:BUILD.serde_derive-1.0.130.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__serde_json__1_0_71",
        url = "https://crates.io/api/v1/crates/serde_json/1.0.71/download",
        type = "tar.gz",
        sha256 = "063bf466a64011ac24040a49009724ee60a57da1b437617ceb32e53ad61bfb19",
        strip_prefix = "serde_json-1.0.71",
        build_file = Label("//cargo/remote:BUILD.serde_json-1.0.71.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__serde_repr__0_1_7",
        url = "https://crates.io/api/v1/crates/serde_repr/0.1.7/download",
        type = "tar.gz",
        sha256 = "98d0516900518c29efa217c298fa1f4e6c6ffc85ae29fd7f4ee48f176e1a9ed5",
        strip_prefix = "serde_repr-0.1.7",
        build_file = Label("//cargo/remote:BUILD.serde_repr-0.1.7.bazel"),
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
        name = "raze__servo_arc__0_1_1",
        url = "https://crates.io/api/v1/crates/servo_arc/0.1.1/download",
        type = "tar.gz",
        sha256 = "d98238b800e0d1576d8b6e3de32827c2d74bee68bb97748dcf5071fb53965432",
        strip_prefix = "servo_arc-0.1.1",
        build_file = Label("//cargo/remote:BUILD.servo_arc-0.1.1.bazel"),
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
        name = "raze__siphasher__0_3_7",
        url = "https://crates.io/api/v1/crates/siphasher/0.3.7/download",
        type = "tar.gz",
        sha256 = "533494a8f9b724d33625ab53c6c4800f7cc445895924a8ef649222dcb76e938b",
        strip_prefix = "siphasher-0.3.7",
        build_file = Label("//cargo/remote:BUILD.siphasher-0.3.7.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__slab__0_4_5",
        url = "https://crates.io/api/v1/crates/slab/0.4.5/download",
        type = "tar.gz",
        sha256 = "9def91fd1e018fe007022791f865d0ccc9b3a0d5001e01aabb8b40e46000afb5",
        strip_prefix = "slab-0.4.5",
        build_file = Label("//cargo/remote:BUILD.slab-0.4.5.bazel"),
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
        name = "raze__slog_async__2_7_0",
        url = "https://crates.io/api/v1/crates/slog-async/2.7.0/download",
        type = "tar.gz",
        sha256 = "766c59b252e62a34651412870ff55d8c4e6d04df19b43eecb2703e417b097ffe",
        strip_prefix = "slog-async-2.7.0",
        build_file = Label("//cargo/remote:BUILD.slog-async-2.7.0.bazel"),
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
        name = "raze__slog_term__2_8_0",
        url = "https://crates.io/api/v1/crates/slog-term/2.8.0/download",
        type = "tar.gz",
        sha256 = "95c1e7e5aab61ced6006149ea772770b84a0d16ce0f7885def313e4829946d76",
        strip_prefix = "slog-term-2.8.0",
        build_file = Label("//cargo/remote:BUILD.slog-term-2.8.0.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__smallvec__1_7_0",
        url = "https://crates.io/api/v1/crates/smallvec/1.7.0/download",
        type = "tar.gz",
        sha256 = "1ecab6c735a6bb4139c0caafd0cc3635748bbb3acf4550e8138122099251f309",
        strip_prefix = "smallvec-1.7.0",
        build_file = Label("//cargo/remote:BUILD.smallvec-1.7.0.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__socket2__0_4_2",
        url = "https://crates.io/api/v1/crates/socket2/0.4.2/download",
        type = "tar.gz",
        sha256 = "5dc90fe6c7be1a323296982db1836d1ea9e47b6839496dde9a541bc496df3516",
        strip_prefix = "socket2-0.4.2",
        build_file = Label("//cargo/remote:BUILD.socket2-0.4.2.bazel"),
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
        name = "raze__string_cache__0_8_2",
        url = "https://crates.io/api/v1/crates/string_cache/0.8.2/download",
        type = "tar.gz",
        sha256 = "923f0f39b6267d37d23ce71ae7235602134b250ace715dd2c90421998ddac0c6",
        strip_prefix = "string_cache-0.8.2",
        build_file = Label("//cargo/remote:BUILD.string_cache-0.8.2.bazel"),
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
        name = "raze__strum__0_23_0",
        url = "https://crates.io/api/v1/crates/strum/0.23.0/download",
        type = "tar.gz",
        sha256 = "cae14b91c7d11c9a851d3fbc80a963198998c2a64eec840477fa92d8ce9b70bb",
        strip_prefix = "strum-0.23.0",
        build_file = Label("//cargo/remote:BUILD.strum-0.23.0.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__strum_macros__0_23_0",
        url = "https://crates.io/api/v1/crates/strum_macros/0.23.0/download",
        type = "tar.gz",
        sha256 = "00ad150e9d51e33e8142984f577662c1324d49f3be45ed37bac8645fdcbe0fe5",
        strip_prefix = "strum_macros-0.23.0",
        build_file = Label("//cargo/remote:BUILD.strum_macros-0.23.0.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__syn__1_0_81",
        url = "https://crates.io/api/v1/crates/syn/1.0.81/download",
        type = "tar.gz",
        sha256 = "f2afee18b8beb5a596ecb4a2dce128c719b4ba399d34126b9e4396e3f9860966",
        strip_prefix = "syn-1.0.81",
        build_file = Label("//cargo/remote:BUILD.syn-1.0.81.bazel"),
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
        name = "raze__term__0_7_0",
        url = "https://crates.io/api/v1/crates/term/0.7.0/download",
        type = "tar.gz",
        sha256 = "c59df8ac95d96ff9bede18eb7300b0fda5e5d8d90960e76f8e14ae765eedbf1f",
        strip_prefix = "term-0.7.0",
        build_file = Label("//cargo/remote:BUILD.term-0.7.0.bazel"),
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
        name = "raze__thin_slice__0_1_1",
        url = "https://crates.io/api/v1/crates/thin-slice/0.1.1/download",
        type = "tar.gz",
        sha256 = "8eaa81235c7058867fa8c0e7314f33dcce9c215f535d1913822a2b3f5e289f3c",
        strip_prefix = "thin-slice-0.1.1",
        build_file = Label("//cargo/remote:BUILD.thin-slice-0.1.1.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__thiserror__1_0_30",
        url = "https://crates.io/api/v1/crates/thiserror/1.0.30/download",
        type = "tar.gz",
        sha256 = "854babe52e4df1653706b98fcfc05843010039b406875930a70e4d9644e5c417",
        strip_prefix = "thiserror-1.0.30",
        build_file = Label("//cargo/remote:BUILD.thiserror-1.0.30.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__thiserror_impl__1_0_30",
        url = "https://crates.io/api/v1/crates/thiserror-impl/1.0.30/download",
        type = "tar.gz",
        sha256 = "aa32fd3f627f367fe16f893e2597ae3c05020f8bba2666a4e6ea73d377e5714b",
        strip_prefix = "thiserror-impl-1.0.30",
        build_file = Label("//cargo/remote:BUILD.thiserror-impl-1.0.30.bazel"),
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
        name = "raze__time__0_1_44",
        url = "https://crates.io/api/v1/crates/time/0.1.44/download",
        type = "tar.gz",
        sha256 = "6db9e6914ab8b1ae1c260a4ae7a49b6c5611b40328a735b21862567685e73255",
        strip_prefix = "time-0.1.44",
        build_file = Label("//cargo/remote:BUILD.time-0.1.44.bazel"),
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
        name = "raze__tinyvec__1_5_1",
        url = "https://crates.io/api/v1/crates/tinyvec/1.5.1/download",
        type = "tar.gz",
        sha256 = "2c1c1d5a42b6245520c249549ec267180beaffcc0615401ac8e31853d4b6d8d2",
        strip_prefix = "tinyvec-1.5.1",
        build_file = Label("//cargo/remote:BUILD.tinyvec-1.5.1.bazel"),
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
        name = "raze__tokio__1_14_0",
        url = "https://crates.io/api/v1/crates/tokio/1.14.0/download",
        type = "tar.gz",
        sha256 = "70e992e41e0d2fb9f755b37446f20900f64446ef54874f40a60c78f021ac6144",
        strip_prefix = "tokio-1.14.0",
        build_file = Label("//cargo/remote:BUILD.tokio-1.14.0.bazel"),
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
        name = "raze__tokio_macros__1_6_0",
        url = "https://crates.io/api/v1/crates/tokio-macros/1.6.0/download",
        type = "tar.gz",
        sha256 = "c9efc1aba077437943f7515666aa2b882dfabfbfdf89c819ea75a8d6e9eaba5e",
        strip_prefix = "tokio-macros-1.6.0",
        build_file = Label("//cargo/remote:BUILD.tokio-macros-1.6.0.bazel"),
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
        name = "raze__tokio_util__0_6_9",
        url = "https://crates.io/api/v1/crates/tokio-util/0.6.9/download",
        type = "tar.gz",
        sha256 = "9e99e1983e5d376cd8eb4b66604d2e99e79f5bd988c3055891dcd8c9e2604cc0",
        strip_prefix = "tokio-util-0.6.9",
        build_file = Label("//cargo/remote:BUILD.tokio-util-0.6.9.bazel"),
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
        name = "raze__tracing__0_1_29",
        url = "https://crates.io/api/v1/crates/tracing/0.1.29/download",
        type = "tar.gz",
        sha256 = "375a639232caf30edfc78e8d89b2d4c375515393e7af7e16f01cd96917fb2105",
        strip_prefix = "tracing-0.1.29",
        build_file = Label("//cargo/remote:BUILD.tracing-0.1.29.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__tracing_core__0_1_21",
        url = "https://crates.io/api/v1/crates/tracing-core/0.1.21/download",
        type = "tar.gz",
        sha256 = "1f4ed65637b8390770814083d20756f87bfa2c21bf2f110babdc5438351746e4",
        strip_prefix = "tracing-core-0.1.21",
        build_file = Label("//cargo/remote:BUILD.tracing-core-0.1.21.bazel"),
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
        name = "raze__typenum__1_14_0",
        url = "https://crates.io/api/v1/crates/typenum/1.14.0/download",
        type = "tar.gz",
        sha256 = "b63708a265f51345575b27fe43f9500ad611579e764c79edbc2037b1121959ec",
        strip_prefix = "typenum-1.14.0",
        build_file = Label("//cargo/remote:BUILD.typenum-1.14.0.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__ucd_trie__0_1_3",
        url = "https://crates.io/api/v1/crates/ucd-trie/0.1.3/download",
        type = "tar.gz",
        sha256 = "56dee185309b50d1f11bfedef0fe6d036842e3fb77413abef29f8f8d1c5d4c1c",
        strip_prefix = "ucd-trie-0.1.3",
        build_file = Label("//cargo/remote:BUILD.ucd-trie-0.1.3.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__unic_char_property__0_9_0",
        url = "https://crates.io/api/v1/crates/unic-char-property/0.9.0/download",
        type = "tar.gz",
        sha256 = "a8c57a407d9b6fa02b4795eb81c5b6652060a15a7903ea981f3d723e6c0be221",
        strip_prefix = "unic-char-property-0.9.0",
        build_file = Label("//cargo/remote:BUILD.unic-char-property-0.9.0.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__unic_char_range__0_9_0",
        url = "https://crates.io/api/v1/crates/unic-char-range/0.9.0/download",
        type = "tar.gz",
        sha256 = "0398022d5f700414f6b899e10b8348231abf9173fa93144cbc1a43b9793c1fbc",
        strip_prefix = "unic-char-range-0.9.0",
        build_file = Label("//cargo/remote:BUILD.unic-char-range-0.9.0.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__unic_common__0_9_0",
        url = "https://crates.io/api/v1/crates/unic-common/0.9.0/download",
        type = "tar.gz",
        sha256 = "80d7ff825a6a654ee85a63e80f92f054f904f21e7d12da4e22f9834a4aaa35bc",
        strip_prefix = "unic-common-0.9.0",
        build_file = Label("//cargo/remote:BUILD.unic-common-0.9.0.bazel"),
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
        name = "raze__unic_ucd_category__0_9_0",
        url = "https://crates.io/api/v1/crates/unic-ucd-category/0.9.0/download",
        type = "tar.gz",
        sha256 = "1b8d4591f5fcfe1bd4453baaf803c40e1b1e69ff8455c47620440b46efef91c0",
        strip_prefix = "unic-ucd-category-0.9.0",
        build_file = Label("//cargo/remote:BUILD.unic-ucd-category-0.9.0.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__unic_ucd_version__0_9_0",
        url = "https://crates.io/api/v1/crates/unic-ucd-version/0.9.0/download",
        type = "tar.gz",
        sha256 = "96bd2f2237fe450fcd0a1d2f5f4e91711124f7857ba2e964247776ebeeb7b0c4",
        strip_prefix = "unic-ucd-version-0.9.0",
        build_file = Label("//cargo/remote:BUILD.unic-ucd-version-0.9.0.bazel"),
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
        name = "raze__unicode_bidi__0_3_7",
        url = "https://crates.io/api/v1/crates/unicode-bidi/0.3.7/download",
        type = "tar.gz",
        sha256 = "1a01404663e3db436ed2746d9fefef640d868edae3cceb81c3b8d5732fda678f",
        strip_prefix = "unicode-bidi-0.3.7",
        build_file = Label("//cargo/remote:BUILD.unicode-bidi-0.3.7.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__unicode_normalization__0_1_19",
        url = "https://crates.io/api/v1/crates/unicode-normalization/0.1.19/download",
        type = "tar.gz",
        sha256 = "d54590932941a9e9266f0832deed84ebe1bf2e4c9e4a3554d393d18f5e854bf9",
        strip_prefix = "unicode-normalization-0.1.19",
        build_file = Label("//cargo/remote:BUILD.unicode-normalization-0.1.19.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__unicode_segmentation__1_8_0",
        url = "https://crates.io/api/v1/crates/unicode-segmentation/1.8.0/download",
        type = "tar.gz",
        sha256 = "8895849a949e7845e06bd6dc1aa51731a103c42707010a5b591c0038fb73385b",
        strip_prefix = "unicode-segmentation-1.8.0",
        build_file = Label("//cargo/remote:BUILD.unicode-segmentation-1.8.0.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__unicode_width__0_1_9",
        url = "https://crates.io/api/v1/crates/unicode-width/0.1.9/download",
        type = "tar.gz",
        sha256 = "3ed742d4ea2bd1176e236172c8429aaf54486e7ac098db29ffe6529e0ce50973",
        strip_prefix = "unicode-width-0.1.9",
        build_file = Label("//cargo/remote:BUILD.unicode-width-0.1.9.bazel"),
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
        name = "raze__url__2_2_2",
        url = "https://crates.io/api/v1/crates/url/2.2.2/download",
        type = "tar.gz",
        sha256 = "a507c383b2d33b5fc35d1861e77e6b383d158b2da5e14fe51b83dfedf6fd578c",
        strip_prefix = "url-2.2.2",
        build_file = Label("//cargo/remote:BUILD.url-2.2.2.bazel"),
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
        name = "raze__utf8_decode__1_0_1",
        url = "https://crates.io/api/v1/crates/utf8-decode/1.0.1/download",
        type = "tar.gz",
        sha256 = "ca61eb27fa339aa08826a29f03e87b99b4d8f0fc2255306fd266bb1b6a9de498",
        strip_prefix = "utf8-decode-1.0.1",
        build_file = Label("//cargo/remote:BUILD.utf8-decode-1.0.1.bazel"),
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
        name = "raze__vcpkg__0_2_15",
        url = "https://crates.io/api/v1/crates/vcpkg/0.2.15/download",
        type = "tar.gz",
        sha256 = "accd4ea62f7bb7a82fe23066fb0957d48ef677f6eeb8215f372f52e48bb32426",
        strip_prefix = "vcpkg-0.2.15",
        build_file = Label("//cargo/remote:BUILD.vcpkg-0.2.15.bazel"),
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
        name = "raze__walkdir__2_3_2",
        url = "https://crates.io/api/v1/crates/walkdir/2.3.2/download",
        type = "tar.gz",
        sha256 = "808cf2735cd4b6866113f648b791c6adc5714537bc222d9347bb203386ffda56",
        strip_prefix = "walkdir-2.3.2",
        build_file = Label("//cargo/remote:BUILD.walkdir-2.3.2.bazel"),
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
        name = "raze__wasi__0_10_0_wasi_snapshot_preview1",
        url = "https://crates.io/api/v1/crates/wasi/0.10.0+wasi-snapshot-preview1/download",
        type = "tar.gz",
        sha256 = "1a143597ca7c7793eff794def352d41792a93c481eb1042423ff7ff72ba2c31f",
        strip_prefix = "wasi-0.10.0+wasi-snapshot-preview1",
        build_file = Label("//cargo/remote:BUILD.wasi-0.10.0+wasi-snapshot-preview1.bazel"),
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
        name = "raze__wasm_bindgen__0_2_78",
        url = "https://crates.io/api/v1/crates/wasm-bindgen/0.2.78/download",
        type = "tar.gz",
        sha256 = "632f73e236b219150ea279196e54e610f5dbafa5d61786303d4da54f84e47fce",
        strip_prefix = "wasm-bindgen-0.2.78",
        build_file = Label("//cargo/remote:BUILD.wasm-bindgen-0.2.78.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__wasm_bindgen_backend__0_2_78",
        url = "https://crates.io/api/v1/crates/wasm-bindgen-backend/0.2.78/download",
        type = "tar.gz",
        sha256 = "a317bf8f9fba2476b4b2c85ef4c4af8ff39c3c7f0cdfeed4f82c34a880aa837b",
        strip_prefix = "wasm-bindgen-backend-0.2.78",
        build_file = Label("//cargo/remote:BUILD.wasm-bindgen-backend-0.2.78.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__wasm_bindgen_futures__0_4_28",
        url = "https://crates.io/api/v1/crates/wasm-bindgen-futures/0.4.28/download",
        type = "tar.gz",
        sha256 = "8e8d7523cb1f2a4c96c1317ca690031b714a51cc14e05f712446691f413f5d39",
        strip_prefix = "wasm-bindgen-futures-0.4.28",
        build_file = Label("//cargo/remote:BUILD.wasm-bindgen-futures-0.4.28.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__wasm_bindgen_macro__0_2_78",
        url = "https://crates.io/api/v1/crates/wasm-bindgen-macro/0.2.78/download",
        type = "tar.gz",
        sha256 = "d56146e7c495528bf6587663bea13a8eb588d39b36b679d83972e1a2dbbdacf9",
        strip_prefix = "wasm-bindgen-macro-0.2.78",
        build_file = Label("//cargo/remote:BUILD.wasm-bindgen-macro-0.2.78.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__wasm_bindgen_macro_support__0_2_78",
        url = "https://crates.io/api/v1/crates/wasm-bindgen-macro-support/0.2.78/download",
        type = "tar.gz",
        sha256 = "7803e0eea25835f8abdc585cd3021b3deb11543c6fe226dcd30b228857c5c5ab",
        strip_prefix = "wasm-bindgen-macro-support-0.2.78",
        build_file = Label("//cargo/remote:BUILD.wasm-bindgen-macro-support-0.2.78.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__wasm_bindgen_shared__0_2_78",
        url = "https://crates.io/api/v1/crates/wasm-bindgen-shared/0.2.78/download",
        type = "tar.gz",
        sha256 = "0237232789cf037d5480773fe568aac745bfe2afbc11a863e97901780a6b47cc",
        strip_prefix = "wasm-bindgen-shared-0.2.78",
        build_file = Label("//cargo/remote:BUILD.wasm-bindgen-shared-0.2.78.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__web_sys__0_3_55",
        url = "https://crates.io/api/v1/crates/web-sys/0.3.55/download",
        type = "tar.gz",
        sha256 = "38eb105f1c59d9eaa6b5cdc92b859d85b926e82cb2e0945cd0c9259faa6fe9fb",
        strip_prefix = "web-sys-0.3.55",
        build_file = Label("//cargo/remote:BUILD.web-sys-0.3.55.bazel"),
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
        name = "raze__which__4_2_2",
        url = "https://crates.io/api/v1/crates/which/4.2.2/download",
        type = "tar.gz",
        sha256 = "ea187a8ef279bc014ec368c27a920da2024d2a711109bfbe3440585d5cf27ad9",
        strip_prefix = "which-4.2.2",
        build_file = Label("//cargo/remote:BUILD.which-4.2.2.bazel"),
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
        name = "raze__xml5ever__0_16_2",
        url = "https://crates.io/api/v1/crates/xml5ever/0.16.2/download",
        type = "tar.gz",
        sha256 = "9234163818fd8e2418fcde330655e757900d4236acd8cc70fef345ef91f6d865",
        strip_prefix = "xml5ever-0.16.2",
        build_file = Label("//cargo/remote:BUILD.xml5ever-0.16.2.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__zip__0_5_13",
        url = "https://crates.io/api/v1/crates/zip/0.5.13/download",
        type = "tar.gz",
        sha256 = "93ab48844d61251bb3835145c521d88aa4031d7139e8485990f60ca911fa0815",
        strip_prefix = "zip-0.5.13",
        build_file = Label("//cargo/remote:BUILD.zip-0.5.13.bazel"),
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
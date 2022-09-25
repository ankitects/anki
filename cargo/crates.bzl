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
        name = "raze__aho_corasick__0_7_19",
        url = "https://crates.io/api/v1/crates/aho-corasick/0.7.19/download",
        type = "tar.gz",
        sha256 = "b4f55bd91a0978cbfd91c457a164bab8b4001c833b7f323132c0a4e1922dd44e",
        strip_prefix = "aho-corasick-0.7.19",
        build_file = Label("//cargo/remote:BUILD.aho-corasick-0.7.19.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__ammonia__3_2_1",
        url = "https://crates.io/api/v1/crates/ammonia/3.2.1/download",
        type = "tar.gz",
        sha256 = "4b477377562f3086b7778d241786e9406b883ccfaa03557c0fe0924b9349f13a",
        strip_prefix = "ammonia-3.2.1",
        build_file = Label("//cargo/remote:BUILD.ammonia-3.2.1.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__android_system_properties__0_1_5",
        url = "https://crates.io/api/v1/crates/android_system_properties/0.1.5/download",
        type = "tar.gz",
        sha256 = "819e7219dbd41043ac279b19830f2efc897156490d7fd6ea916720117ee66311",
        strip_prefix = "android_system_properties-0.1.5",
        build_file = Label("//cargo/remote:BUILD.android_system_properties-0.1.5.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__anyhow__1_0_65",
        url = "https://crates.io/api/v1/crates/anyhow/1.0.65/download",
        type = "tar.gz",
        sha256 = "98161a4e3e2184da77bb14f02184cdd111e83bbbcc9979dfee3c44b9a85f5602",
        strip_prefix = "anyhow-1.0.65",
        build_file = Label("//cargo/remote:BUILD.anyhow-1.0.65.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__arc_swap__1_5_1",
        url = "https://crates.io/api/v1/crates/arc-swap/1.5.1/download",
        type = "tar.gz",
        sha256 = "983cd8b9d4b02a6dc6ffa557262eb5858a27a0038ffffe21a0f133eaa819a164",
        strip_prefix = "arc-swap-1.5.1",
        build_file = Label("//cargo/remote:BUILD.arc-swap-1.5.1.bazel"),
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
        name = "raze__async_trait__0_1_57",
        url = "https://crates.io/api/v1/crates/async-trait/0.1.57/download",
        type = "tar.gz",
        sha256 = "76464446b8bc32758d7e88ee1a804d9914cd9b1cb264c029899680b0be29826f",
        strip_prefix = "async-trait-0.1.57",
        build_file = Label("//cargo/remote:BUILD.async-trait-0.1.57.bazel"),
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
        name = "raze__autocfg__1_1_0",
        url = "https://crates.io/api/v1/crates/autocfg/1.1.0/download",
        type = "tar.gz",
        sha256 = "d468802bab17cbc0cc575e9b053f41e72aa36bfa6b7f55e3529ffa43161b97fa",
        strip_prefix = "autocfg-1.1.0",
        build_file = Label("//cargo/remote:BUILD.autocfg-1.1.0.bazel"),
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
        name = "raze__blake3__1_3_1",
        url = "https://crates.io/api/v1/crates/blake3/1.3.1/download",
        type = "tar.gz",
        sha256 = "a08e53fc5a564bb15bfe6fae56bd71522205f1f91893f9c0116edad6496c183f",
        strip_prefix = "blake3-1.3.1",
        build_file = Label("//cargo/remote:BUILD.blake3-1.3.1.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__block_buffer__0_10_3",
        url = "https://crates.io/api/v1/crates/block-buffer/0.10.3/download",
        type = "tar.gz",
        sha256 = "69cce20737498f97b993470a6e536b8523f0af7892a4f928cceb1ac5e52ebe7e",
        strip_prefix = "block-buffer-0.10.3",
        build_file = Label("//cargo/remote:BUILD.block-buffer-0.10.3.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__bstr__0_2_17",
        url = "https://crates.io/api/v1/crates/bstr/0.2.17/download",
        type = "tar.gz",
        sha256 = "ba3569f383e8f1598449f1a423e72e99569137b47740b1da11ef19af3d5c3223",
        strip_prefix = "bstr-0.2.17",
        build_file = Label("//cargo/remote:BUILD.bstr-0.2.17.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__bumpalo__3_11_0",
        url = "https://crates.io/api/v1/crates/bumpalo/3.11.0/download",
        type = "tar.gz",
        sha256 = "c1ad822118d20d2c234f427000d5acc36eabe1e29a348c89b63dd60b13f28e5d",
        strip_prefix = "bumpalo-3.11.0",
        build_file = Label("//cargo/remote:BUILD.bumpalo-3.11.0.bazel"),
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
        name = "raze__bytes__1_2_1",
        url = "https://crates.io/api/v1/crates/bytes/1.2.1/download",
        type = "tar.gz",
        sha256 = "ec8a7b6a70fde80372154c65702f00a0f56f3e1c36abbc6c440484be248856db",
        strip_prefix = "bytes-1.2.1",
        build_file = Label("//cargo/remote:BUILD.bytes-1.2.1.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__cc__1_0_73",
        url = "https://crates.io/api/v1/crates/cc/1.0.73/download",
        type = "tar.gz",
        sha256 = "2fff2a6927b3bb87f9595d67196a70493f627687a71d87a0d692242c33f58c11",
        strip_prefix = "cc-1.0.73",
        build_file = Label("//cargo/remote:BUILD.cc-1.0.73.bazel"),
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
        name = "raze__chrono__0_4_22",
        url = "https://crates.io/api/v1/crates/chrono/0.4.22/download",
        type = "tar.gz",
        sha256 = "bfd4d1b31faaa3a89d7934dbded3111da0d2ef28e3ebccdb4f0179f5929d1ef1",
        strip_prefix = "chrono-0.4.22",
        build_file = Label("//cargo/remote:BUILD.chrono-0.4.22.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__coarsetime__0_1_22",
        url = "https://crates.io/api/v1/crates/coarsetime/0.1.22/download",
        type = "tar.gz",
        sha256 = "454038500439e141804c655b4cd1bc6a70bcb95cd2bc9463af5661b6956f0e46",
        strip_prefix = "coarsetime-0.1.22",
        build_file = Label("//cargo/remote:BUILD.coarsetime-0.1.22.bazel"),
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
        name = "raze__core_foundation__0_9_3",
        url = "https://crates.io/api/v1/crates/core-foundation/0.9.3/download",
        type = "tar.gz",
        sha256 = "194a7a9e6de53fa55116934067c844d9d749312f75c6f6d0980e8c252f8c2146",
        strip_prefix = "core-foundation-0.9.3",
        build_file = Label("//cargo/remote:BUILD.core-foundation-0.9.3.bazel"),
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
        name = "raze__crc32fast__1_3_2",
        url = "https://crates.io/api/v1/crates/crc32fast/1.3.2/download",
        type = "tar.gz",
        sha256 = "b540bd8bc810d3885c6ea91e2018302f68baba2129ab3e88f32389ee9370880d",
        strip_prefix = "crc32fast-1.3.2",
        build_file = Label("//cargo/remote:BUILD.crc32fast-1.3.2.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__crossbeam_channel__0_5_6",
        url = "https://crates.io/api/v1/crates/crossbeam-channel/0.5.6/download",
        type = "tar.gz",
        sha256 = "c2dd04ddaf88237dc3b8d8f9a3c1004b506b54b3313403944054d23c0870c521",
        strip_prefix = "crossbeam-channel-0.5.6",
        build_file = Label("//cargo/remote:BUILD.crossbeam-channel-0.5.6.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__crossbeam_utils__0_8_11",
        url = "https://crates.io/api/v1/crates/crossbeam-utils/0.8.11/download",
        type = "tar.gz",
        sha256 = "51887d4adc7b564537b15adcfb307936f8075dfcd5f00dde9a9f1d29383682bc",
        strip_prefix = "crossbeam-utils-0.8.11",
        build_file = Label("//cargo/remote:BUILD.crossbeam-utils-0.8.11.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__crypto_common__0_1_6",
        url = "https://crates.io/api/v1/crates/crypto-common/0.1.6/download",
        type = "tar.gz",
        sha256 = "1bfb12502f3fc46cca1bb51ac28df9d618d813cdc3d2f25b9fe775a34af26bb3",
        strip_prefix = "crypto-common-0.1.6",
        build_file = Label("//cargo/remote:BUILD.crypto-common-0.1.6.bazel"),
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
        new_git_repository,
        name = "raze__csv__1_1_6",
        remote = "https://github.com/ankitects/rust-csv.git",
        shallow_since = "1654675287 +1000",
        commit = "1c9d3aab6f79a7d815c69f925a46a4590c115f90",
        build_file = Label("//cargo/remote:BUILD.csv-1.1.6.bazel"),
        init_submodules = True,
    )

    maybe(
        new_git_repository,
        name = "raze__csv_core__0_1_10",
        remote = "https://github.com/ankitects/rust-csv.git",
        shallow_since = "1654675287 +1000",
        commit = "1c9d3aab6f79a7d815c69f925a46a4590c115f90",
        build_file = Label("//cargo/remote:BUILD.csv-core-0.1.10.bazel"),
        init_submodules = True,
    )

    maybe(
        http_archive,
        name = "raze__derive_more__0_99_17",
        url = "https://crates.io/api/v1/crates/derive_more/0.99.17/download",
        type = "tar.gz",
        sha256 = "4fb810d30a7c1953f91334de7244731fc3f3c10d7fe163338a35b9f640960321",
        strip_prefix = "derive_more-0.99.17",
        build_file = Label("//cargo/remote:BUILD.derive_more-0.99.17.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__digest__0_10_5",
        url = "https://crates.io/api/v1/crates/digest/0.10.5/download",
        type = "tar.gz",
        sha256 = "adfbc57365a37acbd2ebf2b64d7e69bb766e2fea813521ed536f5d0520dcf86c",
        strip_prefix = "digest-0.10.5",
        build_file = Label("//cargo/remote:BUILD.digest-0.10.5.bazel"),
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
        name = "raze__dissimilar__1_0_4",
        url = "https://crates.io/api/v1/crates/dissimilar/1.0.4/download",
        type = "tar.gz",
        sha256 = "8c97b9233581d84b8e1e689cdd3a47b6f69770084fc246e86a7f78b0d9c1d4a5",
        strip_prefix = "dissimilar-1.0.4",
        build_file = Label("//cargo/remote:BUILD.dissimilar-1.0.4.bazel"),
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
        name = "raze__either__1_8_0",
        url = "https://crates.io/api/v1/crates/either/1.8.0/download",
        type = "tar.gz",
        sha256 = "90e5c1c8368803113bf0c9584fc495a58b86dc8a29edbf8fe877d21d9507e797",
        strip_prefix = "either-1.8.0",
        build_file = Label("//cargo/remote:BUILD.either-1.8.0.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__encoding_rs__0_8_31",
        url = "https://crates.io/api/v1/crates/encoding_rs/0.8.31/download",
        type = "tar.gz",
        sha256 = "9852635589dc9f9ea1b6fe9f05b50ef208c85c834a562f0c6abb1c475736ec2b",
        strip_prefix = "encoding_rs-0.8.31",
        build_file = Label("//cargo/remote:BUILD.encoding_rs-0.8.31.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__env_logger__0_9_1",
        url = "https://crates.io/api/v1/crates/env_logger/0.9.1/download",
        type = "tar.gz",
        sha256 = "c90bf5f19754d10198ccb95b70664fc925bd1fc090a0fd9a6ebc54acc8cd6272",
        strip_prefix = "env_logger-0.9.1",
        build_file = Label("//cargo/remote:BUILD.env_logger-0.9.1.bazel"),
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
        name = "raze__fastrand__1_8_0",
        url = "https://crates.io/api/v1/crates/fastrand/1.8.0/download",
        type = "tar.gz",
        sha256 = "a7a407cfaa3385c4ae6b23e84623d48c2798d06e3e6a1878f7f59f17b3f86499",
        strip_prefix = "fastrand-1.8.0",
        build_file = Label("//cargo/remote:BUILD.fastrand-1.8.0.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__fixedbitset__0_4_2",
        url = "https://crates.io/api/v1/crates/fixedbitset/0.4.2/download",
        type = "tar.gz",
        sha256 = "0ce7134b9999ecaf8bcd65542e436736ef32ddca1b3e06094cb6ec5755203b80",
        strip_prefix = "fixedbitset-0.4.2",
        build_file = Label("//cargo/remote:BUILD.fixedbitset-0.4.2.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__flate2__1_0_24",
        url = "https://crates.io/api/v1/crates/flate2/1.0.24/download",
        type = "tar.gz",
        sha256 = "f82b0f4c27ad9f8bfd1f3208d882da2b09c301bc1c828fd3a00d0216d2fbbff6",
        strip_prefix = "flate2-1.0.24",
        build_file = Label("//cargo/remote:BUILD.flate2-1.0.24.bazel"),
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
        name = "raze__form_urlencoded__1_1_0",
        url = "https://crates.io/api/v1/crates/form_urlencoded/1.1.0/download",
        type = "tar.gz",
        sha256 = "a9c384f161156f5260c24a097c56119f9be8c798586aecc13afbcbe7b7e26bf8",
        strip_prefix = "form_urlencoded-1.1.0",
        build_file = Label("//cargo/remote:BUILD.form_urlencoded-1.1.0.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__futf__0_1_5",
        url = "https://crates.io/api/v1/crates/futf/0.1.5/download",
        type = "tar.gz",
        sha256 = "df420e2e84819663797d1ec6544b13c5be84629e7bb00dc960d6917db2987843",
        strip_prefix = "futf-0.1.5",
        build_file = Label("//cargo/remote:BUILD.futf-0.1.5.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__futures__0_3_24",
        url = "https://crates.io/api/v1/crates/futures/0.3.24/download",
        type = "tar.gz",
        sha256 = "7f21eda599937fba36daeb58a22e8f5cee2d14c4a17b5b7739c7c8e5e3b8230c",
        strip_prefix = "futures-0.3.24",
        build_file = Label("//cargo/remote:BUILD.futures-0.3.24.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__futures_channel__0_3_24",
        url = "https://crates.io/api/v1/crates/futures-channel/0.3.24/download",
        type = "tar.gz",
        sha256 = "30bdd20c28fadd505d0fd6712cdfcb0d4b5648baf45faef7f852afb2399bb050",
        strip_prefix = "futures-channel-0.3.24",
        build_file = Label("//cargo/remote:BUILD.futures-channel-0.3.24.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__futures_core__0_3_24",
        url = "https://crates.io/api/v1/crates/futures-core/0.3.24/download",
        type = "tar.gz",
        sha256 = "4e5aa3de05362c3fb88de6531e6296e85cde7739cccad4b9dfeeb7f6ebce56bf",
        strip_prefix = "futures-core-0.3.24",
        build_file = Label("//cargo/remote:BUILD.futures-core-0.3.24.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__futures_executor__0_3_24",
        url = "https://crates.io/api/v1/crates/futures-executor/0.3.24/download",
        type = "tar.gz",
        sha256 = "9ff63c23854bee61b6e9cd331d523909f238fc7636290b96826e9cfa5faa00ab",
        strip_prefix = "futures-executor-0.3.24",
        build_file = Label("//cargo/remote:BUILD.futures-executor-0.3.24.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__futures_io__0_3_24",
        url = "https://crates.io/api/v1/crates/futures-io/0.3.24/download",
        type = "tar.gz",
        sha256 = "bbf4d2a7a308fd4578637c0b17c7e1c7ba127b8f6ba00b29f717e9655d85eb68",
        strip_prefix = "futures-io-0.3.24",
        build_file = Label("//cargo/remote:BUILD.futures-io-0.3.24.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__futures_macro__0_3_24",
        url = "https://crates.io/api/v1/crates/futures-macro/0.3.24/download",
        type = "tar.gz",
        sha256 = "42cd15d1c7456c04dbdf7e88bcd69760d74f3a798d6444e16974b505b0e62f17",
        strip_prefix = "futures-macro-0.3.24",
        build_file = Label("//cargo/remote:BUILD.futures-macro-0.3.24.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__futures_sink__0_3_24",
        url = "https://crates.io/api/v1/crates/futures-sink/0.3.24/download",
        type = "tar.gz",
        sha256 = "21b20ba5a92e727ba30e72834706623d94ac93a725410b6a6b6fbc1b07f7ba56",
        strip_prefix = "futures-sink-0.3.24",
        build_file = Label("//cargo/remote:BUILD.futures-sink-0.3.24.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__futures_task__0_3_24",
        url = "https://crates.io/api/v1/crates/futures-task/0.3.24/download",
        type = "tar.gz",
        sha256 = "a6508c467c73851293f390476d4491cf4d227dbabcd4170f3bb6044959b294f1",
        strip_prefix = "futures-task-0.3.24",
        build_file = Label("//cargo/remote:BUILD.futures-task-0.3.24.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__futures_util__0_3_24",
        url = "https://crates.io/api/v1/crates/futures-util/0.3.24/download",
        type = "tar.gz",
        sha256 = "44fb6cb1be61cc1d2e43b262516aafcf63b241cffdb1d3fa115f91d9c7b09c90",
        strip_prefix = "futures-util-0.3.24",
        build_file = Label("//cargo/remote:BUILD.futures-util-0.3.24.bazel"),
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
        name = "raze__generic_array__0_14_6",
        url = "https://crates.io/api/v1/crates/generic-array/0.14.6/download",
        type = "tar.gz",
        sha256 = "bff49e947297f3312447abdca79f45f4738097cc82b06e72054d2223f601f1b9",
        strip_prefix = "generic-array-0.14.6",
        build_file = Label("//cargo/remote:BUILD.generic-array-0.14.6.bazel"),
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
        name = "raze__getrandom__0_2_7",
        url = "https://crates.io/api/v1/crates/getrandom/0.2.7/download",
        type = "tar.gz",
        sha256 = "4eb1a864a501629691edf6c15a593b7a51eebaa1e8468e9ddc623de7c9b58ec6",
        strip_prefix = "getrandom-0.2.7",
        build_file = Label("//cargo/remote:BUILD.getrandom-0.2.7.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__h2__0_3_14",
        url = "https://crates.io/api/v1/crates/h2/0.3.14/download",
        type = "tar.gz",
        sha256 = "5ca32592cf21ac7ccab1825cd87f6c9b3d9022c44d086172ed0966bec8af30be",
        strip_prefix = "h2-0.3.14",
        build_file = Label("//cargo/remote:BUILD.h2-0.3.14.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__hashbrown__0_12_3",
        url = "https://crates.io/api/v1/crates/hashbrown/0.12.3/download",
        type = "tar.gz",
        sha256 = "8a9ee70c43aaf417c914396645a0fa852624801b24ebb7ae78fe8272889ac888",
        strip_prefix = "hashbrown-0.12.3",
        build_file = Label("//cargo/remote:BUILD.hashbrown-0.12.3.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__hashlink__0_8_1",
        url = "https://crates.io/api/v1/crates/hashlink/0.8.1/download",
        type = "tar.gz",
        sha256 = "69fe1fcf8b4278d860ad0548329f892a3631fb63f82574df68275f34cdbe0ffa",
        strip_prefix = "hashlink-0.8.1",
        build_file = Label("//cargo/remote:BUILD.hashlink-0.8.1.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__heck__0_4_0",
        url = "https://crates.io/api/v1/crates/heck/0.4.0/download",
        type = "tar.gz",
        sha256 = "2540771e65fc8cb83cd6e8a237f70c319bd5c29f78ed1084ba5d50eeac86f7f9",
        strip_prefix = "heck-0.4.0",
        build_file = Label("//cargo/remote:BUILD.heck-0.4.0.bazel"),
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
        name = "raze__html5ever__0_25_2",
        url = "https://crates.io/api/v1/crates/html5ever/0.25.2/download",
        type = "tar.gz",
        sha256 = "e5c13fb08e5d4dfc151ee5e88bae63f7773d61852f3bdc73c9f4b9e1bde03148",
        strip_prefix = "html5ever-0.25.2",
        build_file = Label("//cargo/remote:BUILD.html5ever-0.25.2.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__html5ever__0_26_0",
        url = "https://crates.io/api/v1/crates/html5ever/0.26.0/download",
        type = "tar.gz",
        sha256 = "bea68cab48b8459f17cf1c944c67ddc572d272d9f2b274140f223ecb1da4a3b7",
        strip_prefix = "html5ever-0.26.0",
        build_file = Label("//cargo/remote:BUILD.html5ever-0.26.0.bazel"),
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
        name = "raze__http__0_2_8",
        url = "https://crates.io/api/v1/crates/http/0.2.8/download",
        type = "tar.gz",
        sha256 = "75f43d41e26995c17e71ee126451dd3941010b0514a81a9d11f3b341debc2399",
        strip_prefix = "http-0.2.8",
        build_file = Label("//cargo/remote:BUILD.http-0.2.8.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__http_body__0_4_5",
        url = "https://crates.io/api/v1/crates/http-body/0.4.5/download",
        type = "tar.gz",
        sha256 = "d5f38f16d184e36f2408a55281cd658ecbd3ca05cce6d6510a176eca393e26d1",
        strip_prefix = "http-body-0.4.5",
        build_file = Label("//cargo/remote:BUILD.http-body-0.4.5.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__httparse__1_8_0",
        url = "https://crates.io/api/v1/crates/httparse/1.8.0/download",
        type = "tar.gz",
        sha256 = "d897f394bad6a705d5f4104762e116a75639e470d80901eed05a860a95cb1904",
        strip_prefix = "httparse-1.8.0",
        build_file = Label("//cargo/remote:BUILD.httparse-1.8.0.bazel"),
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
        name = "raze__hyper__0_14_20",
        url = "https://crates.io/api/v1/crates/hyper/0.14.20/download",
        type = "tar.gz",
        sha256 = "02c929dc5c39e335a03c405292728118860721b10190d98c2a0f0efd5baafbac",
        strip_prefix = "hyper-0.14.20",
        build_file = Label("//cargo/remote:BUILD.hyper-0.14.20.bazel"),
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
        name = "raze__iana_time_zone__0_1_50",
        url = "https://crates.io/api/v1/crates/iana-time-zone/0.1.50/download",
        type = "tar.gz",
        sha256 = "fd911b35d940d2bd0bea0f9100068e5b97b51a1cbe13d13382f132e0365257a0",
        strip_prefix = "iana-time-zone-0.1.50",
        build_file = Label("//cargo/remote:BUILD.iana-time-zone-0.1.50.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__id_tree__1_8_0",
        url = "https://crates.io/api/v1/crates/id_tree/1.8.0/download",
        type = "tar.gz",
        sha256 = "bcd9db8dd5be8bde5a2624ed4b2dfb74368fe7999eb9c4940fd3ca344b61071a",
        strip_prefix = "id_tree-1.8.0",
        build_file = Label("//cargo/remote:BUILD.id_tree-1.8.0.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__idna__0_3_0",
        url = "https://crates.io/api/v1/crates/idna/0.3.0/download",
        type = "tar.gz",
        sha256 = "e14ddfc70884202db2244c223200c204c2bda1bc6e0998d11b5e024d657209e6",
        strip_prefix = "idna-0.3.0",
        build_file = Label("//cargo/remote:BUILD.idna-0.3.0.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__indexmap__1_9_1",
        url = "https://crates.io/api/v1/crates/indexmap/1.9.1/download",
        type = "tar.gz",
        sha256 = "10a35a97730320ffe8e2d410b5d3b69279b98d2c14bdb8b70ea89ecf7888d41e",
        strip_prefix = "indexmap-1.9.1",
        build_file = Label("//cargo/remote:BUILD.indexmap-1.9.1.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__indoc__1_0_7",
        url = "https://crates.io/api/v1/crates/indoc/1.0.7/download",
        type = "tar.gz",
        sha256 = "adab1eaa3408fb7f0c777a73e7465fd5656136fc93b670eb6df3c88c2c1344e3",
        strip_prefix = "indoc-1.0.7",
        build_file = Label("//cargo/remote:BUILD.indoc-1.0.7.bazel"),
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
        name = "raze__ipnet__2_5_0",
        url = "https://crates.io/api/v1/crates/ipnet/2.5.0/download",
        type = "tar.gz",
        sha256 = "879d54834c8c76457ef4293a689b2a8c59b076067ad77b15efafbb05f92a592b",
        strip_prefix = "ipnet-2.5.0",
        build_file = Label("//cargo/remote:BUILD.ipnet-2.5.0.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__itertools__0_10_5",
        url = "https://crates.io/api/v1/crates/itertools/0.10.5/download",
        type = "tar.gz",
        sha256 = "b0fd2260e829bddf4cb6ea802289de2f86d6a7a690192fbe91b3f46e0f2c8473",
        strip_prefix = "itertools-0.10.5",
        build_file = Label("//cargo/remote:BUILD.itertools-0.10.5.bazel"),
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
        name = "raze__itoa__1_0_3",
        url = "https://crates.io/api/v1/crates/itoa/1.0.3/download",
        type = "tar.gz",
        sha256 = "6c8af84674fe1f223a982c933a0ee1086ac4d4052aa0fb8060c12c6ad838e754",
        strip_prefix = "itoa-1.0.3",
        build_file = Label("//cargo/remote:BUILD.itoa-1.0.3.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__jobserver__0_1_25",
        url = "https://crates.io/api/v1/crates/jobserver/0.1.25/download",
        type = "tar.gz",
        sha256 = "068b1ee6743e4d11fb9c6a1e6064b3693a1b600e7f5f5988047d98b3dc9fb90b",
        strip_prefix = "jobserver-0.1.25",
        build_file = Label("//cargo/remote:BUILD.jobserver-0.1.25.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__js_sys__0_3_60",
        url = "https://crates.io/api/v1/crates/js-sys/0.3.60/download",
        type = "tar.gz",
        sha256 = "49409df3e3bf0856b916e2ceaca09ee28e6871cf7d9ce97a692cacfdb2a25a47",
        strip_prefix = "js-sys-0.3.60",
        build_file = Label("//cargo/remote:BUILD.js-sys-0.3.60.bazel"),
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
        name = "raze__libc__0_2_133",
        url = "https://crates.io/api/v1/crates/libc/0.2.133/download",
        type = "tar.gz",
        sha256 = "c0f80d65747a3e43d1596c7c5492d95d5edddaabd45a7fcdb02b95f644164966",
        strip_prefix = "libc-0.2.133",
        build_file = Label("//cargo/remote:BUILD.libc-0.2.133.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__libsqlite3_sys__0_25_1",
        url = "https://crates.io/api/v1/crates/libsqlite3-sys/0.25.1/download",
        type = "tar.gz",
        sha256 = "9f0455f2c1bc9a7caa792907026e469c1d91761fb0ea37cbb16427c77280cf35",
        strip_prefix = "libsqlite3-sys-0.25.1",
        build_file = Label("//cargo/remote:BUILD.libsqlite3-sys-0.25.1.bazel"),
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
        name = "raze__lock_api__0_4_9",
        url = "https://crates.io/api/v1/crates/lock_api/0.4.9/download",
        type = "tar.gz",
        sha256 = "435011366fe56583b16cf956f9df0095b405b82d76425bc8981c0e22e60ec4df",
        strip_prefix = "lock_api-0.4.9",
        build_file = Label("//cargo/remote:BUILD.lock_api-0.4.9.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__log__0_4_17",
        url = "https://crates.io/api/v1/crates/log/0.4.17/download",
        type = "tar.gz",
        sha256 = "abb12e687cfb44aa40f41fc3978ef76448f9b6038cad6aef4259d3c095a2382e",
        strip_prefix = "log-0.4.17",
        build_file = Label("//cargo/remote:BUILD.log-0.4.17.bazel"),
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
        name = "raze__markup5ever__0_11_0",
        url = "https://crates.io/api/v1/crates/markup5ever/0.11.0/download",
        type = "tar.gz",
        sha256 = "7a2629bb1404f3d34c2e921f21fd34ba00b206124c81f65c50b43b6aaefeb016",
        strip_prefix = "markup5ever-0.11.0",
        build_file = Label("//cargo/remote:BUILD.markup5ever-0.11.0.bazel"),
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
        name = "raze__memchr__2_5_0",
        url = "https://crates.io/api/v1/crates/memchr/2.5.0/download",
        type = "tar.gz",
        sha256 = "2dffe52ecf27772e601905b7522cb4ef790d2cc203488bbd0e2fe85fcb74566d",
        strip_prefix = "memchr-2.5.0",
        build_file = Label("//cargo/remote:BUILD.memchr-2.5.0.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__memoffset__0_6_5",
        url = "https://crates.io/api/v1/crates/memoffset/0.6.5/download",
        type = "tar.gz",
        sha256 = "5aa361d4faea93603064a027415f07bd8e1d5c88c9fbf68bf56a285428fd79ce",
        strip_prefix = "memoffset-0.6.5",
        build_file = Label("//cargo/remote:BUILD.memoffset-0.6.5.bazel"),
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
        name = "raze__mime_guess__2_0_4",
        url = "https://crates.io/api/v1/crates/mime_guess/2.0.4/download",
        type = "tar.gz",
        sha256 = "4192263c238a5f0d0c6bfd21f336a313a4ce1c450542449ca191bb657b4642ef",
        strip_prefix = "mime_guess-2.0.4",
        build_file = Label("//cargo/remote:BUILD.mime_guess-2.0.4.bazel"),
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
        name = "raze__miniz_oxide__0_5_4",
        url = "https://crates.io/api/v1/crates/miniz_oxide/0.5.4/download",
        type = "tar.gz",
        sha256 = "96590ba8f175222643a85693f33d26e9c8a015f599c216509b1a6894af675d34",
        strip_prefix = "miniz_oxide-0.5.4",
        build_file = Label("//cargo/remote:BUILD.miniz_oxide-0.5.4.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__mio__0_8_4",
        url = "https://crates.io/api/v1/crates/mio/0.8.4/download",
        type = "tar.gz",
        sha256 = "57ee1c23c7c63b0c9250c339ffdc69255f110b298b901b9f6c82547b7b87caaf",
        strip_prefix = "mio-0.8.4",
        build_file = Label("//cargo/remote:BUILD.mio-0.8.4.bazel"),
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
        name = "raze__native_tls__0_2_10",
        url = "https://crates.io/api/v1/crates/native-tls/0.2.10/download",
        type = "tar.gz",
        sha256 = "fd7e2f3618557f980e0b17e8856252eee3c97fa12c54dff0ca290fb6266ca4a9",
        strip_prefix = "native-tls-0.2.10",
        build_file = Label("//cargo/remote:BUILD.native-tls-0.2.10.bazel"),
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
        name = "raze__nom__7_1_1",
        url = "https://crates.io/api/v1/crates/nom/7.1.1/download",
        type = "tar.gz",
        sha256 = "a8903e5a29a317527874d0402f867152a3d21c908bb0b933e416c65e301d4c36",
        strip_prefix = "nom-7.1.1",
        build_file = Label("//cargo/remote:BUILD.nom-7.1.1.bazel"),
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
        name = "raze__num_integer__0_1_45",
        url = "https://crates.io/api/v1/crates/num-integer/0.1.45/download",
        type = "tar.gz",
        sha256 = "225d3389fb3509a24c93f5c29eb6bde2586b98d9f016636dff58d7c6f7569cd9",
        strip_prefix = "num-integer-0.1.45",
        build_file = Label("//cargo/remote:BUILD.num-integer-0.1.45.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__num_traits__0_2_15",
        url = "https://crates.io/api/v1/crates/num-traits/0.2.15/download",
        type = "tar.gz",
        sha256 = "578ede34cf02f8924ab9447f50c28075b4d3e5b269972345e7e0372b38c6cdcd",
        strip_prefix = "num-traits-0.2.15",
        build_file = Label("//cargo/remote:BUILD.num-traits-0.2.15.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__num_cpus__1_13_1",
        url = "https://crates.io/api/v1/crates/num_cpus/1.13.1/download",
        type = "tar.gz",
        sha256 = "19e64526ebdee182341572e50e9ad03965aa510cd94427a4549448f285e957a1",
        strip_prefix = "num_cpus-1.13.1",
        build_file = Label("//cargo/remote:BUILD.num_cpus-1.13.1.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__num_enum__0_5_7",
        url = "https://crates.io/api/v1/crates/num_enum/0.5.7/download",
        type = "tar.gz",
        sha256 = "cf5395665662ef45796a4ff5486c5d41d29e0c09640af4c5f17fd94ee2c119c9",
        strip_prefix = "num_enum-0.5.7",
        build_file = Label("//cargo/remote:BUILD.num_enum-0.5.7.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__num_enum_derive__0_5_7",
        url = "https://crates.io/api/v1/crates/num_enum_derive/0.5.7/download",
        type = "tar.gz",
        sha256 = "3b0498641e53dd6ac1a4f22547548caa6864cc4933784319cd1775271c5a46ce",
        strip_prefix = "num_enum_derive-0.5.7",
        build_file = Label("//cargo/remote:BUILD.num_enum_derive-0.5.7.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__num_threads__0_1_6",
        url = "https://crates.io/api/v1/crates/num_threads/0.1.6/download",
        type = "tar.gz",
        sha256 = "2819ce041d2ee131036f4fc9d6ae7ae125a3a40e97ba64d04fe799ad9dabbb44",
        strip_prefix = "num_threads-0.1.6",
        build_file = Label("//cargo/remote:BUILD.num_threads-0.1.6.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__once_cell__1_15_0",
        url = "https://crates.io/api/v1/crates/once_cell/1.15.0/download",
        type = "tar.gz",
        sha256 = "e82dad04139b71a90c080c8463fe0dc7902db5192d939bd0950f074d014339e1",
        strip_prefix = "once_cell-1.15.0",
        build_file = Label("//cargo/remote:BUILD.once_cell-1.15.0.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__openssl__0_10_41",
        url = "https://crates.io/api/v1/crates/openssl/0.10.41/download",
        type = "tar.gz",
        sha256 = "618febf65336490dfcf20b73f885f5651a0c89c64c2d4a8c3662585a70bf5bd0",
        strip_prefix = "openssl-0.10.41",
        build_file = Label("//cargo/remote:BUILD.openssl-0.10.41.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__openssl_macros__0_1_0",
        url = "https://crates.io/api/v1/crates/openssl-macros/0.1.0/download",
        type = "tar.gz",
        sha256 = "b501e44f11665960c7e7fcf062c7d96a14ade4aa98116c004b2e37b5be7d736c",
        strip_prefix = "openssl-macros-0.1.0",
        build_file = Label("//cargo/remote:BUILD.openssl-macros-0.1.0.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__openssl_probe__0_1_5",
        url = "https://crates.io/api/v1/crates/openssl-probe/0.1.5/download",
        type = "tar.gz",
        sha256 = "ff011a302c396a5197692431fc1948019154afc178baf7d8e37367442a4601cf",
        strip_prefix = "openssl-probe-0.1.5",
        build_file = Label("//cargo/remote:BUILD.openssl-probe-0.1.5.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__openssl_sys__0_9_75",
        url = "https://crates.io/api/v1/crates/openssl-sys/0.9.75/download",
        type = "tar.gz",
        sha256 = "e5f9bd0c2710541a3cda73d6f9ac4f1b240de4ae261065d309dbe73d9dceb42f",
        strip_prefix = "openssl-sys-0.9.75",
        build_file = Label("//cargo/remote:BUILD.openssl-sys-0.9.75.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__parking_lot__0_12_1",
        url = "https://crates.io/api/v1/crates/parking_lot/0.12.1/download",
        type = "tar.gz",
        sha256 = "3742b2c103b9f06bc9fff0a37ff4912935851bee6d36f3c02bcc755bcfec228f",
        strip_prefix = "parking_lot-0.12.1",
        build_file = Label("//cargo/remote:BUILD.parking_lot-0.12.1.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__parking_lot_core__0_9_3",
        url = "https://crates.io/api/v1/crates/parking_lot_core/0.9.3/download",
        type = "tar.gz",
        sha256 = "09a279cbf25cb0757810394fbc1e359949b59e348145c643a939a525692e6929",
        strip_prefix = "parking_lot_core-0.9.3",
        build_file = Label("//cargo/remote:BUILD.parking_lot_core-0.9.3.bazel"),
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
        name = "raze__percent_encoding__2_2_0",
        url = "https://crates.io/api/v1/crates/percent-encoding/2.2.0/download",
        type = "tar.gz",
        sha256 = "478c572c3d73181ff3c2539045f6eb99e5491218eae919370993b890cdbdd98e",
        strip_prefix = "percent-encoding-2.2.0",
        build_file = Label("//cargo/remote:BUILD.percent-encoding-2.2.0.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__petgraph__0_6_2",
        url = "https://crates.io/api/v1/crates/petgraph/0.6.2/download",
        type = "tar.gz",
        sha256 = "e6d5014253a1331579ce62aa67443b4a658c5e7dd03d4bc6d302b94474888143",
        strip_prefix = "petgraph-0.6.2",
        build_file = Label("//cargo/remote:BUILD.petgraph-0.6.2.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__phf__0_10_1",
        url = "https://crates.io/api/v1/crates/phf/0.10.1/download",
        type = "tar.gz",
        sha256 = "fabbf1ead8a5bcbc20f5f8b939ee3f5b0f6f281b6ad3468b84656b658b455259",
        strip_prefix = "phf-0.10.1",
        build_file = Label("//cargo/remote:BUILD.phf-0.10.1.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__phf__0_11_1",
        url = "https://crates.io/api/v1/crates/phf/0.11.1/download",
        type = "tar.gz",
        sha256 = "928c6535de93548188ef63bb7c4036bd415cd8f36ad25af44b9789b2ee72a48c",
        strip_prefix = "phf-0.11.1",
        build_file = Label("//cargo/remote:BUILD.phf-0.11.1.bazel"),
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
        name = "raze__phf_codegen__0_10_0",
        url = "https://crates.io/api/v1/crates/phf_codegen/0.10.0/download",
        type = "tar.gz",
        sha256 = "4fb1c3a8bc4dd4e5cfce29b44ffc14bedd2ee294559a294e2a4d4c9e9a6a13cd",
        strip_prefix = "phf_codegen-0.10.0",
        build_file = Label("//cargo/remote:BUILD.phf_codegen-0.10.0.bazel"),
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
        name = "raze__phf_generator__0_11_1",
        url = "https://crates.io/api/v1/crates/phf_generator/0.11.1/download",
        type = "tar.gz",
        sha256 = "b1181c94580fa345f50f19d738aaa39c0ed30a600d95cb2d3e23f94266f14fbf",
        strip_prefix = "phf_generator-0.11.1",
        build_file = Label("//cargo/remote:BUILD.phf_generator-0.11.1.bazel"),
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
        name = "raze__phf_macros__0_11_1",
        url = "https://crates.io/api/v1/crates/phf_macros/0.11.1/download",
        type = "tar.gz",
        sha256 = "92aacdc5f16768709a569e913f7451034034178b05bdc8acda226659a3dccc66",
        strip_prefix = "phf_macros-0.11.1",
        build_file = Label("//cargo/remote:BUILD.phf_macros-0.11.1.bazel"),
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
        name = "raze__phf_shared__0_11_1",
        url = "https://crates.io/api/v1/crates/phf_shared/0.11.1/download",
        type = "tar.gz",
        sha256 = "e1fb5f6f826b772a8d4c0394209441e7d37cbbb967ae9c7e0e8134365c9ee676",
        strip_prefix = "phf_shared-0.11.1",
        build_file = Label("//cargo/remote:BUILD.phf_shared-0.11.1.bazel"),
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
        name = "raze__pin_project__1_0_12",
        url = "https://crates.io/api/v1/crates/pin-project/1.0.12/download",
        type = "tar.gz",
        sha256 = "ad29a609b6bcd67fee905812e544992d216af9d755757c05ed2d0e15a74c6ecc",
        strip_prefix = "pin-project-1.0.12",
        build_file = Label("//cargo/remote:BUILD.pin-project-1.0.12.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__pin_project_internal__1_0_12",
        url = "https://crates.io/api/v1/crates/pin-project-internal/1.0.12/download",
        type = "tar.gz",
        sha256 = "069bdb1e05adc7a8990dce9cc75370895fbe4e3d58b9b73bf1aee56359344a55",
        strip_prefix = "pin-project-internal-1.0.12",
        build_file = Label("//cargo/remote:BUILD.pin-project-internal-1.0.12.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__pin_project_lite__0_2_9",
        url = "https://crates.io/api/v1/crates/pin-project-lite/0.2.9/download",
        type = "tar.gz",
        sha256 = "e0a7ae3ac2f1173085d398531c705756c94a4c56843785df85a60c1a0afac116",
        strip_prefix = "pin-project-lite-0.2.9",
        build_file = Label("//cargo/remote:BUILD.pin-project-lite-0.2.9.bazel"),
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
        name = "raze__pkg_config__0_3_25",
        url = "https://crates.io/api/v1/crates/pkg-config/0.3.25/download",
        type = "tar.gz",
        sha256 = "1df8c4ec4b0627e53bdf214615ad287367e482558cf84b109250b37464dc03ae",
        strip_prefix = "pkg-config-0.3.25",
        build_file = Label("//cargo/remote:BUILD.pkg-config-0.3.25.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__ppv_lite86__0_2_16",
        url = "https://crates.io/api/v1/crates/ppv-lite86/0.2.16/download",
        type = "tar.gz",
        sha256 = "eb9f9e6e233e5c4a35559a617bf40a4ec447db2e84c20b55a6f83167b7e57872",
        strip_prefix = "ppv-lite86-0.2.16",
        build_file = Label("//cargo/remote:BUILD.ppv-lite86-0.2.16.bazel"),
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
        name = "raze__proc_macro_crate__1_2_1",
        url = "https://crates.io/api/v1/crates/proc-macro-crate/1.2.1/download",
        type = "tar.gz",
        sha256 = "eda0fc3b0fb7c975631757e14d9049da17374063edb6ebbcbc54d880d4fe94e9",
        strip_prefix = "proc-macro-crate-1.2.1",
        build_file = Label("//cargo/remote:BUILD.proc-macro-crate-1.2.1.bazel"),
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
        name = "raze__proc_macro2__1_0_43",
        url = "https://crates.io/api/v1/crates/proc-macro2/1.0.43/download",
        type = "tar.gz",
        sha256 = "0a2ca2c61bc9f3d74d2886294ab7b9853abd9c1ad903a3ac7815c58989bb7bab",
        strip_prefix = "proc-macro2-1.0.43",
        build_file = Label("//cargo/remote:BUILD.proc-macro2-1.0.43.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__prost__0_11_0",
        url = "https://crates.io/api/v1/crates/prost/0.11.0/download",
        type = "tar.gz",
        sha256 = "399c3c31cdec40583bb68f0b18403400d01ec4289c383aa047560439952c4dd7",
        strip_prefix = "prost-0.11.0",
        build_file = Label("//cargo/remote:BUILD.prost-0.11.0.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__prost_build__0_11_1",
        url = "https://crates.io/api/v1/crates/prost-build/0.11.1/download",
        type = "tar.gz",
        sha256 = "7f835c582e6bd972ba8347313300219fed5bfa52caf175298d860b61ff6069bb",
        strip_prefix = "prost-build-0.11.1",
        build_file = Label("//cargo/remote:BUILD.prost-build-0.11.1.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__prost_derive__0_11_0",
        url = "https://crates.io/api/v1/crates/prost-derive/0.11.0/download",
        type = "tar.gz",
        sha256 = "7345d5f0e08c0536d7ac7229952590239e77abf0a0100a1b1d890add6ea96364",
        strip_prefix = "prost-derive-0.11.0",
        build_file = Label("//cargo/remote:BUILD.prost-derive-0.11.0.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__prost_types__0_11_1",
        url = "https://crates.io/api/v1/crates/prost-types/0.11.1/download",
        type = "tar.gz",
        sha256 = "4dfaa718ad76a44b3415e6c4d53b17c8f99160dcb3a99b10470fce8ad43f6e3e",
        strip_prefix = "prost-types-0.11.1",
        build_file = Label("//cargo/remote:BUILD.prost-types-0.11.1.bazel"),
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
        name = "raze__pulldown_cmark__0_9_2",
        url = "https://crates.io/api/v1/crates/pulldown-cmark/0.9.2/download",
        type = "tar.gz",
        sha256 = "2d9cc634bc78768157b5cbfe988ffcd1dcba95cd2b2f03a88316c08c6d00ed63",
        strip_prefix = "pulldown-cmark-0.9.2",
        build_file = Label("//cargo/remote:BUILD.pulldown-cmark-0.9.2.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__pyo3__0_17_1",
        url = "https://crates.io/api/v1/crates/pyo3/0.17.1/download",
        type = "tar.gz",
        sha256 = "12f72538a0230791398a0986a6518ebd88abc3fded89007b506ed072acc831e1",
        strip_prefix = "pyo3-0.17.1",
        build_file = Label("//cargo/remote:BUILD.pyo3-0.17.1.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__pyo3_build_config__0_17_1",
        url = "https://crates.io/api/v1/crates/pyo3-build-config/0.17.1/download",
        type = "tar.gz",
        sha256 = "fc4cf18c20f4f09995f3554e6bcf9b09bd5e4d6b67c562fdfaafa644526ba479",
        strip_prefix = "pyo3-build-config-0.17.1",
        build_file = Label("//cargo/remote:BUILD.pyo3-build-config-0.17.1.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__pyo3_ffi__0_17_1",
        url = "https://crates.io/api/v1/crates/pyo3-ffi/0.17.1/download",
        type = "tar.gz",
        sha256 = "a41877f28d8ebd600b6aa21a17b40c3b0fc4dfe73a27b6e81ab3d895e401b0e9",
        strip_prefix = "pyo3-ffi-0.17.1",
        build_file = Label("//cargo/remote:BUILD.pyo3-ffi-0.17.1.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__pyo3_macros__0_17_1",
        url = "https://crates.io/api/v1/crates/pyo3-macros/0.17.1/download",
        type = "tar.gz",
        sha256 = "2e81c8d4bcc2f216dc1b665412df35e46d12ee8d3d046b381aad05f1fcf30547",
        strip_prefix = "pyo3-macros-0.17.1",
        build_file = Label("//cargo/remote:BUILD.pyo3-macros-0.17.1.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__pyo3_macros_backend__0_17_1",
        url = "https://crates.io/api/v1/crates/pyo3-macros-backend/0.17.1/download",
        type = "tar.gz",
        sha256 = "85752a767ee19399a78272cc2ab625cd7d373b2e112b4b13db28de71fa892784",
        strip_prefix = "pyo3-macros-backend-0.17.1",
        build_file = Label("//cargo/remote:BUILD.pyo3-macros-backend-0.17.1.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__quote__1_0_21",
        url = "https://crates.io/api/v1/crates/quote/1.0.21/download",
        type = "tar.gz",
        sha256 = "bbe448f377a7d6961e30f5955f9b8d106c3f5e449d493ee1b125c1d43c2b5179",
        strip_prefix = "quote-1.0.21",
        build_file = Label("//cargo/remote:BUILD.quote-1.0.21.bazel"),
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
        name = "raze__rand__0_8_5",
        url = "https://crates.io/api/v1/crates/rand/0.8.5/download",
        type = "tar.gz",
        sha256 = "34af8d1a0e25924bc5b7c43c079c942339d8f0a8b57c39049bef581b46327404",
        strip_prefix = "rand-0.8.5",
        build_file = Label("//cargo/remote:BUILD.rand-0.8.5.bazel"),
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
        name = "raze__rand_core__0_6_4",
        url = "https://crates.io/api/v1/crates/rand_core/0.6.4/download",
        type = "tar.gz",
        sha256 = "ec0be4795e2f6a28069bec0b5ff3e2ac9bafc99e6a9a7dc3547996c5c816922c",
        strip_prefix = "rand_core-0.6.4",
        build_file = Label("//cargo/remote:BUILD.rand_core-0.6.4.bazel"),
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
        name = "raze__rand_pcg__0_2_1",
        url = "https://crates.io/api/v1/crates/rand_pcg/0.2.1/download",
        type = "tar.gz",
        sha256 = "16abd0c1b639e9eb4d7c50c0b8100b0d0f849be2349829c740fe8e6eb4816429",
        strip_prefix = "rand_pcg-0.2.1",
        build_file = Label("//cargo/remote:BUILD.rand_pcg-0.2.1.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__redox_syscall__0_2_16",
        url = "https://crates.io/api/v1/crates/redox_syscall/0.2.16/download",
        type = "tar.gz",
        sha256 = "fb5a58c1855b4b6819d59012155603f0b22ad30cad752600aadfcb695265519a",
        strip_prefix = "redox_syscall-0.2.16",
        build_file = Label("//cargo/remote:BUILD.redox_syscall-0.2.16.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__redox_users__0_4_3",
        url = "https://crates.io/api/v1/crates/redox_users/0.4.3/download",
        type = "tar.gz",
        sha256 = "b033d837a7cf162d7993aded9304e30a83213c648b6e389db233191f891e5c2b",
        strip_prefix = "redox_users-0.4.3",
        build_file = Label("//cargo/remote:BUILD.redox_users-0.4.3.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__regex__1_6_0",
        url = "https://crates.io/api/v1/crates/regex/1.6.0/download",
        type = "tar.gz",
        sha256 = "4c4eb3267174b8c6c2f654116623910a0fef09c4753f8dd83db29c48a0df988b",
        strip_prefix = "regex-1.6.0",
        build_file = Label("//cargo/remote:BUILD.regex-1.6.0.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__regex_automata__0_1_10",
        url = "https://crates.io/api/v1/crates/regex-automata/0.1.10/download",
        type = "tar.gz",
        sha256 = "6c230d73fb8d8c1b9c0b3135c5142a8acee3a0558fb8db5cf1cb65f8d7862132",
        strip_prefix = "regex-automata-0.1.10",
        build_file = Label("//cargo/remote:BUILD.regex-automata-0.1.10.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__regex_syntax__0_6_27",
        url = "https://crates.io/api/v1/crates/regex-syntax/0.6.27/download",
        type = "tar.gz",
        sha256 = "a3f87b73ce11b1619a3c6332f45341e0047173771e8b8b73f87bfeefb7b56244",
        strip_prefix = "regex-syntax-0.6.27",
        build_file = Label("//cargo/remote:BUILD.regex-syntax-0.6.27.bazel"),
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
        name = "raze__rusqlite__0_28_0",
        url = "https://crates.io/api/v1/crates/rusqlite/0.28.0/download",
        type = "tar.gz",
        sha256 = "01e213bc3ecb39ac32e81e51ebe31fd888a940515173e3a18a35f8c6e896422a",
        strip_prefix = "rusqlite-0.28.0",
        build_file = Label("//cargo/remote:BUILD.rusqlite-0.28.0.bazel"),
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
        name = "raze__rustc_version__0_4_0",
        url = "https://crates.io/api/v1/crates/rustc_version/0.4.0/download",
        type = "tar.gz",
        sha256 = "bfa0f585226d2e68097d4f95d113b15b83a82e819ab25717ec0590d9584ef366",
        strip_prefix = "rustc_version-0.4.0",
        build_file = Label("//cargo/remote:BUILD.rustc_version-0.4.0.bazel"),
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
        name = "raze__rustversion__1_0_9",
        url = "https://crates.io/api/v1/crates/rustversion/1.0.9/download",
        type = "tar.gz",
        sha256 = "97477e48b4cf8603ad5f7aaf897467cf42ab4218a38ef76fb14c2d6773a6d6a8",
        strip_prefix = "rustversion-1.0.9",
        build_file = Label("//cargo/remote:BUILD.rustversion-1.0.9.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__ryu__1_0_11",
        url = "https://crates.io/api/v1/crates/ryu/1.0.11/download",
        type = "tar.gz",
        sha256 = "4501abdff3ae82a1c1b477a17252eb69cee9e66eb915c1abaa4f44d873df9f09",
        strip_prefix = "ryu-1.0.11",
        build_file = Label("//cargo/remote:BUILD.ryu-1.0.11.bazel"),
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
        name = "raze__schannel__0_1_20",
        url = "https://crates.io/api/v1/crates/schannel/0.1.20/download",
        type = "tar.gz",
        sha256 = "88d6731146462ea25d9244b2ed5fd1d716d25c52e4d54aa4fb0f3c4e9854dbe2",
        strip_prefix = "schannel-0.1.20",
        build_file = Label("//cargo/remote:BUILD.schannel-0.1.20.bazel"),
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
        name = "raze__security_framework__2_7_0",
        url = "https://crates.io/api/v1/crates/security-framework/2.7.0/download",
        type = "tar.gz",
        sha256 = "2bc1bb97804af6631813c55739f771071e0f2ed33ee20b68c86ec505d906356c",
        strip_prefix = "security-framework-2.7.0",
        build_file = Label("//cargo/remote:BUILD.security-framework-2.7.0.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__security_framework_sys__2_6_1",
        url = "https://crates.io/api/v1/crates/security-framework-sys/2.6.1/download",
        type = "tar.gz",
        sha256 = "0160a13a177a45bfb43ce71c01580998474f556ad854dcbca936dd2841a5c556",
        strip_prefix = "security-framework-sys-2.6.1",
        build_file = Label("//cargo/remote:BUILD.security-framework-sys-2.6.1.bazel"),
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
        name = "raze__self_cell__0_10_2",
        url = "https://crates.io/api/v1/crates/self_cell/0.10.2/download",
        type = "tar.gz",
        sha256 = "1ef965a420fe14fdac7dd018862966a4c14094f900e1650bbc71ddd7d580c8af",
        strip_prefix = "self_cell-0.10.2",
        build_file = Label("//cargo/remote:BUILD.self_cell-0.10.2.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__semver__1_0_14",
        url = "https://crates.io/api/v1/crates/semver/1.0.14/download",
        type = "tar.gz",
        sha256 = "e25dfac463d778e353db5be2449d1cce89bd6fd23c9f1ea21310ce6e5a1b29c4",
        strip_prefix = "semver-1.0.14",
        build_file = Label("//cargo/remote:BUILD.semver-1.0.14.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__serde__1_0_145",
        url = "https://crates.io/api/v1/crates/serde/1.0.145/download",
        type = "tar.gz",
        sha256 = "728eb6351430bccb993660dfffc5a72f91ccc1295abaa8ce19b27ebe4f75568b",
        strip_prefix = "serde-1.0.145",
        build_file = Label("//cargo/remote:BUILD.serde-1.0.145.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__serde_aux__4_0_0",
        url = "https://crates.io/api/v1/crates/serde-aux/4.0.0/download",
        type = "tar.gz",
        sha256 = "c79c1a5a310c28bf9f7a4b9bd848553051120d80a5952f993c7eb62f6ed6e4c5",
        strip_prefix = "serde-aux-4.0.0",
        build_file = Label("//cargo/remote:BUILD.serde-aux-4.0.0.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__serde_derive__1_0_145",
        url = "https://crates.io/api/v1/crates/serde_derive/1.0.145/download",
        type = "tar.gz",
        sha256 = "81fa1584d3d1bcacd84c277a0dfe21f5b0f6accf4a23d04d4c6d61f1af522b4c",
        strip_prefix = "serde_derive-1.0.145",
        build_file = Label("//cargo/remote:BUILD.serde_derive-1.0.145.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__serde_json__1_0_85",
        url = "https://crates.io/api/v1/crates/serde_json/1.0.85/download",
        type = "tar.gz",
        sha256 = "e55a28e3aaef9d5ce0506d0a14dbba8054ddc7e499ef522dd8b26859ec9d4a44",
        strip_prefix = "serde_json-1.0.85",
        build_file = Label("//cargo/remote:BUILD.serde_json-1.0.85.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__serde_repr__0_1_9",
        url = "https://crates.io/api/v1/crates/serde_repr/0.1.9/download",
        type = "tar.gz",
        sha256 = "1fe39d9fbb0ebf5eb2c7cb7e2a47e4f462fad1379f1166b8ae49ad9eae89a7ca",
        strip_prefix = "serde_repr-0.1.9",
        build_file = Label("//cargo/remote:BUILD.serde_repr-0.1.9.bazel"),
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
        name = "raze__serde_urlencoded__0_7_1",
        url = "https://crates.io/api/v1/crates/serde_urlencoded/0.7.1/download",
        type = "tar.gz",
        sha256 = "d3491c14715ca2294c4d6a88f15e84739788c1d030eed8c110436aafdaa2f3fd",
        strip_prefix = "serde_urlencoded-0.7.1",
        build_file = Label("//cargo/remote:BUILD.serde_urlencoded-0.7.1.bazel"),
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
        name = "raze__sha1__0_6_1",
        url = "https://crates.io/api/v1/crates/sha1/0.6.1/download",
        type = "tar.gz",
        sha256 = "c1da05c97445caa12d05e848c4a4fcbbea29e748ac28f7e80e9b010392063770",
        strip_prefix = "sha1-0.6.1",
        build_file = Label("//cargo/remote:BUILD.sha1-0.6.1.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__sha1_smol__1_0_0",
        url = "https://crates.io/api/v1/crates/sha1_smol/1.0.0/download",
        type = "tar.gz",
        sha256 = "ae1a47186c03a32177042e55dbc5fd5aee900b8e0069a8d70fba96a9375cd012",
        strip_prefix = "sha1_smol-1.0.0",
        build_file = Label("//cargo/remote:BUILD.sha1_smol-1.0.0.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__signal_hook_registry__1_4_0",
        url = "https://crates.io/api/v1/crates/signal-hook-registry/1.4.0/download",
        type = "tar.gz",
        sha256 = "e51e73328dc4ac0c7ccbda3a494dfa03df1de2f46018127f60c693f2648455b0",
        strip_prefix = "signal-hook-registry-1.4.0",
        build_file = Label("//cargo/remote:BUILD.signal-hook-registry-1.4.0.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__siphasher__0_3_10",
        url = "https://crates.io/api/v1/crates/siphasher/0.3.10/download",
        type = "tar.gz",
        sha256 = "7bd3e3206899af3f8b12af284fafc038cc1dc2b41d1b89dd17297221c5d225de",
        strip_prefix = "siphasher-0.3.10",
        build_file = Label("//cargo/remote:BUILD.siphasher-0.3.10.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__slab__0_4_7",
        url = "https://crates.io/api/v1/crates/slab/0.4.7/download",
        type = "tar.gz",
        sha256 = "4614a76b2a8be0058caa9dbbaf66d988527d86d003c11a94fbd335d7661edcef",
        strip_prefix = "slab-0.4.7",
        build_file = Label("//cargo/remote:BUILD.slab-0.4.7.bazel"),
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
        name = "raze__slog_stdlog__4_1_1",
        url = "https://crates.io/api/v1/crates/slog-stdlog/4.1.1/download",
        type = "tar.gz",
        sha256 = "6706b2ace5bbae7291d3f8d2473e2bfab073ccd7d03670946197aec98471fa3e",
        strip_prefix = "slog-stdlog-4.1.1",
        build_file = Label("//cargo/remote:BUILD.slog-stdlog-4.1.1.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__slog_term__2_9_0",
        url = "https://crates.io/api/v1/crates/slog-term/2.9.0/download",
        type = "tar.gz",
        sha256 = "87d29185c55b7b258b4f120eab00f48557d4d9bc814f41713f449d35b0f8977c",
        strip_prefix = "slog-term-2.9.0",
        build_file = Label("//cargo/remote:BUILD.slog-term-2.9.0.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__smallvec__1_9_0",
        url = "https://crates.io/api/v1/crates/smallvec/1.9.0/download",
        type = "tar.gz",
        sha256 = "2fd0db749597d91ff862fd1d55ea87f7855a744a8425a64695b6fca237d1dad1",
        strip_prefix = "smallvec-1.9.0",
        build_file = Label("//cargo/remote:BUILD.smallvec-1.9.0.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__snowflake__1_3_0",
        url = "https://crates.io/api/v1/crates/snowflake/1.3.0/download",
        type = "tar.gz",
        sha256 = "27207bb65232eda1f588cf46db2fee75c0808d557f6b3cf19a75f5d6d7c94df1",
        strip_prefix = "snowflake-1.3.0",
        build_file = Label("//cargo/remote:BUILD.snowflake-1.3.0.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__socket2__0_4_7",
        url = "https://crates.io/api/v1/crates/socket2/0.4.7/download",
        type = "tar.gz",
        sha256 = "02e2d2db9033d13a1567121ddd7a095ee144db4e1ca1b1bda3419bc0da294ebd",
        strip_prefix = "socket2-0.4.7",
        build_file = Label("//cargo/remote:BUILD.socket2-0.4.7.bazel"),
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
        name = "raze__string_cache__0_8_4",
        url = "https://crates.io/api/v1/crates/string_cache/0.8.4/download",
        type = "tar.gz",
        sha256 = "213494b7a2b503146286049378ce02b482200519accc31872ee8be91fa820a08",
        strip_prefix = "string_cache-0.8.4",
        build_file = Label("//cargo/remote:BUILD.string_cache-0.8.4.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__string_cache_codegen__0_5_2",
        url = "https://crates.io/api/v1/crates/string_cache_codegen/0.5.2/download",
        type = "tar.gz",
        sha256 = "6bb30289b722be4ff74a408c3cc27edeaad656e06cb1fe8fa9231fa59c728988",
        strip_prefix = "string_cache_codegen-0.5.2",
        build_file = Label("//cargo/remote:BUILD.string_cache_codegen-0.5.2.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__strum__0_24_1",
        url = "https://crates.io/api/v1/crates/strum/0.24.1/download",
        type = "tar.gz",
        sha256 = "063e6045c0e62079840579a7e47a355ae92f60eb74daaf156fb1e84ba164e63f",
        strip_prefix = "strum-0.24.1",
        build_file = Label("//cargo/remote:BUILD.strum-0.24.1.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__strum_macros__0_24_3",
        url = "https://crates.io/api/v1/crates/strum_macros/0.24.3/download",
        type = "tar.gz",
        sha256 = "1e385be0d24f186b4ce2f9982191e7101bb737312ad61c1f2f984f34bcf85d59",
        strip_prefix = "strum_macros-0.24.3",
        build_file = Label("//cargo/remote:BUILD.strum_macros-0.24.3.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__subtle__2_4_1",
        url = "https://crates.io/api/v1/crates/subtle/2.4.1/download",
        type = "tar.gz",
        sha256 = "6bdef32e8150c2a081110b42772ffe7d7c9032b606bc226c8260fd97e0976601",
        strip_prefix = "subtle-2.4.1",
        build_file = Label("//cargo/remote:BUILD.subtle-2.4.1.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__syn__1_0_100",
        url = "https://crates.io/api/v1/crates/syn/1.0.100/download",
        type = "tar.gz",
        sha256 = "52205623b1b0f064a4e71182c3b18ae902267282930c6d5462c91b859668426e",
        strip_prefix = "syn-1.0.100",
        build_file = Label("//cargo/remote:BUILD.syn-1.0.100.bazel"),
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
        name = "raze__target_lexicon__0_12_4",
        url = "https://crates.io/api/v1/crates/target-lexicon/0.12.4/download",
        type = "tar.gz",
        sha256 = "c02424087780c9b71cc96799eaeddff35af2bc513278cda5c99fc1f5d026d3c1",
        strip_prefix = "target-lexicon-0.12.4",
        build_file = Label("//cargo/remote:BUILD.target-lexicon-0.12.4.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__tempfile__3_3_0",
        url = "https://crates.io/api/v1/crates/tempfile/3.3.0/download",
        type = "tar.gz",
        sha256 = "5cdb1ef4eaeeaddc8fbd371e5017057064af0911902ef36b39801f67cc6d79e4",
        strip_prefix = "tempfile-3.3.0",
        build_file = Label("//cargo/remote:BUILD.tempfile-3.3.0.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__tendril__0_4_3",
        url = "https://crates.io/api/v1/crates/tendril/0.4.3/download",
        type = "tar.gz",
        sha256 = "d24a120c5fc464a3458240ee02c299ebcb9d67b5249c8848b09d639dca8d7bb0",
        strip_prefix = "tendril-0.4.3",
        build_file = Label("//cargo/remote:BUILD.tendril-0.4.3.bazel"),
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
        name = "raze__termcolor__1_1_3",
        url = "https://crates.io/api/v1/crates/termcolor/1.1.3/download",
        type = "tar.gz",
        sha256 = "bab24d30b911b2376f3a13cc2cd443142f0c81dda04c118693e35b3835757755",
        strip_prefix = "termcolor-1.1.3",
        build_file = Label("//cargo/remote:BUILD.termcolor-1.1.3.bazel"),
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
        name = "raze__thiserror__1_0_35",
        url = "https://crates.io/api/v1/crates/thiserror/1.0.35/download",
        type = "tar.gz",
        sha256 = "c53f98874615aea268107765aa1ed8f6116782501d18e53d08b471733bea6c85",
        strip_prefix = "thiserror-1.0.35",
        build_file = Label("//cargo/remote:BUILD.thiserror-1.0.35.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__thiserror_impl__1_0_35",
        url = "https://crates.io/api/v1/crates/thiserror-impl/1.0.35/download",
        type = "tar.gz",
        sha256 = "f8b463991b4eab2d801e724172285ec4195c650e8ec79b149e6c2a8e6dd3f783",
        strip_prefix = "thiserror-impl-1.0.35",
        build_file = Label("//cargo/remote:BUILD.thiserror-impl-1.0.35.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__thread_local__1_1_4",
        url = "https://crates.io/api/v1/crates/thread_local/1.1.4/download",
        type = "tar.gz",
        sha256 = "5516c27b78311c50bf42c071425c560ac799b11c30b31f87e3081965fe5e0180",
        strip_prefix = "thread_local-1.1.4",
        build_file = Label("//cargo/remote:BUILD.thread_local-1.1.4.bazel"),
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
        name = "raze__time__0_3_14",
        url = "https://crates.io/api/v1/crates/time/0.3.14/download",
        type = "tar.gz",
        sha256 = "3c3f9a28b618c3a6b9251b6908e9c99e04b9e5c02e6581ccbb67d59c34ef7f9b",
        strip_prefix = "time-0.3.14",
        build_file = Label("//cargo/remote:BUILD.time-0.3.14.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__time_macros__0_2_4",
        url = "https://crates.io/api/v1/crates/time-macros/0.2.4/download",
        type = "tar.gz",
        sha256 = "42657b1a6f4d817cda8e7a0ace261fe0cc946cf3a80314390b22cc61ae080792",
        strip_prefix = "time-macros-0.2.4",
        build_file = Label("//cargo/remote:BUILD.time-macros-0.2.4.bazel"),
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
        name = "raze__tinyvec__1_6_0",
        url = "https://crates.io/api/v1/crates/tinyvec/1.6.0/download",
        type = "tar.gz",
        sha256 = "87cc5ceb3875bb20c2890005a4e226a4651264a5c75edb2421b52861a0a0cb50",
        strip_prefix = "tinyvec-1.6.0",
        build_file = Label("//cargo/remote:BUILD.tinyvec-1.6.0.bazel"),
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
        name = "raze__tokio__1_21_1",
        url = "https://crates.io/api/v1/crates/tokio/1.21.1/download",
        type = "tar.gz",
        sha256 = "0020c875007ad96677dcc890298f4b942882c5d4eb7cc8f439fc3bf813dc9c95",
        strip_prefix = "tokio-1.21.1",
        build_file = Label("//cargo/remote:BUILD.tokio-1.21.1.bazel"),
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
        name = "raze__tokio_macros__1_8_0",
        url = "https://crates.io/api/v1/crates/tokio-macros/1.8.0/download",
        type = "tar.gz",
        sha256 = "9724f9a975fb987ef7a3cd9be0350edcbe130698af5b8f7a631e23d42d052484",
        strip_prefix = "tokio-macros-1.8.0",
        build_file = Label("//cargo/remote:BUILD.tokio-macros-1.8.0.bazel"),
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
        name = "raze__tokio_util__0_7_4",
        url = "https://crates.io/api/v1/crates/tokio-util/0.7.4/download",
        type = "tar.gz",
        sha256 = "0bb2e075f03b3d66d8d8785356224ba688d2906a371015e225beeb65ca92c740",
        strip_prefix = "tokio-util-0.7.4",
        build_file = Label("//cargo/remote:BUILD.tokio-util-0.7.4.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__toml__0_5_9",
        url = "https://crates.io/api/v1/crates/toml/0.5.9/download",
        type = "tar.gz",
        sha256 = "8d82e1a7758622a465f8cee077614c73484dac5b836c02ff6a40d5d1010324d7",
        strip_prefix = "toml-0.5.9",
        build_file = Label("//cargo/remote:BUILD.toml-0.5.9.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__tower_service__0_3_2",
        url = "https://crates.io/api/v1/crates/tower-service/0.3.2/download",
        type = "tar.gz",
        sha256 = "b6bc1c9ce2b5135ac7f93c72918fc37feb872bdc6a5533a8b85eb4b86bfdae52",
        strip_prefix = "tower-service-0.3.2",
        build_file = Label("//cargo/remote:BUILD.tower-service-0.3.2.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__tracing__0_1_36",
        url = "https://crates.io/api/v1/crates/tracing/0.1.36/download",
        type = "tar.gz",
        sha256 = "2fce9567bd60a67d08a16488756721ba392f24f29006402881e43b19aac64307",
        strip_prefix = "tracing-0.1.36",
        build_file = Label("//cargo/remote:BUILD.tracing-0.1.36.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__tracing_core__0_1_29",
        url = "https://crates.io/api/v1/crates/tracing-core/0.1.29/download",
        type = "tar.gz",
        sha256 = "5aeea4303076558a00714b823f9ad67d58a3bbda1df83d8827d21193156e22f7",
        strip_prefix = "tracing-core-0.1.29",
        build_file = Label("//cargo/remote:BUILD.tracing-core-0.1.29.bazel"),
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
        name = "raze__typenum__1_15_0",
        url = "https://crates.io/api/v1/crates/typenum/1.15.0/download",
        type = "tar.gz",
        sha256 = "dcf81ac59edc17cc8697ff311e8f5ef2d99fcbd9817b34cec66f90b6c3dfd987",
        strip_prefix = "typenum-1.15.0",
        build_file = Label("//cargo/remote:BUILD.typenum-1.15.0.bazel"),
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
        name = "raze__unicode_bidi__0_3_8",
        url = "https://crates.io/api/v1/crates/unicode-bidi/0.3.8/download",
        type = "tar.gz",
        sha256 = "099b7128301d285f79ddd55b9a83d5e6b9e97c92e0ea0daebee7263e932de992",
        strip_prefix = "unicode-bidi-0.3.8",
        build_file = Label("//cargo/remote:BUILD.unicode-bidi-0.3.8.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__unicode_ident__1_0_4",
        url = "https://crates.io/api/v1/crates/unicode-ident/1.0.4/download",
        type = "tar.gz",
        sha256 = "dcc811dc4066ac62f84f11307873c4850cb653bfa9b1719cee2bd2204a4bc5dd",
        strip_prefix = "unicode-ident-1.0.4",
        build_file = Label("//cargo/remote:BUILD.unicode-ident-1.0.4.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__unicode_normalization__0_1_22",
        url = "https://crates.io/api/v1/crates/unicode-normalization/0.1.22/download",
        type = "tar.gz",
        sha256 = "5c5713f0fc4b5db668a2ac63cdb7bb4469d8c9fed047b1d0292cc7b0ce2ba921",
        strip_prefix = "unicode-normalization-0.1.22",
        build_file = Label("//cargo/remote:BUILD.unicode-normalization-0.1.22.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__unicode_width__0_1_10",
        url = "https://crates.io/api/v1/crates/unicode-width/0.1.10/download",
        type = "tar.gz",
        sha256 = "c0edd1e5b14653f783770bce4a4dabb4a5108a5370a5f5d8cfe8710c361f6c8b",
        strip_prefix = "unicode-width-0.1.10",
        build_file = Label("//cargo/remote:BUILD.unicode-width-0.1.10.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__unindent__0_1_10",
        url = "https://crates.io/api/v1/crates/unindent/0.1.10/download",
        type = "tar.gz",
        sha256 = "58ee9362deb4a96cef4d437d1ad49cffc9b9e92d202b6995674e928ce684f112",
        strip_prefix = "unindent-0.1.10",
        build_file = Label("//cargo/remote:BUILD.unindent-0.1.10.bazel"),
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
        name = "raze__url__2_3_1",
        url = "https://crates.io/api/v1/crates/url/2.3.1/download",
        type = "tar.gz",
        sha256 = "0d68c799ae75762b8c3fe375feb6600ef5602c883c5d21eb51c09f22b83c4643",
        strip_prefix = "url-2.3.1",
        build_file = Label("//cargo/remote:BUILD.url-2.3.1.bazel"),
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
        name = "raze__version_check__0_9_4",
        url = "https://crates.io/api/v1/crates/version_check/0.9.4/download",
        type = "tar.gz",
        sha256 = "49874b5167b65d7193b8aba1567f5c7d93d001cafc34600cee003eda787e483f",
        strip_prefix = "version_check-0.9.4",
        build_file = Label("//cargo/remote:BUILD.version_check-0.9.4.bazel"),
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
        name = "raze__wasi__0_11_0_wasi_snapshot_preview1",
        url = "https://crates.io/api/v1/crates/wasi/0.11.0+wasi-snapshot-preview1/download",
        type = "tar.gz",
        sha256 = "9c8d87e72b64a3b4db28d11ce29237c246188f4f51057d65a7eab63b7987e423",
        strip_prefix = "wasi-0.11.0+wasi-snapshot-preview1",
        build_file = Label("//cargo/remote:BUILD.wasi-0.11.0+wasi-snapshot-preview1.bazel"),
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
        name = "raze__wasm_bindgen__0_2_83",
        url = "https://crates.io/api/v1/crates/wasm-bindgen/0.2.83/download",
        type = "tar.gz",
        sha256 = "eaf9f5aceeec8be17c128b2e93e031fb8a4d469bb9c4ae2d7dc1888b26887268",
        strip_prefix = "wasm-bindgen-0.2.83",
        build_file = Label("//cargo/remote:BUILD.wasm-bindgen-0.2.83.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__wasm_bindgen_backend__0_2_83",
        url = "https://crates.io/api/v1/crates/wasm-bindgen-backend/0.2.83/download",
        type = "tar.gz",
        sha256 = "4c8ffb332579b0557b52d268b91feab8df3615f265d5270fec2a8c95b17c1142",
        strip_prefix = "wasm-bindgen-backend-0.2.83",
        build_file = Label("//cargo/remote:BUILD.wasm-bindgen-backend-0.2.83.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__wasm_bindgen_futures__0_4_33",
        url = "https://crates.io/api/v1/crates/wasm-bindgen-futures/0.4.33/download",
        type = "tar.gz",
        sha256 = "23639446165ca5a5de86ae1d8896b737ae80319560fbaa4c2887b7da6e7ebd7d",
        strip_prefix = "wasm-bindgen-futures-0.4.33",
        build_file = Label("//cargo/remote:BUILD.wasm-bindgen-futures-0.4.33.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__wasm_bindgen_macro__0_2_83",
        url = "https://crates.io/api/v1/crates/wasm-bindgen-macro/0.2.83/download",
        type = "tar.gz",
        sha256 = "052be0f94026e6cbc75cdefc9bae13fd6052cdcaf532fa6c45e7ae33a1e6c810",
        strip_prefix = "wasm-bindgen-macro-0.2.83",
        build_file = Label("//cargo/remote:BUILD.wasm-bindgen-macro-0.2.83.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__wasm_bindgen_macro_support__0_2_83",
        url = "https://crates.io/api/v1/crates/wasm-bindgen-macro-support/0.2.83/download",
        type = "tar.gz",
        sha256 = "07bc0c051dc5f23e307b13285f9d75df86bfdf816c5721e573dec1f9b8aa193c",
        strip_prefix = "wasm-bindgen-macro-support-0.2.83",
        build_file = Label("//cargo/remote:BUILD.wasm-bindgen-macro-support-0.2.83.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__wasm_bindgen_shared__0_2_83",
        url = "https://crates.io/api/v1/crates/wasm-bindgen-shared/0.2.83/download",
        type = "tar.gz",
        sha256 = "1c38c045535d93ec4f0b4defec448e4291638ee608530863b1e2ba115d4fff7f",
        strip_prefix = "wasm-bindgen-shared-0.2.83",
        build_file = Label("//cargo/remote:BUILD.wasm-bindgen-shared-0.2.83.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__web_sys__0_3_60",
        url = "https://crates.io/api/v1/crates/web-sys/0.3.60/download",
        type = "tar.gz",
        sha256 = "bcda906d8be16e728fd5adc5b729afad4e444e106ab28cd1c7256e54fa61510f",
        strip_prefix = "web-sys-0.3.60",
        build_file = Label("//cargo/remote:BUILD.web-sys-0.3.60.bazel"),
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
        name = "raze__which__4_3_0",
        url = "https://crates.io/api/v1/crates/which/4.3.0/download",
        type = "tar.gz",
        sha256 = "1c831fbbee9e129a8cf93e7747a82da9d95ba8e16621cae60ec2cdc849bacb7b",
        strip_prefix = "which-4.3.0",
        build_file = Label("//cargo/remote:BUILD.which-4.3.0.bazel"),
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
        name = "raze__windows_sys__0_36_1",
        url = "https://crates.io/api/v1/crates/windows-sys/0.36.1/download",
        type = "tar.gz",
        sha256 = "ea04155a16a59f9eab786fe12a4a450e75cdb175f9e0d80da1e17db09f55b8d2",
        strip_prefix = "windows-sys-0.36.1",
        build_file = Label("//cargo/remote:BUILD.windows-sys-0.36.1.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__windows_aarch64_msvc__0_36_1",
        url = "https://crates.io/api/v1/crates/windows_aarch64_msvc/0.36.1/download",
        type = "tar.gz",
        sha256 = "9bb8c3fd39ade2d67e9874ac4f3db21f0d710bee00fe7cab16949ec184eeaa47",
        strip_prefix = "windows_aarch64_msvc-0.36.1",
        build_file = Label("//cargo/remote:BUILD.windows_aarch64_msvc-0.36.1.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__windows_i686_gnu__0_36_1",
        url = "https://crates.io/api/v1/crates/windows_i686_gnu/0.36.1/download",
        type = "tar.gz",
        sha256 = "180e6ccf01daf4c426b846dfc66db1fc518f074baa793aa7d9b9aaeffad6a3b6",
        strip_prefix = "windows_i686_gnu-0.36.1",
        build_file = Label("//cargo/remote:BUILD.windows_i686_gnu-0.36.1.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__windows_i686_msvc__0_36_1",
        url = "https://crates.io/api/v1/crates/windows_i686_msvc/0.36.1/download",
        type = "tar.gz",
        sha256 = "e2e7917148b2812d1eeafaeb22a97e4813dfa60a3f8f78ebe204bcc88f12f024",
        strip_prefix = "windows_i686_msvc-0.36.1",
        build_file = Label("//cargo/remote:BUILD.windows_i686_msvc-0.36.1.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__windows_x86_64_gnu__0_36_1",
        url = "https://crates.io/api/v1/crates/windows_x86_64_gnu/0.36.1/download",
        type = "tar.gz",
        sha256 = "4dcd171b8776c41b97521e5da127a2d86ad280114807d0b2ab1e462bc764d9e1",
        strip_prefix = "windows_x86_64_gnu-0.36.1",
        build_file = Label("//cargo/remote:BUILD.windows_x86_64_gnu-0.36.1.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__windows_x86_64_msvc__0_36_1",
        url = "https://crates.io/api/v1/crates/windows_x86_64_msvc/0.36.1/download",
        type = "tar.gz",
        sha256 = "c811ca4a8c853ef420abd8592ba53ddbbac90410fab6903b3e79972a631f7680",
        strip_prefix = "windows_x86_64_msvc-0.36.1",
        build_file = Label("//cargo/remote:BUILD.windows_x86_64_msvc-0.36.1.bazel"),
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
        name = "raze__zip__0_6_2",
        url = "https://crates.io/api/v1/crates/zip/0.6.2/download",
        type = "tar.gz",
        sha256 = "bf225bcf73bb52cbb496e70475c7bd7a3f769df699c0020f6c7bd9a96dcf0b8d",
        strip_prefix = "zip-0.6.2",
        build_file = Label("//cargo/remote:BUILD.zip-0.6.2.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__zstd__0_11_2_zstd_1_5_2",
        url = "https://crates.io/api/v1/crates/zstd/0.11.2+zstd.1.5.2/download",
        type = "tar.gz",
        sha256 = "20cc960326ece64f010d2d2107537f26dc589a6573a316bd5b1dba685fa5fde4",
        strip_prefix = "zstd-0.11.2+zstd.1.5.2",
        build_file = Label("//cargo/remote:BUILD.zstd-0.11.2+zstd.1.5.2.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__zstd_safe__5_0_2_zstd_1_5_2",
        url = "https://crates.io/api/v1/crates/zstd-safe/5.0.2+zstd.1.5.2/download",
        type = "tar.gz",
        sha256 = "1d2a5585e04f9eea4b2a3d1eca508c4dee9592a89ef6f450c11719da0726f4db",
        strip_prefix = "zstd-safe-5.0.2+zstd.1.5.2",
        build_file = Label("//cargo/remote:BUILD.zstd-safe-5.0.2+zstd.1.5.2.bazel"),
    )

    maybe(
        http_archive,
        name = "raze__zstd_sys__2_0_1_zstd_1_5_2",
        url = "https://crates.io/api/v1/crates/zstd-sys/2.0.1+zstd.1.5.2/download",
        type = "tar.gz",
        sha256 = "9fd07cbbc53846d9145dbffdf6dd09a7a0aa52be46741825f5c97bdd4f73f12b",
        strip_prefix = "zstd-sys-2.0.1+zstd.1.5.2",
        build_file = Label("//cargo/remote:BUILD.zstd-sys-2.0.1+zstd.1.5.2.bazel"),
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
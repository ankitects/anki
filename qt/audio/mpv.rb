# Source: https://github.com/Homebrew/homebrew-core/blob/6883a6bcb2dabfff96410f2af4002732ac12224e/Formula/m/mpv.rb
# Changes:
# - Disable Javascript/Lua/vapoursynth/yt-dlp
# - Set deployment target

class Mpv < Formula
  desc "Media player based on MPlayer and mplayer2"
  homepage "https://mpv.io"
  url "https://github.com/mpv-player/mpv/archive/refs/tags/v0.41.0.tar.gz"
  sha256 "ee21092a5ee427353392360929dc64645c54479aefdb5babc5cfbb5fad626209"
  license all_of: ["GPL-2.0-or-later", "LGPL-2.1-or-later"]
  revision 4
  compatibility_version 1
  head "https://github.com/mpv-player/mpv.git", branch: "master"

  bottle do
    sha256               arm64_tahoe:   "d82d7d7bd6619371bd9fb273c8387c7c7bb34e3e37bc8b6bb68e0622bde55bcd"
    sha256               arm64_sequoia: "b2eafc6bc8c0265d2ce356ba3819a058a06568a00d19c1cdb01b38a9a1036892"
    sha256               arm64_sonoma:  "53e833faa61805c10bd1c3bf20bc95198e03b701eeb4c544d0c0fbc0f1f4fb00"
    sha256 cellar: :any, sonoma:        "b6dd374d5896f71570fa116ab17c448f2d5dd7c7b2e824515fcb120b97c11467"
    sha256               arm64_linux:   "fc94e7920c2ec65875c212cdb01259f12e94f88976e990ea2706a9289584240e"
    sha256               x86_64_linux:  "e6af765e0c6d1ec79da369f749d6439306fbe7e98bf34fa055674e433e08a1a3"
  end

  depends_on "docutils" => :build
  depends_on "meson" => :build
  depends_on "ninja" => :build
  depends_on "pkgconf" => [:build, :test]
  depends_on xcode: :build
  depends_on "ffmpeg"
  depends_on "jpeg-turbo"
  depends_on "libarchive"
  depends_on "libass"
  depends_on "libbluray"
  depends_on "libplacebo"
  depends_on "little-cms2"
  depends_on "luajit"
  depends_on "mujs"
  depends_on "rubberband"
  depends_on "uchardet"
  depends_on "vapoursynth"
  depends_on "vulkan-loader"
  depends_on "zimg"

  on_macos do
    depends_on "molten-vk"
  end

  on_linux do
    depends_on "alsa-lib"
    depends_on "libva"
    depends_on "libvdpau"
    depends_on "libx11"
    depends_on "libxext"
    depends_on "libxfixes"
    depends_on "libxkbcommon"
    depends_on "libxpresent"
    depends_on "libxrandr"
    depends_on "libxscrnsaver"
    depends_on "libxv"
    depends_on "mesa"
    depends_on "pulseaudio"
    depends_on "wayland"
    depends_on "wayland-protocols" => :no_linkage # needed by mpv.pc
    depends_on "zlib-ng-compat"
  end

  conflicts_with cask: "stolendata-mpv", because: "both install `mpv` binaries"

  def install
    # LANG is unset by default on macOS and causes issues when calling getlocale
    # or getdefaultlocale in docutils. Force the default c/posix locale since
    # that's good enough for building the manpage.
    ENV["LC_ALL"] = "C"

    ENV["MACOSX_DEPLOYMENT_TARGET"] = "11.0"

    # force meson find ninja from homebrew
    ENV["NINJA"] = which("ninja")

    # libarchive is keg-only
    ENV.prepend_path "PKG_CONFIG_PATH", Formula["libarchive"].opt_lib/"pkgconfig" if OS.mac?

    args = %W[
      -Dbuild-date=false
      -Dhtml-build=enabled
      -Djavascript=disabled
      -Dlua=disabled
      -Dvapoursynth=disabled
      -Dytdl=disabled
      -Dlibmpv=true
      -Dlibarchive=enabled
      -Duchardet=enabled
      -Dvulkan=enabled
      --sysconfdir=#{pkgetc}
      --datadir=#{pkgshare}
      --mandir=#{man}
    ]
    if OS.linux?
      args += %w[
        -Degl=enabled
        -Dwayland=enabled
        -Dx11=enabled
      ]
    end

    system "meson", "setup", "build", *args, *std_meson_args
    system "meson", "compile", "-C", "build", "--verbose"
    system "meson", "install", "-C", "build"

    if OS.mac?
      # `pkg-config --libs mpv` includes libarchive, but that package is
      # keg-only so it needs to look for the pkgconfig file in libarchive's opt
      # path.
      libarchive = Formula["libarchive"].opt_prefix
      inreplace lib/"pkgconfig/mpv.pc" do |s|
        s.gsub!(/^Requires\.private:(.*)\blibarchive\b(.*?)(,.*)?$/,
                "Requires.private:\\1#{libarchive}/lib/pkgconfig/libarchive.pc\\3")
      end
    end

    bash_completion.install "etc/mpv.bash-completion" => "mpv"
    zsh_completion.install "etc/_mpv.zsh" => "_mpv"
  end

  test do
    system bin/"mpv", "--ao=null", "--vo=null", test_fixtures("test.wav")
    assert_match "vapoursynth", shell_output("#{bin}/mpv --vf=help")

    # Make sure `pkgconf` can parse `mpv.pc` after the `inreplace`.
    system "pkgconf", "--print-errors", "mpv"
  end
end

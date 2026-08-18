# Source: https://github.com/Homebrew/homebrew-core/blob/6883a6bcb2dabfff96410f2af4002732ac12224e/Formula/m/mpv.rb
# Changes:
# - Disable Javascript/Lua/vapoursynth
# - Set deployment target
# - Fix the ao_coreaudio channel map type for macOS 27 (see below)

class Mpv < Formula
  desc "Media player based on MPlayer and mplayer2"
  homepage "https://mpv.io"
  url "https://github.com/mpv-player/mpv/archive/refs/tags/v0.41.0.tar.gz"
  sha256 "ee21092a5ee427353392360929dc64645c54479aefdb5babc5cfbb5fad626209"
  license all_of: ["GPL-2.0-or-later", "LGPL-2.1-or-later"]
  revision 5
  compatibility_version 1
  head "https://github.com/mpv-player/mpv.git", branch: "master"

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

  # Fix for upstream commit 06fe665b (mpv >= 0.40): it passes an
  # AudioChannelLayout to kAudioOutputUnitProperty_ChannelMap, which expects
  # an SInt32 array. macOS 27 rejects this for mono input (error -50), so
  # mpv falls back to ao_avfoundation, which truncates playback. The patch
  # builds a correctly-typed SInt32 map instead, and keeps init alive if
  # the OS still rejects it.
  # See https://github.com/ankitects/anki/issues/5157
  patch :DATA

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

    # Make sure `pkgconf` can parse `mpv.pc` after the `inreplace`.
    system "pkgconf", "--print-errors", "mpv"
  end
end

__END__
--- a/audio/out/ao_coreaudio.c
+++ b/audio/out/ao_coreaudio.c
@@ -321,12 +321,41 @@
     CHECK_CA_ERROR_L(coreaudio_error_audiounit,
                      "can't link audio unit to selected device");

-    err = AudioUnitSetProperty(p->audio_unit,
-                               kAudioOutputUnitProperty_ChannelMap,
-                               kAudioUnitScope_Global, 0, layout, layout_size);
-
-    CHECK_CA_ERROR_L(coreaudio_error_audiounit,
-                     "unable to set the input channel layout on the audio unit");
+    // kAudioOutputUnitProperty_ChannelMap expects an array of SInt32: one
+    // entry per device output channel, holding the source input channel or
+    // -1. Only set it when the input is a pure reordering of the device
+    // layout, and treat failure as non-fatal: the default 1:1 mapping is
+    // better than losing the coreaudio ao entirely.
+    AudioStreamBasicDescription out_asbd;
+    size = sizeof(out_asbd);
+    err = AudioUnitGetProperty(p->audio_unit,
+                               kAudioUnitProperty_StreamFormat,
+                               kAudioUnitScope_Output, 0, &out_asbd, &size);
+    CHECK_CA_WARN("unable to get the output format of the audio unit");
+    int out_ch = (err == noErr) ? (int)out_asbd.mChannelsPerFrame : 0;
+    if (out_ch == ao->channels.num && out_ch <= MP_NUM_CHANNELS) {
+        struct mp_chmap dev_map;
+        ca_get_active_chmap(ao, p->device, out_ch, &dev_map);
+        SInt32 ch_map[MP_NUM_CHANNELS];
+        bool complete = dev_map.num == out_ch;
+        for (int n = 0; complete && n < out_ch; n++) {
+            ch_map[n] = -1;
+            for (int i = 0; i < ao->channels.num; i++) {
+                if (ao->channels.speaker[i] == dev_map.speaker[n]) {
+                    ch_map[n] = i;
+                    break;
+                }
+            }
+            complete = ch_map[n] >= 0;
+        }
+        if (complete) {
+            err = AudioUnitSetProperty(p->audio_unit,
+                                       kAudioOutputUnitProperty_ChannelMap,
+                                       kAudioUnitScope_Global, 0, ch_map,
+                                       out_ch * sizeof(SInt32));
+            CHECK_CA_WARN("unable to set the channel map on the audio unit");
+        }
+    }

     AURenderCallbackStruct render_cb = (AURenderCallbackStruct) {
         .inputProc       = render_cb_lpcm,

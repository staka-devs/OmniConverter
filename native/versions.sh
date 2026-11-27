# 取得するソースの版とハッシュ。更新はこのファイルだけで行う
# shellcheck disable=SC2034

FFMPEG_URL=https://ffmpeg.org/releases/ffmpeg-9.1.tar.xz
FFMPEG_SHA256=1a4d394ae63d190a526cd052eb4415c156b5c5e2df37efab41a7de0b8fde2433

X264_COMMIT=b35605ace3ddf7c1a5d67a2eb553f034aef41d55
X264_URL=https://code.videolan.org/videolan/x264/-/archive/$X264_COMMIT/x264-$X264_COMMIT.tar.bz2
X264_SHA256=6eeb82934e69fd51e043bd8c5b0d152839638d1ce7aa4eea65a3fedcf83ff224

X265_URL=https://download.videolan.org/videolan/x265/x265_4.2.tar.gz
X265_SHA256=40b1ea0453e0309f0eba934e0ddf533f8f6295966679e8894e8f1c1c8d5e1210

LIBVPX_URL=https://github.com/webmproject/libvpx/archive/refs/tags/v1.17.0.tar.gz
LIBVPX_SHA256=1020f184046187baa2985dbde38e0691f49c44088bca7a1842b0236c6081dc0a

DAV1D_URL=https://downloads.videolan.org/pub/videolan/dav1d/1.5.5/dav1d-1.5.5.tar.xz
DAV1D_SHA256=ca96b1d0ba3f01f55de7231d775892b64994ba061aaeeaaa4f662a390b3abf9e

OPUS_URL=https://downloads.xiph.org/releases/opus/opus-1.6.1.tar.gz
OPUS_SHA256=6ffcb593207be92584df15b32466ed64bbec99109f007c82205f0194572411a1

LAME_URL=https://downloads.sourceforge.net/project/lame/lame/3.100/lame-3.100.tar.gz
LAME_SHA256=ddfe36cab873794038ae2c1210557ad34857a4b6bdc515785d1da9e175b1da1e

LIBWEBP_URL=https://storage.googleapis.com/downloads.webmproject.org/releases/webp/libwebp-1.6.0.tar.gz
LIBWEBP_SHA256=e4ab7009bf0629fd11982d4c2aa83964cf244cffba7347ecd39019a9e38c4564

LIBJPEG_URL=https://github.com/libjpeg-turbo/libjpeg-turbo/releases/download/3.2.1/libjpeg-turbo-3.2.1.tar.gz
LIBJPEG_SHA256=c27ad72a68da151177929aa969ae773a2da5ac2af9ce946079314fdac8cece7f

LIBPNG_URL=https://github.com/pnggroup/libpng/archive/refs/tags/v1.6.59.tar.gz
LIBPNG_SHA256=2540302a1844ad2b2b501977abecfa850f265f97b78f065a712ab4074a89f5b5

LIBDE265_URL=https://github.com/strukturag/libde265/releases/download/v1.1.3/libde265-1.1.3.tar.gz
LIBDE265_SHA256=554228bd17788c99a7e63b37ab5634722190e6e2bf60c1dcb01cef328e133905

LIBAOM_URL=https://storage.googleapis.com/aom-releases/libaom-3.15.3.tar.gz
LIBAOM_SHA256=12c01dc731ed7b5a999e28120b45bf58a002ba3435a4ba2cfbc1e084a27c9c04

LIBHEIF_URL=https://github.com/strukturag/libheif/releases/download/v1.23.7/libheif-1.23.7.tar.gz
LIBHEIF_SHA256=f7483e818503ea6224c8863e4a56ac02c7089021a98472c811c12aa19faf2475

BZIP2_URL=https://sourceware.org/pub/bzip2/bzip2-1.0.8.tar.gz
BZIP2_SHA256=ab5a03176ee106d3f0fa90e381da478ddae405918153cca248e682cd0c4a2269

XZ_URL=https://github.com/tukaani-project/xz/releases/download/v5.8.4/xz-5.8.4.tar.gz
XZ_SHA256=0014c7886930454fe8bd4228665b51af55eeae560ea135c9c4cd33f55b2591d9

ZSTD_URL=https://github.com/facebook/zstd/releases/download/v1.6.0/zstd-1.6.0.tar.gz
ZSTD_SHA256=2ba599ae2a257976278e7e71e998433a387bfd18aa7b78678229fd5d84f5e9b8

LZ4_URL=https://github.com/lz4/lz4/archive/refs/tags/v1.11.1.tar.gz
LZ4_SHA256=683fed08a844ad012196381e40badf80c0bbe4962959761416d41c9e67d77ec7

LIBARCHIVE_URL=https://github.com/libarchive/libarchive/releases/download/v3.8.11/libarchive-3.8.11.tar.xz
LIBARCHIVE_SHA256=a3dc0872b1b39da34dc18ef1b944f0f11ffd37425d70ae04be679279f1cf43d2

LIBTIFF_URL=https://download.osgeo.org/libtiff/tiff-4.7.2.tar.xz
LIBTIFF_SHA256=4996f0c4f93094719b1ca5c6279b20e588773ba8a247533e486416fb662ddb88

HIGHWAY_URL=https://github.com/google/highway/archive/refs/tags/1.4.0.tar.gz
HIGHWAY_SHA256=e72241ac9524bb653ae52ced768b508045d4438726a303f10181a38f764a453c

BROTLI_URL=https://github.com/google/brotli/archive/refs/tags/v1.2.1.tar.gz
BROTLI_SHA256=98d35d11a78dc453aebb7d10c3748f3f73ff78c8267c0dc697b8631f67bf7e8f

LCMS2_URL=https://github.com/mm2/Little-CMS/releases/download/lcms2.20/lcms2-2.20.tar.gz
LCMS2_SHA256=18ff5504fa9cc8a2b1f3591180d90ead16c8a2ea78e530523b46eab70e1ca6b9

LIBJXL_URL=https://github.com/libjxl/libjxl/archive/refs/tags/v0.13.0.tar.gz
LIBJXL_SHA256=24d18c9af2d1f5a17730b3f8d3f70a5c16e5ac303f5d9d5fe9dc00cf7ca82af2

LIBRAW_URL=https://github.com/LibRaw/LibRaw/archive/refs/tags/0.22.3.tar.gz
LIBRAW_SHA256=7edf96c5b70cff88980a02ab0fc8af125cb6a4a377985573c654439d9d02355c

IMAGEMAGICK_URL=https://github.com/ImageMagick/ImageMagick/archive/refs/tags/7.1.2-36.tar.gz
IMAGEMAGICK_SHA256=5ba807e38a6bcb7c3c96a166485e270c5017aaa6b166f0440dc4b2badf6e6167

cmake_minimum_required(VERSION 3.22)
project(thai_chinese_voice LANGUAGES C CXX)
set(CMAKE_C_STANDARD 11)
set(CMAKE_CXX_STANDARD 17)
set(WHISPER_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_SERVER OFF CACHE BOOL "" FORCE)
set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)
set(GGML_OPENMP OFF CACHE BOOL "" FORCE)
include(FetchContent)
# Build-time dependency only: audio processing at run-time is 100% on-device.
# This release is pinned to keep builds reproducible.
FetchContent_Declare(whispercpp
  GIT_REPOSITORY https://github.com/ggml-org/whisper.cpp.git
  GIT_TAG v1.9.4
  GIT_SHALLOW TRUE
  GIT_PROGRESS TRUE
)
FetchContent_MakeAvailable(whispercpp)
add_library(voice_whisper SHARED whisper_native.cpp)
target_link_libraries(voice_whisper PRIVATE whisper log android)
target_include_directories(voice_whisper PRIVATE ${whispercpp_SOURCE_DIR}/include)
target_compile_options(voice_whisper PRIVATE -O3)

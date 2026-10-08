cmake_minimum_required(VERSION 3.22.1)

project(native_playback_bridge)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS OFF)

set(REPO_ROOT ${CMAKE_CURRENT_LIST_DIR}/../../../..)

add_library(native-playback SHARED
    native_hifi_dsp.cpp
    native_playback_bridge.cpp
    native_update_patcher.cpp
    bspatch/bspatch.c
    vendor/bzip2/bzlib.c
    vendor/bzip2/blocksort.c
    vendor/bzip2/compress.c
    vendor/bzip2/decompress.c
    vendor/bzip2/crctable.c
    vendor/bzip2/randtable.c
    vendor/bzip2/huffman.c
    ${REPO_ROOT}/src/playback/playback_queue.cpp
)

target_include_directories(native-playback PRIVATE
    ${CMAKE_CURRENT_LIST_DIR}/bspatch
    ${CMAKE_CURRENT_LIST_DIR}/vendor/bzip2
    ${REPO_ROOT}
)

find_library(log-lib log)

target_link_libraries(native-playback
    ${log-lib}
)

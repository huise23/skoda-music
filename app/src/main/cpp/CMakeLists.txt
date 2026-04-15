cmake_minimum_required(VERSION 3.22.1)

project(native_playback_bridge)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS OFF)

set(REPO_ROOT ${CMAKE_CURRENT_LIST_DIR}/../../../..)

add_library(native-playback SHARED
    native_playback_bridge.cpp
    ${REPO_ROOT}/src/playback/playback_queue.cpp
)

target_include_directories(native-playback PRIVATE
    ${REPO_ROOT}
)

find_library(log-lib log)

target_link_libraries(native-playback
    ${log-lib}
)

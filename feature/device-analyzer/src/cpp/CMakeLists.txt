cmake_minimum_required(VERSION 3.22.1)
project("taraaport_analyzer" LANGUAGES CXX)

add_library(
    taraaport_analyzer
    SHARED
    vulkan_detector_jni.cpp
)

# Menautkan pustaka sistem libvulkan.so dan liblog.so bawaan Android NDK
find_library(vulkan-lib vulkan)
find_library(log-lib log)

target_link_libraries(
    taraaport_analyzer
    ${vulkan-lib}
    ${log-lib}
)
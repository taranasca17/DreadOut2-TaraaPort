cmake_minimum_required(VERSION 3.22.1)
project("taraaport_analyzer" LANGUAGES CXX)

find_library(log-lib log)
find_library(vulkan-lib vulkan)
find_library(android-lib android)

add_library(
    taraaport_analyzer
    SHARED
    vulkan_detector_jni.cpp
)

target_link_libraries(
    taraaport_analyzer
    ${log-lib}
    ${vulkan-lib}
    ${android-lib}
)
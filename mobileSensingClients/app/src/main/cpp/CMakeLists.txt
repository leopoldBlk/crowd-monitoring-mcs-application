# Necessary for running H3 Library on Android, Source: https://github.com/isaacbrodsky/h3-android-sample.git

cmake_minimum_required(VERSION 3.22.1)

project("h3")

add_library(${CMAKE_PROJECT_NAME} SHARED h3.cpp)

target_link_libraries(${CMAKE_PROJECT_NAME} android log)
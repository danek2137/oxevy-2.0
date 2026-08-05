cmake_minimum_required(VERSION 3.10)
project(oxevy-rpc CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

find_package(JNI REQUIRED)

if(WIN32)
    add_library(oxevy-rpc SHARED discord_rpc_windows.cpp)
    target_include_directories(oxevy-rpc PRIVATE ${JNI_INCLUDE_DIRS} ${CMAKE_CURRENT_SOURCE_DIR}/win32)
else()
    add_library(oxevy-rpc SHARED discord_rpc_linux.cpp)
    target_include_directories(oxevy-rpc PRIVATE ${JNI_INCLUDE_DIRS})
endif()

if(WIN32)
    target_link_options(oxevy-rpc PRIVATE -static-libgcc -static-libstdc++ -Wl,--kill-at)
endif()

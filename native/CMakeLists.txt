cmake_minimum_required(VERSION 3.16)
project(cubiomeswrap C)

set(CMAKE_C_STANDARD 99)
set(CMAKE_POSITION_INDEPENDENT_CODE ON)

# Build shared library: .dylib/.so/.dll
add_library(cubiomeswrap SHARED
  ${CMAKE_SOURCE_DIR}/../cubiomes_wrap.c
)

# Include cubiomes core headers
target_include_directories(cubiomeswrap PRIVATE
  ${CMAKE_CURRENT_LIST_DIR}/../external/cubiomes
  ${CMAKE_CURRENT_LIST_DIR}/../external/cubiomes/cubiomes
)

# Compile cubiomes core sources (ONLY .c files from cubiomes core)
file(GLOB CUBIOMES_CORE_SRC
  ${CMAKE_SOURCE_DIR}/../external/cubiomes/cubiomes/*.c
)

target_sources(cubiomeswrap PRIVATE ${CUBIOMES_CORE_SRC})

# Nice name; CMake chooses prefix/suffix per OS
set_target_properties(cubiomeswrap PROPERTIES OUTPUT_NAME "cubiomeswrap")

# Windows convenience
if (WIN32)
  set_target_properties(cubiomeswrap PROPERTIES WINDOWS_EXPORT_ALL_SYMBOLS ON)
endif()
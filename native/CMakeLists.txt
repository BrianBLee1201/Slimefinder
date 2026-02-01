cmake_minimum_required(VERSION 3.16)
project(cubiomeswrap C)

set(CMAKE_C_STANDARD 99)

# cubiomes submodule root (repo has external/cubiomes)
set(CUBIOMES_DIR "${CMAKE_CURRENT_LIST_DIR}/../external/cubiomes")

# headers like generator.h, biomes.h, util.h are in cubiomes/
set(CUBIOMES_INCLUDE "${CUBIOMES_DIR}/cubiomes")

add_library(cubiomeswrap SHARED
  "${CMAKE_CURRENT_LIST_DIR}/../cubiomes_wrap.c"
)

target_include_directories(cubiomeswrap PRIVATE
  "${CUBIOMES_INCLUDE}"
)

# If you build cubiomes sources directly (some setups do), you may also need:
# target_sources(cubiomeswrap PRIVATE
#   "${CUBIOMES_INCLUDE}/generator.c"
#   "${CUBIOMES_INCLUDE}/layers.c"
#   ...
# )

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
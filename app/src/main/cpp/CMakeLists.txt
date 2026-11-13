cmake_minimum_required(VERSION 3.22.1)
project("studio64node")

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# app/libnode  (cpp -> main -> src -> app)
set(LIBNODE_DIR ${CMAKE_SOURCE_DIR}/../../../libnode)

include_directories(${LIBNODE_DIR}/include/node)

add_library(node SHARED IMPORTED)
set_target_properties(node PROPERTIES
    IMPORTED_LOCATION ${LIBNODE_DIR}/bin/${ANDROID_ABI}/libnode.so)

add_library(native-lib SHARED native-lib.cpp)
target_link_libraries(native-lib node log)

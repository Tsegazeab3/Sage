cmake_minimum_required(VERSION 3.18.1)
project(YOLO11NCNN)

# Set ncnn path
set(NCNN_DIR ${CMAKE_SOURCE_DIR}/ncnn)

# Find libraries
find_library(log-lib log)
find_library(android-lib android)
find_library(jnigraphics-lib jnigraphics)

# ncnn library
include_directories(${NCNN_DIR}/include/ncnn)

add_library(lib_ncnn SHARED IMPORTED)
set_target_properties(lib_ncnn PROPERTIES
        IMPORTED_LOCATION ${NCNN_DIR}/lib/${ANDROID_ABI}/libncnn.so
)

# Your YOLO library
add_library(yolo11ncnn SHARED
        yolo_detector.cpp
        jni_bridge.cpp
)

target_include_directories(yolo11ncnn PRIVATE
        ${NCNN_DIR}/include/ncnn
        ${CMAKE_SOURCE_DIR}/include
)

target_link_libraries(yolo11ncnn
        lib_ncnn
        ${log-lib}
        ${android-lib}
        ${jnigraphics-lib}
)

#target_compile_options(yolo11ncnn PRIVATE -fopenmp)
#target_link_libraries(yolo11ncnn -fopenmp)
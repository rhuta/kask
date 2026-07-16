cmake_minimum_required(VERSION 3.22.1)

project("kask_native")

set(JNI_LIBS_DIR ${CMAKE_CURRENT_SOURCE_DIR}/../jniLibs/${ANDROID_ABI})

# Prebuilt llama.cpp libraries
add_library(ggml-base SHARED IMPORTED)
set_target_properties(ggml-base PROPERTIES IMPORTED_LOCATION ${JNI_LIBS_DIR}/libggml-base.so)

add_library(ggml SHARED IMPORTED)
set_target_properties(ggml PROPERTIES IMPORTED_LOCATION ${JNI_LIBS_DIR}/libggml.so)

add_library(ggml-cpu SHARED IMPORTED)
set_target_properties(ggml-cpu PROPERTIES IMPORTED_LOCATION ${JNI_LIBS_DIR}/libggml-cpu.so)

add_library(llama SHARED IMPORTED)
set_target_properties(llama PROPERTIES IMPORTED_LOCATION ${JNI_LIBS_DIR}/libllama.so
        INTERFACE_LINK_LIBRARIES "ggml;ggml-cpu;ggml-base")

add_library(mtmd SHARED IMPORTED)
set_target_properties(mtmd PROPERTIES IMPORTED_LOCATION ${JNI_LIBS_DIR}/libmtmd.so
        INTERFACE_LINK_LIBRARIES "llama")

# JNI bridge
add_library(kask_native SHARED native-lib.cpp)

target_compile_features(kask_native PRIVATE cxx_std_23)
target_compile_options(kask_native PRIVATE -Wno-unused-parameter)
target_link_options(kask_native PRIVATE "-Wl,-z,max-page-size=16384")

# INTERNALIZED b9789 Headers (Project Portability)
target_include_directories(kask_native PRIVATE
        ${CMAKE_CURRENT_SOURCE_DIR}/include
        ${CMAKE_CURRENT_SOURCE_DIR}/include/ggml
        ${CMAKE_CURRENT_SOURCE_DIR}/mtmd
)

find_library(log-lib log)
find_library(jnigraphics-lib jnigraphics)

target_link_libraries(kask_native
        llama
        mtmd
        ${log-lib}
        ${jnigraphics-lib}
)

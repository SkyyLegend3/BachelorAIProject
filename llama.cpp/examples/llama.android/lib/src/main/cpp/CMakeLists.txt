cmake_minimum_required(VERSION 3.22.1)

project("ai-chat" VERSION 1.0.0 LANGUAGES C CXX)

set(CMAKE_C_STANDARD 11)
set(CMAKE_C_STANDARD_REQUIRED true)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED true)

set(CMAKE_C_FLAGS   "${CMAKE_C_FLAGS}"   CACHE STRING "" FORCE)
set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS}" CACHE STRING "" FORCE)

# --------------------------------------------------------------------------
# AI Chat library
# --------------------------------------------------------------------------

if(DEFINED ANDROID_ABI)
    message(STATUS "Detected Android ABI: ${ANDROID_ABI}")
    if(ANDROID_ABI STREQUAL "arm64-v8a")
        set(GGML_SYSTEM_ARCH "ARM")
        # Mit CMake 3.22.x verursacht der KleidiAI-FetchContent-Pfad einen Configure-Fehler.
        # Daher fuer den lokalen Android-Restore deaktiviert.
        set(GGML_CPU_KLEIDIAI OFF)
        set(GGML_OPENMP ON)
    elseif(ANDROID_ABI STREQUAL "x86_64")
        set(GGML_SYSTEM_ARCH "x86")
        set(GGML_CPU_KLEIDIAI OFF)
        set(GGML_OPENMP OFF)
    else()
        message(FATAL_ERROR "Unsupported ABI: ${ANDROID_ABI}")
    endif()
endif()

# Ermittele Projektwurzel von .../llama.cpp/examples/llama.android/lib/src/main/cpp aus.
get_filename_component(PROJECT_ROOT ${CMAKE_CURRENT_LIST_DIR}/../../../../../../../ ABSOLUTE)

# Quellen-Prioritaet:
# 1) third_party/llama.cpp (aktuelle, explizit gepflegte Quelle; gemma4-faehig)
# 2) llama.cpp im Root
# 3) iosApp/llama.cpp als Legacy-Fallback
set(LLAMA_SRC_CANDIDATES
        ${PROJECT_ROOT}/third_party/llama.cpp
        ${CMAKE_CURRENT_LIST_DIR}/../../../../../../
        ${PROJECT_ROOT}/iosApp/llama.cpp
)

set(LLAMA_SRC "")
foreach(candidate IN LISTS LLAMA_SRC_CANDIDATES)
    if(EXISTS ${candidate}/CMakeLists.txt)
        set(LLAMA_SRC ${candidate})
        break()
    endif()
endforeach()

if(LLAMA_SRC STREQUAL "")
    message(FATAL_ERROR "Keine gueltige llama.cpp-Quelle gefunden (third_party, Root, iosApp).")
endif()

message(STATUS "Using llama.cpp source: ${LLAMA_SRC}")
add_subdirectory(${LLAMA_SRC} build-llama)

add_library(${CMAKE_PROJECT_NAME} SHARED
        ai_chat.cpp)

target_compile_definitions(${CMAKE_PROJECT_NAME} PRIVATE
        GGML_SYSTEM_ARCH=${GGML_SYSTEM_ARCH}
        GGML_CPU_KLEIDIAI=$<BOOL:${GGML_CPU_KLEIDIAI}>
        GGML_OPENMP=$<BOOL:${GGML_OPENMP}>
)

target_include_directories(${CMAKE_PROJECT_NAME} PRIVATE
        ${LLAMA_SRC}
        ${LLAMA_SRC}/common
        ${LLAMA_SRC}/vendor
        ${LLAMA_SRC}/include
        ${LLAMA_SRC}/ggml/include
        ${LLAMA_SRC}/ggml/src)

if(TARGET llama-common)
    set(LLAMA_COMMON_TARGET llama-common)
elseif(TARGET common)
    set(LLAMA_COMMON_TARGET common)
else()
    message(FATAL_ERROR "Kein kompatibles llama-common/common Target gefunden.")
endif()

target_link_libraries(${CMAKE_PROJECT_NAME}
        llama
        ${LLAMA_COMMON_TARGET}
        android
        log)

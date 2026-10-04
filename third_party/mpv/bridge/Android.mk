LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)
LOCAL_MODULE := avcodec
LOCAL_SRC_FILES := $(MPV_RUNTIME)/$(TARGET_ARCH_ABI)/libavcodec.so
include $(PREBUILT_SHARED_LIBRARY)

include $(CLEAR_VARS)
LOCAL_MODULE := avformat
LOCAL_SRC_FILES := $(MPV_RUNTIME)/$(TARGET_ARCH_ABI)/libavformat.so
include $(PREBUILT_SHARED_LIBRARY)

include $(CLEAR_VARS)
LOCAL_MODULE := avutil
LOCAL_SRC_FILES := $(MPV_RUNTIME)/$(TARGET_ARCH_ABI)/libavutil.so
include $(PREBUILT_SHARED_LIBRARY)

include $(CLEAR_VARS)
LOCAL_MODULE := swscale
LOCAL_SRC_FILES := $(MPV_RUNTIME)/$(TARGET_ARCH_ABI)/libswscale.so
include $(PREBUILT_SHARED_LIBRARY)

include $(CLEAR_VARS)
LOCAL_MODULE := mpv
LOCAL_SRC_FILES := $(MPV_RUNTIME)/$(TARGET_ARCH_ABI)/libmpv.so
include $(PREBUILT_SHARED_LIBRARY)

include $(CLEAR_VARS)
LOCAL_MODULE := player
LOCAL_CFLAGS := -Werror
LOCAL_CPPFLAGS := -std=c++11
LOCAL_C_INCLUDES := $(MPV_HEADERS)/$(TARGET_ARCH_ABI)/include
LOCAL_SRC_FILES := main.cpp render.cpp log.cpp jni_utils.cpp property.cpp event.cpp node.cpp thumbnail.cpp
LOCAL_LDLIBS := -llog -lGLESv3 -lEGL -latomic
LOCAL_SHARED_LIBRARIES := swscale avcodec avformat avutil mpv
include $(BUILD_SHARED_LIBRARY)

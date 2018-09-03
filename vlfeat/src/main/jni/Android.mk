LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)

#opencv
OPENCVROOT:= C:\Users\forev\Downloads\project\OpenCV-2.4.10-android-sdk
OPENCV_CAMERA_MODULES:=on
OPENCV_INSTALL_MODULES:=on
OPENCV_LIB_TYPE:=SHARED
include ${OPENCVROOT}/sdk/native/jni/OpenCV.mk

LOCAL_CFLAGS := -DVL_DISABLE_SSE2 -DVL_DISABLE_AVX
#-std=c99

FILE_LIST := $(wildcard $(LOCAL_PATH)/vl/*.c)
LOCAL_SRC_FILES := recognition.cpp
LOCAL_SRC_FILES += $(FILE_LIST)
LOCAL_LDLIBS += -llog -lm
LOCAL_MODULE := vlfeat

include $(BUILD_SHARED_LIBRARY)

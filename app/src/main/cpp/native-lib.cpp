#include <jni.h>
#include <string>
#include <ctime>
#include <android/log.h>

#ifdef __aarch64__

#include "arm_neon.h"

#else
#endif


#define MAX(x, y) (((x) > (y)) ? (x) : (y))
#define MIN(x, y) (((x) < (y)) ? (x) : (y))
#define PX(V) MIN(255, MAX(0, V))
#define MS_IN_SECOND 1000000

jlong timeMicro() {
    struct timespec res{};
    clock_gettime(CLOCK_MONOTONIC, &res);
    return (res.tv_sec * MS_IN_SECOND) + (res.tv_nsec / 1000);
}


void changeBrightness(int *data, int len, int diff) {
    int *d = data;
    while (len-- > 0) {
        int v = *d;
        int a = v >> 24 & 0xFF;
        int r = PX((v >> 16 & 0xFF) + diff);
        int g = PX((v >> 8 & 0xFF) + diff);
        int b = PX((v >> 0 & 0xFF) + diff);
        v = (a << 24) | (r << 16) | (g << 8) | b;
        (*d) = v;
        d++;
    }
}

void nchangeBrightness(int *data, int len, int diff) {
    if (diff == 0)return;
#ifdef __aarch64__
    auto *d = (uint8_t *) data;
    uint8x16_t vec1;
    uint8_t b = abs(diff);
    //ARGB => BGRA (due to LE and casting Int -> char)
    uint8x16_t bvec = {b, b, b, 0, b, b, b, 0, b, b, b, 0, b, b, b, 0};
    uint8x16_t (*op)(uint8x16_t, uint8x16_t) = diff > 0 ? vqaddq_u8 : vqsubq_u8;
    for (int i = 0; i < len; i += 4) {
        //load 128bits => 4 pixels => 16 8bit components
        vec1 = vld1q_u8(d + 0);
        vec1 = op(vec1, bvec);
        vst1q_u8(d + 0, vec1);
        d += 16;
    }
#else
    throw "NO NEON";
#endif
}

void nmul(float *data, int len, float multiplier) {
    if (len == 0)return;
#ifdef __aarch64__
    //ARGB => BGRA (due to LE and casting Int -> char)
    float32x4_t vec1;
    float32x4_t bvec = {multiplier, multiplier, multiplier, multiplier};
    for (int i = 0; i < len; i += 4) {
        vec1 = vld1q_f32(data + i);
        vec1 = vmulq_f32(vec1, bvec);
        vst1q_f32(data + i, vec1);
    }
#else
    throw "NO NEON";
#endif
}

extern "C"
JNIEXPORT jboolean JNICALL
Java_com_scurab_gncompute_app_util_BitmapBrightnessChange__1isNeonSupported(JNIEnv *env, jobject thiz) {
#ifdef __aarch64__
    return true;
#else
    return false;
#endif
}

extern "C"
JNIEXPORT jlong JNICALL
Java_com_scurab_gncompute_app_util_BitmapBrightnessChange__1changeBrightnessCIntArray(JNIEnv *env, jobject thiz, jintArray bitmap, jint diff) {
    jlong start = timeMicro();
    int *data = env->GetIntArrayElements(bitmap, nullptr);
    int size = env->GetArrayLength(bitmap);
    changeBrightness(data, size, diff);
    jlong end = timeMicro();
    env->ReleaseIntArrayElements(bitmap, data, 0);
    return end - start;
}

extern "C"
JNIEXPORT jlong  JNICALL
Java_com_scurab_gncompute_app_util_BitmapBrightnessChange__1changeBrightnessCBuffer(JNIEnv *env, jobject thiz, jobject bitmap, jint diff) {
    jlong start = timeMicro();
    int *data = (int *) env->GetDirectBufferAddress(bitmap);
    //byte buffer full of ints
    int size = (int) (env->GetDirectBufferCapacity(bitmap) / 4);
    changeBrightness(data, size, diff);
    jlong end = timeMicro();
    return end - start;
}

extern "C"
JNIEXPORT jlong JNICALL
Java_com_scurab_gncompute_app_util_BitmapBrightnessChange__1changeBrightnessNeon(JNIEnv *env, jobject thiz, jintArray bitmap, jint diff) {
    jlong start = timeMicro();
    int *data = env->GetIntArrayElements(bitmap, nullptr);
    int size = env->GetArrayLength(bitmap);
    nchangeBrightness(data, size, diff);
    jlong end = timeMicro();
    env->ReleaseIntArrayElements(bitmap, data, 0);
    return end - start;
}

extern "C"
JNIEXPORT jlong JNICALL
Java_com_scurab_gncompute_app_util_BitmapBrightnessChange__1changeBrightnessNeonBuffer(JNIEnv *env, jobject thiz, jobject bitmap, jint diff) {
    jlong start = timeMicro();
    int *data = (int *) env->GetDirectBufferAddress(bitmap);
    //byte buffer full of ints
    int size = (int) (env->GetDirectBufferCapacity(bitmap) / 4);
    nchangeBrightness(data, size, diff);
    jlong end = timeMicro();
    return end - start;
}

extern "C"
JNIEXPORT jlong JNICALL
Java_com_scurab_gncompute_app_util_NeonCalc__1mulArray(JNIEnv *env, jobject thiz, jfloatArray array, jfloat multiplier) {
    jlong start = timeMicro();
    float *data = env->GetFloatArrayElements(array, nullptr);
    int len = env->GetArrayLength(array);
    nmul(data, len, multiplier);
    jlong end = timeMicro();
    env->ReleaseFloatArrayElements(array, data, 0);
    return end - start;
}

extern "C"
JNIEXPORT jlong JNICALL
Java_com_scurab_gncompute_app_util_NeonCalc__1mulBuffer(JNIEnv *env, jobject thiz, jobject buffer, jfloat multiplier) {
    jlong start = timeMicro();
    float *data = (float *) env->GetDirectBufferAddress(buffer);
    //byte buffer full of floats
    int len = (int) (env->GetDirectBufferCapacity(buffer) / 4);
    nmul(data, len, multiplier);
    jlong end = timeMicro();
    return end - start;
}

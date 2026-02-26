#include <jni.h>
#include <android/bitmap.h>
#include "../ncnn/include/ncnn/net.h"
#include "ByteTracker.h"

#ifdef __cplusplus
extern "C" {
#endif

struct DetectionResult {
    int classId;
    float confidence;
    float x;
    float y;
    float width;
    float height;
    int trackId; // Added trackId
};

class YOLODetector {
public:
    YOLODetector();
    ~YOLODetector();

    bool loadModel(AAssetManager* mgr, const char* param, const char* bin);
    std::vector<DetectionResult> detect(JNIEnv* env, jobject bitmap);
    std::vector<DetectionResult> detectFromImageProxy(JNIEnv* env, jobject imageProxy);

private:
    ncnn::Net net;
    bool modelLoaded;
    BYTETracker* tracker; // Added tracker

    // --- Reusable Buffers & Tracker Optimization ---
    ncnn::Mat rgb_mat;
    ncnn::Mat resized_input;
    int frame_counter = 0;
    const int TRACKER_FRAME_SKIP = 2; // Run tracker every N frames to save CPU
    std::vector<Object> last_tracked_objects;
    ncnn::Mat transposed_output;

    void preprocess(JNIEnv* env, jobject bitmap, AndroidBitmapInfo& info, void* pixels);
    std::vector<DetectionResult> postprocess(const ncnn::Mat& output, int img_w, int img_h);
};

// JNI Functions
JNIEXPORT jlong JNICALL Java_com_yolo11_YOLODetector_initDetector(JNIEnv* env, jobject thiz);
JNIEXPORT jboolean JNICALL Java_com_yolo11_YOLODetector_loadModel(JNIEnv* env, jobject thiz, jlong nativePtr, jobject assetManager, jstring paramPath, jstring binPath);
JNIEXPORT jobjectArray JNICALL Java_com_yolo11_YOLODetector_detectFromBitmap(JNIEnv* env, jobject thiz, jlong nativePtr, jobject bitmap);
JNIEXPORT void JNICALL Java_com_yolo11_YOLODetector_releaseDetector(JNIEnv* env, jobject thiz, jlong nativePtr);

#ifdef __cplusplus
}
#endif
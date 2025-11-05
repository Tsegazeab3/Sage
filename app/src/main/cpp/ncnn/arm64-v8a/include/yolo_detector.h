#include <jni.h>
#include <android/bitmap.h>
#include <ncnn/net.h>

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
};

class YOLODetector {
public:
    YOLODetector();
    ~YOLODetector();

    bool loadModel(AAssetManager* mgr, const char* param, const char* bin);
    std::vector<DetectionResult> detect(JNIEnv* env, jobject bitmap);

private:
    ncnn::Net net;
    bool modelLoaded;

    ncnn::Mat preprocess(JNIEnv* env, jobject bitmap);
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
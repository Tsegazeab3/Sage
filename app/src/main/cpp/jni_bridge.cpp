#include "yolo_detector.h"
#include <android/asset_manager.h>
#include <android/asset_manager_jni.h>

extern "C" {

JNIEXPORT jlong JNICALL
Java_com_example_objectdetection_YOLODetector_initDetector(JNIEnv* env, jobject thiz) {
    return reinterpret_cast<jlong>(new YOLODetector());
}

JNIEXPORT jboolean JNICALL
Java_com_example_objectdetection_YOLODetector_loadModel(JNIEnv* env, jobject thiz, jlong nativePtr,
                                                        jobject assetManager, jstring paramPath, jstring binPath) {
    auto* detector = reinterpret_cast<YOLODetector*>(nativePtr);
    if (!detector) return JNI_FALSE;

    AAssetManager* mgr = AAssetManager_fromJava(env, assetManager);
    const char* param = env->GetStringUTFChars(paramPath, nullptr);
    const char* bin = env->GetStringUTFChars(binPath, nullptr);

    bool success = detector->loadModel(mgr, param, bin);

    env->ReleaseStringUTFChars(paramPath, param);
    env->ReleaseStringUTFChars(binPath, bin);

    return success ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jobjectArray JNICALL
Java_com_example_objectdetection_YOLODetector_detectFromImageProxy(JNIEnv* env, jobject thiz, jlong nativePtr, jobject imageProxy) {
    auto* detector = reinterpret_cast<YOLODetector*>(nativePtr);
    if (!detector) return nullptr;

    auto detections = detector->detectFromImageProxy(env, imageProxy);

    // Convert to Java objects
    jclass resultClass = env->FindClass("com/example/objectdetection/DetectionResult");
    jmethodID constructor = env->GetMethodID(resultClass, "<init>", "(IFFFFF)V");

    jobjectArray results = env->NewObjectArray(detections.size(), resultClass, nullptr);

    for (int i = 0; i < detections.size(); i++) {
        auto& det = detections[i];
        jobject obj = env->NewObject(resultClass, constructor,
                                     det.classId, det.confidence,
                                     det.x, det.y, det.width, det.height);
        env->SetObjectArrayElement(results, i, obj);
    }

    return results;
}

JNIEXPORT jobjectArray JNICALL
Java_com_example_objectdetection_YOLODetector_detectFromBitmap(JNIEnv* env, jobject thiz, jlong nativePtr, jobject bitmap) {
    auto* detector = reinterpret_cast<YOLODetector*>(nativePtr);
    if (!detector) return nullptr;

    auto detections = detector->detect(env, bitmap);

    // Convert to Java objects
    jclass resultClass = env->FindClass("com/example/objectdetection/DetectionResult");
    jmethodID constructor = env->GetMethodID(resultClass, "<init>", "(IFFFFF)V");

    jobjectArray results = env->NewObjectArray(detections.size(), resultClass, nullptr);

    for (int i = 0; i < detections.size(); i++) {
        auto& det = detections[i];
        jobject obj = env->NewObject(resultClass, constructor,
                                     det.classId, det.confidence,
                                     det.x, det.y, det.width, det.height);
        env->SetObjectArrayElement(results, i, obj);
    }

    return results;
}

JNIEXPORT void JNICALL
Java_com_example_objectdetection_YOLODetector_releaseDetector(JNIEnv* env, jobject thiz, jlong nativePtr) {
    delete reinterpret_cast<YOLODetector*>(nativePtr);
}

}
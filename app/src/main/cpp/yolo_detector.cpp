    #include "yolo_detector.h"
    #include <android/asset_manager.h>
    #include <android/log.h>
    #include <android/asset_manager_jni.h>
    #include <vector>
    #include <android/log.h>
    #include <algorithm>
    #include <android/hardware_buffer.h>
    #include <chrono>
    #include "cpu.h"
#define LOG_TAG "YOLO_NATIVE"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

const float CONF_THRESHOLD = 0.25f;
const float NMS_THRESHOLD = 0.70f;
const int INPUT_SIZE = 640;
const int NUM_CLASSES = 80;

YOLODetector::YOLODetector() : modelLoaded(false) {
    tracker = new BYTETracker(30, 30);
}

YOLODetector::~YOLODetector() {
    net.clear();
    if (tracker) delete tracker;
}


struct Detection {
    std::vector<float> boxes; // [x1, y1, x2, y2, x1, y1, x2, y2, ...]
    std::vector<int> class_ids;
    std::vector<float> confidences;
};
// --- Class Names (COCO 80 classes) ---
const std::vector<std::string> CLASS_NAMES = {
        "person", "bicycle", "car", "motorcycle", "airplane", "bus", "train", "truck", "boat", "traffic light",
        "fire hydrant", "stop sign", "parking meter", "bench", "bird", "cat", "dog", "horse", "sheep", "cow",
        "elephant", "bear", "zebra", "giraffe", "backpack", "umbrella", "handbag", "tie", "suitcase", "frisbee",
        "skis", "snowboard", "sports ball", "kite", "baseball bat", "baseball glove", "skateboard", "surfboard",
        "tennis racket", "water bottle", "wine glass", "cup", "fork", "knife", "spoon", "bowl", "banana", "apple",
        "sandwich", "orange", "broccoli", "carrot", "hot dog", "pizza", "donut", "cake", "chair", "couch",
        "potted plant", "bed", "dining table", "toilet", "tv", "laptop", "mouse", "remote", "keyboard", "cell phone",
        "microwave", "oven", "toaster", "sink", "refrigerator", "book", "clock", "vase", "scissors", "teddy bear",
        "hair drier", "toothbrush"
};


bool YOLODetector::loadModel(AAssetManager* mgr, const char* param, const char* bin) {
        LOGD("Loading model: %s, %s", param, bin);

        if (!mgr) {
            LOGE("AssetManager is null");
            return false;
        }

        // --- Optimize Performance ---
        // Use big cores for performance and to avoid starving the UI thread.
        int big_cores = ncnn::get_big_cpu_count();
        net.opt.num_threads = big_cores > 0 ? big_cores : 4; // Use big cores if available, otherwise default to 4
        net.opt.use_vulkan_compute = true; // Enable GPU acceleration
        net.opt.use_fp16_arithmetic = true;
        net.opt.use_fp16_packed = true;
        net.opt.use_fp16_storage = true;
        
        LOGD("NCNN Options: Threads=%d, Vulkan=%d", net.opt.num_threads, net.opt.use_vulkan_compute);

        // 1. Load param (Corrected previously)
        LOGD("Loading param to ncnn...");
        int ret1 = net.load_param(mgr, param);

        if (ret1 != 0) {
            LOGE("Failed to load param to ncnn, error: %d", ret1);
            return false;
        }
        LOGD("Param loaded successfully");

        // 2. FIX: Load bin directly from AssetManager
        // This uses the correct NCNN API to handle asset streaming for large weight files.
        LOGD("Loading model weights directly from asset manager...");
        int ret2 = net.load_model(mgr, bin); // <--- Use this API

        if (ret2 != 0) {
            LOGE("Failed to load model weights, error: %d", ret2);
            modelLoaded = false;
            // The error code will be your 10633344 (CRC mismatch)
        } else {
            LOGD("Model loaded successfully!");
            modelLoaded = true;
        }

        return modelLoaded;
}



std::vector<int> nms(const std::vector<float>& boxes, const std::vector<float>& scores, float iou_threshold) {
    std::vector<int> indices;
    for (size_t i = 0; i < scores.size(); i++) indices.push_back(i);  // Use scores.size()

    std::sort(indices.begin(), indices.end(), [&](int a, int b) {
        return scores[a] > scores[b];
    });

    std::vector<int> keep;
    while (!indices.empty()) {
        int current = indices[0];
        keep.push_back(current);

        std::vector<int> rest;
        for (size_t i = 1; i < indices.size(); i++) {
            int idx = indices[i];

            // Extract boxes (each box is 4 values: x1,y1,x2,y2)
            float x1_a = boxes[current * 4];
            float y1_a = boxes[current * 4 + 1];
            float x2_a = boxes[current * 4 + 2];
            float y2_a = boxes[current * 4 + 3];

            float x1_b = boxes[idx * 4];
            float y1_b = boxes[idx * 4 + 1];
            float x2_b = boxes[idx * 4 + 2];
            float y2_b = boxes[idx * 4 + 3];

            // Calculate intersection
            float inter_x1 = std::max(x1_a, x1_b);
            float inter_y1 = std::max(y1_a, y1_b);
            float inter_x2 = std::min(x2_a, x2_b);
            float inter_y2 = std::min(y2_a, y2_b);

            float inter_area = std::max(0.0f, inter_x2 - inter_x1) * std::max(0.0f, inter_y2 - inter_y1);
            float area_a = (x2_a - x1_a) * (y2_a - y1_a);
            float area_b = (x2_b - x1_b) * (y2_b - y1_b);
            float union_area = area_a + area_b - inter_area;

            float iou = (union_area > 0) ? (inter_area / union_area) : 0.0f;

            if (iou <= iou_threshold) {
                rest.push_back(idx);
            }
        }
        indices = rest;
    }
    return keep;
}


std::vector<DetectionResult> YOLODetector::detect(JNIEnv* env, jobject bitmap) {
    auto start = std::chrono::high_resolution_clock::now();
    std::vector<DetectionResult> results;
    if (!modelLoaded) return results;

    AndroidBitmapInfo info;
    void* pixels;

    if (AndroidBitmap_getInfo(env, bitmap, &info) < 0) return results;
    if (AndroidBitmap_lockPixels(env, bitmap, &pixels) < 0) return results;

    // --- Optimized Preprocessing ---
    preprocess(env, bitmap, info, pixels);
    
    // --- Inference ---
    ncnn::Mat output;
    ncnn::Extractor ex = net.create_extractor();
    ex.input("in0", this->resized_input);  // Use your input layer name
    ex.extract("out0", output);  // Use your output layer name
    LOGD("output.w: %d, output.h: %d", output.w, output.h);

    std::vector<DetectionResult> raw_detections = postprocess(output, info.width, info.height);
    
    // --- Optimized ByteTrack Integration ---
    std::vector<Object> tracker_objects;
    for(const auto& det : raw_detections) {
        Object obj;
        obj.x = det.x;
        obj.y = det.y;
        obj.width = det.width;
        obj.height = det.height;
        obj.label = det.classId;
        obj.prob = det.confidence;
        tracker_objects.push_back(obj);
    }
    
    frame_counter++;
    std::vector<Object> tracked_objects;
    if (frame_counter >= TRACKER_FRAME_SKIP) {
        tracked_objects = tracker->update(tracker_objects);
        last_tracked_objects = tracked_objects;
        frame_counter = 0; // Reset counter
    } else {
        tracked_objects = last_tracked_objects; // Use stale tracks
    }
    
    for(const auto& t_obj : tracked_objects) {
        DetectionResult res;
        res.classId = t_obj.label;
        res.confidence = t_obj.prob;
        res.x = t_obj.x;
        res.y = t_obj.y;
        res.width = t_obj.width;
        res.height = t_obj.height;
        res.trackId = t_obj.track_id;
        results.push_back(res);
    }

    AndroidBitmap_unlockPixels(env, bitmap);

    auto end = std::chrono::high_resolution_clock::now();
    auto duration = std::chrono::duration_cast<std::chrono::milliseconds>(end - start).count();
    float fps = 1000.0f / (duration > 0 ? duration : 1);
    LOGD("Inference time: %lld ms, FPS: %.2f", duration, fps);

    return results;
}

void YOLODetector::preprocess(JNIEnv* env, jobject bitmap, AndroidBitmapInfo& info, void* pixels) {
    // Now you have access to the actual pixel data
    ncnn::Mat input = ncnn::Mat::from_pixels(
            (unsigned char*)pixels,
            ncnn::Mat::PIXEL_RGBA2RGB,
            info.width,
            info.height
    );

    // Resize using ncnn (no OpenCV) and store in the class member
    ncnn::resize_bilinear(input, this->resized_input, 640, 640);

    // Normalize
    const float norm_vals[3] = {1.0f / 255.0f, 1.0f / 255.0f, 1.0f / 255.0f};
    this->resized_input.substract_mean_normalize(nullptr, norm_vals);
}

std::vector<DetectionResult> YOLODetector::postprocess(const ncnn::Mat& output, int img_w, int img_h) {
    std::vector<DetectionResult> results;
    Detection det;

    // Transpose for better memory access
    this->transposed_output.create(output.h, output.w);
    for (int i = 0; i < output.h; i++) {
        for (int j = 0; j < output.w; j++) {
            this->transposed_output.row(j)[i] = output.row(i)[j];
        }
    }

    int num_boxes = this->transposed_output.h;     // 8400
    int num_features = this->transposed_output.w;  // 84

    // Process detections
    for (int i = 0; i < num_boxes; i++) {
        const float* box_features = this->transposed_output.row(i);
        
        float cx = box_features[0];
        float cy = box_features[1];
        float w = box_features[2];
        float h = box_features[3];

        // Get class scores
        const float* class_scores = box_features + 4;
        float max_conf = 0.0f;
        int class_id = 0;
        for (int j = 0; j < NUM_CLASSES; j++) {
            if (class_scores[j] > max_conf) {
                max_conf = class_scores[j];
                class_id = j;
            }
        }

        if (max_conf < CONF_THRESHOLD) continue;

        // Convert center to corners
        float x1 = cx - w / 2.0f;
        float y1 = cy - h / 2.0f;
        float x2 = cx + w / 2.0f;
        float y2 = cy + h / 2.0f;

        // Scale from 640x640 to original image size
        float scale_x = (float)img_w / INPUT_SIZE;
        float scale_y = (float)img_h / INPUT_SIZE;

        int x1_orig = static_cast<int>(x1 * scale_x);
        int y1_orig = static_cast<int>(y1 * scale_y);
        int x2_orig = static_cast<int>(x2 * scale_x);
        int y2_orig = static_cast<int>(y2 * scale_y);

        // Clamp to image boundaries
        x1_orig = std::max(0, std::min(x1_orig, img_w - 1));
        y1_orig = std::max(0, std::min(y1_orig, img_h - 1));
        x2_orig = std::max(0, std::min(x2_orig, img_w - 1));
        y2_orig = std::max(0, std::min(y2_orig, img_h - 1));

        int box_width = x2_orig - x1_orig;
        int box_height = y2_orig - y1_orig;

        if (box_width > 0 && box_height > 0) {
            det.boxes.push_back(x1_orig);
            det.boxes.push_back(y1_orig);
            det.boxes.push_back(x2_orig);
            det.boxes.push_back(y2_orig);
            det.class_ids.push_back(class_id);
            det.confidences.push_back(max_conf);
        }
    }

    // Apply NMS and convert to DetectionResult
    if (!det.boxes.empty()) {
        std::vector<int> keep = nms(det.boxes, det.confidences, NMS_THRESHOLD);

        for (int idx : keep) {
            DetectionResult result;
            result.classId = det.class_ids[idx];
            result.confidence = det.confidences[idx];
            result.x = det.boxes[idx * 4];
            result.y = det.boxes[idx * 4 + 1];
            result.width = det.boxes[idx * 4 + 2] - det.boxes[idx * 4];
            result.height = det.boxes[idx * 4 + 3] - det.boxes[idx * 4 + 1];
            result.trackId = -1; // Default
            results.push_back(result);
        }
    }

    return results;
}
    std::vector<DetectionResult> YOLODetector::detectFromImageProxy(JNIEnv* env, jobject imageProxy) {
        auto start = std::chrono::high_resolution_clock::now();
        std::vector<DetectionResult> results;
        if (!modelLoaded) return results;

        // Get ImageProxy width and height
        jclass imageProxyClass = env->GetObjectClass(imageProxy);
        jmethodID getWidth = env->GetMethodID(imageProxyClass, "getWidth", "()I");
        jmethodID getHeight = env->GetMethodID(imageProxyClass, "getHeight", "()I");
        int width = env->CallIntMethod(imageProxy, getWidth);
        int height = env->CallIntMethod(imageProxy, getHeight);

        // Get Y plane (YUV_420_888)
        jmethodID getPlanes = env->GetMethodID(imageProxyClass, "getPlanes", "()[Landroid/media/Image$Plane;");
        jobjectArray planes = (jobjectArray)env->CallObjectMethod(imageProxy, getPlanes);
        jobject yPlane = env->GetObjectArrayElement(planes, 0);

        jclass planeClass = env->GetObjectClass(yPlane);
        jmethodID getBuffer = env->GetMethodID(planeClass, "getBuffer", "()Ljava/nio/ByteBuffer;");
        jobject yBuffer = env->CallObjectMethod(yPlane, getBuffer);

        unsigned char* yData = (unsigned char*)env->GetDirectBufferAddress(yBuffer);
        if (!yData) return results;

        // --- Optimized Preprocessing ---
        // Convert YUV420SP to RGB (reusing rgb_mat)
        ncnn::yuv420sp2rgb(yData, width, height, this->rgb_mat);

        // Preprocess: resize + normalize (reusing resized_input)
        ncnn::resize_bilinear(this->rgb_mat, this->resized_input, INPUT_SIZE, INPUT_SIZE);
        const float norm_vals[3] = {1.f/255.f, 1.f/255.f, 1.f/255.f};
        this->resized_input.substract_mean_normalize(nullptr, norm_vals);

        // Run inference
        ncnn::Mat output;
        ncnn::Extractor ex = net.create_extractor();
        ex.input("in0", this->resized_input);
        ex.extract("out0", output);

        // Postprocess detections
        std::vector<DetectionResult> raw_detections = postprocess(output, width, height);

        // --- Optimized ByteTrack Integration ---
        std::vector<Object> tracker_objects;
        for(const auto& det : raw_detections) {
            Object obj;
            obj.x = det.x;
            obj.y = det.y;
            obj.width = det.width;
            obj.height = det.height;
            obj.label = det.classId;
            obj.prob = det.confidence;
            tracker_objects.push_back(obj);
        }
        
        frame_counter++;
        std::vector<Object> tracked_objects;
        if (frame_counter >= TRACKER_FRAME_SKIP) {
            tracked_objects = tracker->update(tracker_objects);
            last_tracked_objects = tracked_objects;
            frame_counter = 0; // Reset counter
        } else {
            tracked_objects = last_tracked_objects; // Use stale tracks
        }
        
        for(const auto& t_obj : tracked_objects) {
            DetectionResult res;
            res.classId = t_obj.label;
            res.confidence = t_obj.prob;
            res.x = t_obj.x;
            res.y = t_obj.y;
            res.width = t_obj.width;
            res.height = t_obj.height;
            res.trackId = t_obj.track_id;
            results.push_back(res);
        }

        auto end = std::chrono::high_resolution_clock::now();
        auto duration = std::chrono::duration_cast<std::chrono::milliseconds>(end - start).count();
        float fps = 1000.0f / (duration > 0 ? duration : 1);
        LOGD("ImageProxy Inference time: %lld ms, FPS: %.2f", duration, fps);

        return results;
    }
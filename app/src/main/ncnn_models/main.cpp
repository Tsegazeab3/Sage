#include <chrono>
#include <iostream>
#include <ncnn/net.h>
#include <opencv/opencv.hpp>

int main() {
  ncnn::Net net;
  net.load_param("model.ncnn.param");
  net.load_model("model.ncnn.bin");

  // Search/load image
  std::string filename = "test.jpg"; // replace with your file
  cv::Mat img = cv::imread(filename);
  if (img.empty()) {
    std::cerr << "File not found: " << filename << std::endl;
    return -1;
  }

  cv::cvtColor(img, img, cv::COLOR_BGR2RGB);

  // Convert to ncnn::Mat
  ncnn::Mat in = ncnn::Mat::from_pixels_resize(img.data, ncnn::Mat::PIXEL_RGB,
                                               img.cols, img.rows, 224, 224);

  auto start = std::chrono::high_resolution_clock::now();

  ncnn::Extractor ex = net.create_extractor();
  ex.input("input", in);

  ncnn::Mat out;
  ex.extract("output", out);

  auto end = std::chrono::high_resolution_clock::now();
  double inference_time =
      std::chrono::duration<double, std::milli>(end - start).count();

  std::cout << "Inference done in " << inference_time << " ms" << std::endl;
  std::cout << "Output shape: " << out.w << "x" << out.h << "x" << out.c
            << std::endl;

  return 0;
}

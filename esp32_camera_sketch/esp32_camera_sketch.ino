#include "esp_camera.h"
#include <WiFi.h>
#include <WiFiUdp.h>
#include "soc/soc.h" // For disabling brownout detector
#include "soc/rtc_cntl_reg.h" // For disabling brownout detector

// Your WiFi credentials
const char* ssid = "aurak-residence";
const char* password = "8R3s!de@aurak8";

// Pin definition for AI-THINKER ESP32-CAM
#define PWDN_GPIO_NUM     32
#define RESET_GPIO_NUM    -1
#define XCLK_GPIO_NUM      0
#define SIOD_GPIO_NUM     26
#define SIOC_GPIO_NUM     27

#define Y9_GPIO_NUM       35
#define Y8_GPIO_NUM       34
#define Y7_GPIO_NUM       39
#define Y6_GPIO_NUM       36
#define Y5_GPIO_NUM       21
#define Y4_GPIO_NUM       19
#define Y3_GPIO_NUM       18
#define Y2_GPIO_NUM        5
#define VSYNC_GPIO_NUM    25
#define HREF_GPIO_NUM     23
#define PCLK_GPIO_NUM     22

// UDP settings
WiFiUDP udp;
IPAddress broadcastIP;
const int udpPort = 44444;

// Define a safe payload size, leaving room for IP/UDP headers to avoid fragmentation
#define MAX_PACKET_SIZE 1400 

uint16_t frame_id = 0;

void setup() {
  WRITE_PERI_REG(RTC_CNTL_BROWN_OUT_REG, 0); //disable brownout detector
  Serial.begin(115200);
  Serial.setDebugOutput(true);
  Serial.println();

  // Connect to Wi-Fi
  WiFi.begin(ssid, password);
  while (WiFi.status() != WL_CONNECTED) {
    delay(500);
    Serial.print(".");
  }
  Serial.println("");
  Serial.println("WiFi connected");
  Serial.print("IP Address: ");
  Serial.println(WiFi.localIP());

  // Calculate broadcast IP
  broadcastIP = WiFi.localIP() | ~WiFi.subnetMask();
  Serial.print("Broadcast IP: ");
  Serial.println(broadcastIP);

  // Camera configuration
  camera_config_t config;
  config.ledc_channel = LEDC_CHANNEL_0;
  config.ledc_timer = LEDC_TIMER_0;
  config.pin_d0 = Y2_GPIO_NUM;
  config.pin_d1 = Y3_GPIO_NUM;
  config.pin_d2 = Y4_GPIO_NUM;
  config.pin_d3 = Y5_GPIO_NUM;
  config.pin_d4 = Y6_GPIO_NUM;
  config.pin_d5 = Y7_GPIO_NUM;
  config.pin_d6 = Y8_GPIO_NUM;
  config.pin_d7 = Y9_GPIO_NUM;
  config.pin_xclk = XCLK_GPIO_NUM;
  config.pin_pclk = PCLK_GPIO_NUM;
  config.pin_vsync = VSYNC_GPIO_NUM;
  config.pin_href = HREF_GPIO_NUM;
  config.pin_sccb_sda = SIOD_GPIO_NUM;
  config.pin_sccb_scl = SIOC_GPIO_NUM;
  config.pin_pwdn = PWDN_GPIO_NUM;
  config.pin_reset = RESET_GPIO_NUM;
  config.xclk_freq_hz = 20000000;
  config.pixel_format = PIXFORMAT_JPEG;
  config.frame_size = FRAMESIZE_VGA; // 640x480
  config.jpeg_quality = 15; // 0-63, 0 is best quality
  config.fb_count = 1;

  // Camera init
  esp_err_t err = esp_camera_init(&config);
  if (err != ESP_OK) {
    Serial.printf("Camera init failed with error 0x%x", err);
    return;
  }
}

void loop() {
  camera_fb_t * fb = NULL;
  
  fb = esp_camera_fb_get();
  if (!fb) {
    Serial.println("Camera capture failed");
    return;
  }

  // --- UDP Frame Fragmentation and Sending Logic ---
  size_t total_packets = (fb->len + MAX_PACKET_SIZE - 1) / MAX_PACKET_SIZE;

  for (size_t i = 0; i < total_packets; i++) {
    size_t offset = i * MAX_PACKET_SIZE;
    size_t chunk_size = (i == total_packets - 1) ? (fb->len - offset) : MAX_PACKET_SIZE;

    // 4-byte header: frame_id (2 bytes), packet_num (1 byte), total_packets (1 byte)
    uint8_t header[4];
    header[0] = (frame_id >> 8) & 0xFF; // Frame ID High Byte
    header[1] = frame_id & 0xFF;        // Frame ID Low Byte
    header[2] = (uint8_t)i;             // Packet Number
    header[3] = (uint8_t)total_packets; // Total Packets

    udp.beginPacket(broadcastIP, udpPort);
    udp.write(header, 4);
    udp.write(fb->buf + offset, chunk_size);
    udp.endPacket();
  }

  frame_id++;
  
  esp_camera_fb_return(fb);

  // Delay for frame rate control
  vTaskDelay(200 / portTICK_PERIOD_MS); // Approximately 2.5 FPS
}
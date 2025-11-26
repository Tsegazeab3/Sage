#include <SPI.h>
#include <WiFi101.h>

char ssid[] = "M35";      // Your Wi-Fi network SSID
char pass[] = "00000001";  // Your Wi-Fi network password
IPAddress serverIP(100, 82, 61, 94); // The IP address of your Android device
int serverPort = 8080;               // The port the TCP server is listening on

WiFiClient client;

const int sensorPin1 = 4; // Front sensor
const int sensorPin2 = 5; // Overhead sensor

unsigned long lastReadTime = 0;
const unsigned long readInterval = 100; // 100ms

// --- Thresholds ---
float frontDistanceThreshold = 100.0; // Default front threshold in cm, will be updated by app
float overheadDistanceThreshold = 50.0; // Default overhead threshold in cm, will be updated by app

// State to track if we are in a danger state
bool dangerState = false;

// --- Function Prototypes ---
long readUltrasonicDistance(int sensorPin);
void connectToWiFi();
void connectToServer();
void sendMessage(const char* message);
void parseServerMessage(String message);

void setup() {
  Serial.begin(9600);
  Serial.println("Ultrasonic Arduino Sketch Starting...");

  connectToWiFi();
}

void loop() {
  if (!client.connected()) {
    connectToServer();
  }

  // Check for incoming messages from the server
  if (client.available()) {
    String line = client.readStringUntil('\n');
    line.trim();
    Serial.print("Received: ");
    Serial.println(line);
    parseServerMessage(line);
  }

  // Read sensors at intervals
  unsigned long currentTime = millis();
  if (currentTime - lastReadTime >= readInterval) {
    lastReadTime = currentTime;

    long duration1 = readUltrasonicDistance(sensorPin1);
    long duration2 = readUltrasonicDistance(sensorPin2);

    int distance1 = duration1 * 0.01723; // cm
    int distance2 = duration2 * 0.01723; // cm

    // Check for danger conditions using the respective thresholds
    bool isDanger = (distance1 > 0 && distance1 < frontDistanceThreshold) || 
                    (distance2 > 0 && distance2 < overheadDistanceThreshold);

    if (isDanger && !dangerState) {
      // State changed from SAFE to DANGER
      sendMessage("DANGER");
      dangerState = true;
    } else if (!isDanger && dangerState) {
      // State changed from DANGER to SAFE
      sendMessage("SAFE");
      dangerState = false;
    }
  }
}

void parseServerMessage(String message) {
    if (message.startsWith("THRESHOLDS:")) {
        // Expected format: "THRESHOLDS:front_threshold:overhead_threshold"
        int firstColon = message.indexOf(':');
        int secondColon = message.indexOf(':', firstColon + 1);

        if (firstColon > 0 && secondColon > 0) {
            String frontStr = message.substring(firstColon + 1, secondColon);
            String overheadStr = message.substring(secondColon + 1);

            frontDistanceThreshold = frontStr.toFloat();
            overheadDistanceThreshold = overheadStr.toFloat();

            Serial.println("Updated thresholds:");
            Serial.print("  Front: ");
            Serial.println(frontDistanceThreshold);
            Serial.print("  Overhead: ");
            Serial.println(overheadDistanceThreshold);
        }
    }
}

long readUltrasonicDistance(int sensorPin) {
  pinMode(sensorPin, OUTPUT);
  digitalWrite(sensorPin, LOW);
  delayMicroseconds(2);
  digitalWrite(sensorPin, HIGH);
  delayMicroseconds(10);
  digitalWrite(sensorPin, LOW);
  pinMode(sensorPin, INPUT);
  return pulseIn(sensorPin, HIGH);
}

void connectToWiFi() {
  Serial.print("Connecting to Wi-Fi");
  while (WiFi.status() != WL_CONNECTED) {
    WiFi.begin(ssid, pass);
    for (int i = 0; i < 10 && WiFi.status() != WL_CONNECTED; i++) {
      delay(1000);
      Serial.print(".");
    }
  }
  Serial.println("\nWiFi connected!");
}

void connectToServer() {
  while (!client.connected()) {
    Serial.print("Connecting to server...");
    if (client.connect(serverIP, serverPort)) {
      Serial.println(" connected!");
      // Identify this client to the server
      client.println("IAM:ULTRASONIC");
    } else {
      Serial.println(" failed, retrying in 5 seconds...");
      delay(5000);
    }
  }
}

void sendMessage(const char* message) {
  if (client.connected()) {
    client.println(message);
    Serial.print("Message sent to server: ");
    Serial.println(message);
  } else {
    Serial.println("Client not connected. Cannot send message.");
  }
}
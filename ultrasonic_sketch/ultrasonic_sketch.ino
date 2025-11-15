#include <SPI.h>
#include <WiFi101.h>

char ssid[] = "OPPO";      // Your Wi-Fi network SSID
char pass[] = "23542354";  // Your Wi-Fi network password
IPAddress serverIP(10, 110, 40, 114); // The IP address of your Android device
int serverPort = 8080;               // The port the TCP server is listening on

WiFiClient client;

const int sensorPin1 = 4; // 3-pin sensor: same pin for trigger and echo
const int sensorPin2 = 5; // 3-pin sensor: same pin for trigger and echo

unsigned long lastReadTime = 0;
const unsigned long readInterval = 100; // 100ms
const int DISTANCE_THRESHOLD = 50; // in cm

void setup() {
  Serial.begin(9600);
  Serial.println("Ultrasonic Arduino Sketch Starting...");

  connectToWiFi();
}

void loop() {
  if (!client.connected()) {
    connectToServer();
  }

  // Read sensors at intervals
  unsigned long currentTime = millis();
  if (currentTime - lastReadTime >= readInterval) {
    lastReadTime = currentTime;

    long duration1 = readUltrasonicDistance(sensorPin1);
    long duration2 = readUltrasonicDistance(sensorPin2);

    int distance1 = duration1 * 0.01723; // cm
    int distance2 = duration2 * 0.01723; // cm

    // Only send a message if an object is too close
    if (distance1 < DISTANCE_THRESHOLD || distance2 < DISTANCE_THRESHOLD) {
      sendMessage("DANGER");
    }
  }

  // We don't expect any messages from the server in this version, so just flush the buffer.
  if (client.available()) {
    client.flush();
  }

  delay(50);
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
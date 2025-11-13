#include <SPI.h>
#include <WiFi101.h>

char ssid[] = "NOSTALGIA";      // Your Wi-Fi network SSID
char pass[] = "lifelife";  // Your Wi-Fi network password
IPAddress buttonArduinoIP(192, 168, 137, 104); // The IP address of your ButtonArduino
int buttonArduinoPort = 8081;

WiFiClient client;

const int triggerPin1 = 4;
const int echoPin1 = 4;
const int triggerPin2 = 5;
const int echoPin2 = 5;

unsigned long lastReadTime = 0;
const unsigned long readInterval = 100; // 100ms

long readUltrasonicDistance(int triggerPin, int echoPin) {
  pinMode(triggerPin, OUTPUT);
  digitalWrite(triggerPin, LOW);
  delayMicroseconds(2);

  digitalWrite(triggerPin, HIGH);
  delayMicroseconds(10);
  digitalWrite(triggerPin, LOW);

  pinMode(echoPin, INPUT);
  return pulseIn(echoPin, HIGH); // Duration in microseconds
}

void setup() {
  Serial.begin(9600);

  // Connect to Wi-Fi
  Serial.print("Connecting to Wi-Fi");
  while (WiFi.begin(ssid, pass) != WL_CONNECTED) {
    delay(1000);
    Serial.print(".");
  }
  Serial.println("\nConnected!");
}

void loop() {
  // Connect to server if not connected
  if (!client.connected()) {
    Serial.println("Connecting to ButtonArduino...");
    if (client.connect(buttonArduinoIP, buttonArduinoPort)) {
      Serial.println("Connected to ButtonArduino!");
    } else {
      Serial.println("Connection failed, retrying...");
      delay(2000);
      return;
    }
  }

  // Read sensors at intervals
  unsigned long currentTime = millis();
  if (currentTime - lastReadTime >= readInterval) {
    lastReadTime = currentTime;

    // Read pulse time
    long duration1 = readUltrasonicDistance(triggerPin1, echoPin1);
    long duration2 = readUltrasonicDistance(triggerPin2, echoPin2);

    // Convert duration to cm (speed of sound: 343 m/s)
    int distance1 = duration1 * 0.01723; // cm
    int distance2 = duration2 * 0.01723; // cm

    // Send to server
    String cmd = String(distance1) + "," + String(distance2);
    client.println(cmd);

    Serial.print("Sent command: ");
    Serial.println(cmd);
  }

  // Check server response
  if (client.available()) {
    String response = client.readStringUntil('\n');
    response.trim();
    if (response.length() > 0) {
      Serial.print("Server: ");
      Serial.println(response);
    }
  }
}

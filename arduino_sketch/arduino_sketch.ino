#include <SPI.h>
#include <WiFi101.h>

char ssid[] = "OPPO";      // Your Wi-Fi network SSID
char pass[] = "23542354";  // Your Wi-Fi network password
IPAddress serverIP(10, 110, 40, 114); // The IP address of your Android device
int serverPort = 8080;               // The port the TCP server is listening on

WiFiServer server(8081);

const int buttonPin1 = 7;
const int buttonPin2 = 11;
const int buttonPin3 = 9;
const int buzzerPin = 8;
const int DISTANCE_THRESHOLD = 50; // in cm

// Variables to store the last state of the buttons, for edge detection
unsigned long lastButtonPressTime1 = 0;
unsigned long lastButtonPressTime2 = 0;
unsigned long lastButtonPressTime3 = 0;
bool lastButtonState1 = HIGH;
bool lastButtonState2 = HIGH;
bool lastButtonState3 = HIGH;
bool button1PressedOnce = false;
bool button2PressedOnce = false;
bool button3PressedOnce = false;
const unsigned long doublePressTimeout = 500; // 500ms timeout for double press

unsigned long lastReconnectAttempt = 0;
bool wifiFirstConnected = false;
void printWifiStatus(); // Forward declaration

void setup() {
  Serial.begin(9600);
  Serial.println("Sketch starting... Serial connection is active.");

  pinMode(buttonPin1, INPUT_PULLUP);
  pinMode(buttonPin2, INPUT_PULLUP);
  pinMode(buttonPin3, INPUT_PULLUP);
  pinMode(buzzerPin, OUTPUT);

  connectToWiFi();
  server.begin();
}

void loop() {
  if (WiFi.status() != WL_CONNECTED) {
    if (wifiFirstConnected) { // Only print this message if it was previously connected
        Serial.println("WiFi disconnected.");
        wifiFirstConnected = false;
    }
    // Non-blocking reconnection attempt
    unsigned long currentMillis = millis();
    if (currentMillis - lastReconnectAttempt > 10000) { // Try every 10 seconds
      lastReconnectAttempt = currentMillis;
      Serial.println("Attempting to reconnect to WiFi...");
      WiFi.begin(ssid, pass);
    }
  } else {
    if (!wifiFirstConnected) {
      wifiFirstConnected = true;
      Serial.println("\nWiFi connected!");
      Serial.flush(); // Force send of serial data
      printWifiStatus(); // This will now also send the IP
    }
    handleClient();
  }

  // Read the current state of the buttons
  bool currentButtonState1 = digitalRead(buttonPin1);
  bool currentButtonState2 = digitalRead(buttonPin2);
  bool currentButtonState3 = digitalRead(buttonPin3);

  // Button 1 logic
  if (lastButtonState1 == HIGH && currentButtonState1 == LOW) { // Button 1 pressed
    if (!button1PressedOnce) {
      button1PressedOnce = true;
      lastButtonPressTime1 = millis();
    }
  } else if (lastButtonState1 == LOW && currentButtonState1 == HIGH) { // Button 1 released
    if (button1PressedOnce) {
      sendMessage("BUTTON_1_PRESSED");
      button1PressedOnce = false;
    }
  }

  // Button 2 logic
  if (lastButtonState2 == HIGH && currentButtonState2 == LOW) { // Button 2 pressed
    if (!button2PressedOnce) {
      button2PressedOnce = true;
      lastButtonPressTime2 = millis();
    }
  } else if (lastButtonState2 == LOW && currentButtonState2 == HIGH) { // Button 2 released
    if (button2PressedOnce) {
      sendMessage("BUTTON_2_PRESSED");
      button2PressedOnce = false;
    }
  }

  // Button 3 logic for single and double press
  if (lastButtonState3 == HIGH && currentButtonState3 == LOW) { // Button 3 pressed
    if (!button3PressedOnce) {
      button3PressedOnce = true;
      lastButtonPressTime3 = millis();
    } else {
      if (millis() - lastButtonPressTime3 < doublePressTimeout) {
        sendMessage("button3_pressed_twice");
        button3PressedOnce = false; // Reset after double press
      }
    }
  } else if (lastButtonState3 == LOW && currentButtonState3 == HIGH) { // Button 3 released
    // This part is intentionally left blank for button 3 to handle double press correctly
  }

  // Check for single press timeout for button 3
  if (button3PressedOnce && (millis() - lastButtonPressTime3 >= doublePressTimeout)) {
    sendMessage("BUTTON_3_PRESSED");
    button3PressedOnce = false;
  }

  // Update last button states
  lastButtonState1 = currentButtonState1;
  lastButtonState2 = currentButtonState2;
  lastButtonState3 = currentButtonState3;

  delay(50); // Debounce delay
}

void handleClient() {
  WiFiClient client = server.available();
  if (client) {
    Serial.println("New client");
    while (client.connected()) {
      if (client.available()) {
        String line = client.readStringUntil('\n');
        line.trim();
        Serial.print("Received: ");
        Serial.println(line);
        if (line == "DANGER_DETECTED") {
          Serial.println("DANGER DETECTED! Activating buzzer.");
          digitalWrite(buzzerPin, HIGH);
          delay(5000);
          digitalWrite(buzzerPin, LOW);
        } else {
          // Assuming the message from the ultrasonic sensor is "distance1,distance2"
          int commaIndex = line.indexOf(',');
          if (commaIndex > 0) {
            String distance1Str = line.substring(0, commaIndex);
            String distance2Str = line.substring(commaIndex + 1);
            int distance1 = distance1Str.toInt();
            int distance2 = distance2Str.toInt();

            if (distance1 < DISTANCE_THRESHOLD || distance2 < DISTANCE_THRESHOLD) {
              Serial.println("Object too close! Activating buzzer.");
              digitalWrite(buzzerPin, HIGH);
              delay(500);
              digitalWrite(buzzerPin, LOW);
            }
          }
        }
      }
    }
    client.stop();
    Serial.println("Client disconnected");
  }
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
  // The loop() function will now handle printing status and sending IP.
}

void sendMessage(const char* message) {
  WiFiClient localClient; // Use a local client for each attempt

  while (true) {
    if (localClient.connect(serverIP, serverPort)) {
      localClient.println(message);
      Serial.print("Message sent to server: ");
      Serial.println(message);
      localClient.stop();
      return; // Success
    }
    Serial.print("Connection to server failed for message: ");
    Serial.println(message);
    Serial.println("Retrying in 5 second...");
    delay(5000);
  }
}

void printWifiStatus() {
  // print the SSID of the network you're attached to:
  Serial.print("SSID: ");
  Serial.println(WiFi.SSID());

  // print your device's IP address:
  IPAddress ip = WiFi.localIP();
  Serial.print("IP Address: ");
  Serial.println(ip);

  // print the received signal strength:
  long rssi = WiFi.RSSI();
  Serial.print("signal strength (RSSI):");
  Serial.print(rssi);
  Serial.println(" dBm");

  Serial.flush(); // Force send of serial data

  // Send the IP address to the server
  char ipStr[16];
  sprintf(ipStr, "%d.%d.%d.%d", ip[0], ip[1], ip[2], ip[3]);
  char message[32];
  sprintf(message, "IP:%s", ipStr);
  sendMessage(message);
}
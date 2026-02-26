#include <SPI.h>
#include <WiFi101.h>

char ssid[] = "M35";      // Your Wi-Fi network SSID
char pass[] = "00000001";  // Your Wi-Fi network password
IPAddress serverIP(100, 82, 7, 74); // The IP address of your Android device
int serverPort = 8080;               // The port the TCP server is listening on

WiFiClient client;

const int buttonPin1 = 7;
const int buttonPin2 = 11;
const int buttonPin3 = 9;
const int buzzerPin = 1;

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

void setup() {
  Serial.begin(9600);
  Serial.println("Button Arduino Sketch Starting...");

  pinMode(buttonPin1, INPUT_PULLUP);
  pinMode(buttonPin2, INPUT_PULLUP);
  pinMode(buttonPin3, INPUT_PULLUP);
  pinMode(buzzerPin, OUTPUT);

  connectToWiFi();
}

void loop() {
  if (!client.connected()) {
    connectToServer();
  }

  // Handle incoming messages from the server
  if (client.available()) {
    String line = client.readStringUntil('\n');
    line.trim();
    Serial.print("Received from server: ");
    Serial.println(line);
    if (line == "BUZZ") {
      Serial.println("BUZZ command received! Activating buzzer.");
      digitalWrite(buzzerPin, HIGH);
    } else if (line == "STOP_BUZZ") {
      Serial.println("STOP_BUZZ command received! Deactivating buzzer.");
      digitalWrite(buzzerPin, LOW);
    }
  }

  // Read the current state of the buttons and send messages
  handleButtons();

  delay(50); // Debounce delay
}

void handleButtons() {
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
      client.println("IAM:BUTTON");
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

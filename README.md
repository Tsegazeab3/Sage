# Object Detection for Accessibility

This is an Android application for object detection designed with accessibility in mind. The app helps visually impaired users navigate their environment by detecting objects and providing audio and haptic feedback. It can operate in two modes: "House Mode" for indoor environments and "Road Mode" for outdoor environments.

## Features

*   **Real-time Object Detection:** The app uses a YOLO model running on the device with the NCNN deep learning framework to detect objects in real-time from the phone's camera or an external ESP32 camera.
*   **Multiple Operating Modes:**
    *   **House Mode:** Optimized for indoor environments, with a specific list of objects it can detect.
    *   **Road Mode (In-Progress):** Intended for outdoor use.
*   **Hardware Integration:**
    *   **Arduino:** The app communicates with an Arduino device to receive button inputs for controlling the app and to send configuration data like ultrasonic sensor thresholds for obstacle avoidance.
    *   **ESP32 Camera:** The app can stream video from an external ESP32 camera.
*   **Accessibility Features:**
    *   **Audio Feedback:** Provides spoken feedback for all major actions and detections.
    *   **Haptic Feedback:** The Arduino integration provides haptic feedback through connected motors or vibrators.
    *   **Simple UI:** The user interface is designed to be simple and easy to navigate, with large buttons and clear layouts.
*   **Emergency Assistance:** A dedicated hardware button can trigger an emergency call to a pre-configured number.
*   **Customization:**
    *   **Dangerous Items:** Users can specify a list of "dangerous" objects. The app will provide a more urgent warning when these are detected.
    *   **Sensor Thresholds:** The sensitivity of the ultrasonic sensors can be adjusted.
    *   **Camera Source:** Users can switch between the phone's camera and an ESP32 camera.

## Hardware Controller

The hardware controller is an Arduino-based device with three buttons that allow the user to control the app without touching the screen.

*   **Button 1:** Toggles between "House Mode" and "Road Mode".
*   **Button 2:** Opens the camera in "Preview" mode, showing a live feed with object detection overlays.
*   **Button 3:** Opens the camera in "Search" mode. In this mode, the user can select an object to search for, and the app will provide feedback when the object is detected.
*   **Button 3 (Double Press):** Immediately initiates a call to the pre-configured emergency number.

## Operation Modes

The application has several modes of operation that can be configured in the settings screen or via the hardware controller.

*   **Operating Mode:**
    *   **House Mode:** This mode is optimized for indoor environments. It uses a specific set of object classes for detection that are commonly found in a house.
    *   **Road Mode:** This mode is intended for outdoor environments. This feature is still under development.

*   **Detection Mode:**
    *   **Automatic:** The application continuously detects objects and provides feedback automatically.
    *   **Manual:** The user needs to trigger the detection manually by pressing a button.

*   **Camera Source:**
    *   **Phone Camera:** Uses the phone's built-in camera for object detection.
    *   **ESP32 Camera:** Streams video from an external ESP32 camera for object detection.

## Arduino Connector

The `ArduinoConnector.kt` object manages the communication between the Android app and the external Arduino/ESP32 devices. It uses a TCP server to listen for connections from two clients:

*   **`BUTTON` client:** This client is responsible for sending button press events to the app. The app listens for these events to trigger actions like changing the operating mode or opening the camera. The app can also send commands to this client to control a buzzer (e.g., `BUZZ`, `STOP_BUZZ`).
*   **`ULTRASONIC` client:** This client sends ultrasonic sensor data to the app. The app uses this data to detect obstacles and provides feedback to the user. The app can configure the ultrasonic sensor thresholds by sending a `THRESHOLDS` message to this client.

The communication is handled by the `TCPServer` class, which is not detailed here.

## Project Structure

The project is divided into several modules:

*   **`app`:** The main Android application module, written in Kotlin.
    *   **`src/main/java`:** Contains the Kotlin source code for the application.
        *   **`MainActivity.kt`:** The main entry point of the application.
        *   **`AccessibilityFirstApp.kt`:** The main Composable function that defines the UI and navigation of the app.
        *   **`yolodetector.kt`:** A wrapper around the native C++ code for the YOLO detector.
        *   **`ArduinoConnector.kt`:** Handles communication with the Arduino device.
        *   **`ESP32CameraStream.kt`:** Handles the video stream from the ESP32 camera.
    *   **`src/main/cpp`:** Contains the C++ source code for the object detector, using the NCNN framework.
    *   **`src/main/assets`:** Contains the YOLO model files (`model.ncnn.param` and `model.ncnn.bin`).
*   **`arduino_sketch`:** Contains the Arduino sketch for the hardware controller.
*   **`esp32_camera_sketch`:** Contains the Arduino sketch for the ESP32 camera.

## How to Build

1.  Open the project in Android Studio.
2.  Make sure you have the Android NDK installed.
3.  Build the project. Android Studio will automatically compile the C++ code and include it in the APK.

## Dependencies

*   **NCNN:** A high-performance neural network inference framework for mobile platforms.
*   **Jetpack Compose:** Android's modern toolkit for building native UI.
*   **CameraX:** A Jetpack library for camera development.
*   **Coroutines:** For asynchronous programming.
*   **Material Design:** For UI components.

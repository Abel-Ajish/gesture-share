# GestureShare

GestureShare is an Android application that allows users to share screenshots by drawing a circle gesture on their screen while using any app.

## Features
- **Background Overlay**: Transparent overlay detects circle gestures without interrupting your workflow.
- **Circle Gesture Detection**: Sophisticated detection logic that ignores random scribbles.
- **Background Screenshot**: Captures the screen using MediaProjection API without interrupting the user.
- **Local Network Sharing**: TCP-based local transfer between devices on the same subnet.
- **Bidirectional**: Each device can both send and receive screenshots.
- **Animated Receipt**: Smooth animation of received screenshots with a glowing circle.

## How to Build and Get the APK
You can generate the APK in two main ways:

### 1. Using Android Studio (Recommended)
1.  **Open the Project**: Open Android Studio and select "Open" then navigate to the `gesture-share-app` folder.
2.  **Sync Project**: Wait for the Gradle sync to complete.
3.  **Build APK**: Go to the top menu: `Build` -> `Build Bundle(s) / APK(s)` -> `Build APK(s)`.
4.  **Locate APK**: Once the build finishes, a notification will appear. Click "locate" or find it at:
    `app/build/outputs/apk/debug/app-debug.apk`

### 2. Using Command Line (Gradle)
If you have Gradle installed on your system, run the following command in the project root:
```bash
gradle assembleDebug
```
The generated APK will be available at:
`app/build/outputs/apk/debug/app-debug.apk`

### 3. Using GitHub Actions (Automated CI)
This project includes a GitHub Actions workflow that automatically builds the APK on every push to `main` or `master`.
1.  **Push to GitHub**: Push this code to a GitHub repository.
2.  **Go to Actions**: Navigate to the "Actions" tab in your repository.
3.  **Download Artifact**: Once the "Android CI" workflow completes, click on the run and download the `GestureShare-Debug-APK` from the artifacts section.

## Architecture
- **MVP-like Structure**:
  - `overlay`: Handles the `OverlayService` and transparent window.
  - `gesture`: Logic for circle detection (`CircleDetector`).
  - `screenshot`: Encapsulates MediaProjection API (`ScreenCapture`).
  - `network`: TCP-based `Sender` and `Receiver`.
  - `ui`: Animation and UI components.
- **Concurrency**: Uses Kotlin Coroutines for non-blocking network and IO operations.

## Important Note on Overlay Pass-Through
Android's `SYSTEM_ALERT_WINDOW` can either capture touches OR pass them through. To capture gestures and then pass them through, a more complex approach using `AccessibilityService` is usually required. This app uses a full-screen transparent overlay for gesture detection as per the requirements.

# PiNote APK

Android companion app for [PiNote](https://github.com/Awesmoe/pinote) — draw handwritten notes with your S Pen (or finger!) and send them to a Raspberry Pi display.

## Features

- Freehand drawing canvas with S Pen support
- S Pen only mode (reject finger input)
- Send notes to Pi over local network with automatic Tailscale fallback
- Multiple server profiles — switch between Pi devices from the main screen
- Send line breaks to space out notes on the Pi
- Clear Pi display remotely

## Setup

1. Set up the [PiNote server](https://github.com/Awesmoe/pinote) on your Raspberry Pi
2. Build and install this app on any Android device (S Pen features optional)
3. Open Settings (gear icon) and add a server with your Pi's local IP (and optionally a Tailscale IP for remote access)
4. Draw and hit "Send to Pi"

To manage multiple Pi servers, add additional profiles in Settings. Switch between them using the buttons above the action bar on the main screen.

## Building

Open in Android Studio and run on device. Requires min SDK 26 (Android 8.0+).

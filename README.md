# PiNote APK

Android companion app for [PiNote](https://github.com/Awesmoe/pinote) — draw handwritten notes with your S Pen and send them to a Raspberry Pi display.

## Features

- Freehand drawing canvas with S Pen support
- S Pen only mode (reject finger input)
- Send notes to Pi over local network or Tailscale fallback
- Configurable server IP and port
- Clear Pi display remotely

## Setup

1. Set up the [PiNote server](https://github.com/Awesmoe/pinote) on your Raspberry Pi
2. Build and install this app on a Samsung device with S Pen
3. Open Settings (gear icon) and enter your Pi's IP address
4. Draw and hit "Send to Pi"

## Building

Open in Android Studio and run on device. Requires min SDK 26.

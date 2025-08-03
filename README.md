# LabelPrinter
An Android application for USB printing with the Brother PT-D600 label printer.

## Features
### Image Manipulation
- Crop, rotate or flip images
- Dither images, or use a threshold to convert the image to B/W
- Print preview
### Printer Status Integration
- Automatically detect the size of label
- Multiple copies support (reducing paper usage)
- Printing progress & error detection

https://github.com/user-attachments/assets/0bfce49f-b533-46df-825e-272f097e0c08

## Technical Details
This project is built using the native [Jetpack Compose](https://developer.android.com/compose) framework for Android with [Material Design 3](https://developer.android.com/develop/ui/compose/designsystems/material3) UI components.

### Architecture Overview
![Architecture Overview](doc/labelprinterarchitecture.svg)

### USB Protocol
Documentation is readily available for similar models in the same family as the PT-D600 label printer. Thankfully, there are only minimal differences in the protocol between the models. To further verify this, the data packets sent using the official drivers were captured and analyzed using a Hex Editor like ImHex Editor (which I highly recommend).
![USB Byte Stream analyzed using ImHex Editor](doc/labelprinterimhex.png "USB byte stream analyzed using ImHex Editor")
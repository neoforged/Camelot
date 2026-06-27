# Scam Detection Module
The scam detection module (id: `scam-detection`, configuration class: `ScamDetection`) has a few features that can be used to detect potential scams,
and temporarily time-out the user, pending moderator verification.

## Image content scam detection
The module can scan the content of attachments using Optical Character Recognition.
Image text will be tested against the provided regular expressions, and if a match is found, the message is flagged as a potential scam.
![Image scam detection](./image-scam-detection.png)

::: warning
While the module itself is light on memory and CPU consumption, OCR is not.  
For accuracy, all images sent in your server will be upscaled by a factor of 3 and then run through tesseract-ocr serially.  
This will incur a significant memory and processing cost that you should account for.
:::

## Bot Permissions
The following bot permissions are required by this module: `Kick Members`, `Ban Members`, `Time out Members`, `Manage Messages`.  
The following intents are required by this module: `Message content`.

This module does not require permanent data storage.

# Automated Hardware Inventory Scanner

A high-performance Android application designed to automate large-scale hardware verification and replace manual Excel data entry. This tool provides a multi-threaded, asynchronous data pipeline that safely processes real-time hardware camera feeds, queries a local SQLite database, and dynamically reads/writes to complex `.xlsx` files without blocking the main UI thread.

## 🚀 Business Impact
Developed to solve a real-world operational bottleneck, this application eliminates the need for manual serial number tracking. Operators can securely import a master manifest, utilize hardware-accelerated QR/Barcode scanning for instant verification, and export a modified, timestamped master file—reducing inventory audit times and eliminating human error.

## 🛠️ Architecture & Tech Stack
Built entirely in **Kotlin** utilizing modern Android development standards:

* **UI Layer:** Jetpack Compose (Declarative, reactive UI)
* **Architecture:** MVVM (Model-View-ViewModel) for strict separation of concerns
* **Concurrency:** Kotlin Coroutines (`Dispatchers.IO`) for safe, asynchronous database queries and file I/O
* **Local Storage:** Room Database (SQLite abstraction layer)
* **Hardware Integration:** CameraX API for high-resolution, lifecycle-aware barcode scanning
* **File I/O:** Apache POI for dynamic Excel workbook parsing and modification

## ⚙️ Core System Features

* **Asynchronous Excel Parsing:** Safely loads large `.xlsx` manifests into local cache memory, reading specific indices while preserving untouched proprietary columns.
* **Real-Time Vision Processing:** Uses ML Kit / CameraX to process camera frames in real-time. Implements mathematical debouncing to prevent duplicate scan logs within a 5-second window.
* **Thread-Safe Database Transactions:** Utilizes StateFlow and Room DAOs to pass data from the hardware thread, through the ViewModel, and into the local SQLite database without dropping frames or freezing the UI.
* **Dynamic Data Appending:** Intelligently checks scanned serial numbers against the master list. Matches are timestamped in-place, while unknown foreign hardware generates dynamically appended rows at the end of the data structure.
* **Haptic Feedback Loop:** Triggers device vibration motors upon successful data insertion, allowing operators to scan rapidly without visual confirmation.

## 📱 App Workflow

1.  **Import:** The user selects a master `.xlsx` file. The app caches the file and builds a local SQLite database of expected hardware.
2.  **Scan:** The user points the camera at a hardware part. The app decodes the serial number, verifies it against the database, updates the row to "Scanned", and vibrates.
3.  **Export:** The app opens the cached `.xlsx` file, updates the exact cells with precise timestamps, dynamically appends any unknown scanned parts, and outputs the final master sheet.
4.  **Clear:** A single command instantly wipes the local SQLite database, resetting the tool for the next shipment.

## 💡 Engineering Focus
This project demonstrates rigorous state management and the ability to build robust, crash-resistant mobile tools that handle complex file I/O and hardware streams concurrently.

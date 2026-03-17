# Itinerary Prettifier
## 📋 Project Overview

The **Itinerary Prettifier** is a command-line utility designed to transform raw, machine-readable flight itinerary text files into customer-friendly documents. It automatically replaces cryptic airport IATA/ICAO codes with real airport names or city names and converts ISO 8601 timestamps into human-readable formats (dates, 12h/24h time).

---
## 🛠 Setup and Installation
1. Prerequisites: Ensure you have Java 17 or higher installed on your system.
2. Download: Place Prettifier.java in your desired directory.
3. Compilation: Open your terminal in the directory and run:
```bash
javac Prettifier.java
```
---
## 🏃‍♀️ Usage Guide
The tool requires three arguments: the input file, the output file, and the airport lookup CSV.

Standard command:
```Bash
java Prettifier ./input.txt ./output.txt ./airport-lookup.csv
```
Help command:
```Bash
java Prettifier -h
```
---
## 📄 Additional Features & Bonus Functionality
**Dynamic CSV Parsing:** The program automatically detects column order in the airport-lookup.csv by reading the header row, supporting flexible data structures.

**City Name Support:** By prefixing codes with * (e.g., *#LAX), the program outputs the city name instead of the airport name.

**Bonus (Colored Output):** A --color flag has been implemented to print the processed itinerary to the console with ANSI color coding for better readability.
Use: 
```bash
java Prettifier --color ./input.txt ./output.txt ./airport-lookup.csv
```
The output file remains clean and plain-text, while the console display highlights dates (green), times (cyan), and Airport names (yellow).
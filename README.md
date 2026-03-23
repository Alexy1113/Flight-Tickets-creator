# Itinerary Prettifier
# 📋 Project Overview

The **Itinerary Prettifier** is a command-line utility designed to transform raw, machine-readable flight itinerary text files into customer-friendly documents. It automatically replaces cryptic airport IATA/ICAO codes with real airport names or city names and converts ISO 8601 timestamps into human-readable formats (dates, 12h/24h time).

---
# 🛠 Setup and Installation
1. Prerequisites: Ensure you have Java 17 or higher installed on your system.
2. Download: Place Prettifier.java in your desired directory.
3. Compilation: Open your terminal in the directory and run:
```bash
javac Prettifier.java
```
---
# 🏃‍♀️ Usage Guide
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
# 📄Functionality
**Dynamic CSV Parsing:** The program automatically detects column order in the airport-lookup.csv by reading the header row, supporting flexible data structures.

**Internal Handling:** The program uses a strict CharsetDecoder to ensure that any corrupted data (invalid byte sequences) in the source CSV is detected, triggering an Airport lookup malformed error as per requirements.

**City Name Support:** By prefixing codes with * (`*#LAX`), the program outputs the city name instead of the airport name.

**Data Integrity & Consistency:** Deep CSV Validation & Consistency Check: The program performs a rigorous validation of the CSV file. If duplicate airport codes (IATA/ICAO) are encountered, the system compares all available fields: Name, Municipality, ISO Country, and Coordinates. Any discrepancy in data for the same airport code will trigger an `Airport lookup malformed` error. This ensures 100% data integrity and output reliability.

### 🛡️Replacement Logic
The replacement engine operates with high predictability:
1. **Validation First:** Every match is strictly checked against the database before any string modification occurs.
2. **Instant Termination:** The use of `System.exit(1)` ensures that a single missing data point halts the entire pipeline, preventing the generation of incomplete output.
3. **Regex Safety:** `Matcher.quoteReplacement` is used to treat airport names as literal strings, preventing any internal special characters from interfering with the Regex engine.
**Whitespace Cleaning:** All CSV data (codes, names, coordinates) is automatically cleared of random spaces at the edges (trim()), which prevents comparison errors.
**Line Break Normalization:** The program automatically recognizes any system line endings (Windows \r\n or Unix \n) and converts them to a single standard, while preserving the paragraph structure.
---
# 📅 Supported Time/Date Formats
The program recognizes and converts the following timestamps:  
`D(ISO_DATE)` -> `05 Apr 2007` (Format: Day Month Year).  
`T12(ISO_DATE)` -> `10:30 AM (+02:00)`(12-hour format with offset).  
`T24(ISO_DATE)` -> `22:30 (+02:00)` (24-hour format with offset).  
**Smart Timezone Handling:** Even if the original date contains Z (Zulu) time, the program correctly converts it to `(+00:00)` format, maintaining output consistency.

---
# Error Messages
* **Input not found** — the ticket file is missing.
* **Airport lookup not found** — the required code is not in the directory.  
* **Airport lookup malformed** — an error in the CSV structure, encoding, or inconsistent data (different coordinates for the same code).
---
# ⚙️ Technical Details  
**Language:** Java 17+  
**Memory Efficiency:** Using HashMap provides data retrieval in constant time $O(1)$, which allows instant processing of even huge files and directories.  
**Robust Regex:** Patterns are designed to not affect plain text that looks like codes unless it is marked with the special `#` or `##` characters.

---
# 🚀 Bonus (Colored Console Output):
The output file remains clean and plain-text, while the console display highlights dates (green), times (cyan), and Airport names (yellow).

# 🛠 Troubleshooting: Encoding Issues in Terminal (Special Characters)
If you see:  
`Rivi├иre` or `Rivi?re` instead of `Rivière`

#### 1. 🪟 Windows PowerShell & CMD  
Before running the program, execute the following command in your terminal to enable UTF-8 support:

* **PowerShell:**
 ```PowerShell
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
```
* **Command Prompt (CMD) and DOS:**
```cmd
chcp 65001
```
#### 2.🐧 Linux & 🍎 macOS & Git Bash Users
If you encounter encoding issues in Linux or Git Bash:

* **Linux:** Ensure your locale is set to UTF-8 by running `export LANG=en_US.UTF-8`.
* **Git Bash:** 1. Right-click the terminal -> **Options** -> **Text** -> Set **Character set** to `UTF-8`.
* **Execution:** You can force Java to use UTF-8 by adding the encoding flag:
  ```bash
  java -Dfile.encoding=UTF-8 -jar your_program.jar
  ```

#### 3. ⚙️ IDE Configuration (Recommended)

To avoid manually setting the encoding in the terminal, configure your IDE:

* **VS Code:** Add `"JAVA_TOOL_OPTIONS": "-Dfile.encoding=UTF-8"` to your `terminal.integrated.env.windows` settings.
* **IntelliJ IDEA:** Add `-Dfile.encoding=UTF-8` to your **VM options** in the Run/Debug configuration.
* **Global:** Set the system environment variable `JAVA_TOOL_OPTIONS` to `-Dfile.encoding=UTF-8`.
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

**City Name Support:** By prefixing codes with * (e.g., *#LAX), the program outputs the city name instead of the airport name.

### 🛡️Replacement Logic
The replacement engine operates with high predictability:
1. **Validation First:** Every match is strictly checked against the database before any string modification occurs.
2. **Instant Termination:** The use of `System.exit(1)` ensures that a single missing data point halts the entire pipeline, preventing the generation of incomplete output.
3. **Regex Safety:** `Matcher.quoteReplacement` is used to treat airport names as literal strings, preventing any internal special characters from interfering with the Regex engine.
---
# 🚀 Bonus (Colored Console Output):
The output file remains clean and plain-text, while the console display highlights dates (green), times (cyan), and Airport names (yellow).

## 🛠 Troubleshooting: Encoding Issues in Terminal (Special Characters)
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
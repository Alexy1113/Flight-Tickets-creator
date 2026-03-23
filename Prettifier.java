import java.io.*;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Prettifier {
    
    // Create static "dictionaries" for fast lookups

    // 1. Dictionaries for primary data (used in the ticket)
    static Map<String, String> iataToName = new HashMap<>();
    static Map<String, String> icaoToName = new HashMap<>();
    static Map<String, String> iataToCity = new HashMap<>();
    static Map<String, String> icaoToCity = new HashMap<>();

    // 2. Additional dictionaries for CSV data integrity checks
    static Map<String, String> iataToCountry = new HashMap<>();
    static Map<String, String> icaoToCountry = new HashMap<>();
    static Map<String, String> iataToCoords = new HashMap<>();
    static Map<String, String> icaoToCoords = new HashMap<>();
    
    // The main method that starts the program.
    // It checks user commands, loads data, and manages file streams.
    public static void main(String[] args) {

        // Force set UTF-8 for console output
        System.setOut(new PrintStream(System.out, true, StandardCharsets.UTF_8));
        
        // 1. Check if the user requested help via the "-h" flag
        if (args.length > 0 && args[0].equals("-h")) {
            System.out.println("\u001B[33mitinerary usage:\n$ java Prettifier.java ./input.txt ./output.txt ./airport-lookup.csv\u001B[0m");
            System.exit(0); // Exit with code 0, as the user just wanted to see the instructions
        }

        // 2. Check if exactly 3 arguments are passed (input file, output file, data file)
        // If there are not 3 arguments, the call was incorrect
        if (args.length != 3) {
            System.out.println("\u001B[33mitinerary usage:\n$ java Prettifier.java ./input.txt ./output.txt ./airport-lookup.csv\u001B[0m");
            System.exit(1); // Exit the program, as we cannot continue without the file paths
        }
        
        // Logic to check for the existence of input and lookup, if one is missing, or both are
        boolean errorFound = false;
        File inputFile = new File(args[0]); // Create a File object for input.txt (first argument)
        File lookupFile = new File(args[2]); // Create a File object for the lookup.csv path (third argument)

        // 4. Start the content processing
            
        // If the input file does not exist, inform the user
        if (!inputFile.exists()) {
            System.out.println("\u001B[31mInput not found\u001B[0m");
            errorFound = true; // If it doesn't exist, set the error flag
        }
        
        // If lookup.csv does not exist, inform the user
        if (!lookupFile.exists()) {
            System.out.println("\u001B[31mAirport lookup not found\u001B[0m");
            errorFound = true; // If it doesn't exist, set the error flag
        }

        // If at least one file is not found - exit (if both are missing, it will print both messages)
        if (errorFound) {
            System.exit(1); 
        }

        try {
            // First, load the data from the CSV
            loadAirportData(args[2]);

            // Read the entire text from the file into one large string
            String content = Files.readString(inputFile.toPath());
        
            // Call the text processing method, which replaces codes and formats dates
            String result = processItinerary(content);
        
            // Write the resulting "pretty" text to the output file (second argument)
            Files.writeString(Paths.get(args[1]), result);

            // Print the colored result to the console (bonus feature)
            printColoredOutput(result);
        
        } catch (IOException e) {
            // If a system error occurred (e.g., no read/write permissions),
            // we just terminate the program (extra error messages are not required by the spec)
        }
    }

    // Create a method to print "Airport lookup malformed" in red and exit the program
    private static void failMalformed() {
    System.out.println("\u001B[31mAirport lookup malformed\u001B[0m");
    System.exit(1);
}

    // Method to read the CSV file and populate our dictionaries
    private static void loadAirportData(String path) {
        File file = new File(path);
        
        // Create a "strict" UTF-8 decoder
        CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder();
        // REPORT forces the program to throw an exception if a byte is "broken" or not UTF-8
        decoder.onMalformedInput(CodingErrorAction.REPORT);
        decoder.onUnmappableCharacter(CodingErrorAction.REPORT);

        // Open the file for reading
        // Read the very first line (header) to understand where the data is
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), decoder))) {
            String headerLine = reader.readLine();
            if (headerLine == null) throw new IOException("Empty file");

            // Split the header by commas to determine the CSV structure
            String[] header = headerLine.split(",");
            int nameIdx = -1, icaoIdx = -1, iataIdx = -1, cityIdx = -1;
            int isoCountryIdx = -1, coordsIdx = -1;

            // Search the header for the columns we need (as their order can be arbitrary)
            for (int i = 0; i < header.length; i++) {
                String headerTitle = header[i].trim().toLowerCase();
                switch (headerTitle) {
                    case "name" -> nameIdx = i;
                    case "icao_code" -> icaoIdx = i;
                    case "iata_code" -> iataIdx = i;
                    case "municipality" -> cityIdx = i;
                    case "iso_country" -> isoCountryIdx = i; // Find the country index
                    case "coordinates" -> coordsIdx = i;   // Find the coordinates index
                }
            }

            // If at least one of the required columns was not found - the file is "malformed"
            if (nameIdx == -1 || icaoIdx == -1 || iataIdx == -1 || cityIdx == -1 || isoCountryIdx == -1 || coordsIdx == -1) {
                failMalformed();
            }

            // Read the file line by line to the very end
            String line;
            while ((line = reader.readLine()) != null) {
                
            // Check for replacement characters that were already in the file as text
            if (line.contains("\uFFFD")) {
                failMalformed();
            }
                
                // Split the string into parts. -1 allows seeing even empty cells
                String[] cols = line.split(",", -1);
                
                // Check if the row has fewer data items than columns in the header
                if (cols.length < header.length) {
                    failMalformed();
                }
                
                // Check each cell: if it's empty - it's an error
                for (String col : cols) {
                    if (col.trim().isEmpty()) {
                        failMalformed();
                    }
                }

                // Extract the necessary data from the array using the previously found indices, trimming any accidental whitespace
                String name = cols[nameIdx].trim();
                String city = cols[cityIdx].trim();
                String icao = cols[icaoIdx].trim();
                String iata = cols[iataIdx].trim();
                String country = cols[isoCountryIdx].trim();
                String coords = cols[coordsIdx].trim();

                // Check for IATA duplicates
                // If the code has been seen before, but the data in the duplicate columns is different - it's "malformed"
                if (iataToName.containsKey(iata)) {
                    if (!iataToName.get(iata).equals(name) || 
                        !iataToCity.get(iata).equals(city) ||
                        !iataToCountry.get(iata).equals(country) || 
                        !iataToCoords.get(iata).equals(coords)) {
                        failMalformed();
                    }
                }

                // Check for ICAO duplicates
                // If the code has been seen before, but the data in the duplicate columns is different - it's "malformed"
                if (icaoToName.containsKey(icao)) {
                    if (!icaoToName.get(icao).equals(name) || 
                        !icaoToCity.get(icao).equals(city) ||
                        !icaoToCountry.get(icao).equals(country) ||
                        !icaoToCoords.get(icao).equals(coords)) {
                        failMalformed();
                    }
                }
                
                // Save the data to all dictionaries
                iataToName.put(iata, name);
                iataToCity.put(iata, city);
                iataToCountry.put(iata, country);
                iataToCoords.put(iata, coords);
                
                icaoToName.put(icao, name);
                icaoToCity.put(icao, city);
                icaoToCountry.put(icao, country);
                icaoToCoords.put(icao, coords);
            }
            
        } catch (CharacterCodingException e) {
        // THIS BLOCK will execute if the decoder detects bytes from a different encoding (not UTF-8) with broken characters (with non-English letters)
            failMalformed();
        } catch (IOException e) {
        // Triggers on system errors (file is busy, disk is corrupted, etc.)
            failMalformed();
        }
    }

    // Method that controls the entire text processing chain
    private static String processItinerary(String text) {
        // 1. Normalize all types of line breaks to a single \n format
        text = text.replaceAll("\\R", "\n");

        // 2. Find and replace airport codes with names/cities
        text = replaceCodes(text);
        
        // 3. Find date tags (D, T12, T24) and convert them to a human-readable format
        text = replaceDates(text);

        // 4. Remove extra empty lines: leave a maximum of one empty line (two \n)
        text = text.replaceAll("\n{3,}", "\n\n");
        // 5. Remove all empty lines at the end of the file that are followed by nothing
        return text.stripTrailing();
    }

    // Method for finding and replacing airport codes with names/cities
private static String replaceCodes(String text) {
    // Updated regular expression to support the city prefix (*)
    // Group 1: (\\*?) - Determines if there is a '*' character (for a city request)
    // Group 2: (##([A-Z]{4})|#([A-Z]{3})) - Original pattern for codes
    // Group 3: ([A-Z]{4}) - ICAO code
    // Group 4: ([A-Z]{3}) - IATA code
    Pattern airportPattern = Pattern.compile("(\\*?)(##([A-Z]{4})|#([A-Z]{3}))");
    Matcher codeMatcher = airportPattern.matcher(text);
    StringBuilder resultBuilder = new StringBuilder();

    while (codeMatcher.find()) {
        // Check if the '*' prefix was found for a city lookup
        boolean isCityLookup = codeMatcher.group(1).equals("*");
        
        String icaoCode = codeMatcher.group(3); // 4-letter ICAO code
        String iataCode = codeMatcher.group(4); // 3-letter IATA code
        String replacement = null;

        if (icaoCode != null) {
            // If it's ICAO, choose whether to search for a city or an airport name
            replacement = isCityLookup ? icaoToCity.get(icaoCode) : icaoToName.get(icaoCode);
        } else if (iataCode != null) {
            // If it's IATA, also choose between city and name
            replacement = isCityLookup ? iataToCity.get(iataCode) : iataToName.get(iataCode);
        }
        // If the code is not found in the CSV database
        if (replacement == null) {
            System.out.println("\u001B[31mAirport lookup not found\u001B[0m");
            System.exit(1); // Immediate termination, the file will NOT be created
        }
        // Airport code found => insert the NAME (replacement)
            codeMatcher.appendReplacement(resultBuilder, Matcher.quoteReplacement(replacement));
        
        }
        codeMatcher.appendTail(resultBuilder);
        return resultBuilder.toString();
    }

    // Method for converting ISO 8601 dates to a convenient format
    // Method for finding and replacing date/time formats (D, T12, T24)
    private static String replaceDates(String text) {
    // The regular expression looks for D, T12, or T24, and then the text inside the parentheses
    Pattern datePattern = Pattern.compile("(D|T12|T24)\\(([^)]+)\\)");
    Matcher dateMatcher = datePattern.matcher(text);
    StringBuilder resultBuilder = new StringBuilder();

    while (dateMatcher.find()) {
        try {
            String type = dateMatcher.group(1);  // Tag type: D, T12, or T24
            String dtStr = dateMatcher.group(2); // Content inside the parentheses (date in ISO 8601 format)

            // If the date ends with 'Z' (Zulu time), change it to '+00:00',
            // so that Java can parse the time zone correctly.
            if (dtStr.contains("Z")) {
                dtStr = dtStr.replace("Z", "+00:00");
            }

            // Convert the text into an OffsetDateTime object for convenient handling
            OffsetDateTime odt = OffsetDateTime.parse(dtStr);

            // 1. Calculate the time zone offset manually so that it is always (+HH:mm)
            // Even if the offset is zero, according to the spec, we must output (+00:00)
            int totalSeconds = odt.getOffset().getTotalSeconds();
            int absSeconds = Math.abs(totalSeconds);
            String formattedOffset = String.format("(%s%02d:%02d)", 
                totalSeconds >= 0 ? "+" : "-", // Determine the sign: plus or minus
                absSeconds / 3600,             // Get the hours
                (absSeconds % 3600) / 60       // Get the minutes
            );

            String replacement;
            // 2. Choose the output format depending on the tag type
            if (type.equals("D")) {
                // For dates, the format is strictly: 05 Apr 2007 (day month year)
                replacement = odt.format(DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.US));
            } else {
                // For T12, use 12-hour format without a space before AM/PM (e.g., 10:30AM)
                // For T24, use the regular 24-hour format (e.g., 22:30)
                String pattern = type.equals("T12") ? "hh:mm a" : "HH:mm";
                
                // Concatenate the formatted time and our time zone in parentheses
                replacement = odt.format(DateTimeFormatter.ofPattern(pattern, Locale.US)) + " " + formattedOffset;
            }

            // Replace the old tag (e.g., D(...)) with the new "pretty" text
            dateMatcher.appendReplacement(resultBuilder, Matcher.quoteReplacement(replacement));

        } catch (Exception e) {
            // If the date inside the parentheses was "broken" or the format was incorrect,
            // according to the spec, we must leave the text untouched (m.group(0))
            dateMatcher.appendReplacement(resultBuilder, Matcher.quoteReplacement(dateMatcher.group(0)));
        }
    }
    // Add the tail of the text, where there were no tags, to the result
    dateMatcher.appendTail(resultBuilder);
    return resultBuilder.toString();
}

    // Method for printing text to the console with color highlighting (bonus feature)
    private static void printColoredOutput(String text) {
    // 1. Regex for airports (YELLOW)
    // Add exclusions
    String airportPattern = "\\b(?!Flight|Depart|Arrival|Departure|Check-in)" +
    "([A-Z][\\p{L}0-9.'-]*" + 
    // Added support for lowercase conjunctions (de, d', of, the) for international names
    "(?: (?!at|to|from|in|by|and)(?:[A-Z][\\p{L}0-9.'-]*|de|d'|of|the))* " + 
    "(?:Airport|Air base|Bay|Island|Field|International|Municipal|Heliport|Airstrip|Aviation|Regional|Station|Base|Port|Aéroport))";

    String colored = text
        // 2. Color the airports
        .replaceAll(airportPattern, "\u001B[33m$1\u001B[0m")
        
        // Dates (GREEN) - e.g. 16 Mar 2026
        .replaceAll("(\\d{1,2} [A-Z][a-z]{2} \\d{4})", "\u001B[32m$1\u001B[0m")
        
        // Time (CYAN) - e.g. 14:30 or 02:30 PM
        .replaceAll("(\\d{2}:\\d{2}(?: [AP]M)?)", "\u001B[36m$1\u001B[0m")
        
        // 3. Color the dates and times
        // Offset in parentheses (CYAN) - e.g. (+02:00)
        // Color only the content inside the parentheses
        .replaceAll("\\(([^)]+)\\)", "(\u001B[36m$1\u001B[0m)");

    System.out.println(colored);
    }
}
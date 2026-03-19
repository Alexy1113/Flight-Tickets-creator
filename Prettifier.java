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
    
    // Создаем «словари» для быстрого поиска.
    // Ключ — это код (например, "LAX"), значение — то, что нужно вывести (название аэропорта).
    static Map<String, String> iataToName = new HashMap<>();
    static Map<String, String> icaoToName = new HashMap<>();
    static Map<String, String> iataToCity = new HashMap<>();
    static Map<String, String> icaoToCity = new HashMap<>();
    
    /**
     * Основной метод, который запускает программу.
     * Он проверяет команды пользователя, загружает данные и управляет потоками файлов.
     */
    public static void main(String[] args) {

        // Принудительно устанавливаем UTF-8 для вывода в консоль
        System.setOut(new PrintStream(System.out, true, StandardCharsets.UTF_8));
        
        // 1. Проверяем, вызвал ли пользователь справку через флаг "-h"
        if (args.length > 0 && args[0].equals("-h")) {
            System.out.println("\u001B[33mitinerary usage:\n$ java Prettifier.java ./input.txt ./output.txt ./airport-lookup.csv\u001B[0m");
            System.exit(0); // Завершаем работу с кодом 0, так как пользователь просто хотел узнать инструкцию
        }

        // 2. Проверяем, переданы ли ровно 3 аргумента (входной файл, выходной файл, файл данных)
        // Если аргументов не 3, значит вызов был некорректным
        if (args.length != 3) {
            System.out.println("\u001B[33mitinerary usage:\n$ java Prettifier.java ./input.txt ./output.txt ./airport-lookup.csv\u001B[0m");
            System.exit(1); // Завершаем программу, так как не можем продолжить без путей к файлам
        }
        
        // ЛОГИКА ДВОЙНОЙ ПРОВЕРКИ
        boolean errorFound = false;
        File inputFile = new File(args[0]); // Создаем объект файла для input.txt (первый аргумент)
        File lookupFile = new File(args[2]); // Создаем объект файла для lookup.csv пути (второй аргумент)

        // 4. Начинаем процесс обработки контента
            
        // Если входного файла не существует, сообщаем об этом пользователю
        if (!inputFile.exists()) {
            System.out.println("\u001B[31mInput not found\u001B[0m");
            errorFound = true; // Если не существует, записываем флаг ошибки
        }
        
        // Если lookup.csv не существует, сообщаем об этом пользователю
        if (!lookupFile.exists()) {
            System.out.println("\u001B[31mAirport lookup not found\u001B[0m");
            errorFound = true; // Если не существует, записываем флаг ошибки
        }

        // Если хотя бы один файл не найден — выходим (если оба — напечатает оба сообщения)
        if (errorFound) {
            System.exit(1); 
        }

        try {
            // Сначала загружаем данные из CSV
            loadAirportData(args[2]);

            // Считываем весь текст из файла в одну большую строку
            String content = Files.readString(inputFile.toPath());
        
            // Вызываем метод обработки текста, который заменяет коды и форматирует даты
            String result = processItinerary(content);
        
            // Записываем полученный «красивый» текст в выходной файл (второй аргумент)
            Files.writeString(Paths.get(args[1]), result);

            // Выводим цветной результат в консоль (бонусная функция)
            printColoredOutput(result);
        
        } catch (IOException e) {
            // Если возникла системная ошибка (например, нет прав на чтение/запись),
            // мы просто завершаем работу (в рамках ТЗ лишние сообщения об ошибках не требуются)
        }
    }

    // Создаём метод для выведения Airport lookup malformed в консоль красным цветом и закрыванием программы
    private static void failMalformed() {
    System.out.println("\u001B[31mAirport lookup malformed\u001B[0m");
    System.exit(1);
}

    // Метод для чтения CSV файла и заполнения наших словарей
    private static void loadAirportData(String path) {
        File file = new File(path);
        
        // Создаем "строгий" декодер UTF-8
        CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder();
        // REPORT заставляет программу выкинуть исключение, если байт "битый" или не из UTF-8
        decoder.onMalformedInput(CodingErrorAction.REPORT);
        decoder.onUnmappableCharacter(CodingErrorAction.REPORT);

        // Открываем файл для чтения
        // Читаем самую первую строку (заголовок), чтобы понять, где какие данные
        try (BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(file), decoder))) {
            String headerLine = br.readLine();
            if (headerLine == null) throw new IOException("Empty file");

            // Разбиваем заголовок по запятым для определения структуры CSV
            String[] header = headerLine.split(",");
            int nameIdx = -1, icaoIdx = -1, iataIdx = -1, cityIdx = -1;

            // Ищем в заголовке нужные нам колонки (так как их порядок может быть любым)
            for (int i = 0; i < header.length; i++) {
                String h = header[i].trim().toLowerCase();
                if (h.equals("name")) nameIdx = i;
                else if (h.equals("icao_code")) icaoIdx = i;
                else if (h.equals("iata_code")) iataIdx = i;
                else if (h.equals("municipality")) cityIdx = i;
            }

            // Если хоть одна из нужных колонок не нашлась — значит файл «битый»
            if (nameIdx == -1 || icaoIdx == -1 || iataIdx == -1 || cityIdx == -1) {
                failMalformed();
            }

            // Читаем файл построчно до самого конца
            String line;
            while ((line = br.readLine()) != null) {
                
            //Проверка на ромбики, которые уже были в файле как текст
            if (line.contains("\uFFFD")) {
                failMalformed();
            }
                
                // Разделяем строку на части. -1 позволяет увидеть даже пустые ячейки
                String[] cols = line.split(",", -1);
                
                // Проверяем не меньше ли в строке данных, чем колонок в заголовке
                if (cols.length < header.length) {
                    failMalformed();
                }
                
                // Проверяем каждую ячейку: если она пустая — это ошибка
                for (String col : cols) {
                    if (col.trim().isEmpty()) {
                        failMalformed();
                    }
                }

                // Извлекаем нужные данные из массива по найденным ранее индексам очищая их от случайных пробелов по краям
                String name = cols[nameIdx].trim();
                String city = cols[cityIdx].trim();
                String icao = cols[icaoIdx].trim();
                String iata = cols[iataIdx].trim();

                // Сохраняем данные в наши «словари» для быстрого поиска в будущем по кодам (O(1))
                iataToName.put(iata, name);
                icaoToName.put(icao, name);
                iataToCity.put(iata, city);
                icaoToCity.put(icao, city);
            }
            
        } catch (CharacterCodingException e) {
        // ЭТОТ БЛОК сработает, если декодер обнаружил байты другой кодировки (не UTF-8) с битыми символами (с не-английскими буквами)
            failMalformed();
        } catch (IOException e) {
        // Срабатывает при системных ошибках (файл занят, поврежден диск и т.д.)
            failMalformed();
        }
    }

    // Метод, который управляет всей цепочкой обработки текста
    private static String processItinerary(String text) {
        // 1. Нормализуем все виды переносов строк к единому формату \n
        text = text.replaceAll("\\R", "\n");

        // 2. Ищем и заменяем коды аэропортов на названия/города
        text = replaceCodes(text);
        
        // 3. Находим метки дат (D, T12, T24) и превращаем их в человекочитаемый формат
        text = replaceDates(text);

        // 4. Убираем лишние пустые строки: оставляем максимум одну пустую строку (два \n)
        text = text.replaceAll("\n{3,}", "\n\n");
        // 5. Убираем все пустые строки в конце файла, после которых ничего нет
        return text.stripTrailing();
    }

    // Метод для поиска и замены кодов аэропортов на названия/города
private static String replaceCodes(String text) {
    // Обновленное регулярное выражение для поддержки префикса города (*)
    // Группа 1: (\\*?) - Определяет, есть ли символ '*' (для запроса города)
    // Группа 2: (##([A-Z]{4})|#([A-Z]{3})) - Исходный паттерн для кодов
    // Группа 3: ([A-Z]{4}) - Код ICAO
    // Группа 4: ([A-Z]{3}) - Код IATA
    Pattern p = Pattern.compile("(\\*?)(##([A-Z]{4})|#([A-Z]{3}))");
    Matcher m = p.matcher(text);
    StringBuilder sb = new StringBuilder();

    while (m.find()) {
        // Проверяем, был ли найден префикс '*' для поиска города
        boolean isCityLookup = m.group(1).equals("*");
        
        String icaoCode = m.group(3); // 4-буквенный код ICAO
        String iataCode = m.group(4); // 3-буквенный код IATA
        String replacement = null;

        if (icaoCode != null) {
            // Если это ICAO, выбираем, что искать: город или название аэропорта
            replacement = isCityLookup ? icaoToCity.get(icaoCode) : icaoToName.get(icaoCode);
        } else if (iataCode != null) {
            // Если это IATA, также выбираем между городом и названием
            replacement = isCityLookup ? iataToCity.get(iataCode) : iataToName.get(iataCode);
        }
        // Если код не найден в базе данных CSV
        if (replacement == null) {
            System.out.println("\u001B[31mAirport lookup not found\u001B[0m");
            System.exit(1); // Немедленное завершение, файл НЕ будет создан
        }
        // Код аэропорта найден => вставляем НАЗВАНИЕ (replacement)
            m.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        
        }
        m.appendTail(sb);
        return sb.toString();
    }

    // Метод для конвертации ISO 8601 дат в удобный формат
    // Метод для поиска и замены форматов дат/времени (D, T12, T24)
    private static String replaceDates(String text) {
    // Регулярное выражение ищет D, T12 или T24, а затем текст внутри скобок
    Pattern p = Pattern.compile("(D|T12|T24)\\(([^)]+)\\)");
    Matcher m = p.matcher(text);
    StringBuilder sb = new StringBuilder();

    while (m.find()) {
        try {
            String type = m.group(1);  // Тип метки: D, T12 или T24
            String dtStr = m.group(2); // Содержимое внутри скобок (дата в формате ISO 8601)

            // Если дата заканчивается на 'Z' (Zulu time), меняем его на '+00:00',
            // чтобы Java могла правильно распарсить часовой пояс.
            if (dtStr.contains("Z")) {
                dtStr = dtStr.replace("Z", "+00:00");
            }

            // Превращаем текст в объект даты OffsetDateTime для удобной работы
            OffsetDateTime odt = OffsetDateTime.parse(dtStr);

            // 1. Считаем смещение часового пояса вручную, чтобы оно всегда было (+HH:mm)
            // Даже если смещение нулевое, по ТЗ мы должны вывести (+00:00)
            int totalSeconds = odt.getOffset().getTotalSeconds();
            int absSeconds = Math.abs(totalSeconds);
            String formattedOffset = String.format("(%s%02d:%02d)", 
                totalSeconds >= 0 ? "+" : "-", // Определяем знак: плюс или минус
                absSeconds / 3600,             // Получаем часы
                (absSeconds % 3600) / 60       // Получаем минуты
            );

            String replacement;
            // 2. Выбираем формат вывода в зависимости от типа метки
            if (type.equals("D")) {
                // Для дат формат строго: 05 Apr 2007 (день месяц год)
                replacement = odt.format(DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.US));
            } else {
                // Для T12 используем 12-часовой формат без пробела перед AM/PM (например, 10:30AM)
                // Для T24 используем обычный 24-часовой формат (например, 22:30)
                String pattern = type.equals("T12") ? "hh:mm a" : "HH:mm";
                
                // Склеиваем отформатированное время и наш часовой пояс в скобках
                replacement = odt.format(DateTimeFormatter.ofPattern(pattern, Locale.US)) + " " + formattedOffset;
            }

            // Заменяем старую метку (например, D(...)) на новый "красивый" текст
            m.appendReplacement(sb, Matcher.quoteReplacement(replacement));

        } catch (Exception e) {
            // Если дата внутри скобок была "битая" или формат неверный, 
            // по ТЗ мы обязаны оставить текст нетронутым (m.group(0))
            m.appendReplacement(sb, Matcher.quoteReplacement(m.group(0)));
        }
    }
    // Добавляем к результату хвост текста, в котором не было меток
    m.appendTail(sb);
    return sb.toString();
}

    // Метод для вывода текста в консоль с цветовым выделением (бонусная функция)
    private static void printColoredOutput(String text) {
    // 1. Регулярка для аэропортов (ЖЕЛТЫЙ)
    // Добавляем исключения
    String airportPattern = "\\b(?!Flight|Depart|Arrival|Departure|Check-in)" +
    "([A-Z][\\p{L}0-9.'-]*" + 
    // Добавлена поддержка строчных связок (de, d', of, the) для международных названий
    "(?: (?!at|to|from|in|by|and)(?:[A-Z][\\p{L}0-9.'-]*|de|d'|of|the))* " + 
    "(?:Airport|Air base|Bay|Island|Field|International|Municipal|Heliport|Airstrip|Aviation|Regional|Station|Base|Port|Aéroport))";

    String colored = text
        // 2. Красим аэропорты
        .replaceAll(airportPattern, "\u001B[33m$1\u001B[0m")
        
        // Даты (ЗЕЛЕНЫЙ) - напр. 16 Mar 2026
        .replaceAll("(\\d{1,2} [A-Z][a-z]{2} \\d{4})", "\u001B[32m$1\u001B[0m")
        
        // Время (ГОЛУБОЙ) - напр. 14:30 или 02:30 PM
        .replaceAll("(\\d{2}:\\d{2}(?: [AP]M)?)", "\u001B[36m$1\u001B[0m")
        
        // 3. Красим даты и время
        // Смещение в скобках (ГОЛУБОЙ) - напр. (+02:00)
        // Красим только содержимое внутри скобок
        .replaceAll("\\(([^)]+)\\)", "(\u001B[36m$1\u001B[0m)");

    System.out.println(colored);
    }
}
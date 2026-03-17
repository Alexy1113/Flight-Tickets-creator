import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Prettifier {
    
    // Создаем «словари» для быстрого поиска.
    // Ключ — это код (например, "LAX"), значение — то, что нужно вывести (название аэропорта или город).
    static Map<String, String> iataToName = new HashMap<>();
    static Map<String, String> icaoToName = new HashMap<>();
    static Map<String, String> iataToCity = new HashMap<>();
    static Map<String, String> icaoToCity = new HashMap<>();
    
    /**
     * Основной метод, который запускает программу.
     * Он проверяет команды пользователя, загружает данные и управляет потоками файлов.
     */
    public static void main(String[] args) {
        
        // 1. Проверяем, вызвал ли пользователь справку через флаг "-h"
        if (args.length > 0 && args[0].equals("-h")) {
            System.out.println("itinerary usage:\n$ java Prettifier.java ./input.txt ./output.txt ./airport-lookup.csv");
            return; // Завершаем работу, так как пользователь просто хотел узнать инструкцию
        }

        // 2. Проверяем, переданы ли ровно 3 аргумента (входной файл, выходной файл, файл данных)
        // Если аргументов не 3, значит вызов был некорректным
        if (args.length != 3) {
            System.out.println("itinerary usage:\n$ java Prettifier.java ./input.txt ./output.txt ./airport-lookup.csv");
            return; // Завершаем программу, так как не можем продолжить без путей к файлам
        }

        // 3. Загружаем справочник аэропортов из файла, указанного третьим аргументом (args[2])
        // Внутри этого метода происходит проверка на существование файла и парсинг CSV
        loadAirportData(args[2]);

        // 4. Начинаем процесс обработки контента
        try {
            // Создаем объект файла для входного пути (первый аргумент)
            File inputFile = new File(args[0]);
            
            // Если входного файла не существует, сообщаем об этом пользователю
            if (!inputFile.exists()) {
                System.out.println("Input not found");
                return; // Останавливаем выполнение, чтобы не создавать пустой output.txt
            }
            
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

    // Метод для чтения CSV файла и заполнения наших словарей
    private static void loadAirportData(String path) {
        File file = new File(path);
        // Если файла по указанному пути нет — выводим ошибку и завершаем программу
        if (!file.exists()) {
            System.out.println("Airport lookup not found");
            System.exit(1);
        }

        // Открываем файл для чтения
        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            // Читаем самую первую строку (заголовок), чтобы понять, где какие данные
            String headerLine = br.readLine();
            if (headerLine == null) throw new IOException("Empty file");

            // Разбиваем заголовок по запятым
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
                System.out.println("Airport lookup malformed");
                System.exit(1);
            }

            // Читаем файл построчно до самого конца
            String line;
            while ((line = br.readLine()) != null) {
                // Разделяем строку на части. -1 позволяет увидеть даже пустые ячейки
                String[] cols = line.split(",", -1);
                
                // Проверяем: не меньше ли в строке данных, чем колонок в заголовке
                if (cols.length < header.length) {
                    System.out.println("Airport lookup malformed");
                    System.exit(1);
                }
                
                // Проверяем каждую ячейку: если она пустая — это ошибка
                for (String col : cols) {
                    if (col.trim().isEmpty()) {
                        System.out.println("Airport lookup malformed");
                        System.exit(1);
                    }
                }

                // Извлекаем нужные данные из массива по найденным ранее индексам
                String name = cols[nameIdx].trim();
                String city = cols[cityIdx].trim();
                String icao = cols[icaoIdx].trim();
                String iata = cols[iataIdx].trim();

                // Сохраняем данные в наши «словари» для быстрого поиска в будущем
                iataToName.put(iata, name);
                icaoToName.put(icao, name);
                iataToCity.put(iata, city);
                icaoToCity.put(icao, city);
            }
        } catch (IOException e) {
            // Если возникла любая ошибка чтения — значит данные повреждены
            System.out.println("Airport lookup malformed");
            System.exit(1);
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
    // Регулярное выражение ищет два варианта:
    // Группа 1: ##([A-Z]{4}) — две решетки и 4 буквы (ICAO)
    // Группа 2: #([A-Z]{3})  — одна решетка и 3 буквы (IATA)
    Pattern p = Pattern.compile("##([A-Z]{4})|#([A-Z]{3})");
    Matcher m = p.matcher(text);
    StringBuilder sb = new StringBuilder();

    // Цикл проходит по всему тексту и ищет совпадения с шаблоном
    while (m.find()) {
        String replacement = null;

        // Проверяем, какая группа сработала
        if (m.group(1) != null) {
            // Если нашли ##XXXX, берем значение из словаря ICAO
            String icaoCode = m.group(1);
            replacement = icaoToName.get(icaoCode);
        } else if (m.group(2) != null) {
            // Если нашли #XXX, берем значение из словаря IATA
            String iataCode = m.group(2);
            replacement = iataToName.get(iataCode);
        }

        // Если код найден в нашем справочнике (replacement не пустой)
        if (replacement != null) {
            // Заменяем код на название аэропорта
            m.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        } else {
            // Если кода нет в базе, оставляем его как был в тексте (например, #BUA)
            m.appendReplacement(sb, Matcher.quoteReplacement(m.group(0)));
        }
    }
    
    // Добавляем оставшуюся часть текста после последнего найденного кода
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
                String pattern = type.equals("T12") ? "hh:mma" : "HH:mm";
                
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
    String colored = text
        // 1. Названия аэропортов (ЖЕЛТЫЙ)
        // Окрашиваются в желтый (33) слова:
        // Airport|Air base|Bay|Island|Field|International|Municipal|Heliport|Airstrip|Aviation|Regional|Station|Base|Port
        // И слова с загрлавной буквы перед ними, кроме слов:
        // Flight, Depart, Arrival, Departure, arrival, departure
        .replaceAll("\\b(?!Flight|Depart|Arrival|Departure|arrival|departure)([A-Z][a-z0-9]+(?: [A-Z][a-z0-9]+)* (?:Airport|Air base|Bay|Island|Field|International|Municipal|Heliport|Airstrip|Aviation|Regional|Station|Base|Port))", "\u001B[33m$1\u001B[0m")
        // 2. Даты — зеленый (32)
        .replaceAll("([0-9]{2} [A-Z][a-z]{2} [0-9]{4})", "\u001B[32m$1\u001B[0m") 
        
        // 3. Время — голубой (36)
        .replaceAll("([0-9]{2}:[0-9]{2}( [AP]M)?)", "\u001B[36m$1\u001B[0m") 
        
        // 4. Смещение в скобках: 
        // Ищем открывающую скобку '(', затем всё до закрывающей, затем ')'
        // Красим только содержимое (группа $1) в голубой (36)
        .replaceAll("\\(([^)]+)\\)", "(\u001B[36m$1\u001B[0m)");
    
    System.out.println(colored);
}
}
// SeatingDatabase.java
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SeatingDatabase {
    private final Path dbPath;
    private final Path fallbackFilePath;
    private final Map<String, Student> fallbackMap;
    private final boolean sqliteAvailable;

    public SeatingDatabase(String dbFilePath) {
        this.dbPath = Path.of(dbFilePath);
        this.fallbackFilePath = Path.of("data", "seatingData.txt");
        this.fallbackMap = new HashMap<>();
        this.sqliteAvailable = isSqliteAvailable();
        if (sqliteAvailable) {
            initializeDatabase();
            importLegacyDataIfEmpty();
        } else {
            System.out.println("sqlite3 not available; falling back to text file storage.");
            loadFallbackData();
        }
    }

    private void initializeDatabase() {
        runSql("CREATE TABLE IF NOT EXISTS students (" +
                "roll TEXT PRIMARY KEY," +
                "room TEXT NOT NULL," +
                "floor TEXT NOT NULL," +
                "seat TEXT NOT NULL," +
                "exam_name TEXT," +
                "exam_date TEXT," +
                "exam_time TEXT" +
                ");");
    }

    private void importLegacyDataIfEmpty() {
        int count = getStudentCount();
        if (count > 0) {
            return;
        }
        loadDataIfPresent(Path.of("data", "seatingData.txt"));
        loadDataIfPresent(Path.of("data", "seating.txt"));
    }

    private int getStudentCount() {
        List<String> rows = runSqlQuery("SELECT COUNT(*) FROM students;");
        if (rows == null || rows.isEmpty()) {
            return 0;
        }
        try {
            return Integer.parseInt(rows.get(0).trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private void loadDataIfPresent(Path path) {
        if (path == null || !Files.exists(path)) {
            return;
        }
        try (BufferedReader br = new BufferedReader(new FileReader(path.toFile()))) {
            String line;
            while ((line = br.readLine()) != null) {
                // Example format: R001,Room-101,First Floor,S12
                String[] parts = line.split(",");
                if (parts.length >= 4) {
                    String examName = parts.length > 4 ? parts[4] : "N/A";
                    String examDate = parts.length > 5 ? parts[5] : "N/A";
                    String examTime = parts.length > 6 ? parts[6] : "N/A";
                    Student student = new Student(parts[0], parts[1], parts[2], parts[3], examName, examDate, examTime);
                    if (sqliteAvailable) {
                        addStudent(student);
                    } else {
                        fallbackMap.put(student.getRollNumber(), student);
                    }
                }
            }
        } catch (IOException e) {
            System.out.println("Error loading data: " + e.getMessage());
        }
    }

    public Student getStudent(String rollNumber) {
        if (rollNumber == null || rollNumber.isBlank()) {
            return null;
        }
        if (!sqliteAvailable) {
            return fallbackMap.get(rollNumber.trim());
        }
        String sql = "SELECT roll, room, floor, seat, exam_name, exam_date, exam_time FROM students " +
                "WHERE roll = '" + escapeSql(rollNumber.trim()) + "' LIMIT 1;";
        List<String> rows = runSqlQuery(sql);
        if (rows == null || rows.isEmpty()) {
            return null;
        }
        String[] parts = parseCsvRow(rows.get(0));
        if (parts.length < 4) {
            return null;
        }
        String examName = parts.length > 4 ? parts[4] : "N/A";
        String examDate = parts.length > 5 ? parts[5] : "N/A";
        String examTime = parts.length > 6 ? parts[6] : "N/A";
        return new Student(parts[0], parts[1], parts[2], parts[3], examName, examDate, examTime);
    }

    public List<Student> getAllStudents() {
        List<Student> students = new ArrayList<>();
        if (!sqliteAvailable) {
            students.addAll(fallbackMap.values());
            return students;
        }
        String sql = "SELECT roll, room, floor, seat, exam_name, exam_date, exam_time FROM students " +
                "ORDER BY roll COLLATE NOCASE;";
        List<String> rows = runSqlQuery(sql);
        if (rows == null) {
            return students;
        }
        for (String row : rows) {
            String[] parts = parseCsvRow(row);
            if (parts.length < 4) {
                continue;
            }
            String examName = parts.length > 4 ? parts[4] : "N/A";
            String examDate = parts.length > 5 ? parts[5] : "N/A";
            String examTime = parts.length > 6 ? parts[6] : "N/A";
            students.add(new Student(parts[0], parts[1], parts[2], parts[3], examName, examDate, examTime));
        }
        return students;
    }

    public boolean addStudent(Student student) {
        if (student == null || student.getRollNumber() == null || student.getRollNumber().isBlank()) {
            return false;
        }
        if (!sqliteAvailable) {
            fallbackMap.put(student.getRollNumber(), student);
            return appendToFallbackFile(student);
        }
        String sql = "INSERT OR REPLACE INTO students " +
                "(roll, room, floor, seat, exam_name, exam_date, exam_time) VALUES (" +
                "'" + escapeSql(student.getRollNumber()) + "'," +
                "'" + escapeSql(student.getRoom()) + "'," +
                "'" + escapeSql(student.getFloor()) + "'," +
                "'" + escapeSql(student.getSeatNumber()) + "'," +
                "'" + escapeSql(student.getExamName()) + "'," +
                "'" + escapeSql(student.getExamDate()) + "'," +
                "'" + escapeSql(student.getExamTime()) + "'" +
                ");";
        return runSql(sql);
    }

    public boolean deleteStudent(String rollNumber) {
        if (rollNumber == null || rollNumber.isBlank()) {
            return false;
        }
        String key = rollNumber.trim();
        if (!sqliteAvailable) {
            Student removed = fallbackMap.remove(key);
            return removed != null && rewriteFallbackFile();
        }
        String sql = "DELETE FROM students WHERE roll = '" + escapeSql(key) + "';";
        return runSql(sql);
    }

    private void loadFallbackData() {
        loadDataIfPresent(fallbackFilePath);
        loadDataIfPresent(Path.of("data", "seating.txt"));
    }

    private boolean appendToFallbackFile(Student student) {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(fallbackFilePath.toFile(), true))) {
            writer.write(String.join(",",
                    student.getRollNumber(),
                    student.getRoom(),
                    student.getFloor(),
                    student.getSeatNumber(),
                    student.getExamName(),
                    student.getExamDate(),
                    student.getExamTime()));
            writer.newLine();
            return true;
        } catch (IOException e) {
            System.out.println("Error saving fallback data: " + e.getMessage());
            return false;
        }
    }

    private boolean rewriteFallbackFile() {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(fallbackFilePath.toFile(), false))) {
            for (Student student : fallbackMap.values()) {
                writer.write(String.join(",",
                        student.getRollNumber(),
                        student.getRoom(),
                        student.getFloor(),
                        student.getSeatNumber(),
                        student.getExamName(),
                        student.getExamDate(),
                        student.getExamTime()));
                writer.newLine();
            }
            return true;
        } catch (IOException e) {
            System.out.println("Error rewriting fallback data: " + e.getMessage());
            return false;
        }
    }

    private boolean runSql(String sql) {
        return runSqlQuery(sql) != null;
    }

    private List<String> runSqlQuery(String sql) {
        if (!sqliteAvailable) {
            return null;
        }
        List<String> output = new ArrayList<>();
        ProcessBuilder builder = new ProcessBuilder(
                "sqlite3",
                "-csv",
                dbPath.toString(),
                sql
        );
        try {
            Process process = builder.start();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.add(line);
                }
            }
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                System.out.println("Error executing SQL: " + sql);
                return null;
            }
        } catch (IOException | InterruptedException e) {
            System.out.println("Error executing SQL: " + e.getMessage());
            return null;
        }
        return output;
    }

    private boolean isSqliteAvailable() {
        ProcessBuilder builder = new ProcessBuilder("sqlite3", "-version");
        try {
            Process process = builder.start();
            int exitCode = process.waitFor();
            return exitCode == 0;
        } catch (IOException | InterruptedException e) {
            return false;
        }
    }

    private String escapeSql(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("'", "''");
    }

    private String[] parseCsvRow(String row) {
        if (row == null) {
            return new String[0];
        }
        List<String> fields = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < row.length(); i++) {
            char c = row.charAt(i);
            if (c == '"') {
                if (inQuotes && i + 1 < row.length() && row.charAt(i + 1) == '"') {
                    current.append('"');
                    i++;
                } else {
                    inQuotes = !inQuotes;
                }
            } else if (c == ',' && !inQuotes) {
                fields.add(current.toString());
                current.setLength(0);
            } else {
                current.append(c);
            }
        }
        fields.add(current.toString());
        return fields.toArray(new String[0]);
    }
}

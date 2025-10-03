// SeatingDatabase.java
import java.io.*;
import java.util.*;

public class SeatingDatabase {
    private Map<String, Student> studentMap;

    public SeatingDatabase(String filePath) {
        studentMap = new HashMap<>();
        loadData(filePath);
    }

    private void loadData(String filePath) {
        try (BufferedReader br = new BufferedReader(new FileReader(filePath))) {
            String line;
            while ((line = br.readLine()) != null) {
                // Example format: R001,Room-101,First Floor,S12
                String[] parts = line.split(",");
                if (parts.length == 4) {
                    Student student = new Student(parts[0], parts[1], parts[2], parts[3]);
                    studentMap.put(parts[0], student);
                }
            }
        } catch (IOException e) {
            System.out.println("Error loading data: " + e.getMessage());
        }
    }

    public Student getStudent(String rollNumber) {
        return studentMap.get(rollNumber);
    }
}

// SeatingViewer.java
import java.util.Scanner;

public class SeatingViewer {
    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);
        SeatingDatabase db = new SeatingDatabase("data/seatingData.txt");

        System.out.print("Enter Roll Number: ");
        String rollNo = scanner.nextLine();

        Student student = db.getStudent(rollNo);

        if (student != null) {
            System.out.println("\n--- Seating Details ---");
            System.out.println(student.getDetails());
        } else {
            System.out.println("No record found for Roll Number: " + rollNo);
        }

        scanner.close();
    }
}

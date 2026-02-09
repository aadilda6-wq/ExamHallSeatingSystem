public class Student {
    private String rollNumber;
    private String room;
    private String floor;
    private String seatNumber;
    private String examName;
    private String examDate;
    private String examTime;

    public Student(String rollNumber, String room, String floor, String seatNumber, String examName, String examDate, String examTime) {
        this.rollNumber = rollNumber;
        this.room = room;
        this.floor = floor;
        this.seatNumber = seatNumber;
        this.examName = examName;
        this.examDate = examDate;
        this.examTime = examTime;
    }

    public String getRollNumber() {
        return rollNumber;
    }

    public String getRoom() {
        return room;
    }

    public String getFloor() {
        return floor;
    }

    public String getSeatNumber() {
        return seatNumber;
    }

    public String getExamName() {
        return examName;
    }

    public String getExamDate() {
        return examDate;
    }

    public String getExamTime() {
        return examTime;
    }

    // Helper: numeric index from seat label e.g. "S12" -> 12
    public int getSeatIndex() {
        if (seatNumber == null) return -1;
        String digits = seatNumber.replaceAll("[^0-9]", "");
        if (digits.isEmpty()) return -1;
        try {
            return Integer.parseInt(digits);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    public String getDetails() {
        return "Roll Number: " + rollNumber +
               "\nRoom: " + room +
               "\nFloor: " + floor +
               "\nSeat Number: " + seatNumber +
               "\nExam Name: " + examName +
               "\nExam Date: " + examDate +
               "\nExam Time: " + examTime;
    }
}

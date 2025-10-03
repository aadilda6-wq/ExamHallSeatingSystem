import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpExchange;
import java.io.*;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

public class SeatingWebServer {
    private static SeatingDatabase db;

    // Optional: per-room seat counts. Defaults to 48 if not listed.
    private static final Map<String, Integer> ROOM_SEAT_COUNTS = new HashMap<>();
    static {
        ROOM_SEAT_COUNTS.put("room-101", 48);
        ROOM_SEAT_COUNTS.put("room-102", 48);
        ROOM_SEAT_COUNTS.put("room-201", 48);
    }

    public static void main(String[] args) throws Exception {
        db = new SeatingDatabase("data/seatingData.txt");

        HttpServer server = HttpServer.create(new InetSocketAddress(8080), 0);
        server.createContext("/", SeatingWebServer::handleLandingPage);
        server.createContext("/search", SeatingWebServer::handleSearch);
        server.setExecutor(null);
        System.out.println("Server running at http://localhost:8080/");
        server.start();
    }

    // Landing page with modern UI
    private static void handleLandingPage(HttpExchange exchange) throws IOException {
        String html = "<!DOCTYPE html>" +
                "<html lang='en'><head>" +
                "<meta charset='UTF-8'>" +
                "<meta name='viewport' content='width=device-width, initial-scale=1.0'>" +
                "<title>Exam Seating Finder</title>" +
                "<link href='https://cdn.jsdelivr.net/npm/bootstrap@5.3.0/dist/css/bootstrap.min.css' rel='stylesheet'>" +
                "<style>" +
                "body {background: linear-gradient(135deg, #f5f7fa, #e4ecf7); font-family: 'Segoe UI', sans-serif;}" +
                ".hero-section {min-height: 100vh; display: flex; align-items: center;}" +
                ".hero-text h1 {font-size: 2.8rem; font-weight: 700; color: #2c3e50;}" +
                ".hero-text p {font-size: 1.1rem; margin: 15px 0; color: #555;}" +
                ".btn-custom {background-color: #6c63ff; color: #fff; padding: 12px 25px; font-size: 1.1rem; border-radius: 30px; transition: all 0.3s;}" +
                ".btn-custom:hover {background-color: #5145cd;}" +
                ".hero-image img {max-width: 100%;}" +
                "</style></head>" +
                "<body>" +
                "<nav class='navbar navbar-expand-lg navbar-light bg-white shadow-sm fixed-top'>" +
                "<div class='container'>" +
                "<a class='navbar-brand fw-bold' href='#'>ExamSeating</a>" +
                "<button class='navbar-toggler' type='button' data-bs-toggle='collapse' data-bs-target='#navbarNav'>" +
                "<span class='navbar-toggler-icon'></span></button>" +
                "<div class='collapse navbar-collapse justify-content-end' id='navbarNav'>" +
                "<ul class='navbar-nav'>" +
                "<li class='nav-item'><a class='nav-link' href='#'>Home</a></li>" +
                "<li class='nav-item'><a class='nav-link' href='#'>About</a></li>" +
                "<li class='nav-item'><a class='nav-link' href='#'>Work</a></li>" +
                "<li class='nav-item'><a class='btn btn-custom ms-2' href='#form-section'>Get Started</a></li>" +
                "</ul></div></div></nav>" +

                "<section class='hero-section'>" +
                "<div class='container'>" +
                "<div class='row align-items-center'>" +
                "<div class='col-md-6 hero-text'>" +
                "<h1>Find Your Exam Seat Easily</h1>" +
                "<p>Enter your roll number to quickly find your exam hall, floor, and seat number. No more confusion on exam day.</p>" +
                "<a href='#form-section' class='btn btn-custom'>Find My Seat</a>" +
                "</div>" +
                "<div class='col-md-6 hero-image text-center'>" +
                "<img src='https://static.vecteezy.com/system/resources/previews/003/133/624/original/exchange-education-and-turnover-vector.jpg' alt='Illustration'>" +
                "</div></div></div></section>" +

                "<section id='form-section' class='py-5'>" +
                "<div class='container'>" +
                "<div class='card shadow-lg p-4 rounded-4'>" +
                "<h2 class='text-center text-primary mb-4'>Exam Hall Seating Finder</h2>" +
                "<form action='/search' method='get'>" +
                "<div class='mb-3'>" +
                "<label for='roll' class='form-label'>Roll Number</label>" +
                "<input type='text' id='roll' name='roll' class='form-control' required>" +
                "</div>" +
                "<div class='d-grid'>" +
                "<button type='submit' class='btn btn-custom btn-lg'>Search</button>" +
                "</div></form></div></div></section>" +

                "<script src='https://cdn.jsdelivr.net/npm/bootstrap@5.3.0/dist/js/bootstrap.bundle.min.js'></script>" +
                "</body></html>";

        sendResponse(exchange, html);
    }

    // Search result page with floor plan image + seating grid
    private static void handleSearch(HttpExchange exchange) throws IOException {
        String query = exchange.getRequestURI().getQuery();
        String roll = (query != null && query.startsWith("roll=")) ? query.substring(5) : null;

        Student student = (roll != null) ? db.getStudent(roll) : null;

        String response;
        if (student != null) {
            // Extract fields the same way your existing code does
            String[] details = student.getDetails().split("\n");
            String room = details.length > 1 ? details[1].split(": ")[1] : "";
            String floor = details.length > 2 ? details[2].split(": ")[1] : "";
            String seatLabel = details.length > 3 ? details[3].split(": ")[1] : "";

            // Build floor plan (base64 inline)
            String floorPlanHtml = buildFloorPlanImageHtml(room, floor);

            // Build seating grid with the student's seat highlighted
            String seatGridHtml = buildSeatGridHtml(room, seatLabel);

            response = "<!DOCTYPE html><html><head>" +
                    "<title>Seating Result</title>" +
                    "<link href='https://cdn.jsdelivr.net/npm/bootstrap@5.3.0/dist/css/bootstrap.min.css' rel='stylesheet'>" +
                    "<style>" +
                    "body{background:#f6f8fb}" +
                    ".floorplan img{max-width:100%;height:auto;border-radius:12px;border:1px solid #e3e6ea}" +
                    ".seat-grid{display:grid;grid-template-columns:repeat(8,48px);gap:10px;justify-content:center}" +
                    ".seat{width:48px;height:48px;border-radius:12px;display:flex;align-items:center;justify-content:center;" +
                    "font-weight:600;color:#334155;background:#f1f5f9;border:1px solid #e2e8f0}" +
                    ".seat.selected{background:#6c63ff;color:#fff;border-color:#5145cd;box-shadow:0 0 0 4px rgba(108,99,255,.15)}" +
                    "</style>" +
                    "</head><body class='bg-light'>" +
                    "<div class='container mt-5'>" +
                    "<div class='card shadow-lg p-4 rounded-4'>" +
                    "<h2 class='text-success text-center mb-4'>Seating Details</h2>" +
                    "<ul class='list-group mb-4'>" +
                    "<li class='list-group-item'><b>Roll:</b> " + student.getRollNumber() + "</li>" +
                    "<li class='list-group-item'><b>Room:</b> " + room + "</li>" +
                    "<li class='list-group-item'><b>Floor:</b> " + floor + "</li>" +
                    "<li class='list-group-item'><b>Seat:</b> " + seatLabel + "</li>" +
                    "</ul>" +

                    "<div class='row g-4'>" +
                    "<div class='col-lg-6'>" +
                    "<div class='card p-3 floorplan'>" +
                    "<h5 class='mb-3'>Floor Plan</h5>" +
                    floorPlanHtml +
                    "</div></div>" +

                    "<div class='col-lg-6'>" +
                    "<div class='card p-3'>" +
                    "<h5 class='mb-3'>Seating arrangement</h5>" +
                    seatGridHtml +
                    "<div class='text-muted text-center mt-2' style='font-size:0.9rem;'>Your seat is highlighted</div>" +
                    "</div></div>" +
                    "</div>" +

                    "<div class='text-center mt-4'>" +
                    "<a href='/' class='btn btn-outline-primary'>Search Again</a>" +
                    "</div></div></div></body></html>";
        } else {
            response = "<!DOCTYPE html><html><head>" +
                    "<title>No Record</title>" +
                    "<link href='https://cdn.jsdelivr.net/npm/bootstrap@5.3.0/dist/css/bootstrap.min.css' rel='stylesheet'>" +
                    "</head><body class='bg-light'>" +
                    "<div class='container mt-5'>" +
                    "<div class='card shadow-lg p-4 rounded-4 text-center'>" +
                    "<h3 class='text-danger'>No record found for that roll number.</h3>" +
                    "<a href='/' class='btn btn-outline-secondary mt-3'>Try Again</a>" +
                    "</div></div></body></html>";
        }

        sendResponse(exchange, response);
    }

    private static void sendResponse(HttpExchange exchange, String response) throws IOException {
        exchange.sendResponseHeaders(200, response.getBytes().length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(response.getBytes());
        }
    }

    // Helper: build <img> tag with embedded base64 data, or a warning if not found
    private static String buildFloorPlanImageHtml(String room, String floor) {
        try {
            String floorSlug = toFloorSlug(floor);      // e.g., "First Floor" -> "first"
            String roomSlug = toRoomSlug(room);         // e.g., "Room-101" -> "room101"

            // Support both .png and .jpg
            Path path = findFloorPlanPath(floorSlug, roomSlug);
            if (path != null && Files.exists(path)) {
                byte[] bytes = Files.readAllBytes(path);
                String base64 = Base64.getEncoder().encodeToString(bytes);
                String mime = getImageMimeFromFileName(path.getFileName().toString());
                return "<img src='data:" + mime + ";base64," + base64 + "' alt='Floor plan for " +
                        escape(room) + " (" + escape(floor) + ")'>";
            } else {
                String expected = "floorplan_" + floorSlug + "_" + roomSlug + ".png or .jpg";
                return "<div class='alert alert-warning mb-0'>No floor plan found. Expected: static/" +
                        expected + "</div>";
            }
        } catch (Exception ex) {
            return "<div class='alert alert-danger mb-0'>Failed to load floor plan.</div>";
        }
    }

    private static Path findFloorPlanPath(String floorSlug, String roomSlug) {
        Path png = Paths.get("static", "floorplan_" + floorSlug + "_" + roomSlug + ".png");
        if (Files.exists(png)) return png;
        Path jpg = Paths.get("static", "floorplan_" + floorSlug + "_" + roomSlug + ".jpg");
        if (Files.exists(jpg)) return jpg;
        return null;
    }

    private static String getImageMimeFromFileName(String fileName) {
        String f = fileName.toLowerCase();
        if (f.endsWith(".png")) return "image/png";
        if (f.endsWith(".jpg") || f.endsWith(".jpeg")) return "image/jpeg";
        if (f.endsWith(".svg")) return "image/svg+xml";
        return "application/octet-stream";
    }

    // Seating grid helpers
    private static String buildSeatGridHtml(String room, String seatLabel) {
        int seatIndex = extractSeatIndex(seatLabel); // 1-based
        int totalSeats = seatCountForRoom(room);
        if (seatIndex > totalSeats) totalSeats = seatIndex; // ensure seat is visible

        StringBuilder sb = new StringBuilder();
        sb.append("<div class='seat-grid'>");
        for (int i = 1; i <= totalSeats; i++) {
            boolean selected = (i == seatIndex);
            sb.append("<div class='seat")
              .append(selected ? " selected" : "")
              .append("' title='S").append(i).append("'>")
              .append(i)
              .append("</div>");
        }
        sb.append("</div>");
        return sb.toString();
    }

    private static int extractSeatIndex(String seatLabel) {
        if (seatLabel == null) return -1;
        String digits = seatLabel.replaceAll("[^0-9]", "");
        if (digits.isEmpty()) return -1;
        try {
            return Integer.parseInt(digits);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private static int seatCountForRoom(String room) {
        if (room == null) return 48;
        String key = room.trim().toLowerCase().replace(' ', '-'); // e.g., "Room-101" -> "room-101"
        Integer count = ROOM_SEAT_COUNTS.get(key);
        return count != null ? count : 48;
    }

    // Slug helpers (for floor-plan file names)
    private static String toFloorSlug(String floor) {
        if (floor == null) return "unknown";
        String f = floor.toLowerCase();
        if (f.contains("first")) return "first";
        if (f.contains("second")) return "second";
        if (f.contains("third")) return "third";
        if (f.contains("ground")) return "ground";
        // fallback: first token letters/numbers only
        String token = f.split("\\s+")[0];
        return token.replaceAll("[^a-z0-9]", "");
    }

    private static String toRoomSlug(String room) {
        if (room == null) return "unknown";
        return room.toLowerCase().replaceAll("[^a-z0-9]", ""); // "Room-101" -> "room101"
    }

    private static String escape(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#39;");
    }
}
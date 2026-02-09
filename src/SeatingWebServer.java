import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpExchange;
import java.io.*;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
        db = new SeatingDatabase("data/seating.db");

        HttpServer server = HttpServer.create(new InetSocketAddress(8080), 0);
        server.createContext("/", SeatingWebServer::handleLandingPage);
        server.createContext("/search", SeatingWebServer::handleSearch);
        server.createContext("/admin/login", SeatingWebServer::handleAdminLogin);
        server.createContext("/admin/logout", SeatingWebServer::handleAdminLogout);
        server.createContext("/admin", SeatingWebServer::handleAdminPage);
        server.createContext("/admin/add", SeatingWebServer::handleAdminAdd);
        server.createContext("/admin/upload", SeatingWebServer::handleAdminUpload);
        server.createContext("/admin/delete", SeatingWebServer::handleAdminDelete);
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
                "<li class='nav-item'><a class='nav-link' href='/admin'>Admin</a></li>" +
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

            response = "<!DOCTYPE html>" +
                    "<html lang='en'><head>" +
                    "<meta charset='UTF-8'>" +
                    "<meta name='viewport' content='width=device-width, initial-scale=1.0'>" +
                    "<title>Seating Result</title>" +
                    "<link href='https://cdn.jsdelivr.net/npm/bootstrap@5.3.0/dist/css/bootstrap.min.css' rel='stylesheet'>" +
                    "<style>" +
                    "body{background:#f6f8fb}" +
                    ".floorplan img{max-width:100%;height:auto;border-radius:12px;border:1px solid #e3e6ea}" +
                    ".seat-grid{display:grid;grid-template-columns:repeat(6,48px);gap:10px;justify-content:center}" +
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
                    "<li class='list-group-item'><b>Exam:</b> " + escape(student.getExamName()) + "</li>" +
                    "<li class='list-group-item'><b>Date:</b> " + escape(student.getExamDate()) + "</li>" +
                    "<li class='list-group-item'><b>Time:</b> " + escape(student.getExamTime()) + "</li>" +
                    "</ul>" +

                    "<div class='row g-4'>" +
                    "<div class='col-lg-6'>" +
                    "<div class='card p-3 floorplan'>" +
                    "<h5 class='mb-3'> </h5>" +
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

    private static void handleAdminPage(HttpExchange exchange) throws IOException {
        if (!isAdminAuthenticated(exchange)) {
            exchange.getResponseHeaders().add("Location", "/admin/login");
            exchange.sendResponseHeaders(302, -1);
            return;
        }
        String status = getQueryParam(exchange.getRequestURI().getQuery(), "status");
        String addMessage = "";
        String uploadMessage = "";
        if ("success".equals(status)) {
            addMessage = "<div class='alert alert-success'>Entry saved successfully.</div>";
        } else if ("error".equals(status)) {
            addMessage = "<div class='alert alert-danger'>Unable to save entry. Please check the fields.</div>";
        } else if ("upload_success".equals(status)) {
            String count = getQueryParam(exchange.getRequestURI().getQuery(), "count");
            String failed = getQueryParam(exchange.getRequestURI().getQuery(), "failed");
            uploadMessage = "<div class='alert alert-success'>Uploaded " + escape(count) +
                    " record(s). Skipped " + escape(failed) + " row(s).</div>";
        } else if ("upload_error".equals(status)) {
            uploadMessage = "<div class='alert alert-danger'>Unable to process the CSV upload. Please verify the file.</div>";
        } else if ("delete_success".equals(status)) {
            addMessage = "<div class='alert alert-success'>Student entry deleted successfully.</div>";
        } else if ("delete_error".equals(status)) {
            addMessage = "<div class='alert alert-danger'>Unable to delete the student entry.</div>";
        }

        List<Student> students = new ArrayList<>(db.getAllStudents());
        students.sort(Comparator.comparing(Student::getRollNumber, String.CASE_INSENSITIVE_ORDER));
        Set<String> rooms = new LinkedHashSet<>();
        Set<String> floors = new LinkedHashSet<>();
        Set<String> exams = new LinkedHashSet<>();
        Set<String> examSessions = new LinkedHashSet<>();
        for (Student student : students) {
            addIfPresent(rooms, student.getRoom());
            addIfPresent(floors, student.getFloor());
            addIfPresent(exams, student.getExamName());
            addIfPresent(examSessions, formatExamSession(student));
        }
        String studentTable = buildStudentTableSection(students);

        String html = "<!DOCTYPE html>" +
                "<html lang='en'><head>" +
                "<meta charset='UTF-8'>" +
                "<meta name='viewport' content='width=device-width, initial-scale=1.0'>" +
                "<title>Admin - Exam Seating</title>" +
                "<link href='https://cdn.jsdelivr.net/npm/bootstrap@5.3.0/dist/css/bootstrap.min.css' rel='stylesheet'>" +
                "<style>" +
                ":root{--bg:#f4f6fb;--panel:#ffffff;--muted:#8a94a6;--primary:#5b6df9;--accent:#7cc4ff;--shadow:0 12px 30px rgba(15,23,42,.08)}" +
                "body{background:var(--bg);font-family:'Segoe UI',sans-serif;color:#1f2937}" +
                ".admin-shell{min-height:100vh;display:grid;grid-template-columns:260px 1fr}" +
                ".sidebar{background:#f9fafc;padding:28px 22px;border-right:1px solid #edf0f6}" +
                ".brand{display:flex;align-items:center;gap:12px;font-weight:700;color:#3f4d67;font-size:1.1rem}" +
                ".brand-badge{width:44px;height:44px;border-radius:14px;background:linear-gradient(135deg,#5b6df9,#7cc4ff);" +
                "display:flex;align-items:center;justify-content:center;color:#fff;font-weight:600}" +
                ".nav-title{font-size:.75rem;text-transform:uppercase;color:var(--muted);margin:26px 0 12px}" +
                ".nav-link{display:flex;align-items:center;gap:10px;color:#52607a;padding:10px 12px;border-radius:12px}" +
                ".nav-link.active{background:#eef1ff;color:#3b4cca;font-weight:600}" +
                ".main{padding:24px 36px 48px}" +
                ".topbar{background:var(--panel);border-radius:18px;padding:16px 22px;box-shadow:var(--shadow);display:flex;" +
                "align-items:center;justify-content:space-between;margin-bottom:24px;gap:16px;flex-wrap:wrap}" +
                ".search-bar{flex:1;min-width:220px;max-width:520px;background:#f3f5fb;border-radius:999px;padding:10px 16px;color:var(--muted)}" +
                ".avatar{width:40px;height:40px;border-radius:50%;background:#dbe2ff;display:flex;align-items:center;justify-content:center;" +
                "color:#3b4cca;font-weight:600}" +
                ".logout-btn{border-radius:999px;padding:8px 16px;border:1px solid #dbe2ff;background:#f5f7ff;color:#3b4cca;" +
                "font-weight:600;font-size:.9rem;text-decoration:none}" +
                ".logout-btn:hover{background:#e9edff}" +
                ".stat-grid{display:grid;grid-template-columns:repeat(auto-fit,minmax(220px,1fr));gap:18px;margin-bottom:24px}" +
                ".stat-card{background:var(--panel);border-radius:18px;padding:18px 20px;box-shadow:var(--shadow)}" +
                ".stat-card h6{color:var(--muted);font-size:.8rem;margin-bottom:6px;text-transform:uppercase}" +
                ".stat-card .stat-value{font-size:1.4rem;font-weight:700}" +
                ".detail-card{background:var(--panel);border-radius:18px;padding:18px 20px;box-shadow:var(--shadow);height:100%}" +
                ".detail-card h5{font-weight:600;margin-bottom:12px}" +
                ".badge-list{display:flex;flex-wrap:wrap;gap:8px}" +
                ".badge-soft{background:#eef1ff;color:#3b4cca;padding:6px 12px;border-radius:999px;font-size:.85rem;font-weight:600}" +
                ".empty-state{color:var(--muted);font-size:.9rem}" +
                ".table-card{background:var(--panel);border-radius:20px;padding:20px;box-shadow:var(--shadow)}" +
                ".table thead th{color:#6b7280;font-size:.8rem;text-transform:uppercase;letter-spacing:.02em;" +
                "border-bottom:1px solid #edf0f6}" +
                ".table tbody tr{border-color:#eef2f6}" +
                ".table tbody tr:hover{background:#f8f9ff}" +
                ".btn-ghost{border:1px solid #e2e8f0;background:#fff;color:#475569;padding:6px 12px;border-radius:10px;" +
                "font-size:.85rem}" +
                ".btn-ghost:hover{background:#f8fafc}" +
                ".form-card{background:var(--panel);border-radius:20px;padding:24px;box-shadow:var(--shadow)}" +
                ".form-card h3{font-weight:700}" +
                ".btn-primary-soft{background:linear-gradient(135deg,#5b6df9,#7cc4ff);border:none;border-radius:12px;padding:12px 18px}" +
                ".btn-primary-soft:hover{filter:brightness(0.96)}" +
                ".upload-hint{background:#f6f8ff;border-radius:14px;padding:14px 16px;color:#5b647a;font-size:.9rem}" +
                ".helper-link{color:#5b6df9;text-decoration:none}" +
                ".helper-link:hover{text-decoration:underline}" +
                "@media (max-width: 992px){" +
                ".admin-shell{grid-template-columns:1fr}" +
                ".sidebar{position:sticky;top:0;z-index:10;display:flex;flex-wrap:wrap;gap:12px;align-items:center;" +
                "justify-content:space-between;border-right:none;border-bottom:1px solid #edf0f6}" +
                ".nav-title{width:100%;margin:12px 0 6px}" +
                ".nav-link{padding:8px 10px}" +
                ".main{padding:20px}" +
                "}" +
                "@media (max-width: 720px){" +
                ".topbar{padding:14px 16px}" +
                ".search-bar{max-width:100%}" +
                ".stat-grid{grid-template-columns:1fr}" +
                ".form-card{padding:20px}" +
                "}" +
                "@media (max-width: 576px){" +
                ".sidebar{padding:18px}" +
                ".brand{font-size:1rem}" +
                ".brand-badge{width:38px;height:38px}" +
                ".form-card h3{font-size:1.2rem}" +
                "}" +
                "</style></head><body>" +
                "<div class='admin-shell'>" +
                "<aside class='sidebar'>" +
                "<div class='brand'><div class='brand-badge'>EX</div>ExamSeating</div>" +
                "<div class='nav-title'>Admin Panel</div>" +
                "<div class='nav-link active'>Dashboard</div>" +
                "<div class='nav-link'>Manage Seats</div>" +
                "<div class='nav-link'>Exam Schedule</div>" +
                "<div class='nav-link'>Reports</div>" +
                "<div class='nav-title'>Quick Links</div>" +
                "<a class='nav-link' href='/admin/logout'>Return to Search</a>" +
                "</aside>" +
                "<main class='main'>" +
                "<div class='topbar'>" +
                "<div class='search-bar'>Search rolls, rooms, or exams...</div>" +
                "<div class='d-flex align-items-center gap-3 flex-wrap'>" +
                "<div class='text-muted'>Admin</div>" +
                "<div class='avatar'>AD</div>" +
                "<a class='logout-btn' href='/admin/logout'>Logout</a>" +
                "</div></div>" +
                "<div class='stat-grid'>" +
                "<div class='stat-card'>" +
                "<h6>Total Students</h6>" +
                "<div class='stat-value'>" + students.size() + "</div>" +
                "<div class='text-muted small'>Current records</div>" +
                "</div>" +
                "<div class='stat-card'>" +
                "<h6>Active Rooms</h6>" +
                "<div class='stat-value'>" + rooms.size() + "</div>" +
                "<div class='text-muted small'>Rooms with entries</div>" +
                "</div>" +
                "<div class='stat-card'>" +
                "<h6>Exam Sessions</h6>" +
                "<div class='stat-value'>" + exams.size() + "</div>" +
                "<div class='text-muted small'>Distinct exams</div>" +
                "</div>" +
                "</div>" +
                "<div class='row g-4 mb-4'>" +
                "<div class='col-lg-4'>" +
                "<div class='detail-card'>" +
                "<h5>Rooms</h5>" +
                buildBadgeList(rooms, "No rooms yet") +
                "</div></div>" +
                "<div class='col-lg-4'>" +
                "<div class='detail-card'>" +
                "<h5>Floors</h5>" +
                buildBadgeList(floors, "No floors yet") +
                "</div></div>" +
                "<div class='col-lg-4'>" +
                "<div class='detail-card'>" +
                "<h5>Exam Sessions</h5>" +
                buildBadgeList(examSessions, "No exams yet") +
                "</div></div>" +
                "</div>" +
                "<div class='table-card mb-4'>" +
                "<div class='d-flex flex-wrap justify-content-between align-items-center mb-2'>" +
                "<div>" +
                "<h3 class='mb-1'>Student List</h3>" +
                "<p class='text-muted mb-0'>View roll numbers with seating and exam metadata.</p>" +
                "</div>" +
                "<a class='helper-link' href='#add-entry'>Add entry</a>" +
                "</div>" +
                studentTable +
                "</div>" +
                "<div class='form-card mb-4'>" +
                "<div class='d-flex flex-wrap justify-content-between align-items-center mb-2'>" +
                "<div>" +
                "<h3 class='mb-1'>Upload Student CSV</h3>" +
                "<p class='text-muted mb-0'>Add student seating and class details in bulk.</p>" +
                "</div>" +
                "<a class='helper-link' href='#add-entry'>Add manually</a>" +
                "</div>" +
                uploadMessage +
                "<form action='/admin/upload' method='post' enctype='multipart/form-data' class='mt-3'>" +
                "<div class='row g-3 align-items-center'>" +
                "<div class='col-lg-8'>" +
                "<input class='form-control' type='file' name='csvFile' accept='.csv' required>" +
                "</div>" +
                "<div class='col-lg-4 d-grid'>" +
                "<button type='submit' class='btn btn-primary-soft'>Upload CSV</button>" +
                "</div>" +
                "</div>" +
                "<div class='upload-hint mt-3'>Expected columns: roll, room, floor, seat, examName, examDate, examTime. " +
                "Headers are optional.</div>" +
                "</form>" +
                "</div>" +
                "<div class='form-card'>" +
                "<div class='d-flex flex-wrap justify-content-between align-items-center mb-2'>" +
                "<div>" +
                "<h3 class='mb-1'>Add Exam Seating Details</h3>" +
                "<p class='text-muted mb-0'>Create seating entries with schedule metadata for quick lookups.</p>" +
                "</div>" +
                "<a class='helper-link' href='/'>View search</a>" +
                "</div>" +
                addMessage +
                "<form id='add-entry' action='/admin/add' method='post' class='mt-3'>" +
                "<div class='row g-3'>" +
                "<div class='col-md-6'>" +
                "<label class='form-label' for='roll'>Roll Number</label>" +
                "<input class='form-control' id='roll' name='roll' placeholder='CS2024XXX' required>" +
                "</div>" +
                "<div class='col-md-6'>" +
                "<label class='form-label' for='room'>Room</label>" +
                "<input class='form-control' id='room' name='room' placeholder='Room-101' required>" +
                "</div>" +
                "<div class='col-md-6'>" +
                "<label class='form-label' for='floor'>Floor</label>" +
                "<input class='form-control' id='floor' name='floor' placeholder='First Floor' required>" +
                "</div>" +
                "<div class='col-md-6'>" +
                "<label class='form-label' for='seat'>Seat Number</label>" +
                "<input class='form-control' id='seat' name='seat' placeholder='S01' required>" +
                "</div>" +
                "<div class='col-md-6'>" +
                "<label class='form-label' for='examName'>Exam Name</label>" +
                "<input class='form-control' id='examName' name='examName' placeholder='Data Structures' required>" +
                "</div>" +
                "<div class='col-md-3'>" +
                "<label class='form-label' for='examDate'>Exam Date</label>" +
                "<input class='form-control' id='examDate' name='examDate' type='date' required>" +
                "</div>" +
                "<div class='col-md-3'>" +
                "<label class='form-label' for='examTime'>Exam Time</label>" +
                "<input class='form-control' id='examTime' name='examTime' type='time' required>" +
                "</div>" +
                "</div>" +
                "<div class='d-flex flex-wrap justify-content-between align-items-center mt-4'>" +
                "<div class='text-muted small'>Tip: Uploading data in batches will be supported soon.</div>" +
                "<button type='submit' class='btn btn-primary-soft'>Save Entry</button>" +
                "</div></form>" +
                "</div>" +
                "</main></div>" +
                "</body></html>";

        sendResponse(exchange, html);
    }

    private static void handleAdminLogin(HttpExchange exchange) throws IOException {
        String message = "";
        if ("post".equalsIgnoreCase(exchange.getRequestMethod())) {
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            Map<String, String> data = parseFormBody(body);
            String username = data.getOrDefault("username", "").trim();
            String password = data.getOrDefault("password", "").trim();
            if (isValidAdminCredentials(username, password)) {
                exchange.getResponseHeaders().add("Set-Cookie", "admin=1; Path=/; HttpOnly");
                exchange.getResponseHeaders().add("Location", "/admin");
                exchange.sendResponseHeaders(302, -1);
                return;
            }
            message = "<div class='alert alert-danger'>Invalid credentials. Please try again.</div>";
        }
        String html = "<!DOCTYPE html>" +
                "<html lang='en'><head>" +
                "<meta charset='UTF-8'>" +
                "<meta name='viewport' content='width=device-width, initial-scale=1.0'>" +
                "<title>Admin Login</title>" +
                "<link href='https://cdn.jsdelivr.net/npm/bootstrap@5.3.0/dist/css/bootstrap.min.css' rel='stylesheet'>" +
                "<style>" +
                "body{background:#f3f6fb;font-family:'Segoe UI',sans-serif}" +
                ".login-shell{min-height:100vh;display:flex;align-items:center;justify-content:center;padding:24px}" +
                ".login-card{width:100%;max-width:560px;background:#fff;border-radius:20px;box-shadow:0 20px 50px rgba(15,23,42,.08);" +
                "padding:38px 40px;text-align:center}" +
                ".login-title{font-size:2rem;font-weight:700;color:#3b4cca;margin-bottom:6px}" +
                ".login-subtitle{color:#6b7280;margin-bottom:26px}" +
                ".login-input{border-radius:12px;padding:12px 14px}" +
                ".login-btn{width:100%;border-radius:12px;padding:12px 16px;background:linear-gradient(135deg,#4c45f3,#6d7bff);" +
                "border:none;color:#fff;font-weight:600}" +
                ".login-btn:hover{filter:brightness(.95)}" +
                ".login-foot{margin-top:16px;font-size:.9rem;color:#8a94a6}" +
                ".login-foot a{color:#4c45f3;text-decoration:none}" +
                ".login-foot a:hover{text-decoration:underline}" +
                "@media (max-width: 576px){" +
                ".login-card{padding:28px 22px}" +
                ".login-title{font-size:1.6rem}" +
                "}" +
                "</style></head><body>" +
                "<div class='login-shell'>" +
                "<div class='login-card'>" +
                "<div class='login-title'>Login</div>" +
                "<div class='login-subtitle'>Hey, enter your details to sign in to your account.</div>" +
                "<div class='text-muted mb-3' style='font-size:.9rem;'>Default login: <b>admin</b> / <b>admin123</b></div>" +
                message +
                "<form action='/admin/login' method='post' class='text-start'>" +
                "<div class='mb-3'>" +
                "<label class='form-label' for='username'>Username or Email</label>" +
                "<input class='form-control login-input' id='username' name='username' placeholder='Enter your username/email' required>" +
                "</div>" +
                "<div class='mb-3'>" +
                "<label class='form-label' for='password'>Password</label>" +
                "<input type='password' class='form-control login-input' id='password' name='password' placeholder='Enter your password' required>" +
                "</div>" +
                "<button type='submit' class='login-btn'>Login</button>" +
                "</form>" +
                "<div class='login-foot'>Don't have an account? <a href='/'>Signup Now</a></div>" +
                "</div></div></body></html>";
        sendResponse(exchange, html);
    }

    private static void handleAdminLogout(HttpExchange exchange) throws IOException {
        exchange.getResponseHeaders().add("Set-Cookie", "admin=; Path=/; Max-Age=0; HttpOnly");
        exchange.getResponseHeaders().add("Location", "/");
        exchange.sendResponseHeaders(302, -1);
    }

    private static void handleAdminAdd(HttpExchange exchange) throws IOException {
        if (!isAdminAuthenticated(exchange)) {
            exchange.getResponseHeaders().add("Location", "/admin/login");
            exchange.sendResponseHeaders(302, -1);
            return;
        }
        if (!"post".equalsIgnoreCase(exchange.getRequestMethod())) {
            exchange.getResponseHeaders().add("Location", "/admin?status=error");
            exchange.sendResponseHeaders(302, -1);
            return;
        }
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        Map<String, String> data = parseFormBody(body);

        String roll = data.getOrDefault("roll", "").trim();
        String room = data.getOrDefault("room", "").trim();
        String floor = data.getOrDefault("floor", "").trim();
        String seat = data.getOrDefault("seat", "").trim();
        String examName = data.getOrDefault("examName", "").trim();
        String examDate = data.getOrDefault("examDate", "").trim();
        String examTime = data.getOrDefault("examTime", "").trim();

        boolean ok = !(roll.isEmpty() || room.isEmpty() || floor.isEmpty() || seat.isEmpty()
                || examName.isEmpty() || examDate.isEmpty() || examTime.isEmpty());
        if (ok) {
            Student student = new Student(roll, room, floor, seat, examName, examDate, examTime);
            ok = db.addStudent(student);
        }
        String redirect = ok ? "/admin?status=success" : "/admin?status=error";
        exchange.getResponseHeaders().add("Location", redirect);
        exchange.sendResponseHeaders(302, -1);
    }

    private static void handleAdminUpload(HttpExchange exchange) throws IOException {
        if (!isAdminAuthenticated(exchange)) {
            exchange.getResponseHeaders().add("Location", "/admin/login");
            exchange.sendResponseHeaders(302, -1);
            return;
        }
        if (!"post".equalsIgnoreCase(exchange.getRequestMethod())) {
            exchange.getResponseHeaders().add("Location", "/admin?status=upload_error");
            exchange.sendResponseHeaders(302, -1);
            return;
        }
        String contentType = exchange.getRequestHeaders().getFirst("Content-Type");
        if (contentType == null || !contentType.contains("multipart/form-data")) {
            exchange.getResponseHeaders().add("Location", "/admin?status=upload_error");
            exchange.sendResponseHeaders(302, -1);
            return;
        }
        String boundary = extractBoundary(contentType);
        if (boundary == null) {
            exchange.getResponseHeaders().add("Location", "/admin?status=upload_error");
            exchange.sendResponseHeaders(302, -1);
            return;
        }
        byte[] bodyBytes = exchange.getRequestBody().readAllBytes();
        String csvContent = extractMultipartFile(bodyBytes, boundary, "csvFile");
        if (csvContent == null || csvContent.isBlank()) {
            exchange.getResponseHeaders().add("Location", "/admin?status=upload_error");
            exchange.sendResponseHeaders(302, -1);
            return;
        }
        CsvUploadResult result = ingestCsv(csvContent);
        String redirect = "/admin?status=upload_success&count=" + result.added + "&failed=" + result.skipped;
        exchange.getResponseHeaders().add("Location", redirect);
        exchange.sendResponseHeaders(302, -1);
    }

    private static void handleAdminDelete(HttpExchange exchange) throws IOException {
        if (!isAdminAuthenticated(exchange)) {
            exchange.getResponseHeaders().add("Location", "/admin/login");
            exchange.sendResponseHeaders(302, -1);
            return;
        }
        if (!"post".equalsIgnoreCase(exchange.getRequestMethod())) {
            exchange.getResponseHeaders().add("Location", "/admin?status=delete_error");
            exchange.sendResponseHeaders(302, -1);
            return;
        }
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        Map<String, String> data = parseFormBody(body);
        String roll = data.getOrDefault("roll", "").trim();
        boolean ok = db.deleteStudent(roll);
        String redirect = ok ? "/admin?status=delete_success" : "/admin?status=delete_error";
        exchange.getResponseHeaders().add("Location", redirect);
        exchange.sendResponseHeaders(302, -1);
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

    private static Map<String, String> parseFormBody(String body) {
        Map<String, String> data = new HashMap<>();
        if (body == null || body.isBlank()) {
            return data;
        }
        String[] pairs = body.split("&");
        for (String pair : pairs) {
            String[] kv = pair.split("=", 2);
            if (kv.length == 2) {
                String key = URLDecoder.decode(kv[0], StandardCharsets.UTF_8);
                String value = URLDecoder.decode(kv[1], StandardCharsets.UTF_8);
                data.put(key, value);
            }
        }
        return data;
    }

    private static boolean isValidAdminCredentials(String username, String password) {
        return "admin".equalsIgnoreCase(username) && "admin123".equals(password);
    }

    private static boolean isAdminAuthenticated(HttpExchange exchange) {
        String cookieHeader = exchange.getRequestHeaders().getFirst("Cookie");
        if (cookieHeader == null) {
            return false;
        }
        String[] cookies = cookieHeader.split(";");
        for (String cookie : cookies) {
            String[] kv = cookie.trim().split("=", 2);
            if (kv.length == 2 && "admin".equals(kv[0]) && "1".equals(kv[1])) {
                return true;
            }
        }
        return false;
    }

    private static String getQueryParam(String query, String key) {
        if (query == null || key == null) {
            return "";
        }
        String[] pairs = query.split("&");
        for (String pair : pairs) {
            String[] kv = pair.split("=", 2);
            if (kv.length == 2 && key.equals(kv[0])) {
                return URLDecoder.decode(kv[1], StandardCharsets.UTF_8);
            }
        }
        return "";
    }

    private static String extractBoundary(String contentType) {
        String[] parts = contentType.split(";");
        for (String part : parts) {
            String trimmed = part.trim();
            if (trimmed.startsWith("boundary=")) {
                return "--" + trimmed.substring("boundary=".length());
            }
        }
        return null;
    }

    private static String extractMultipartFile(byte[] bodyBytes, String boundary, String fieldName) {
        String body = new String(bodyBytes, StandardCharsets.UTF_8);
        String[] parts = body.split(boundary);
        for (String part : parts) {
            if (!part.contains("name=\"" + fieldName + "\"")) {
                continue;
            }
            int headerEnd = part.indexOf("\r\n\r\n");
            int offset = 4;
            if (headerEnd < 0) {
                headerEnd = part.indexOf("\n\n");
                offset = 2;
            }
            if (headerEnd < 0) {
                continue;
            }
            String payload = part.substring(headerEnd + offset);
            int trailing = payload.lastIndexOf("\r\n");
            if (trailing > -1) {
                payload = payload.substring(0, trailing);
            }
            return payload.trim();
        }
        return null;
    }

    private static CsvUploadResult ingestCsv(String csvContent) {
        String[] lines = csvContent.split("\\r?\\n");
        int added = 0;
        int skipped = 0;
        for (String line : lines) {
            if (line == null || line.isBlank()) {
                continue;
            }
            String trimmed = line.trim();
            if (trimmed.toLowerCase().startsWith("roll")) {
                continue;
            }
            String[] parts = trimmed.split(",", -1);
            if (parts.length < 4) {
                skipped++;
                continue;
            }
            String roll = parts[0].trim();
            String room = parts[1].trim();
            String floor = parts[2].trim();
            String seat = parts[3].trim();
            String examName = parts.length > 4 ? parts[4].trim() : "N/A";
            String examDate = parts.length > 5 ? parts[5].trim() : "N/A";
            String examTime = parts.length > 6 ? parts[6].trim() : "N/A";
            if (roll.isEmpty() || room.isEmpty() || floor.isEmpty() || seat.isEmpty()) {
                skipped++;
                continue;
            }
            Student student = new Student(roll, room, floor, seat, examName, examDate, examTime);
            if (db.addStudent(student)) {
                added++;
            } else {
                skipped++;
            }
        }
        return new CsvUploadResult(added, skipped);
    }

    private static class CsvUploadResult {
        private final int added;
        private final int skipped;

        private CsvUploadResult(int added, int skipped) {
            this.added = added;
            this.skipped = skipped;
        }
    }

    private static void addIfPresent(Set<String> target, String value) {
        if (target == null || value == null) {
            return;
        }
        String trimmed = value.trim();
        if (!trimmed.isEmpty() && !"N/A".equalsIgnoreCase(trimmed)) {
            target.add(trimmed);
        }
    }

    private static String formatExamSession(Student student) {
        if (student == null) {
            return "";
        }
        String name = safeValue(student.getExamName());
        String date = safeValue(student.getExamDate());
        String time = safeValue(student.getExamTime());
        StringBuilder sb = new StringBuilder();
        if (!name.isEmpty()) {
            sb.append(name);
        }
        if (!date.isEmpty() || !time.isEmpty()) {
            if (sb.length() > 0) {
                sb.append(" ");
            }
            sb.append("(");
            if (!date.isEmpty()) {
                sb.append(date);
            }
            if (!date.isEmpty() && !time.isEmpty()) {
                sb.append(", ");
            }
            if (!time.isEmpty()) {
                sb.append(time);
            }
            sb.append(")");
        }
        return sb.toString().trim();
    }

    private static String safeValue(String value) {
        if (value == null) {
            return "";
        }
        String trimmed = value.trim();
        return "N/A".equalsIgnoreCase(trimmed) ? "" : trimmed;
    }

    private static String buildBadgeList(Set<String> items, String emptyMessage) {
        if (items == null || items.isEmpty()) {
            return "<div class='empty-state'>" + escape(emptyMessage) + "</div>";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("<div class='badge-list'>");
        for (String item : items) {
            sb.append("<span class='badge-soft'>").append(escape(item)).append("</span>");
        }
        sb.append("</div>");
        return sb.toString();
    }

    private static String buildStudentTableSection(List<Student> students) {
        if (students == null || students.isEmpty()) {
            return "<div class='empty-state'>No student entries available yet.</div>";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("<div class='table-responsive'>");
        sb.append("<table class='table align-middle'>");
        sb.append("<thead><tr>");
        sb.append("<th>Roll</th><th>Room</th><th>Floor</th><th>Seat</th>");
        sb.append("<th>Exam</th><th>Date</th><th>Time</th>");
        sb.append("<th>Actions</th>");
        sb.append("</tr></thead><tbody>");
        for (Student student : students) {
            sb.append("<tr>");
            sb.append("<td>").append(escape(student.getRollNumber())).append("</td>");
            sb.append("<td>").append(escape(student.getRoom())).append("</td>");
            sb.append("<td>").append(escape(student.getFloor())).append("</td>");
            sb.append("<td>").append(escape(student.getSeatNumber())).append("</td>");
            sb.append("<td>").append(escape(student.getExamName())).append("</td>");
            sb.append("<td>").append(escape(student.getExamDate())).append("</td>");
            sb.append("<td>").append(escape(student.getExamTime())).append("</td>");
            sb.append("<td>");
            sb.append("<form action='/admin/delete' method='post' onsubmit=\"return confirm('Delete this student?');\">");
            sb.append("<input type='hidden' name='roll' value='").append(escape(student.getRollNumber())).append("'>");
            sb.append("<button type='submit' class='btn-ghost'>Delete</button>");
            sb.append("</form>");
            sb.append("</td>");
            sb.append("</tr>");
        }
        sb.append("</tbody></table></div>");
        return sb.toString();
    }
}

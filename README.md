Exam Hall Seating System
Project Overview

The Exam Hall Seating System is a semi-automated web-based application developed to help students quickly find their assigned exam room, floor, and seat number using their roll number. The system reduces confusion during examinations and minimizes the manual effort required by examination staff.

Seating data is entered once by the administrator, after which the system automatically retrieves and displays the required information. The application also supports floor plan visualization, allowing students to identify their exact seating location within the exam hall.

Key Features

Search exam seating details using roll number

Displays room number, floor, and seat number

Visual floor plan support with seat highlighting

Semi-automated seating management

Simple and user-friendly web interface

Reduces time and effort for students and staff

System Architecture

Frontend: HTML, CSS, JavaScript

Backend: Java (HttpServer)

Data Storage: Text file (seatingData.txt)

Deployment:

Frontend hosted as a static site

Backend deployed using Docker on Render

Project Structure
ExamHallSeatingSystem/
├── src/
│   ├── Student.java
│   ├── SeatingDatabase.java
│   └── SeatingWebServer.java
├── data/
│   └── seatingData.txt
├── static/
│   ├── floorplan_first_room101.png
│   ├── floorplan_first_room102.png
│   └── floorplan_second_room201.jpg
├── Dockerfile
└── README.md

Technologies Used

Core Java

Java HttpServer

HTML5

CSS3

Bootstrap

Docker

Git and GitHub

How to Run the Project Locally
Step 1: Compile Java Files
javac src/*.java

Step 2: Run the Java Web Server
java -cp src SeatingWebServer

Step 3: Access the Application

Open a browser and navigate to:

http://localhost:8080

Docker Deployment (Backend)
Dockerfile
FROM openjdk:17-jdk-slim
WORKDIR /app
COPY src ./src
COPY data ./data
RUN javac src/*.java
EXPOSE 8080
CMD ["java", "-cp", "src", "SeatingWebServer"]


On Render, select Docker as the runtime environment and configure the service to use port 8080.

Sample Seating Data Format
CS2024001,Room-101,First Floor,S01
CS2024002,Room-101,First Floor,S02
CS2024003,Room-102,First Floor,S01

Advantages

Eliminates manual seat searching during examinations

Improves accuracy in seat allocation

Reduces administrative workload

Easy to update and maintain

Suitable for academic environments

Future Enhancements

Fully automated seat allocation logic

Database integration (MySQL or PostgreSQL)

Admin dashboard for managing seating data

Student authentication system

Enhanced UI and mobile responsiveness

Project Type

This project is designed as an academic mini project for Computer Science and Information Technology students, demonstrating the integration of a Java backend with a web-based frontend.

References

Oracle Java Documentation

Bootstrap Official Documentation

W3Schools Web Tutorials

Render Deployment Documentation

Stack Overflow Community Resources

# Library Management System — Render Ready

## Stack

- Java 17
- Spring Boot 3.5.6
- Spring MVC + Thymeleaf
- JDBC / JdbcTemplate
- PostgreSQL
- Docker
- Render

## Why this version fixes the Render "Not Found" problem

The project is a real Spring Boot web service. The root route is:

GET /

and it returns `login.html`.

It also has:

GET /dashboard
GET /health

Render runs the application on the port supplied by the `PORT` environment variable. The application listens on `0.0.0.0` and the Docker image exposes port 10000.

The `render.yaml` file creates a Render web service and a Render PostgreSQL database and wires the database credentials into the web service.

## JDBC flow

Java application
    ↓
Spring JdbcTemplate
    ↓
JDBC API
    ↓
PostgreSQL JDBC Driver
    ↓
PostgreSQL database

The application uses real SQL for login, books, members, issue and return operations.

## Deploy from GitHub

1. Put ALL project files in the root of your GitHub repository.
2. Push to GitHub.
3. In Render choose New → Blueprint.
4. Select the GitHub repository.
5. Render reads `render.yaml`.
6. It creates:
   - `library-management-web`
   - `library-db`
7. Wait for the database and web service to become live.
8. Open the generated `onrender.com` URL.

You do NOT need to create a MySQL database on Render for this version.

## Login

Username:
admin

Password:
admin123

## Database

The database schema is automatically created from:

src/main/resources/schema.sql

Demo data is inserted from:

src/main/resources/data.sql

Spring Boot runs the SQL initialization at startup.

## Local run

Install Java 17 and Maven, then run:

mvn spring-boot:run

The local database defaults are:

Host: localhost
Port: 5432
Database: library_db
User: postgres
Password: postgres

You can override them with environment variables:

DB_HOST
DB_PORT
DB_NAME
DB_USER
DB_PASSWORD

Local URL:

http://localhost:8080

Health check:

http://localhost:8080/health

## Important

The demo login stores a simple password for learning purposes. For a production application, passwords should be hashed with BCrypt/Argon2 and authentication should use Spring Security.

Do not commit real database passwords or other secrets to GitHub. Render supports environment variables for secrets.

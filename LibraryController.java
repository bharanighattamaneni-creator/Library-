package com.library;

import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

@Controller
public class LibraryController {

    private final JdbcTemplate jdbc;

    public LibraryController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @GetMapping("/health")
    @ResponseBody
    public Map<String, Object> health() {
        try {
            Integer one = jdbc.queryForObject("SELECT 1", Integer.class);
            return Map.of("status", "UP", "database", one != null && one == 1 ? "UP" : "DOWN");
        } catch (Exception e) {
            return Map.of("status", "UP", "database", "DOWN", "message", "Application is running but database is unavailable");
        }
    }

    @GetMapping("/")
    public String loginPage() {
        return "login";
    }

    @PostMapping("/login")
    public String login(@RequestParam String username,
                        @RequestParam String password,
                        Model model) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM users WHERE username = ? AND password = ?",
                Integer.class, username, password);

        if (count != null && count > 0) {
            return "redirect:/dashboard";
        }

        model.addAttribute("error", "Invalid username or password");
        return "login";
    }

    @GetMapping("/dashboard")
    public String dashboard(Model model) {
        loadDashboard(model);
        return "dashboard";
    }

    private void loadDashboard(Model model) {
        List<Map<String, Object>> books = jdbc.queryForList(
                "SELECT id, title, author, category, quantity, available_quantity " +
                "FROM books ORDER BY id DESC");

        List<Map<String, Object>> members = jdbc.queryForList(
                "SELECT id, name, email, phone FROM members ORDER BY id DESC");

        List<Map<String, Object>> issued = jdbc.queryForList(
                "SELECT i.id, b.title AS book_title, m.name AS member_name, " +
                "i.issue_date, i.due_date, i.return_date, i.fine " +
                "FROM issued_books i " +
                "JOIN books b ON b.id = i.book_id " +
                "JOIN members m ON m.id = i.member_id " +
                "ORDER BY i.id DESC");

        model.addAttribute("books", books);
        model.addAttribute("members", members);
        model.addAttribute("issued", issued);
    }

    @PostMapping("/books/add")
    public String addBook(@RequestParam String title,
                          @RequestParam String author,
                          @RequestParam(required = false, defaultValue = "") String category,
                          @RequestParam int quantity,
                          Model model) {
        if (quantity < 1) {
            model.addAttribute("message", "Quantity must be at least 1.");
            return dashboard(model);
        }

        jdbc.update(
                "INSERT INTO books(title, author, category, quantity, available_quantity) VALUES (?, ?, ?, ?, ?)",
                title.trim(), author.trim(), category.trim(), quantity, quantity);

        return "redirect:/dashboard";
    }

    @PostMapping("/books/delete/{id}")
    public String deleteBook(@PathVariable long id, Model model) {
        try {
            jdbc.update("DELETE FROM books WHERE id = ?", id);
        } catch (DataAccessException e) {
            model.addAttribute("message",
                    "This book cannot be deleted because it has issue history. Return the book first or keep the record.");
        }
        return "redirect:/dashboard";
    }

    @PostMapping("/members/add")
    public String addMember(@RequestParam String name,
                            @RequestParam(required = false, defaultValue = "") String email,
                            @RequestParam(required = false, defaultValue = "") String phone) {
        jdbc.update(
                "INSERT INTO members(name, email, phone) VALUES (?, ?, ?)",
                name.trim(), email.trim(), phone.trim());
        return "redirect:/dashboard";
    }

    @PostMapping("/members/delete/{id}")
    public String deleteMember(@PathVariable long id, Model model) {
        try {
            jdbc.update("DELETE FROM members WHERE id = ?", id);
        } catch (DataAccessException e) {
            model.addAttribute("message",
                    "This member cannot be deleted because they have issue history.");
        }
        return "redirect:/dashboard";
    }

    @PostMapping("/issue")
    @Transactional
    public String issueBook(@RequestParam long bookId,
                            @RequestParam long memberId,
                            @RequestParam int days,
                            Model model) {
        if (days < 1 || days > 60) {
            model.addAttribute("message", "Loan period must be between 1 and 60 days.");
            return dashboard(model);
        }

        Integer available = jdbc.queryForObject(
                "SELECT available_quantity FROM books WHERE id = ? FOR UPDATE",
                Integer.class, bookId);

        if (available == null) {
            model.addAttribute("message", "Book not found.");
            return dashboard(model);
        }

        Integer memberExists = jdbc.queryForObject(
                "SELECT COUNT(*) FROM members WHERE id = ?",
                Integer.class, memberId);

        if (memberExists == null || memberExists == 0) {
            model.addAttribute("message", "Member not found.");
            return dashboard(model);
        }

        if (available <= 0) {
            model.addAttribute("message", "No available copy of this book.");
            return dashboard(model);
        }

        LocalDate issueDate = LocalDate.now();
        LocalDate dueDate = issueDate.plusDays(days);

        jdbc.update(
                "INSERT INTO issued_books(book_id, member_id, issue_date, due_date, return_date, fine) " +
                "VALUES (?, ?, ?, ?, NULL, 0)",
                bookId, memberId, issueDate, dueDate);

        jdbc.update(
                "UPDATE books SET available_quantity = available_quantity - 1 WHERE id = ?",
                bookId);

        return "redirect:/dashboard";
    }

    @PostMapping("/return/{id}")
    @Transactional
    public String returnBook(@PathVariable long id, Model model) {
        Map<String, Object> issue;
        try {
            issue = jdbc.queryForMap(
                    "SELECT id, book_id, due_date, return_date FROM issued_books WHERE id = ? FOR UPDATE",
                    id);
        } catch (DataAccessException e) {
            model.addAttribute("message", "Issue record not found.");
            return dashboard(model);
        }

        if (issue.get("return_date") != null) {
            model.addAttribute("message", "This book has already been returned.");
            return dashboard(model);
        }

        LocalDate dueDate = ((java.sql.Date) issue.get("due_date")).toLocalDate();
        LocalDate returnDate = LocalDate.now();

        long overdueDays = Math.max(0, ChronoUnit.DAYS.between(dueDate, returnDate));
        double fine = overdueDays * 5.0;

        long bookId = ((Number) issue.get("book_id")).longValue();

        jdbc.update(
                "UPDATE issued_books SET return_date = ?, fine = ? WHERE id = ?",
                returnDate, fine, id);

        jdbc.update(
                "UPDATE books SET available_quantity = LEAST(quantity, available_quantity + 1) WHERE id = ?",
                bookId);

        return "redirect:/dashboard";
    }
}

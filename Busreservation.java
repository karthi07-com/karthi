import java.util.*;
import java.sql.*;

/*
 * MODELS
 */
class Bus {
    int busNo;
    String from;
    String to;
    int seats;

    Bus(int busNo, String from, String to, int seats) {
        this.busNo = busNo;
        this.from = from;
        this.to = to;
        this.seats = seats;
    }
}

class Booking {
    int id;
    String name;
    String location;
    int seats;
    String date;

    Booking(String name, String location, int seats, String date) {
        this.name = name;
        this.location = location;
        this.seats = seats;
        this.date = date;
    }
}

/*
 * SERVICE
 */
class ReservationService {

    public void addBus(Bus bus) throws Exception {
        var con = DB.getConnection();

        var check = con.prepareStatement("SELECT 1 FROM buses WHERE busNo=?");
        check.setInt(1, bus.busNo);
        var rs = check.executeQuery();
        if (rs.next()) {
            System.out.println("Bus number already exists!");
            return;
        }

        var pst = con.prepareStatement(
                "INSERT INTO buses(busNo,fromCity,toCity,seats) VALUES (?,?,?,?)");

        pst.setInt(1, bus.busNo);
        pst.setString(2, bus.from);
        pst.setString(3, bus.to);
        pst.setInt(4, bus.seats);

        pst.executeUpdate();
        con.close();
        System.out.println("Bus added successfully!");
    }

    public void viewBuses() throws Exception {
        var con = DB.getConnection();
        var rs = con.createStatement().executeQuery("SELECT * FROM buses");

        System.out.println("\n--- Bus Details ---");
        while (rs.next()) {
            System.out.println("Bus No: " + rs.getInt("busNo") +
                    ", From: " + rs.getString("fromCity") +
                    ", To: " + rs.getString("toCity") +
                    ", Seats: " + rs.getInt("seats"));
        }
        con.close();
    }

    public void searchBus(String from, String to) throws Exception {
        var con = DB.getConnection();
        var pst = con.prepareStatement(
                "SELECT * FROM buses WHERE fromCity=? AND toCity=?");

        pst.setString(1, from);
        pst.setString(2, to);
        var rs = pst.executeQuery();

        boolean found = false;

        System.out.println("\n--- Search Results ---");
        while (rs.next()) {
            found = true;
            System.out.println("Bus No: " + rs.getInt("busNo") +
                    " Seats: " + rs.getInt("seats"));
        }

        if (!found) System.out.println("No buses available!");

        con.close();
    }

    public void bookTicket(Booking booking) throws Exception {
        var con = DB.getConnection();
        con.setAutoCommit(false);

        try {
            var pst = con.prepareStatement(
                    "SELECT seats FROM buses WHERE fromCity=? FOR UPDATE");
            pst.setString(1, booking.location);
            var rs = pst.executeQuery();

            if (!rs.next()) {
                System.out.println("Bus not found!");
                return;
            }

            int available = rs.getInt("seats");
            System.out.println("Available seats: " + available);

            if (booking.seats > available) {
                System.out.println("Not enough seats!");
                return;
            }

            String pnr = "PNR" + System.currentTimeMillis();

            var insert = con.prepareStatement(
                    "INSERT INTO bookings(passengerName,location,seatsBooked,travelDate,pnr) VALUES (?,?,?,?,?)");

            insert.setString(1, booking.name);
            insert.setString(2, booking.location);
            insert.setInt(3, booking.seats);
            insert.setString(4, booking.date);
            insert.setString(5, pnr);
            insert.executeUpdate();

            var update = con.prepareStatement(
                    "UPDATE buses SET seats=seats-? WHERE fromCity=?");
            update.setInt(1, booking.seats);
            update.setString(2, booking.location);
            update.executeUpdate();

            con.commit();

            System.out.println("\nBooking Successful!");
            System.out.println("Your PNR: " + pnr);

        } catch (Exception e) {
            con.rollback();
            System.out.println("Booking failed: " + e.getMessage());
        } finally {
            con.setAutoCommit(true);
            con.close();
        }
    }

    public void viewBookings() throws Exception {
        var con = DB.getConnection();
        var rs = con.createStatement().executeQuery("SELECT * FROM bookings");

        System.out.println("\n--- Booking Details ---");
        while (rs.next()) {
            System.out.println(
                    "ID: " + rs.getInt("id") +
                    ", Passenger: " + rs.getString("passengerName") +
                    ", Location: " + rs.getString("location") +
                    ", Date: " + rs.getString("travelDate") +
                    ", PNR: " + rs.getString("pnr") +
                    ", Seats: " + rs.getInt("seatsBooked"));
        }
        con.close();
    }

    public void viewBookingsByDate(String date) throws Exception {
        var con = DB.getConnection();

        var pst = con.prepareStatement(
                "SELECT * FROM bookings WHERE travelDate=?");
        pst.setString(1, date);
        var rs = pst.executeQuery();

        System.out.println("\n--- Bookings on " + date + " ---");
        while (rs.next()) {
            System.out.println(
                    "Passenger: " + rs.getString("passengerName") +
                    ", Location: " + rs.getString("location") +
                    ", Seats: " + rs.getInt("seatsBooked") +
                    ", PNR: " + rs.getString("pnr"));
        }
        con.close();
    }

    public void cancelBooking(int id) throws Exception {
        var con = DB.getConnection();

        var rs = con.createStatement()
                .executeQuery("SELECT location, seatsBooked FROM bookings WHERE id=" + id);

        if (!rs.next()) {
            System.out.println("Booking not found!");
            return;
        }

        String loc = rs.getString("location");
        int booked = rs.getInt("seatsBooked");

        con.createStatement().executeUpdate("DELETE FROM bookings WHERE id=" + id);

        var ups = con.prepareStatement(
                "UPDATE buses SET seats=seats+? WHERE fromCity=?");
        ups.setInt(1, booked);
        ups.setString(2, loc);
        ups.executeUpdate();

        con.close();
        System.out.println("Booking canceled!");
    }
}

/*
 * CONTROLLER
 */
public class Busreservation {

    static Scanner sc = new Scanner(System.in);
    static ReservationService service = new ReservationService();
    static boolean isAdmin = false;

    static int getInt(String msg) {
        while (true) {
            try {
                System.out.print(msg);
                return Integer.parseInt(sc.next());
            } catch (Exception e) {
                System.out.println("Enter a valid number!");
            }
        }
    }

static void createAdmin() {
    try (Connection con = DB.getConnection()) {

        String createTable = """
            CREATE TABLE IF NOT EXISTS admins (
                id INT PRIMARY KEY AUTO_INCREMENT,
                username VARCHAR(50) UNIQUE,
                password VARCHAR(100)
            )
        """;

        con.createStatement().execute(createTable);

        System.out.print("Enter admin username: ");
        String username = sc.next();

        System.out.print("Enter admin password: ");
        String pass = sc.next();

        PreparedStatement ps = con.prepareStatement(
                "INSERT INTO admins (username, password) VALUES (?, ?)"
        );

        ps.setString(1, username);
        ps.setString(2, pass);
        ps.executeUpdate();

        System.out.println("Admin saved to database!");

    } catch (Exception e) {
        System.out.println("DB Error: " + e.getMessage());
    }
}

    // LOGIN ADMIN (CHECK DB)
    static void loginAdmin() {
        try {
            System.out.print("Admin username: ");
            String u = sc.next();

            System.out.print("Password: ");
            String p = sc.next();

            var con = DB.getConnection();
            var pst = con.prepareStatement(
                    "SELECT * FROM admins WHERE username=? AND password=?");

            pst.setString(1, u);
            pst.setString(2, p);

            var rs = pst.executeQuery();

            if (rs.next()) {
                isAdmin = true;
                System.out.println("Admin login successful!");
            } else {
                System.out.println("Invalid credentials");
            }

            con.close();

        } catch (Exception e) {
            System.out.println("Error: " + e);
        }
    }

    public static void main(String[] args) {

        while (true) {
            System.out.println("\n===== BUS RESERVATION SYSTEM =====");
            System.out.println("1. Create Admin");
            System.out.println("2. Admin Login");
            System.out.println("3. Add Bus (Admin)");
            System.out.println("4. View Bus Details");
            System.out.println("5. Book Ticket");
            System.out.println("6. View Bookings");
            System.out.println("7. Search Bus");
            System.out.println("8. Cancel Booking (Admin)");
            System.out.println("9. View Bookings by Date");
            System.out.println("10. Exit");

            int choice = getInt("Enter your choice: ");

            try {
                switch (choice) {

                    case 1 -> createAdmin();
                    case 2 -> loginAdmin();

                    case 3 -> {
                        if (!isAdmin) { System.out.println("Admin only!"); break; }
                        int no = getInt("Enter Bus Number: ");
                        System.out.print("From: ");
                        String from = sc.next();
                        System.out.print("To: ");
                        String to = sc.next();
                        int seats = getInt("Seats: ");
                        service.addBus(new Bus(no, from, to, seats));
                    }

                    case 4 -> service.viewBuses();

                    case 5 -> {
                        sc.nextLine();
                        System.out.print("Passenger Name: ");
                        String name = sc.nextLine();
                        System.out.print("Location: ");
                        String loc = sc.nextLine();
                        System.out.print("Travel Date (YYYY-MM-DD): ");
                        String date = sc.nextLine();
                        int s = getInt("Seats to book: ");
                        service.bookTicket(new Booking(name, loc, s, date));
                    }

                    case 6 -> service.viewBookings();

                    case 7 -> {
                        System.out.print("From: ");
                        String f = sc.next();
                        System.out.print("To: ");
                        String t = sc.next();
                        service.searchBus(f, t);
                    }

                    case 8 -> {
                        if (!isAdmin) { System.out.println("Admin only!"); break; }
                        int id = getInt("Enter booking ID: ");
                        service.cancelBooking(id);
                    }

                    case 9 -> {
                        sc.nextLine();
                        System.out.print("Enter date (YYYY-MM-DD): ");
                        String d = sc.nextLine();
                        service.viewBookingsByDate(d);
                    }

                    case 10 -> {
                        System.out.println("Thank you!");
                        return;
                    }

                    default -> System.out.println("Invalid choice!");
                }
            } catch (Exception e) {
                System.out.println("Error: " + e);
            }
        }
    }
}

/*
 * DB HELPER
 */
class DB {
    public static Connection getConnection() throws Exception {
        return DriverManager.getConnection(
                "jdbc:mysql://localhost:3306/busdb",
                "root",          // change if needed
                ""      
        );
    }
}

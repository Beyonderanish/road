import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.Scanner;

public class Main {
    static boolean autoCheckDone = false;

    public static void main(String[] args) {
        Logger logger = new Logger();

        while (true) {
            UserSession session = AuthService.loginMenu();
            int choice;
            do {
                System.out.println("\n==== RTO SYSTEM ====");
                System.out.println("Role: " + session.role);

                if (session.role == Role.CLERK) {
                    Clerk.printMenu();
                } else if (session.role == Role.DRIVER) {
                    Driver.printMenu();
                } else if (session.role == Role.INSPECTOR) {
                    Inspector.printMenu();
                } else if (session.role == Role.SUPERINTENDENT) {
                    Superintendent.printMenu();
                }
                System.out.println("0. Logout");

                choice = ValidationService.getInt("Enter choice: ");

                VehicleService.runDailyComplianceCheck();

                if (!autoCheckDone) {
                    DriverService.runLicenseAutoDeactivation();
                    autoCheckDone = true;
                }

                if (choice == 0) {
                    System.out.println("🔄 Logging out...");
                    break;
                }

                if (session.role == Role.CLERK) {
                    Clerk.handleChoice(choice, session, logger);
                } else if (session.role == Role.DRIVER) {
                    Driver.handleChoice(choice, session, logger);
                } else if (session.role == Role.INSPECTOR) {
                    Inspector.handleChoice(choice, session, logger);
                } else if (session.role == Role.SUPERINTENDENT) {
                    Superintendent.handleChoice(choice, session, logger);
                }

            } while (choice != 0);
        }
    }
}

enum Role {
    CLERK, INSPECTOR, DRIVER, SUPERINTENDENT
}

class UserSession {
    String username;
    Role role;
    boolean active;
    int driverId;
}

class AuthDB {
    static String[] usernames = {"clerk1", "insp1", "super1"};
    static String[] passwords = {"123", "123", "123"};
    static Role[] roles = {Role.CLERK, Role.INSPECTOR, Role.SUPERINTENDENT};
    static boolean[] activeStatus = {true, true, true};

    static int validate(String u, String p) {
        for (int i = 0; i < usernames.length; i++) {
            if (usernames[i].equals(u) && passwords[i].equals(p)) {
                return i;
            }
        }
        return -1;
    }
}

class AuthService {
    static UserSession loginMenu() {
        while (true) {
            System.out.println("\n==== LOGIN MENU ====");
            System.out.println("1. Staff Login");
            System.out.println("2. Driver Login");
            System.out.println("0. Exit");

            int ch = ValidationService.getInt("Enter choice: ");

            if (ch == 1) {
                return staffLogin();
            } else if (ch == 2) {
                if (!DriverService.hasRegisteredDrivers()) {
                    System.out.println("❌ No registered drivers available to log in");
                    continue;
                }
                UserSession s = driverLogin();
                if (s != null) return s;
            } else if (ch == 0) {
                System.out.print("Are you sure you want to exit the system? (yes/no): ");
                String confirm = ValidationService.sc.nextLine();
                if (confirm.equalsIgnoreCase("yes")) {
                    System.out.println("👋 Exiting program...");
                    System.exit(0);
                } else {
                    System.out.println("🔁 Exit cancelled. Returning to login menu...");
                    continue; 
                }
            }
            if (ch != 1 && ch != 2 && ch != 0) {
                System.out.println("❌ Invalid choice");
            }
        }
    }

    static UserSession staffLogin() {
        while (true) {
            System.out.print("\nUsername: ");
            String u = ValidationService.sc.nextLine();
            System.out.print("Password: ");
            String p = ValidationService.sc.nextLine();

            int idx = AuthDB.validate(u, p);
            if (idx != -1) {
                UserSession s = new UserSession();
                s.username = u;
                s.role = AuthDB.roles[idx];
                s.active = AuthDB.activeStatus[idx];
                if (!s.active) {
                    System.out.println("❌ Staff inactive");
                    continue;
                }
                System.out.println("✅ Login success");
                return s;
            }
            System.out.println("❌ Invalid login");
        }
    }

    static UserSession driverLogin() {
        System.out.print("Enter License Number: ");
        String lic = ValidationService.sc.nextLine().trim();

        for (int i = 0; i < DriverService.driverCount; i++) {
            if (!DriverService.drivers[i].isDeleted &&
                DriverService.drivers[i].licenseNo.equals(lic)) {

                UserSession s = new UserSession();
                s.username = DriverService.drivers[i].name;
                s.role = Role.DRIVER;
                s.active = DriverService.drivers[i].isActive;
                s.driverId = DriverService.drivers[i].id;

                System.out.println("✅ Driver Login Success: " + s.username);
                return s;
            }
        }
        System.out.println("❌ Driver not found");
        return null;
    }
}

class Logger {
    String[] logs = new String[100];
    int index = 0;

    void log(String action, String role) {
        if (index < logs.length) {
            LocalDateTime now = LocalDateTime.now();
            String date = now.toLocalDate().toString();
            String time = now.toLocalTime().truncatedTo(ChronoUnit.SECONDS).toString();
            logs[index++] = "Date: " + date +
                            " | Time: " + time +
                            " | Action: " + action +
                            " | Role: " + role;
        }
    }

    void show() {
        System.out.println("\n--- LOGS ---");
        if (index == 0) {
            System.out.println("No logs available");
            return;
        }
        for (int i = 0; i < index; i++) {
            System.out.println(logs[i]);
        }
    }
}

class ValidationService {
    static Scanner sc = new Scanner(System.in);

    static String getName() {
        while (true) {
            System.out.print("Name: ");
            String n = sc.nextLine().trim();
            if (n.matches("[a-zA-Z ]+")) return n;
            System.out.println("❌ Invalid name");
        }
    }

    static LocalDate getDOB() {
        while (true) {
            try {
                System.out.print("DOB (yyyy-mm-dd): ");
                LocalDate dob = LocalDate.parse(sc.nextLine().trim());
                if (dob.isAfter(LocalDate.now())) {
                    System.out.println("❌ DOB cannot be in the future");
                } else {
                    return dob;
                }
            } catch (Exception e) {
                System.out.println("❌ Invalid DOB format");
            }
        }
    }

    static LocalDate getLicenseIssueDate() {
        while (true) {
            try {
                System.out.print("License Issue Date (yyyy-mm-dd): ");
                LocalDate issueDate = LocalDate.parse(sc.nextLine().trim());
                if (issueDate.isAfter(LocalDate.now())) {
                    System.out.println("❌ Issue date cannot be in the future");
                } else {
                    return issueDate;
                }
            } catch (Exception e) {
                System.out.println("❌ Invalid date format");
            }
        }
    }

    static LocalDate getViolationDate() {
        while (true) {
            try {
                System.out.print("Enter Violation Date (yyyy-mm-dd): ");
                LocalDate date = LocalDate.parse(sc.nextLine().trim());
                if (date.isAfter(LocalDate.now())) {
                    System.out.println("❌ Violation date cannot be in the future");
                } else {
                    return date;
                }
            } catch (Exception e) {
                System.out.println("❌ Invalid date format");
            }
        }
    }

    static String getLicense() {
        while (true) {
            System.out.print("License Number (6 digits): ");
            String lic = sc.nextLine().trim();
            if (lic.matches("^\\d{6}$")) return lic;
            System.out.println("❌ Invalid format. License must be exactly 6 digits.");
        }
    }

    static String getRegNumber() {
        while (true) {
            System.out.print("Registration No (KA-01-AB-1234): ");
            String reg = sc.nextLine().trim().toUpperCase();
            if (reg.matches("^[A-Z]{2}-\\d{2}-[A-Z]{2}-\\d{4}$")) return reg;
            System.out.println("❌ Invalid registration number format");
        }
    }

    static String getModel() {
        while (true) {
            System.out.print("Vehicle Model: ");
            String model = sc.nextLine().trim();
            if (!model.isEmpty()) return model;
            System.out.println("❌ Model cannot be empty");
        }
    }

    static int getInt(String msg) {
        while (true) {
            try {
                System.out.print(msg);
                int val = sc.nextInt();
                sc.nextLine();
                return val;
            } catch (Exception e) {
                System.out.println("❌ Invalid number");
                sc.nextLine();
            }
        }
    }
}
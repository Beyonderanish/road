import java.time.LocalDate;
import java.time.Period;
import java.time.temporal.ChronoUnit;

public class Clerk {
    public static void printMenu() {
        System.out.println("1. Register Driver");
        System.out.println("2. Register Vehicle");
        System.out.println("3. View Logs");
        System.out.println("4. Soft Delete Driver");
        System.out.println("5. Soft Delete Vehicle");
        System.out.println("6. Dashboard");
        System.out.println("7. View Expired Licenses");
    }

    public static void handleChoice(int choice, UserSession session, Logger logger) {
        switch (choice) {
            case 1: DriverService.registerDriver(logger, session); break;
            case 2: VehicleService.registerVehicle(logger, session); break;
            case 3: logger.show(); break;
            case 4: DeleteService.softDeleteDriver(logger, session); break;
            case 5: DeleteService.softDeleteVehicle(logger, session); break;
            case 6: DashboardService.showDashboard(session); break;
            case 7: DriverService.viewExpiredLicenses(session); break;
            default: System.out.println("❌ Invalid choice");
        }
    }
}

class DriverService {
    static Driver[] drivers = new Driver[20];
    static int driverCount = 0;
    static int nextDriverId = 1;

    static void registerDriver(Logger logger, UserSession session) {
        System.out.println("\n--- Driver Registration ---");
        if (session.role != Role.CLERK || !session.active) {
            System.out.println("❌ Only active clerks can register drivers");
            return;
        }
        int target = ValidationService.getInt("Enter number of drivers to register: ");
        if (target <= 0) {
            System.out.println("❌ Invalid count");
            return;
        }
        int completed = 0;
        while (completed < target) {
            System.out.println("\n👉 Driver " + (completed + 1) + " of " + target);
            String name = ValidationService.getName();
            LocalDate dob = ValidationService.getDOB();
            int age = Period.between(dob, LocalDate.now()).getYears();
            System.out.println("✅ Age: " + age + " years");
            if (age < 18) {
                System.out.println("❌ Driver must be at least 18 years old");
                continue;
            }
            String license = ValidationService.getLicense();
            if (licenseExists(license)) {
                System.out.println("❌ License number already exists");
                continue;
            }
            LocalDate issueDate = ValidationService.getLicenseIssueDate();
            LocalDate minimumIssueDate = dob.plusYears(18);
            if (issueDate.isBefore(minimumIssueDate)) {
                System.out.println("❌ License can only be issued after driver turns 18");
                continue;
            }
            LocalDate expiryDate = issueDate.plusYears(1);
            Driver d = new Driver();
            d.create(nextDriverId, name, dob, license, issueDate, expiryDate);
            drivers[driverCount++] = d;
            logger.log("Driver Registered ID: " + nextDriverId, session.role.toString());
            nextDriverId++;
            completed++;
            System.out.println("✅ Registered successfully (" + completed + "/" + target + ")");
        }
        System.out.println("\n✅ All drivers registered successfully!");
    }

    static boolean licenseExists(String lic) {
        for (int i = 0; i < driverCount; i++) {
            if (drivers[i].licenseNo.equals(lic)) return true;
        }
        return false;
    }

    static boolean isExpired(Driver d) {
        return d.expiryDate.isBefore(LocalDate.now());
    }

    static Driver findDriver(int id) {
        for (int i = 0; i < driverCount; i++) {
            if (drivers[i].id == id && !drivers[i].isDeleted) return drivers[i];
        }
        return null;
    }

    static boolean hasRegisteredDrivers() {
        for (int i = 0; i < driverCount; i++) {
            if (drivers[i] != null && !drivers[i].isDeleted) return true;
        }
        return false;
    }

    static void viewMyDetails(UserSession session) {
        for (int i = 0; i < driverCount; i++) {
            if (!drivers[i].isDeleted && drivers[i].name.equals(session.username)) {
                Driver d = drivers[i];
                System.out.println("\n--- MY DETAILS ---");
                System.out.println("ID: " + d.id);
                System.out.println("Name: " + d.name);
                System.out.println("Expiry: " + d.expiryDate);
                return;
            }
        }
        System.out.println("❌ Driver not found");
    }

    static void viewExpiredLicenses(UserSession session) {
        if (session.role != Role.CLERK) {
            System.out.println("❌ Only clerk allowed");
            return;
        }
        System.out.println("\n--- EXPIRED LICENSES ---");
        if (!hasRegisteredDrivers()) {
            System.out.println("❌ No registered drivers yet");
            return;
        }
        Driver[] list = new Driver[driverCount];
        int size = 0;
        for (int i = 0; i < driverCount; i++) {
            Driver d = drivers[i];
            if (d.expiryDate.isBefore(LocalDate.now()) && !d.isInRenewal) {
                list[size++] = d;
            }
        }
        if (size == 0) {
            System.out.println("No expired licenses");
            return;
        }
        for (int i = 0; i < size - 1; i++) {
            for (int j = i + 1; j < size; j++) {
                long d1 = ChronoUnit.DAYS.between(list[i].expiryDate, LocalDate.now());
                long d2 = ChronoUnit.DAYS.between(list[j].expiryDate, LocalDate.now());
                if (d1 > d2) {
                    Driver temp = list[i];
                    list[i] = list[j];
                    list[j] = temp;
                }
            }
        }
        for (int i = 0; i < size; i++) {
            Driver d = list[i];
            long days = ChronoUnit.DAYS.between(d.expiryDate, LocalDate.now());
            int vehicleCount = 0;
            for (int j = 0; j < VehicleService.vehicleCount; j++) {
                if (VehicleService.vehicles[j].ownerId == d.id && !VehicleService.vehicles[j].isDeleted){
                    vehicleCount++;
                }
            }
            System.out.print(d.name + " | ID: " + d.id + " | Expired: " + days + " days" + " | Vehicles: " + vehicleCount);
            if (days > 90) System.out.print(" ⚠ LEGAL ACTION REQUIRED");
            System.out.println();
        }
    }

    static void runLicenseAutoDeactivation() {
        for (int i = 0; i < driverCount; i++) {
            Driver d = drivers[i];
            if (d == null || d.isDeleted) continue;
            if (!d.isActive) continue;
            if (LocalDate.now().isAfter(d.expiryDate.plusDays(15))) {
                d.isActive = false;
                d.deactivatedDate = LocalDate.now();
                d.wasPreviouslyActive = true;
                if (!d.isArchived) {
                    ArchiveService.archiveDriver(d);
                    d.isArchived = true;
                }
                System.out.println("⚠ License auto-deactivated for Driver ID: " + d.id);
            }
        }
    }

    static void requestLicenseRenewal(UserSession session) {
        if (session.role != Role.DRIVER) {
            System.out.println("❌ Only drivers can request renewal");
            return;
        }
        for (int i = 0; i < driverCount; i++) {
            Driver d = drivers[i];
            if (!d.isDeleted && d.id == session.driverId) {
                long daysLeft = ChronoUnit.DAYS.between(LocalDate.now(), d.expiryDate);
                if (daysLeft < 0) {
                    System.out.println("⚠ License expired " + Math.abs(daysLeft) + " days ago.");
                } else if (daysLeft > 30) {
                    System.out.println("❌ Too early. License expires in " + daysLeft + " days.");
                    return;
                }
                if (d.renewalRequested) {
                    System.out.println("❌ Renewal already requested");
                    return;
                }
                d.renewalRequested = true;
                System.out.println("✅ Renewal request submitted");
                return;
            }
        }
    }

    static void approveRenewal(UserSession session) {
        if (session.role != Role.INSPECTOR) {
            System.out.println("❌ Only Inspector can approve renewals");
            return;
        }
        boolean found = false;
        System.out.println("\n--- PENDING RENEWAL REQUESTS ---");
        for (int i = 0; i < driverCount; i++) {
            Driver d = drivers[i];
            if (d != null && d.renewalRequested) {
                System.out.println("ID: " + d.id + " | Name: " + d.name + " | License Expiry: " + d.expiryDate);
                found = true;
            }
        }
        if (!found) {
            System.out.println("✅ No pending renewal requests");
            return;
        }
        int id = ValidationService.getInt("Enter Driver ID: ");
        Driver d = findDriver(id);
        if (d == null || !d.renewalRequested) {
            System.out.println("❌ No renewal request found");
            return;
        }
        d.renewalApproved = true;
        d.isActive = true;
        d.expiryDate = LocalDate.now().plusYears(1);
        d.renewalRequested = false;
        System.out.println("✅ License renewed successfully");
    }

    static boolean hasValidDrivers() {
        for (int i = 0; i < driverCount; i++) {
            Driver d = drivers[i];
            if (d != null && !d.isDeleted && d.isActive && !isExpired(d)) return true;
        }
        return false;
    }
}

class DeleteService {
    static void softDeleteDriver(Logger logger, UserSession session) {
        if (!DriverService.hasRegisteredDrivers()) {
            System.out.println("❌ No driver data available to delete");
            return;
        }
        int id = ValidationService.getInt("Enter Driver ID: ");
        Driver d = DriverService.findDriver(id);
        if (d == null) {
            System.out.println("❌ Not found");
            return;
        }
        d.isDeleted = true;
        d.isActive = false;
        logger.log("Driver Deleted ID: " + id, session.role.toString());
        System.out.println("✅ Deleted");
    }

    static void softDeleteVehicle(Logger logger, UserSession session) {
        if (!VehicleService.hasRegisteredVehicles()) {
            System.out.println("❌ No vehicle data available to delete");
            return;
        }
        int id = ValidationService.getInt("Enter Vehicle ID: ");
        Vehicle v = VehicleService.findVehicle(id);
        if (v == null) {
            System.out.println("❌ Not found");
            return;
        }
        v.isDeleted = true;
        logger.log("Vehicle Deleted ID: " + id, session.role.toString());
        System.out.println("✅ Deleted");
    }
}

class ArchiveService {
    static Driver[] archivedDrivers = new Driver[50];
    static int archiveCount = 0;

    static void archiveDriver(Driver d) {
        if (archiveCount < archivedDrivers.length) {
            archivedDrivers[archiveCount++] = d;
            System.out.println("📦 Driver archived: " + d.id);
        }
    }

    static void showArchivedDrivers() {
        System.out.println("\n--- ARCHIVED DRIVERS ---");
        if (archiveCount == 0) {
            System.out.println("No archived drivers");
            return;
        }
        for (int i = 0; i < archiveCount; i++) {
            Driver d = archivedDrivers[i];
            System.out.println(d.id + " | " + d.name + " | Expired: " + d.expiryDate);
        }
    }
}
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

public class Driver {
    int id;
    String name;
    LocalDate dob;
    String licenseNo;
    LocalDate issueDate;
    LocalDate expiryDate;
    boolean isActive;
    boolean isDeleted;
    boolean isInRenewal = false;
    LocalDate deactivatedDate;
    boolean wasPreviouslyActive = false;
    boolean isArchived = false;
    boolean renewalRequested = false;
    boolean renewalApproved = false;

    void create(int id, String name, LocalDate dob, String licenseNo,
                LocalDate issueDate, LocalDate expiryDate) {
        this.id = id;
        this.name = name;
        this.dob = dob;
        this.licenseNo = licenseNo;
        this.issueDate = issueDate;
        this.expiryDate = expiryDate;
        this.isActive = true;
        this.isDeleted = false;
    }

    public static void printMenu() {
        System.out.println("1. View My Details");
        System.out.println("2. Pay Fine");
        System.out.println("3. Renew Vehicle Tax");
        System.out.println("4. View Reminders");
        System.out.println("5. Request License Renewal");
    }

    public static void handleChoice(int choice, UserSession session, Logger logger) {
        switch (choice) {
            case 1: DriverService.viewMyDetails(session); break;
            case 2: ViolationService.applyDailyPenaltyUpdate(); ViolationService.payFine(session); break;
            case 3: VehicleService.renewTax(session); break;
            case 4: ViolationService.applyDailyPenaltyUpdate(); VehicleService.showReminders(session); break;
            case 5: DriverService.requestLicenseRenewal(session); break;
            default: System.out.println("❌ Invalid choice");
        }
    }
}

class Vehicle {
    int id;
    String regNo;
    String model;
    int ownerId;
    boolean isDeleted;
    boolean impounded = false;
    double taxAmount = 1000;
    LocalDate registrationDate;
    LocalDate taxExpiry;
    boolean isCommercial = false;
    boolean hasRecall = false;
    boolean taxPaid;
    boolean isNonCompliant = false;
    LocalDate nonComplianceDate;

    void create(int id, String regNo, String model, int ownerId) {
        this.id = id;
        this.regNo = regNo;
        this.model = model;
        this.ownerId = ownerId;
        this.isDeleted = false;
    }
}

class Violation {
    int id;
    int driverId;
    String vehicleRegNo;
    String type;
    double fineAmount;
    String date;
    boolean isPaid;
    LocalDate dueDate;
    int penaltyCount = 0;
    boolean escalatedToCourt = false;
    LocalDate lastPenaltyDate;

    void create(int id, int driverId, String vehicleRegNo,
                String type, double fineAmount, String date, boolean isPaid) {
        this.id = id;
        this.driverId = driverId;
        this.vehicleRegNo = vehicleRegNo;
        this.type = type;
        this.fineAmount = fineAmount;
        this.date = date;
        this.isPaid = isPaid;
    }
}

class VehicleService {
    static Vehicle[] vehicles = new Vehicle[50];
    static int vehicleCount = 0;
    static int nextVehicleId = 1;

    static void registerVehicle(Logger logger, UserSession session) {
        System.out.println("\n--- Vehicle Registration ---");
        if (session.role != Role.CLERK || !session.active) {
            System.out.println("❌ Only active clerks can register vehicles");
            return;
        }
        if (!DriverService.hasValidDrivers()) {
            System.out.println("❌ No valid drivers available. Vehicle cannot be registered.");
            return;
        }

        int target = ValidationService.getInt("Enter number of vehicles to register: ");
        if (target <= 0) {
            System.out.println("❌ Invalid count");
            return;
        }
        int completed = 0;
        while (completed < target) {
            System.out.println("\n👉 Vehicle " + (completed + 1) + " of " + target);
            int driverId = ValidationService.getInt("Enter Driver ID: ");
            int vehicleCountForDriver = countVehiclesByDriver(driverId);
            Driver d = DriverService.findDriver(driverId);

            if (d == null) {
                System.out.println("❌ Driver not found");
                continue;
            }
            if (!d.isActive) {
                System.out.println("❌ Driver is inactive");
                continue;
            }
            if (DriverService.isExpired(d)) {
                System.out.println("❌ License expired on: " + d.expiryDate);
                continue;
            }
            if (vehicleCountForDriver >= 5) {
                System.out.println("❌ Driver already has maximum allowed vehicles (5)");
                continue;
            }

            String reg = ValidationService.getRegNumber();
            if (vehicleExists(reg)) {
                System.out.println("❌ Already registered");
                continue;
            }

            String model = ValidationService.getModel();
            Vehicle v = new Vehicle();
            System.out.print("Enter Registration Date (yyyy-mm-dd): ");
            LocalDate regDate = LocalDate.parse(ValidationService.sc.nextLine());

            if (regDate.isAfter(LocalDate.now())) {
                System.out.println("❌ Registration date cannot be future");
                continue;
            }
            LocalDate eligibleDate = d.dob.plusYears(18);
            if (regDate.isBefore(eligibleDate)) {
                System.out.println("❌ Vehicle cannot be registered before owner turns 18");
                System.out.println("✅ Owner eligible only after: " + eligibleDate);
                continue;
            }
            System.out.print("Is Commercial Vehicle? (yes/no): ");
            v.isCommercial = ValidationService.sc.nextLine().equalsIgnoreCase("yes");

            v.create(nextVehicleId, reg, model, driverId);
            v.registrationDate = regDate;

            if (v.isCommercial) v.taxExpiry = regDate.plusYears(1);
            else v.taxExpiry = regDate.plusYears(15);

            vehicles[vehicleCount++] = v;
            logger.log("Vehicle Registered ID: " + nextVehicleId, session.role.toString());

            nextVehicleId++;
            completed++;
            System.out.println("✅ Registered successfully (" + completed + "/" + target + ")");
        }
        System.out.println("\n✅ All vehicles registered successfully!");
    }

    static boolean vehicleExists(String reg) {
        for (int i = 0; i < vehicleCount; i++) {
            if (vehicles[i].regNo.equalsIgnoreCase(reg)) return true;
        }
        return false;
    }

    static Vehicle findVehicle(int id) {
        for (int i = 0; i < vehicleCount; i++) {
            if (vehicles[i].id == id && !vehicles[i].isDeleted) return vehicles[i];
        }
        return null;
    }

    static boolean hasRegisteredVehicles() {
        for (int i = 0; i < vehicleCount; i++) {
            if (vehicles[i] != null && !vehicles[i].isDeleted) return true;
        }
        return false;
    }

    static void renewTax(UserSession session) {
        if (session.role != Role.DRIVER) {
            System.out.println("❌ Only driver allowed");
            return;
        }
        if (!hasVehicleForDriver(session.driverId)) {
            System.out.println("❌ You have no registered vehicles");
            return;
        }
        int vid = ValidationService.getInt("Enter Vehicle ID: ");
        Vehicle v = findVehicle(vid);
        if (v == null || v.isDeleted) {
            System.out.println("❌ Vehicle not found");
            return;
        }
        if (v.ownerId != session.driverId) {
            System.out.println("❌ You are not the owner of this vehicle");
            return;
        }
        if (!v.isCommercial) {
            System.out.println("✅ Private vehicle tax is one-time. No renewal required.");
            return;
        }
        for (int i = 0; i < ViolationService.violationCount; i++) {
            Violation vio = ViolationService.violations[i];
            if (vio.vehicleRegNo.equalsIgnoreCase(v.regNo) && !vio.isPaid) {
                System.out.println("❌ Clear all violations before renewing tax");
                return;
            }
        }
        if (LocalDate.now().isBefore(v.taxExpiry.minusMonths(1))) {
            System.out.println("❌ Too early for renewal");
            return;
        }
        if (v.isNonCompliant) {
            System.out.println("⚠ Vehicle is NON-COMPLIANT due to expired tax.");
            System.out.println("✅ Proceeding with renewal...");
        }
        double totalAmount = v.taxAmount;
        if (LocalDate.now().isAfter(v.taxExpiry)) {
            long days = ChronoUnit.DAYS.between(v.taxExpiry, LocalDate.now());
            long months = days / 30;
            double penalty = v.taxAmount * 0.05 * months;
            totalAmount += penalty;
            System.out.println("⚠ Late penalty: Rs." + Math.round(penalty));
        }
        totalAmount = Math.round(totalAmount);
        System.out.println("Total Payable: Rs." + totalAmount);
        double amt = ValidationService.getInt("Enter amount: ");
        if (amt != totalAmount) {
            System.out.println("❌ Please pay full amount");
            return;
        }
        LocalDate baseDate = LocalDate.now().isAfter(v.taxExpiry) ? LocalDate.now() : v.taxExpiry;
        v.taxExpiry = baseDate.plusYears(1);
        v.taxPaid = true;
        v.isNonCompliant = false;
        v.nonComplianceDate = null;
        System.out.println("✅ Tax paid and renewed successfully");
        System.out.println("📅 New Expiry Date: " + v.taxExpiry);
    }

    static void showReminders(UserSession session) {
        if (!hasRegisteredVehicles()) {
            System.out.println("❌ No registered vehicles yet");
            return;
        }
        boolean hasVehicleReminder = false;
        System.out.println("\n--- LICENSE STATUS ---");
        Driver d = DriverService.findDriver(session.driverId);
        if (d != null) {
            long licDays = ChronoUnit.DAYS.between(LocalDate.now(), d.expiryDate);
            if (licDays <= 30 && licDays >= 0) {
                System.out.println("📄 License expires in " + licDays + " days");
            } else if (licDays < 0) {
                System.out.println("❌ License EXPIRED " + Math.abs(licDays) + " days ago");
            } else {
                System.out.println("✅ License is up to date");
            }
        }
        System.out.println("\n--- VEHICLE REMINDERS ---");
        if (!hasVehicleForDriver(session.driverId)) {
            System.out.println("❌ No vehicles registered for this driver");
            return;
        }
        for (int i = 0; i < vehicleCount; i++) {
            Vehicle v = vehicles[i];
            if (v == null || v.isDeleted || v.ownerId != session.driverId) continue;
            long days = ChronoUnit.DAYS.between(LocalDate.now(), v.taxExpiry);
            if (days <= 30) {
                System.out.println("\nVehicle ID: " + v.id + " (" + v.regNo + ")");
                if (days <= 30 && days > 15) System.out.println("🟡 WARNING: Tax expires in " + days + " days");
                else if (days <= 15 && days > 5) System.out.println("🟠 MEDIUM WARNING: Tax expires in " + days + " days");
                else if (days <= 5 && days >= 0) System.out.println("🔴 HIGH WARNING: Tax expires in " + days + " days");
                else if (days < 0) System.out.println("❌ TAX EXPIRED " + Math.abs(days) + " days ago");
                hasVehicleReminder = true;
            }
        }
        if (!hasVehicleReminder) System.out.println("\n✅ No pending vehicle tax reminders");
    }

    static void listVehiclesWithAlerts() {
        for (int i = 0; i < vehicleCount; i++) {
            Vehicle v = vehicles[i];
            if (v == null || v.isDeleted) continue;
            long days = ChronoUnit.DAYS.between(LocalDate.now(), v.taxExpiry);
            if (days <= 30 || LocalDate.now().isAfter(v.taxExpiry)) {
                System.out.println("\nVehicle ID: " + v.id + " Reg: " + v.regNo);
                boolean hasViolation = false;
                for (int j = 0; j < ViolationService.violationCount; j++) {
                    Violation vio = ViolationService.violations[j];
                    if (vio.vehicleRegNo.equalsIgnoreCase(v.regNo) && !vio.isPaid) hasViolation = true;
                }
                if (hasViolation) System.out.println("⚠ Unpaid Violations Present");
            }
        }
    }

    static void viewVehicleDetails(UserSession session) {
        if (session.role != Role.INSPECTOR && session.role != Role.SUPERINTENDENT) {
            System.out.println("❌ Access denied");
            return;
        }
        if (!hasRegisteredVehicles()) {
            System.out.println("❌ No registered vehicles yet");
            return;
        }
        int vid = ValidationService.getInt("Enter Vehicle ID: ");
        Vehicle v = findVehicle(vid);
        if (v == null) {
            System.out.println("❌ Vehicle not found");
            return;
        }
        if (v.impounded) {
            System.out.println("❌ Vehicle is restricted (impounded/stolen)");
            System.out.println("⚠ Possible reason: Stolen / Legal hold / Recall issue");
            return;
        }

        System.out.println("\n--- VEHICLE DETAILS ---");
        System.out.println("Reg No: " + v.regNo);
        Driver owner = DriverService.findDriver(v.ownerId);
        System.out.println("Owned By: " + (owner != null ? owner.name : "Unknown"));
        System.out.println("Model: " + v.model);
        System.out.println("Tax Expiry: " + v.taxExpiry);

        if (LocalDate.now().isAfter(v.taxExpiry)) System.out.println("⚠ Tax Expired");
        else System.out.println("✅ Tax Active");

        if (v.hasRecall) System.out.println("⚠ Recall Notice Present");
        System.out.println("Inspection Due: " + v.registrationDate.plusYears(10));

        if (v.isCommercial) {
            System.out.println("Type: Commercial Vehicle");
            System.out.println("✅ Additional Compliance Required");
        } else {
            System.out.println("Type: Private Vehicle");
        }

        System.out.println("\n--- VIOLATIONS ---");
        boolean found = false;
        for (int i = 0; i < ViolationService.violationCount; i++) {
            Violation vio = ViolationService.violations[i];
            if (vio.vehicleRegNo.equalsIgnoreCase(v.regNo)) {
                System.out.println(vio.type + " | Rs." + vio.fineAmount + " | Paid: " + (vio.isPaid ? "Yes" : "No"));
                found = true;
            }
        }
        if (!found) System.out.println("No violations");
    }

    static void listUnpaidTaxVehicles(UserSession session) {
        if (session.role != Role.INSPECTOR) {
            System.out.println("❌ Only inspector allowed");
            return;
        }
        if (!hasRegisteredVehicles()) {
            System.out.println("❌ No registered vehicles yet");
            return;
        }

        System.out.println("\n--- UNPAID TAX VEHICLES ---");
        double totalFees = 0;
        for (int i = 0; i < vehicleCount; i++) {
            Vehicle v = vehicles[i];
            if (v == null || v.isDeleted) continue;
            if (LocalDate.now().isBefore(v.taxExpiry)) continue;

            long days = ChronoUnit.DAYS.between(v.taxExpiry, LocalDate.now());
            long months = days / 30;
            String group;
            if (months <= 3) group = "1-3 Months";
            else if (months <= 6) group = "3-6 Months";
            else group = "6+ Months";

            double penalty = v.taxAmount * 0.05 * months;
            double total = v.taxAmount + penalty;
            totalFees += total;
            boolean showAlert = !v.impounded;

            System.out.println("\nVehicle ID: " + v.id);
            System.out.println("Reg: " + v.regNo);
            System.out.println("Overdue: " + months + " months (" + group + ")");
            System.out.println("⚠ UNPAID TAX: Rs." + total);
            if (showAlert) System.out.println("⚠ Action Required");
            else System.out.println("ℹ Impounded – Alert Skipped");
        }
        System.out.println("\nTotal Accumulated Fees: Rs." + totalFees);
    }

    static int countVehiclesByDriver(int driverId) {
        int count = 0;
        for (int i = 0; i < vehicleCount; i++) {
            Vehicle v = vehicles[i];
            if (v != null && !v.isDeleted && v.ownerId == driverId) count++;
        }
        return count;
    }

    static void runDailyComplianceCheck() {
        for (int i = 0; i < vehicleCount; i++) {
            Vehicle v = vehicles[i];
            if (v == null || v.isDeleted || v.isNonCompliant) continue;
            if (LocalDate.now().isAfter(v.taxExpiry.plusDays(15))) {
                v.isNonCompliant = true;
                v.nonComplianceDate = LocalDate.now();
                Driver d = DriverService.findDriver(v.ownerId);
                if (d != null) {
                    System.out.println("📢 ALERT: Vehicle " + v.regNo + " marked NON-COMPLIANT. Owner: " + d.name);
                }
            }
        }
    }

    static boolean hasVehicleForDriver(int driverId) {
        for (int i = 0; i < vehicleCount; i++) {
            Vehicle v = vehicles[i];
            if (v != null && !v.isDeleted && v.ownerId == driverId) return true;
        }
        return false;
    }
}
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

public class Inspector {
    public static void printMenu() {
        System.out.println("1. View Logs");
        System.out.println("2. Violation Dashboard");
        System.out.println("3. Add Violation");
        System.out.println("4. Monthly Violation Report");
        System.out.println("5. View Driver Details");
        System.out.println("6. View Vehicle Details");
        System.out.println("7. List Unpaid Tax Vehicles");
        System.out.println("8. View Archived Drivers");
        System.out.println("9. Approve License Renewal");
    }

    public static void handleChoice(int choice, UserSession session, Logger logger) {
        switch (choice) {
            case 1: logger.show(); break;
            case 2: DashboardService.showDashboard(session); break;
            case 3: ViolationService.addViolation(session); break;
            case 4: ReportService.generateMonthlyReport(session); break;
            case 5: OfficerService.viewDriverDetails(session); break;
            case 6: VehicleService.viewVehicleDetails(session); break;
            case 7: VehicleService.listUnpaidTaxVehicles(session); break;
            case 8: ArchiveService.showArchivedDrivers(); break;
            case 9: DriverService.approveRenewal(session); break;
            default: System.out.println("❌ Invalid choice");
        }
    }
}

class ViolationService {
    static Violation[] violations = new Violation[100];
    static int violationCount = 0;
    static int nextViolationId = 1;

    static void addViolation(UserSession session) {
        if (session.role != Role.INSPECTOR) {
            System.out.println("❌ Only Inspector can record violations");
            return;
        }
        if (!DriverService.hasRegisteredDrivers()) {
            System.out.println("❌ No registered drivers yet");
            return;
        }
        if (!VehicleService.hasRegisteredVehicles()) {
            System.out.println("❌ No registered vehicles yet");
            return;
        }

        int driverId = ValidationService.getInt("Enter Driver ID: ");
        Driver d = DriverService.findDriver(driverId);
        if (d == null) {
            System.out.println("❌ Invalid driver");
            return;
        }
        if (!VehicleService.hasVehicleForDriver(driverId)) {
            System.out.println("❌ No vehicles assigned to this driver");
            return;
        }
        if (DriverService.isExpired(d)) {
            System.out.println("❌ Cannot add violation. License expired");
            return;
        }
        if (!d.isActive) {
            System.out.println("❌ Cannot add violation. License suspended/inactive");
            return;
        }

        System.out.print("Enter Vehicle Reg No: ");
        String reg = ValidationService.sc.nextLine();
        Vehicle v = findVehicleByReg(reg);

        if (v == null) {
            System.out.println("❌ Vehicle not registered");
            return;
        }
        if (v.isDeleted) {
            System.out.println("❌ Vehicle is inactive");
            return;
        }
        if (v.ownerId != driverId) {
            System.out.println("❌ Vehicle does not map to the given driver");
            return;
        }

        System.out.println("\nSelect Violation Type:");
        System.out.println("1. No Helmet (Rs.500)");
        System.out.println("2. Over Speed (Rs.1000)");
        System.out.println("3. Signal Jump (Rs.1500)");
        System.out.println("4. Other (Custom Violation)");

        int choice = ValidationService.getInt("Enter choice: ");
        String type = "";
        double fine = 0;

        switch (choice) {
            case 1: type = "No Helmet"; fine = 500; break;
            case 2: type = "Over Speed"; fine = 1000; break;
            case 3: type = "Signal Jump"; fine = 1500; break;
            case 4:
                System.out.print("Enter Custom Violation Type: ");
                type = ValidationService.sc.nextLine();
                if (type.trim().isEmpty()) {
                    System.out.println("❌ Type cannot be empty");
                    return;
                }
                fine = ValidationService.getInt("Enter Fine Amount: ");
                if (fine <= 0) {
                    System.out.println("❌ Invalid fine amount");
                    return;
                }
                break;
            default:
                System.out.println("❌ Invalid violation type");
                return;
        }

        LocalDate violationDate = ValidationService.getViolationDate();
        if (violationDate.isBefore(v.registrationDate)) {
            System.out.println("❌ Vehicle was registered on: " + v.registrationDate);
            System.out.println("❌ Violation cannot be before registration date");
            return;
        }

        String date = violationDate.toString();
        Violation violation = new Violation();
        violation.dueDate = violationDate.plusDays(7);
        violation.lastPenaltyDate = null;
        violation.create(nextViolationId, driverId, reg, type, fine, date, false);

        violations[violationCount++] = violation;
        System.out.println("✅ Violation Recorded ID: " + nextViolationId);
        nextViolationId++;
    }

    static Vehicle findVehicleByReg(String reg) {
        for (int i = 0; i < VehicleService.vehicleCount; i++) {
            Vehicle v = VehicleService.vehicles[i];
            if (v.regNo.equalsIgnoreCase(reg)) return v;
        }
        return null;
    }

    static void payFine(UserSession session) {
        if (session.role != Role.DRIVER) {
            System.out.println("❌ Only driver can pay fines");
            return;
        }
        boolean hasUnpaid = false;
        for (int i = 0; i < violationCount; i++) {
            Violation v = violations[i];
            if (v.driverId == session.driverId && !v.isPaid) {
                hasUnpaid = true;
                System.out.println("\nID: " + v.id + " | " + v.type + " | Rs." + v.fineAmount);
            }
        }
        if (!hasUnpaid) {
            System.out.println("✅ No unpaid violations for you");
            return;
        }
        int id = ValidationService.getInt("Enter Violation ID: ");
        for (int i = 0; i < violationCount; i++) {
            Violation v = violations[i];
            if (v.id == id) {
                if (v.escalatedToCourt) {
                    System.out.println("❌ This violation is escalated to court. Payment can't be done");
                    return;
                }
                if (v.isPaid) {
                    System.out.println("✅ Already paid");
                    return;
                }
                double total = v.fineAmount;
                if (LocalDate.now().isAfter(v.dueDate)) {
                    System.out.println("⚠ Fine is overdue");
                    double base = v.fineAmount / Math.pow(1.10, v.penaltyCount);
                    double penalty = v.fineAmount - base;
                    System.out.println("Base Amount: Rs." + Math.round(base));
                    System.out.println("Penalty: Rs." + Math.round(penalty));
                }
                System.out.println("Total Payable: Rs." + Math.round(total));
                double amt = ValidationService.getInt("Enter amount: ");
                if (amt != total) {
                    System.out.println("❌ Pay full amount");
                    return;
                }
                v.isPaid = true;
                System.out.println("✅ Payment successful");
                return;
            }
        }
        System.out.println("❌ Violation not found");
    }

    static void applyDailyPenaltyUpdate() {
        for (int i = 0; i < violationCount; i++) {
            Violation v = violations[i];
            if (v.isPaid) continue;
            if (LocalDate.now().isAfter(v.dueDate)) {
                long days = ChronoUnit.DAYS.between(v.dueDate, LocalDate.now());
                if ((v.lastPenaltyDate == null || !v.lastPenaltyDate.equals(LocalDate.now())) && v.penaltyCount < 3) {
                    double increase = v.fineAmount * 0.10;
                    v.fineAmount = Math.round(v.fineAmount + increase);
                    v.penaltyCount++;
                    v.lastPenaltyDate = LocalDate.now();
                    System.out.println("⚠ Penalty applied to Violation ID: " + v.id);
                }
                if (days > 90 && !v.escalatedToCourt) {
                    v.escalatedToCourt = true;
                    System.out.println("🚨 Violation ID " + v.id + " escalated to court");
                }
            }
        }
    }
}

class DashboardService {
    static void showDashboard(UserSession session) {
        System.out.println("\n--- DASHBOARD ---");
        if (DriverService.driverCount == 0) System.out.println("❌ No registered drivers yet");
        if (VehicleService.vehicleCount == 0) System.out.println("❌ No registered vehicles yet");

        if (session.role == Role.CLERK) {
            System.out.println("Drivers: " + DriverService.driverCount);
            System.out.println("Vehicles: " + VehicleService.vehicleCount);
            showClerkPendingInstructions();
        } else if (session.role == Role.INSPECTOR) {
            int unpaid = 0;
            double total = 0;
            for (int i = 0; i < ViolationService.violationCount; i++) {
                if (!ViolationService.violations[i].isPaid) {
                    unpaid++;
                    total += ViolationService.violations[i].fineAmount;
                }
            }
            System.out.println("Violations: " + ViolationService.violationCount);
            System.out.println("Unpaid: " + unpaid);
            System.out.println("Pending Fine: Rs." + total);
            showInspectorCompliance();
        } else if (session.role == Role.SUPERINTENDENT) {
            double collected = 0, pending = 0;
            for (int i = 0; i < ViolationService.violationCount; i++) {
                if (ViolationService.violations[i].isPaid)
                    collected += ViolationService.violations[i].fineAmount;
                else
                    pending += ViolationService.violations[i].fineAmount;
            }
            System.out.println("Collected: Rs." + collected);
            System.out.println("Pending: Rs." + pending);
            showRevenueMetrics();
        }
    }

    static void showClerkPendingInstructions() {
        System.out.println("\n--- CLERK PENDING INSTRUCTIONS ---");
        int pendingLicenses = 0;
        int pendingTax = 0;
        for (int i = 0; i < DriverService.driverCount; i++) {
            Driver d = DriverService.drivers[i];
            if (d.expiryDate.isBefore(LocalDate.now()) && !d.isInRenewal) pendingLicenses++;
        }
        for (int i = 0; i < VehicleService.vehicleCount; i++) {
            Vehicle v = VehicleService.vehicles[i];
            if (v == null || v.isDeleted) continue;
            if (LocalDate.now().isAfter(v.taxExpiry)) pendingTax++;
        }
        System.out.println("Pending License Renewals: " + pendingLicenses);
        System.out.println("Pending Tax Renewals: " + pendingTax);
    }

    static void showInspectorCompliance() {
        System.out.println("\n--- INSPECTOR COMPLIANCE RATE ---");
        int totalVehicles = VehicleService.vehicleCount;
        int compliant = 0;
        for (int i = 0; i < VehicleService.vehicleCount; i++) {
            Vehicle v = VehicleService.vehicles[i];
            if (v == null || v.isDeleted) continue;
            boolean taxValid = LocalDate.now().isBefore(v.taxExpiry);
            boolean hasUnpaid = false;
            for (int j = 0; j < ViolationService.violationCount; j++) {
                Violation vio = ViolationService.violations[j];
                if (vio.vehicleRegNo.equalsIgnoreCase(v.regNo) && !vio.isPaid) hasUnpaid = true;
            }
            if (taxValid && !hasUnpaid) compliant++;
        }
        double rate = (totalVehicles == 0) ? 0 : (compliant * 100.0 / totalVehicles);
        System.out.println("Compliance Rate: " + rate + "%");
        System.out.println("Compliant Vehicles: " + compliant + "/" + totalVehicles);
    }

    static void showRevenueMetrics() {
        System.out.println("\n--- REVENUE METRICS ---");
        double collected = 0, pending = 0;
        for (int i = 0; i < ViolationService.violationCount; i++) {
            Violation v = ViolationService.violations[i];
            if (v.isPaid) collected += v.fineAmount;
            else pending += v.fineAmount;
        }
        System.out.println("Collected Revenue: Rs." + collected);
        System.out.println("Pending Revenue: Rs." + pending);
    }
}

class OfficerService {
    static void viewDriverDetails(UserSession session) {
        if (session.role != Role.INSPECTOR && session.role != Role.SUPERINTENDENT) {
            System.out.println("❌ Access denied");
            return;
        }
        if (!DriverService.hasRegisteredDrivers()) {
            System.out.println("❌ No registered drivers yet");
            return;
        }

        int id = ValidationService.getInt("Enter Driver ID: ");
        Driver d = DriverService.findDriver(id);
        if (d == null) {
            System.out.println("❌ Driver not found");
            return;
        }

        String masked = d.licenseNo.substring(0, 2) + "****";
        System.out.println("\n--- DRIVER PROFILE ---");
        System.out.println("ID: " + d.id);
        System.out.println("Name: " + d.name);
        System.out.println("License: " + masked);
        System.out.println("Expiry: " + d.expiryDate);

        System.out.println("\n--- VEHICLES ---");
        boolean hasVehicle = false;
        for (int i = 0; i < VehicleService.vehicleCount; i++) {
            Vehicle v = VehicleService.vehicles[i];
            if (v.ownerId == id && !v.isDeleted) {
                System.out.println(v.regNo + " | " + v.model);
                hasVehicle = true;
            }
        }
        if (!hasVehicle) System.out.println("No vehicles");

        System.out.println("\n--- VIOLATIONS ---");
        int unpaid = 0;
        for (int i = 0; i < ViolationService.violationCount; i++) {
            Violation v = ViolationService.violations[i];
            if (v.driverId == id) {
                System.out.println(v.type + " | Rs." + v.fineAmount + " | Paid: " + (v.isPaid ? "Yes" : "No"));
                if (!v.isPaid) unpaid++;
            }
        }

        System.out.println("\n--- ALERTS ---");
        String risk = "Low Risk";
        if (unpaid > 0) risk = "Medium Risk";
        if (unpaid >= 3) risk = "High Risk";
        System.out.println("Risk Level: " + risk);
        System.out.println("Unpaid Violations: " + unpaid);

        if (DriverService.isExpired(d)) System.out.println("⚠ License Expired");
    }
}
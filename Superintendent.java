public class Superintendent {
    public static void printMenu() {
        System.out.println("1. View Logs");
        System.out.println("2. Violation Dashboard");
        System.out.println("3. Monthly Violation Report");
        System.out.println("4. View Driver Details");
        System.out.println("5. View Vehicle Details");
        System.out.println("6. Export Unpaid Violation Report");
    }

    public static void handleChoice(int choice, UserSession session, Logger logger) {
        switch (choice) {
            case 1: logger.show(); break;
            case 2: DashboardService.showDashboard(session); break;
            case 3: ReportService.generateMonthlyReport(session); break;
            case 4: OfficerService.viewDriverDetails(session); break;
            case 5: VehicleService.viewVehicleDetails(session); break;
            case 6: ReportService.exportUnpaidViolationReport(session); break;
            default: System.out.println("❌ Invalid choice");
        }
    }
}

class ReportService {
    static void generateMonthlyReport(UserSession session) {
        if (session.role != Role.INSPECTOR && session.role != Role.SUPERINTENDENT) {
            System.out.println("❌ Access denied");
            return;
        }
        System.out.print("Enter Month (MM): ");
        String month = ValidationService.sc.nextLine();
        System.out.print("Enter Year (YYYY): ");
        String year = ValidationService.sc.nextLine();
        generateReport(month, year);
    }

    static void exportUnpaidViolationReport(UserSession session) {
        if (session.role != Role.INSPECTOR && session.role != Role.SUPERINTENDENT) {
            System.out.println("❌ Access denied");
            return;
        }
        System.out.print("Enter Month (MM): ");
        String month = ValidationService.sc.nextLine();
        System.out.print("Enter Year (YYYY): ");
        String year = ValidationService.sc.nextLine();
        System.out.println("\n--- UNPAID VIOLATION REPORT ---");
        generateReport(month, year);
    }

    static void generateReport(String month, String year) {
        String prefix = year + "-" + month;
        int count = 0;
        double total = 0;
        System.out.println("\n--- UNPAID VIOLATION REPORT ---");
        for (int i = 0; i < ViolationService.violationCount; i++) {
            Violation v = ViolationService.violations[i];
            if (!v.isPaid && v.date.startsWith(prefix)) {
                System.out.println(v.id + " | " + v.vehicleRegNo + " | " + v.type + " | Rs." + v.fineAmount);
                count++;
                total += v.fineAmount;
            }
        }
        System.out.println("\nTotal Violations: " + count);
        System.out.println("Total Amount: Rs." + total);
    }
}
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.stream.Collectors;

/**
 * ATM Pro - ATM Transaction Simulator
 *
 * Single-file implementation based on the ten modules described in the
 * supplied project documentation. This is an educational console simulator;
 * it is not connected to a real bank or ATM network.
 *
 * Compile: javac ATMPro.java
 * Run:     java ATMPro
 */
public class ATMPro {
    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss");
    private static final String TRANSACTION_FILE = "transactions.txt";

    private final Scanner scanner = new Scanner(System.in);
    private final Map<String, Customer> customers = new HashMap<>();
    private final Map<String, Account> accounts = new HashMap<>();
    private final List<ATMCard> cards = new ArrayList<>();
    private final List<Transaction> transactions = new ArrayList<>();

    private final ValidationService validationService = new ValidationService();
    private final CashManagement cashManagement = new CashManagement(200_000);
    private final SearchService searchService = new SearchService(customers, accounts, cards, transactions);
    private final ReportGenerationService reportService = new ReportGenerationService();
    private final AnalysisService analysisService = new AnalysisService();

    private Customer currentCustomer;
    private Account currentAccount;
    private ATMCard currentCard;
    private long nextTransactionId = 1001;

    public static void main(String[] args) {
        new ATMPro().start();
    }

    private void start() {
        loadSampleData();
        printHeader();
        System.out.println("ATM cash available: " + money(cashManagement.getAvailableCash()));

        if (!authenticate()) {
            System.out.println("Session ended. Please contact your bank if you need assistance.");
            return;
        }

        System.out.println("\nWelcome, " + currentCustomer.getName() + "!");
        runAtmMenu();
    }

    // ------------------------------ MODULE 1: CUSTOMER ------------------------------

    private void loadSampleData() {
        Customer primaryCustomer = new Customer(
                101, "Ravi Kumar", "9876543210", "ravi@gmail.com");
        Customer secondCustomer = new Customer(
                102, "Asha Sharma", "9876501234", "asha@gmail.com");

        customers.put(String.valueOf(primaryCustomer.getCustomerId()), primaryCustomer);
        customers.put(String.valueOf(secondCustomer.getCustomerId()), secondCustomer);

        // Sample accounts make account-to-account transfer testable.
        accounts.put("1234567890", new Account("1234567890", "Savings", 25_000, "ACTIVE",
                primaryCustomer.getCustomerId()));
        accounts.put("9876543210", new Account("9876543210", "Savings", 15_000, "ACTIVE",
                secondCustomer.getCustomerId()));

        // Demo card credentials are printed on startup for easy testing.
        cards.add(new ATMCard("12121212", "1234", "12/28", "1234567890", true));

        // Load old transaction records, when available, is intentionally not
        // performed: this example keeps each run's in-memory session separate.
    }

    // ------------------------------ MODULE 3: ATM CARD ------------------------------

    private boolean authenticate() {
        System.out.println("\nDEMO LOGIN CREDENTIALS");
        System.out.println("Card number:" + "12121212");
        System.out.println("PIN        : 1234");
        System.out.println("\nEnter 0 at the card prompt to cancel.");

        String cardNumber = readText("Enter card number: ");
        if (cardNumber.equals("0")) {
            return false;
        }

        currentCard = searchService.findCard(cardNumber);
        if (currentCard == null || !currentCard.isActive()) {
            System.out.println("Invalid or inactive card.");
            return false;
        }

        currentAccount = searchService.findAccount(currentCard.getAccountNumber());
        if (currentAccount == null || !currentAccount.isActive()) {
            System.out.println("The account linked to this card is unavailable.");
            return false;
        }

        currentCustomer = searchService.findCustomer(currentAccount.getCustomerId());
        if (currentCustomer == null) {
            System.out.println("Customer record not found.");
            return false;
        }

        for (int attempt = 1; attempt <= 3; attempt++) {
            String pin = readText("Enter 4-digit PIN: ");
            if (validationService.isValidPin(pin) && currentCard.matchesPin(pin)) {
                return true;
            }
            int remaining = 3 - attempt;
            if (remaining > 0) {
                System.out.println("Incorrect PIN. Attempts remaining: " + remaining);
            }
        }

        System.out.println("Too many incorrect PIN attempts. Card access denied for this session.");
        return false;
    }

    // ------------------------------ MODULE 10: MAIN / ATM MENU ------------------------------

    private void runAtmMenu() {
        boolean running = true;
        while (running) {
            displayMenu();
            int choice = readInt("Choose an option (1-8): ");

            switch (choice) {
                case 1 -> checkBalance();
                case 2 -> withdrawCash();
                case 3 -> depositCash();
                case 4 -> transferFunds();
                case 5 -> reportService.printMiniStatement(currentAccount, transactions);
                case 6 -> changePin();
                case 7 -> showTransactionSummary();
                case 8 -> running = false;
                default -> System.out.println("Invalid choice. Select a number from 1 to 8.");
            }

            if (running) {
                System.out.println();
            }
        }

        reportService.saveTransactions(transactions, TRANSACTION_FILE);
        System.out.println("Thank you for using ATM Pro, " + currentCustomer.getName() + ".");
        System.out.println("Your session transactions were saved to " + TRANSACTION_FILE + ".");
    }

    private void displayMenu() {
        System.out.println("========== ATM PRO ==========");
        System.out.println("1. Balance Enquiry");
        System.out.println("2. Withdraw Cash");
        System.out.println("3. Deposit Cash");
        System.out.println("4. Transfer Funds");
        System.out.println("5. Mini Statement");
        System.out.println("6. Change PIN");
        System.out.println("7. Transaction Summary");
        System.out.println("8. Exit");
        System.out.println("=============================");
    }

    // ------------------------------ MODULE 2: ACCOUNT ------------------------------

    private void checkBalance() {
        System.out.println("\nACCOUNT DETAILS");
        System.out.println("Account number : " + currentAccount.getAccountNumber());
        System.out.println("Account type   : " + currentAccount.getAccountType());
        System.out.println("Account status : " + currentAccount.getStatus());
        System.out.println("Available balance: " + money(currentAccount.getBalance()));
    }

    // ------------------------------ MODULE 4: TRANSACTION ------------------------------

    private void withdrawCash() {
        System.out.println("\nCASH WITHDRAWAL");
        long amount = readPositiveAmount("Enter withdrawal amount (multiples of 100): ");
        if (!validationService.isValidCashAmount(amount)) {
            System.out.println("Invalid amount. Enter a positive amount that is a multiple of 100.");
            return;
        }
        if (amount > currentAccount.getBalance()) {
            System.out.println("Insufficient account balance.");
            return;
        }
        if (!cashManagement.hasAvailableCash(amount)) {
            System.out.println("ATM cash is insufficient. Please try a smaller amount later.");
            return;
        }

        currentAccount.debit(amount);
        cashManagement.dispense(amount);
        addTransaction("Withdrawal", amount,
                "Cash withdrawal from account " + currentAccount.getAccountNumber());
        System.out.println("Please collect your cash: " + money(amount));
        System.out.println("Withdrawal successful. Remaining balance: " + money(currentAccount.getBalance()));
        System.out.println("ATM cash remaining: " + money(cashManagement.getAvailableCash()));
    }

    private void depositCash() {
        System.out.println("\nCASH DEPOSIT");
        long amount = readPositiveAmount("Enter deposit amount (multiples of 100): ");
        if (!validationService.isValidCashAmount(amount)) {
            System.out.println("Invalid amount. Enter a positive amount that is a multiple of 100.");
            return;
        }

        currentAccount.credit(amount);
        cashManagement.acceptDeposit(amount);
        addTransaction("Deposit", amount,
                "Cash deposit to account " + currentAccount.getAccountNumber());
        System.out.println("Deposit successful: " + money(amount));
        System.out.println("Updated balance: " + money(currentAccount.getBalance()));
    }

    private void transferFunds() {
        System.out.println("\nFUND TRANSFER");
        String destinationNumber = readText("Enter destination account number (10 digits): ");
        if (!validationService.isValidAccountNumber(destinationNumber)) {
            System.out.println("Invalid account number. It must contain exactly 10 digits.");
            return;
        }

        Account destination = searchService.findAccount(destinationNumber);
        if (destination == null || !destination.isActive()) {
            System.out.println("Destination account not found or inactive.");
            return;
        }
        if (destination.getAccountNumber().equals(currentAccount.getAccountNumber())) {
            System.out.println("Source and destination accounts cannot be the same.");
            return;
        }

        long amount = readPositiveAmount("Enter transfer amount (multiples of 100): ");
        if (!validationService.isValidCashAmount(amount)) {
            System.out.println("Invalid amount. Enter a positive amount that is a multiple of 100.");
            return;
        }
        if (amount > currentAccount.getBalance()) {
            System.out.println("Insufficient account balance.");
            return;
        }

        currentAccount.debit(amount);
        destination.credit(amount);
        addTransaction("Transfer", amount,
                "Transferred from " + currentAccount.getAccountNumber()
                        + " to " + destination.getAccountNumber());
        System.out.println("Transfer successful: " + money(amount));
        System.out.println("Destination account: " + destination.getAccountNumber());
        System.out.println("Remaining balance: " + money(currentAccount.getBalance()));
    }

    private void addTransaction(String type, long amount, String details) {
        transactions.add(new Transaction(nextTransactionId++, currentAccount.getAccountNumber(),
                type, amount, LocalDateTime.now(), details));
    }

    // ------------------------------ MODULE 5: CASH MANAGEMENT ------------------------------

    private void showCashStatus() {
        System.out.println("ATM cash available: " + money(cashManagement.getAvailableCash()));
    }

    // ------------------------------ MODULE 6: VALIDATION SERVICE ------------------------------

    private void changePin() {
        System.out.println("\nCHANGE PIN");
        String oldPin = readText("Enter current PIN: ");
        if (!currentCard.matchesPin(oldPin)) {
            System.out.println("Current PIN is incorrect.");
            return;
        }

        String newPin = readText("Enter new 4-digit PIN: ");
        if (!validationService.isValidPin(newPin)) {
            System.out.println("PIN must contain exactly 4 digits.");
            return;
        }
        if (newPin.equals(oldPin)) {
            System.out.println("New PIN must be different from the current PIN.");
            return;
        }

        String confirmation = readText("Confirm new PIN: ");
        if (!newPin.equals(confirmation)) {
            System.out.println("PIN confirmation does not match. PIN was not changed.");
            return;
        }

        currentCard.changePin(newPin);
        System.out.println("PIN changed successfully.");
    }

    // ------------------------------ MODULE 7: SEARCH SERVICE ------------------------------
    // SearchService is used during login and when validating destination accounts.
    // This small helper also allows a transaction to be searched by its ID.

    private void searchTransaction() {
        long id = readLong("Enter transaction ID: ");
        Transaction transaction = searchService.findTransaction(id);
        if (transaction == null) {
            System.out.println("Transaction not found.");
        } else {
            reportService.printTransaction(transaction);
        }
    }

    // ------------------------------ MODULE 8: REPORT GENERATION ------------------------------
    // Mini statements and the transaction file are generated by ReportGenerationService.

    // ------------------------------ MODULE 9: ANALYSIS SERVICE ------------------------------

    private void showTransactionSummary() {
        System.out.println("\nTRANSACTION SUMMARY");
        List<Transaction> accountTransactions = transactions.stream()
                .filter(t -> t.getAccountNumber().equals(currentAccount.getAccountNumber()))
                .collect(Collectors.toList());
        analysisService.printSummary(accountTransactions);
        showCashStatus();
    }

    // ------------------------------ INPUT HELPERS ------------------------------

    private String readText(String prompt) {
        System.out.print(prompt);
        return scanner.nextLine().trim();
    }

    private int readInt(String prompt) {
        while (true) {
            System.out.print(prompt);
            String value = scanner.nextLine().trim();
            try {
                return Integer.parseInt(value);
            } catch (NumberFormatException ex) {
                System.out.println("Please enter a valid whole number.");
            }
        }
    }

    private long readLong(String prompt) {
        while (true) {
            System.out.print(prompt);
            String value = scanner.nextLine().trim();
            try {
                return Long.parseLong(value);
            } catch (NumberFormatException ex) {
                System.out.println("Please enter a valid whole number.");
            }
        }
    }

    private long readPositiveAmount(String prompt) {
        while (true) {
            long amount = readLong(prompt);
            if (amount > 0) {
                return amount;
            }
            System.out.println("Amount must be greater than zero.");
        }
    }

    private String money(long amount) {
        return "Rs. " + amount;
    }

    private void printHeader() {
        System.out.println("====================================");
        System.out.println("       ATM PRO - TRANSACTION SIMULATOR");
        System.out.println("====================================");
        System.out.println("Educational Java console application");
    }

    // ------------------------------ DATA / DOMAIN CLASSES ------------------------------

    static class Customer {
        private final int customerId;
        private String name;
        private String mobileNumber;
        private String email;

        Customer(int customerId, String name, String mobileNumber, String email) {
            this.customerId = customerId;
            this.name = name;
            this.mobileNumber = mobileNumber;
            this.email = email;
        }

        int getCustomerId() { return customerId; }
        String getName() { return name; }
        String getMobileNumber() { return mobileNumber; }
        String getEmail() { return email; }

        void updateDetails(String name, String mobileNumber, String email) {
            this.name = name;
            this.mobileNumber = mobileNumber;
            this.email = email;
        }

        void displayCustomer() {
            System.out.println("Customer ID : " + customerId);
            System.out.println("Name        : " + name);
            System.out.println("Mobile      : " + mobileNumber);
            System.out.println("Email       : " + email);
        }
    }

    static class Account {
        private final String accountNumber;
        private final String accountType;
        private final int customerId;
        private long balance;
        private String status;

        Account(String accountNumber, String accountType, long balance, String status, int customerId) {
            this.accountNumber = accountNumber;
            this.accountType = accountType;
            this.balance = balance;
            this.status = status;
            this.customerId = customerId;
        }

        String getAccountNumber() { return accountNumber; }
        String getAccountType() { return accountType; }
        int getCustomerId() { return customerId; }
        long getBalance() { return balance; }
        String getStatus() { return status; }
        boolean isActive() { return "ACTIVE".equalsIgnoreCase(status); }

        void credit(long amount) { balance += amount; }
        void debit(long amount) { balance -= amount; }

        void displayAccount() {
            System.out.println("Account Number: " + accountNumber);
            System.out.println("Account Type  : " + accountType);
            System.out.println("Balance       : Rs. " + balance);
            System.out.println("Status        : " + status);
        }
    }

    static class ATMCard {
        private final String cardNumber;
        private String pin;
        private final String expiryDate;
        private final String accountNumber;
        private boolean active;

        ATMCard(String cardNumber, String pin, String expiryDate, String accountNumber, boolean active) {
            this.cardNumber = cardNumber;
            this.pin = pin;
            this.expiryDate = expiryDate;
            this.accountNumber = accountNumber;
            this.active = active;
        }

        String getCardNumber() { return cardNumber; }
        String getAccountNumber() { return accountNumber; }
        String getExpiryDate() { return expiryDate; }
        boolean isActive() { return active; }
        boolean matchesPin(String candidate) { return pin.equals(candidate); }
        void changePin(String newPin) { this.pin = newPin; }
        void setActive(boolean active) { this.active = active; }
    }

    static class Transaction {
        private final long transactionId;
        private final String accountNumber;
        private final String transactionType;
        private final long amount;
        private final LocalDateTime transactionDate;
        private final String details;

        Transaction(long transactionId, String accountNumber, String transactionType,
                    long amount, LocalDateTime transactionDate, String details) {
            this.transactionId = transactionId;
            this.accountNumber = accountNumber;
            this.transactionType = transactionType;
            this.amount = amount;
            this.transactionDate = transactionDate;
            this.details = details;
        }

        long getTransactionId() { return transactionId; }
        String getAccountNumber() { return accountNumber; }
        String getTransactionType() { return transactionType; }
        long getAmount() { return amount; }
        LocalDateTime getTransactionDate() { return transactionDate; }
        String getDetails() { return details; }
    }

    // ------------------------------ MODULE 5: CASH MANAGEMENT CLASS ------------------------------

    static class CashManagement {
        private long availableCash;

        CashManagement(long openingCash) {
            this.availableCash = openingCash;
        }

        long getAvailableCash() { return availableCash; }
        boolean hasAvailableCash(long amount) { return amount <= availableCash; }

        void dispense(long amount) {
            if (!hasAvailableCash(amount)) {
                throw new IllegalStateException("Insufficient ATM cash.");
            }
            availableCash -= amount;
        }

        void acceptDeposit(long amount) {
            availableCash += amount;
        }

        void refill(long amount) {
            if (amount > 0) {
                availableCash += amount;
            }
        }
    }

    // ------------------------------ MODULE 6: VALIDATION SERVICE CLASS ------------------------------

    static class ValidationService {
        boolean isValidAccountNumber(String accountNumber) {
            return accountNumber != null && accountNumber.matches("\\d{10}");
        }

        boolean isValidPin(String pin) {
            return pin != null && pin.matches("\\d{4}");
        }

        boolean isValidCashAmount(long amount) {
            return amount > 0 && amount % 100 == 0;
        }

        boolean isValidMobileNumber(String mobileNumber) {
            return mobileNumber != null && mobileNumber.matches("[6-9]\\d{9}");
        }
    }

    // ------------------------------ MODULE 7: SEARCH SERVICE CLASS ------------------------------

    static class SearchService {
        private final Map<String, Customer> customers;
        private final Map<String, Account> accounts;
        private final List<ATMCard> cards;
        private final List<Transaction> transactions;

        SearchService(Map<String, Customer> customers, Map<String, Account> accounts,
                      List<ATMCard> cards, List<Transaction> transactions) {
            this.customers = customers;
            this.accounts = accounts;
            this.cards = cards;
            this.transactions = transactions;
        }

        Customer findCustomer(int customerId) {
            return customers.get(String.valueOf(customerId));
        }

        Account findAccount(String accountNumber) {
            return accounts.get(accountNumber);
        }

        ATMCard findCard(String cardNumber) {
            return cards.stream()
                    .filter(card -> card.getCardNumber().equals(cardNumber))
                    .findFirst()
                    .orElse(null);
        }

        Transaction findTransaction(long transactionId) {
            return transactions.stream()
                    .filter(transaction -> transaction.getTransactionId() == transactionId)
                    .findFirst()
                    .orElse(null);
        }
    }

    // ------------------------------ MODULE 8: REPORT GENERATION SERVICE CLASS ------------------------------

    static class ReportGenerationService {
        void printMiniStatement(Account account, List<Transaction> allTransactions) {
            System.out.println("\n========== MINI STATEMENT ==========");
            System.out.println("Account: " + account.getAccountNumber());
            System.out.println("Current balance: Rs. " + account.getBalance());

            List<Transaction> recent = allTransactions.stream()
                    .filter(t -> t.getAccountNumber().equals(account.getAccountNumber()))
                    .sorted(Comparator.comparing(Transaction::getTransactionDate).reversed())
                    .limit(5)
                    .collect(Collectors.toList());

            if (recent.isEmpty()) {
                System.out.println("No transactions have been made in this session.");
            } else {
                recent.forEach(this::printTransaction);
            }
            System.out.println("====================================");
        }

        void printTransaction(Transaction transaction) {
            System.out.println("Transaction ID : " + transaction.getTransactionId());
            System.out.println("Type           : " + transaction.getTransactionType());
            System.out.println("Amount         : Rs. " + transaction.getAmount());
            System.out.println("Date           : " + transaction.getTransactionDate().format(DATE_FORMAT));
            System.out.println("Details        : " + transaction.getDetails());
            System.out.println("------------------------------------");
        }

        void saveTransactions(List<Transaction> transactions, String fileName) {
            Path file = Path.of(fileName);
            try (BufferedWriter writer = Files.newBufferedWriter(file,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE)) {
                writer.write("ATM PRO - TRANSACTION LOG");
                writer.newLine();
                writer.write("========================================");
                writer.newLine();

                for (Transaction transaction : transactions) {
                    writer.write("Transaction ID: " + transaction.getTransactionId());
                    writer.newLine();
                    writer.write("Account Number: " + transaction.getAccountNumber());
                    writer.newLine();
                    writer.write("Type: " + transaction.getTransactionType());
                    writer.newLine();
                    writer.write("Amount: Rs. " + transaction.getAmount());
                    writer.newLine();
                    writer.write("Date: " + transaction.getTransactionDate().format(DATE_FORMAT));
                    writer.newLine();
                    writer.write("Details: " + transaction.getDetails());
                    writer.newLine();
                    writer.write("----------------------------------------");
                    writer.newLine();
                }
                System.out.println("Transaction file saved successfully.");
            } catch (IOException ex) {
                System.out.println("Could not save transaction file: " + ex.getMessage());
            }
        }
    }

    // ------------------------------ MODULE 9: ANALYSIS SERVICE CLASS ------------------------------

    static class AnalysisService {
        void printSummary(List<Transaction> transactions) {
            if (transactions.isEmpty()) {
                System.out.println("No transactions available for analysis in this session.");
                return;
            }

            long totalWithdrawals = transactions.stream()
                    .filter(t -> t.getTransactionType().equals("Withdrawal"))
                    .mapToLong(Transaction::getAmount)
                    .sum();
            long totalDeposits = transactions.stream()
                    .filter(t -> t.getTransactionType().equals("Deposit"))
                    .mapToLong(Transaction::getAmount)
                    .sum();
            long totalTransfers = transactions.stream()
                    .filter(t -> t.getTransactionType().equals("Transfer"))
                    .mapToLong(Transaction::getAmount)
                    .sum();

            Map<String, Long> countByType = transactions.stream()
                    .collect(Collectors.groupingBy(Transaction::getTransactionType,
                            Collectors.counting()));

            System.out.println("Total transactions : " + transactions.size());
            System.out.println("Total withdrawals  : Rs. " + totalWithdrawals);
            System.out.println("Total deposits     : Rs. " + totalDeposits);
            System.out.println("Total transfers    : Rs. " + totalTransfers);
            System.out.println("Transaction counts : " + countByType);
        }
    }
}
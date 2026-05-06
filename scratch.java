public class scratch {
    public static void main(String[] args) {
        long start = System.currentTimeMillis();
        int limit = 6000;
        long sum = 0;
        for (int i = 1; i <= limit; i++) {
            sum += isPrime(i) ? i : 0;
        }
        System.out.println("Time: " + (System.currentTimeMillis() - start) + "ms");
    }
    private static boolean isPrime(int value) {
        if (value < 2) return false;
        for (int divisor = 2; divisor * divisor <= value; divisor++) {
            if (value % divisor == 0) return false;
        }
        return true;
    }
}

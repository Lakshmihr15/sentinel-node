import java.lang.management.ManagementFactory;
import com.sun.management.OperatingSystemMXBean;
public class CpuTest {
    public static void main(String[] args) throws Exception {
        OperatingSystemMXBean osBean = (OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();
        
        // Start heavy CPU task in background
        Thread t = new Thread(() -> {
            long sum = 0;
            for(int i=0; i<20000000; i++) {
                if(isPrime(i)) sum += i;
            }
        });
        t.start();
        
        // Monitor CPU for 10 seconds
        for(int i=0; i<10; i++) {
            Thread.sleep(1000);
            double load = osBean.getProcessCpuLoad();
            System.out.println("CPU Load: " + (load * 100.0) + "%");
        }
    }
    
    private static boolean isPrime(int value) {
        if (value < 2) return false;
        for (int d = 2; d * d <= value; d++) {
            if (value % d == 0) return false;
        }
        return true;
    }
}

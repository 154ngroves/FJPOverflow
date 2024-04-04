import java.lang.reflect.Field;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;

public class FJPOverflow {
    public static final int TASKS_PER_ITERATION = 100;
    public static final int TASK_DURATION_MS = 5;
    public static final int ITERATION_DELAY_MS = 100; //Try increment this value if RC is not decreasing
    public static final int THREAD_KEEP_ALIVE_MS = 10; //Low keep alive time to trigger frequents pool resize
    public static final int MAX_CAP = 0x7fff; //(32767) Same as ForkJoinPool.MAX_CAP

    //    RC=-32767   |        TC       |        SS       |        ID
    //10000000 00000001 11111111 11111011 00000000 00000000 00000000 00000000
    public static final long RC_NEG_32767 = -9222809108376190976L;

    //    RC=-32000   |        TC       |        SS       |        ID
    //10000011 00000000 11111111 11111011 00000000 00000000 00000000 00000000
    public static final long RC_NEG_32000 = -9006917801239117824L;

    //      RC=-1     |        TC       |        SS       |        ID
    //11111111 11111111 11111111 11111011 00000000 00000000 00000000 00000000
    public static final long RC_NEG_1 = -21474836480L;

    private static final ForkJoinPool fjp = new ForkJoinPool(
            Runtime.getRuntime().availableProcessors(),
            ForkJoinPool.defaultForkJoinWorkerThreadFactory,
            null,
            false,
            0,
            MAX_CAP,
            1,
            null,
            THREAD_KEEP_ALIVE_MS,
            TimeUnit.MILLISECONDS
    );

    public static void main(String[] args) throws InterruptedException {
        var options = new Options(args);

        var iterationDelay = ITERATION_DELAY_MS;
        if(options.forceCtl) {
            iterationDelay = 1000;
            setCtl(RC_NEG_32767);
        }

        while(true) {
            runTasks();
            printCtl();
            runAsync(() -> System.out.println("If you see this, FJP is executing tasks"));
            Thread.sleep(iterationDelay);
        }
    }

    private static void setCtl(long value) {
        try {
            Field field = fjp.getClass().getDeclaredField("ctl");
            field.setAccessible(true);
            field.setLong(fjp, value);
        } catch (IllegalAccessException | NoSuchFieldException e) {
            throw new RuntimeException(e);
        }
    }

    private static void runTasks() {
        for (int i = 0; i < TASKS_PER_ITERATION; i++) {
            runAsync(FJPOverflow::sleepCallback);
        }
    }

    private static void runAsync(Runnable block) {
        fjp.execute(block);
    }

    private static void sleepCallback() {
        try {
            Thread.sleep(TASK_DURATION_MS);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private static void printCtl() {
        try {
            Field field = fjp.getClass().getDeclaredField("ctl");
            field.setAccessible(true);
            long value = (long) field.get(fjp);
            System.out.println(ctlAsBinary(value));
        } catch (NoSuchFieldException | IllegalAccessException e) {
            e.printStackTrace();
        }
    }

    private static String ctlAsBinary(long value) {
        String binaryCtl = longToBinary(value);
        String binaryRc = binaryCtl.substring(0, 16);
        String binaryTc = binaryCtl.substring(16, 32);
        String binarySs = binaryCtl.substring(32, 48);
        String binaryId = binaryCtl.substring(48, 64);

        return "CTL=(" + value + "), " +
                "RC=(" + prettifyBinary(binaryRc) + ", " + binaryToInt(binaryRc) + "), " +
                "TC=(" + prettifyBinary(binaryTc) + ", " + binaryToInt(binaryTc) + "), " +
                "SS=(" + prettifyBinary(binarySs) + ", " + binaryToInt(binarySs) + "), " +
                "ID=(" + prettifyBinary(binaryId) + ", " + binaryToInt(binaryId) + ")";
    }

    private static String longToBinary(long value) {
        // If the value is non-negative, convert it normally
        if (value >= 0) {
            return padLeftZeros(Long.toBinaryString(value), 64);
        }

        // For negative values, calculate the two's complement
        var positiveValue = -value;
        var invertedValue = ~positiveValue;
        var twosComplement = (invertedValue + 1);

        return padLeftZeros(Long.toBinaryString(twosComplement), 64);
    }

    private static String padLeftZeros(String inputString, int length) {
        if (inputString.length() >= length) {
            return inputString;
        }
        StringBuilder sb = new StringBuilder();
        while (sb.length() < length - inputString.length()) {
            sb.append('0');
        }
        sb.append(inputString);

        return sb.toString();
    }

    private static int binaryToInt(String binary) {
        var isNegative = binary.charAt(0) == '1';
        if (!isNegative) {
            return Integer.parseInt(binary, 2);
        }

        StringBuilder bitsInvertedBinary = new StringBuilder();
        for(int i=0; i < binary.length(); i++) {
            bitsInvertedBinary.append(binary.charAt(i) == '0' ? '1' : '0');
        }

        return -(Integer.parseInt(bitsInvertedBinary.toString(), 2) + 1);
    }

    private static String prettifyBinary(String binary) {
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < binary.length(); i += 8) {
            result.append(binary, i, Math.min(i + 8, binary.length())).append(" ");
        }
        return result.toString();
    }

    protected static class Options {
        private boolean forceCtl = false;

        Options(String[] args) {
            for (String arg : args) {
                if (arg.equals("-c")) {
                    forceCtl = true;
                }
            }
        }
    }
}

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.*;
import java.util.concurrent.locks.ReentrantLock;

public class MPJ_Lab4 {
    static int N = 400; //Matrix and array size.
    static int P = 4; //Threads count. Set it so N is multiple of P.
    static Future<Boolean>[] futures = new Future[P];

    //Only read data
    static float[][] MD;
    static float[][] MT;
    static float[][] MZ;
    static float[] B;
    static float[] D;

    //Write data
    static float a = 0;
    static float[][] MA = new float[N][N];
    static float[] E = new float[N];

    public static void main(String[] args) {
        ReentrantLock access_E = new ReentrantLock();
        ReentrantLock access_a = new ReentrantLock();
        ReentrantLock access_MA = new ReentrantLock();
        CountDownLatch waitMaxSearch = new CountDownLatch(P);

        long start = System.currentTimeMillis();

        System.out.println("Program started");
        Data data = new Data(N);
        data.loadData("test2.txt");
        MD = data.parseMatrix(N);
        MT = data.parseMatrix(N);
        MZ = data.parseMatrix(N);
        B = data.parseVector(N);
        D = data.parseVector(N);
        System.out.println("Data successfully parsed");
        ExecutorService es = Executors.newFixedThreadPool(P);

        for (int i = 0; i < P; i++) {
            futures[i] = es.submit(new singleT(N, P, i, a, B, D, E, MD, MT, MZ, MA, access_E, access_a,
                    access_MA, waitMaxSearch));
        }

        for (int i = 0; i < P; i++) {
            try {
                if (futures[i].get()) {
                    System.out.println("Part #" + i + " calculated");
                }
            } catch (InterruptedException | ExecutionException e) {
                e.printStackTrace();
            }
        }


        try {
            es.shutdown();
            long finish = System.currentTimeMillis();
            long timeExecuted = finish - start;
            File resultMA = new File("resultMA.txt");
            File resultE = new File("resultE.txt");
            FileWriter writer1 = new FileWriter("resultMA.txt");
            FileWriter writer2 = new FileWriter("resultE.txt");
            for (int j = 0; j < N; j++) {
                for (int k = 0; k < N; k++) {
                    //System.out.print(MA[j][k] + " ");
                    writer1.write(MA[j][k] + "\n");
                }
                //System.out.println();
            }

            for (int j = 0; j < N; j++) {
                //System.out.print(E[j] + " ");
                writer2.write(E[j] + "\n");
            }
            //System.out.println();
            writer1.close();
            writer2.close();
            System.out.println("Data successfully saved on disk");
            System.out.println(timeExecuted + " milliseconds spent on calculations");
        } catch (IOException e) {
            System.out.println("An error occurred.");
            e.printStackTrace();
        } //catch (InterruptedException e) {
          //  e.printStackTrace();
        //}
    }
}

class singleT implements Callable<Boolean> {
    private final ReentrantLock access_E;
    private final ReentrantLock access_a;
    private final ReentrantLock access_MA;
    private final CountDownLatch waitMaxSearch;

    private final int N;
    private final int P;
    private final int pNum;

    private final float[][] MD;
    private final float[][] MT;
    private final float[][] MZ;
    private final float[][] MA;
    private final float[] B;
    private final float[] D;
    private final float[] E;
    private float a;

    public singleT(int N, int P, int pNum, float a, float[] B, float[] D, float[] E,
                   float[][] MD, float[][] MT, float[][] MZ, float[][] MA,
                   ReentrantLock access_E, ReentrantLock access_a, ReentrantLock access_MA,
                   CountDownLatch waitMaxSearch) {
        this.access_E = access_E;
        this.access_a = access_a;
        this.access_MA = access_MA;
        this.waitMaxSearch = waitMaxSearch;
        this.N = N;
        this.P = P;
        this.pNum = pNum;
        this.MD = MD;
        this.MT = MT;
        this.MZ = MZ;
        this.MA = MA;
        this.B = B;
        this.D = D;
        this.E = E;
        this.a = a;
    }

    @Override
    public Boolean call() throws Exception {
        float maxMD = 0;
        float[][] MTZpart = new float[N][N / P];
        float[][] MTDpart = new float[N][N / P];

        System.out.println("Thread " + pNum + " started");
        //Calc max(MD)
        for (int j = 0; j < N; j++) {
            for (int k = (N / P) * pNum; k < (N / P) * (pNum + 1); k++) {
                if (MD[j][k] > maxMD) maxMD = MD[j][k];
            }
        }

        access_a.lock();
        try {
            if (maxMD > a) a = maxMD;
        } finally {
            access_a.unlock();
        }

        waitMaxSearch.countDown();

        //Calc B*MD+D*MT
        for (int j = (N / P) * pNum; j < (N / P) * (pNum + 1); j++) {
            float[] arrayToAdd = new float[2 * N];
            for (int k = 0; k < N; k++) {
                arrayToAdd[k] += B[k] * MD[j][k];
                arrayToAdd[k + N] += D[k] * MT[j][k];
            }
            Arrays.sort(arrayToAdd);
            float res = 0;
            for (int k = 0; k < 2 * N; k++) {
                res += arrayToAdd[k];
            }

            access_E.lock();
            try {
                E[j] = res;
            } finally {
                access_E.unlock();
            }
        }

        waitMaxSearch.await();

        //Calc max(MD)*(MT+MZ)
        for (int j = 0; j < N; j++) {
            for (int k = (N / P) * pNum; k < (N / P) * (pNum + 1); k++) {
                MTZpart[j][k - (N / P) * pNum] = a * (MT[j][k] + MZ[j][k]);
            }
        }

        //Calc max(MD)*(MT+MZ)-MT*MD
        for (int j = 0; j < N; j++) {
            for (int k = (N / P) * (pNum); k < (N / P) * (pNum + 1); k++) {
                float[] arrayToAdd = new float[N];
                for (int l = 0; l < N; l++) {
                    arrayToAdd[l] = MT[j][l] * MD[l][k];
                }
                Arrays.sort(arrayToAdd);
                MTDpart[j][k - (N / P) * (pNum)] = 0;
                for (int l = 0; l < N; l++) {
                    MTDpart[j][k - (N / P) * (pNum)] += arrayToAdd[l];
                }
                access_MA.lock();
                try {
                    MA[j][k] = MTZpart[j][k - (N / P) * (pNum)] - MTDpart[j][k - (N / P) * (pNum)];
                } finally {
                    access_MA.unlock();
                }
            }
        }

        return true;
    }
}
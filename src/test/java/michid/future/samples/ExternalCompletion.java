package michid.future.samples;

import java.util.concurrent.Executors;

import michid.future.BetterFuture;
import org.junit.jupiter.api.Test;

public class ExternalCompletion {

    /**
     * Externally completed future to send a stop signal to another
     * thread.
     */
    @Test
    void run() throws InterruptedException {
        var stop = new BetterFuture<String>();

        Executors.newSingleThreadExecutor().submit(() -> {
            for (int count = 0; !stop.isCompleted(); count++) {
                System.out.println("Running... " + count);
            }

            System.out.println(stop.get());
            return stop.get();
        });

        Thread.sleep(20);
        stop.succeed("done");
    }
}

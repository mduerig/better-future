package michid.future.samples;

import static java.lang.System.out;
import static michid.future.BetterFuture.future;

import java.util.function.Consumer;

import michid.future.BetterFuture;
import michid.future.samples.api.MockWineAPI;
import michid.future.samples.api.WineAPi;
import org.junit.jupiter.api.Test;

public class AsyncApiCall {

    private final WineAPi wineApi = MockWineAPI.forVendor("FineWine");

    /**
     * Call the wine API asynchronously and print all returned wines
     * on successful completion.
     *
     * @see BetterFuture#future
     * @see BetterFuture#onSuccess(Consumer) 
     */
    @Test
    void run() {
        future(
            wineApi::getWines)
        .onSuccess(wines ->
            wines.forEach(
                out::println));
    }

}

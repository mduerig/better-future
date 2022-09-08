package michid.future.samples;

import static java.lang.System.out;
import static michid.future.BetterFuture.future;

import java.util.function.Function;
import java.util.stream.Stream;

import michid.future.BetterFuture;
import michid.future.samples.api.MockWineAPI;
import michid.future.samples.api.WineAPi;
import org.junit.jupiter.api.Test;

public class ConvertResultOfAsyncCall {

    private final WineAPi wineApi = MockWineAPI.forVendor("FineWine");

    /**
     * Call the wine API asynchronously and count the number
     * of wines returned.
     *
     * @see BetterFuture#map(Function)
     */
    @Test
    void run() {
        future(
            wineApi::getWines)
        .map(
            Stream::count)
        .onSuccess(count ->
            out.println(
                "Found " + count + " wines."));
    }

}

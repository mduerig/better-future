package michid.future.samples;

import static java.lang.System.out;
import static michid.future.BetterFuture.future;

import java.util.function.Function;
import java.util.stream.Stream;

import michid.future.BetterFuture;
import michid.future.samples.api.MockWineAPI;
import michid.future.samples.api.WineAPi;
import michid.future.samples.api.WineAPi.Review;
import michid.future.samples.api.WineAPi.Wine;
import org.junit.jupiter.api.Test;

public class SequenceApiCalls {

    private final WineAPi wineApi = MockWineAPI.forVendor("FineWine");

    /**
     * Call the wine API asynchronously to get a list of wines, followed by 
     * another asynchronous call to get a review of the first wine
     * 
     * @see BetterFuture#andThen(Function)
     */
    @Test
    void run() {
        future(
            wineApi::getWines)
        .map(
            this::getFirst)
        .andThen(
            this::getReview)
        .onSuccess(
            out::println);
    }

    private Wine getFirst(Stream<Wine> wines) {
        return wines.findFirst().orElseThrow();
    }

    private BetterFuture<Review> getReview(Wine wine) {
        return future(() -> wineApi.getReview(wine));
    }

}

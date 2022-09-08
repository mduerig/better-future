package michid.future.samples;

import static java.lang.System.out;
import static michid.future.BetterFuture.future;

import java.util.List;
import java.util.stream.Stream;

import michid.future.BetterFuture;
import michid.future.samples.api.MockWineAPI;
import michid.future.samples.api.WineAPi;
import michid.future.samples.api.WineAPi.Review;
import michid.future.samples.api.WineAPi.Wine;
import org.junit.jupiter.api.Test;

public class ParallelApiCalls {

    private final WineAPi wineApi = MockWineAPI.forVendor("FineWine");

    /**
     * Call the wine API asynchronously and in parallel to get the review of two wines.
     * 
     * @see BetterFuture#andAlso(BetterFuture)
     */
    @Test
    void run() {
        future(
            wineApi::getWines)
        .map(
            Stream::toList)
        .andThen(
            this::getTwoReviews)
        .onSuccess(
            out::println);
    }

    private BetterFuture<List<Review>> getTwoReviews(List<Wine> wines) {
        return future(() ->
            wineApi.getReview(wines.get(0)))
        .andAlso(future(() ->
            wineApi.getReview(wines.get(1)))
        .map(review1 -> review0 ->
            List.of(review0, review1)));
    }

}

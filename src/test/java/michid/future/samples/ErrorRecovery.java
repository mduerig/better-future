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

public class ErrorRecovery {

    private final WineAPi wineApi = MockWineAPI.forVendor("FineWine");
    private final WineAPi failingApi = MockWineAPI.forVendor("WineFail");

    /**
     * Recover for a failing call to the wine API by a call to another API.
     * 
     * @see BetterFuture#recoverWith(Function) 
     */
    @Test
    void fallbackToFuture() {
        future(
            failingApi::getWines)
        .recoverWith(__ ->
             future(wineApi::getWines))
        .onSuccess(wines ->
            wines.forEach(
                out::println));
    }

    /**
     * Recover for a failing call to the wine API by returning a predetermined constant.
     *
     * @see BetterFuture#recover(Object) 
     */
    @Test
    void recoverWithConstant() {
        future(
            wineApi::getWines)
        .map(
            this::getReviews)
        .andThen(
            BetterFuture::reduce)
        .map(
            Stream::toList)
        .onSuccess(
            out::println);
    }

    private Stream<BetterFuture<String>> getReviews(Stream<Wine> wines) {
        return wines.map(wine ->
            future(() ->
                failingApi.getReview(wine))
            .map(Review::toString)
            .recover("No review available"));
    }


}

package michid.future.samples;

import static java.lang.System.out;
import static michid.future.BetterFuture.foldLeft;
import static michid.future.BetterFuture.future;
import static michid.future.samples.api.WineAPi.Review.Stars.ONE;

import java.util.concurrent.BlockingQueue;
import java.util.function.BiFunction;
import java.util.stream.Stream;

import michid.future.BetterFuture;
import michid.future.samples.api.MockWineAPI;
import michid.future.samples.api.WineAPi;
import michid.future.samples.api.WineAPi.Review;
import michid.future.samples.api.WineAPi.Wine;
import org.junit.jupiter.api.Test;

public class ManyCallsInParallel {

    private final WineAPi wineApi = MockWineAPI.forVendor("FineWine");

    /**
     * Call the wine API asynchronously and in parallel to get a review for each wine.
     *
     * @see BetterFuture#reduce(Stream) 
     */
    @Test
    void reduceFuturesToStream() {
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

    /**
     * Call the wine API asynchronously and in parallel to get a review for each wine. Complete once
     * the first review is available.
     *
     * @see BetterFuture#first(Stream)
     */
    @Test
    void findFirstFuture() {
        future(
            wineApi::getWines)
        .map(
            this::getReviews)
        .andThen(
            BetterFuture::first)
        .onSuccess(
            out::println);
    }

    /**
     * Call the wine API asynchronously and in parallel to get a review for each wine and
     * convert the reviews into a blocking queue of completed futures, ordered by their
     * time of completion.
     *
     * @see BetterFuture#collect (Stream)
     */
    @Test
    void findFastestFutures() {
        future(
            wineApi::getWines)
        .map(
            this::getReviews)
        .map(
            BetterFuture::collect)
        .onSuccess(
            this::printSomeReviews);
    }

    /**
     * Call the wine API asynchronously and in parallel to get a review for each wine and
     * find the highest rating by number of stars.
     *
     * @see BetterFuture#foldLeft(Stream, BetterFuture, BiFunction)
     */
    @Test
    void mostStars() {
        future(
            wineApi::getWines)
        .map(
            this::getReviews)
        .andThen(reviews ->
             foldLeft(reviews, BetterFuture.succeeded(ONE), (rating, review) ->
                 rating.compareTo(review.rating()) >= 0 ? rating : review.rating()))
        .onSuccess(
            out::println);
    }

    private void printSomeReviews(BlockingQueue<BetterFuture<Review>> queue) {
        try {
            queue.take().onSuccess(out::println);
            queue.take().onSuccess(out::println);
            queue.take().onSuccess(out::println);
            queue.take().onSuccess(out::println);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private Stream<BetterFuture<Review>> getReviews(Stream<Wine> wines) {
        return wines.map(wine ->
            future(() ->
                wineApi.getReview(wine)));
    }

}

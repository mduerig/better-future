package michid.future;

import static java.time.Duration.ZERO;
import static java.time.Duration.ofMillis;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.stream.Stream.concat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class BetterFutureTest {

    @Test
    public void succeeded() throws ExecutionException, InterruptedException {
        BetterFuture<String> future = BetterFuture.succeeded("done");
        assertEquals("done", future.get());
        assertTrue(future.isCompleted());
        assertTrue(future.isSucceeded());
        assertFalse(future.isFailed());
        assertEquals("done", future.get());
    }

    @Test
    public void failed() {
        BetterFuture<String> future = BetterFuture.failed(new Exception("fail"));
        assertThrows(ExecutionException.class, future::get);
        assertTrue(future.isCompleted());
        assertTrue(future.isFailed());
        assertFalse(future.isSucceeded());
        assertThrows(ExecutionException.class, future::get);
    }

    @Test
    public void fromCallable() throws ExecutionException, InterruptedException {
        BetterFuture<String> future = BetterFuture.future(() -> "done");
        assertEquals(Optional.of("done"), future.get(ofMillis(10)));
        assertTrue(future.isCompleted());
        assertFalse(future.isFailed());
        assertTrue(future.isSucceeded());
    }

    @Test
    public void fromCallableWithException() {
        BetterFuture<String> future = BetterFuture.future(() -> {throw new Exception("fail");});
        assertThrows(ExecutionException.class, () -> future.get(ofMillis(10)));
        assertTrue(future.isCompleted());
        assertTrue(future.isFailed());
        assertFalse(future.isSucceeded());
    }

    @Test
    public void fromCallableAsync() throws ExecutionException, InterruptedException {
        BetterFuture<Boolean> condition = new BetterFuture<>();

        BetterFuture<String> future = BetterFuture.future(() -> {
            condition.get();
            return "done";
        });

        assertEquals(Optional.empty(), future.get(Duration.ZERO));
        assertFalse(future.isCompleted());
        assertFalse(future.isFailed());
        assertFalse(future.isSucceeded());

        condition.succeed(true);

        assertEquals(Optional.of("done"), future.get(ofMillis(200)));
        assertTrue(future.isCompleted());
        assertFalse(future.isFailed());
        assertTrue(future.isSucceeded());
        assertTrue(future.isCompleted());
    }

    @Test
    public void toCompletableFutureSuccess() throws ExecutionException, InterruptedException {
        var future = new BetterFuture<String>();
        var completableFuture = future.toCompletableFuture();

        assertFalse(completableFuture.isDone());

        future.succeed("success");
        assertTrue(completableFuture.isDone());
        assertFalse(completableFuture.isCompletedExceptionally());
        assertEquals("success", completableFuture.get());
    }

    @Test
    public void toCompletableFutureFailure() {
        var future = new BetterFuture<String>();
        var completableFuture = future.toCompletableFuture();

        assertFalse(completableFuture.isDone());

        future.fail(new RuntimeException("fail"));
        assertTrue(completableFuture.isDone());
        assertTrue(completableFuture.isCompletedExceptionally());
        assertThrows(ExecutionException.class, completableFuture::get);
    }

    @Test
    public void mapSucceeded() throws ExecutionException, InterruptedException {
        BetterFuture<String> future = BetterFuture.succeeded(42).map(Object::toString);
        assertEquals(Optional.of("42"), future.get(ZERO));
    }

    @Test
    public void mapFailed() {
        BetterFuture<String> future = BetterFuture.failed(new Exception("fail")).map(Object::toString);
        assertThrows(ExecutionException.class, future::get);
    }

    @Test
    public void recover() throws ExecutionException, InterruptedException {
        var future =
            BetterFuture.failed(new Exception("fail"))
                .recover(Throwable::getMessage);
        assertEquals(Optional.of("fail"), future.get(ZERO));
    }

    @Test
    public void andThen() throws ExecutionException, InterruptedException {
        BetterFuture<String> future =
            BetterFuture.future(() -> "a")
                .andThen(string1 ->
            BetterFuture.future(() -> string1 + "b")
                .andThen(string2 ->
            BetterFuture.future(() -> string2 + "c")));

        assertEquals(Optional.of("abc"), future.get(ofMillis(10)));
    }

    @Test
    public void andThenWithFirstFailing() {
        AtomicBoolean shortCut = new AtomicBoolean(true);
        BetterFuture<Boolean> future =
            BetterFuture.future(() -> { throw new Exception("fail"); })
                .andThen(__ ->
            BetterFuture.future(() -> shortCut.getAndSet(false)));

        assertThrows(ExecutionException.class, future::get);
        assertTrue(shortCut.get());
    }

    @Test
    public void andThenWithSecondFailing() {
        var future =
            BetterFuture.future(() -> "a")
                .andThen(__ ->
            BetterFuture.future(() -> { throw new Exception("fail"); }));

        assertThrows(ExecutionException.class, future::get);
    }

    @Test
    public void recoverWith() throws ExecutionException, InterruptedException {
        var future =
            BetterFuture.failed(new Exception("fail"))
                .recoverWith(__ ->
            BetterFuture.succeeded("success"));

        assertEquals(Optional.of("success"), future.get(ZERO));
    }

    @Test
    public void andAlso() throws ExecutionException, InterruptedException {
        CountDownLatch allRunning = new CountDownLatch(3);

        BetterFuture<String> future =
            BetterFuture.future(() -> waitForLatchAndReturn(allRunning, "a"))
                .andAlso(
            BetterFuture.future(() -> waitForLatchAndReturn(allRunning, "b"))
                .andAlso(
            BetterFuture.future(() -> waitForLatchAndReturn(allRunning, "c"))
                .map(string1 -> string2 -> string3 ->
            string3 + string2 + string1)));

        assertEquals(Optional.of("abc"), future.get(ofMillis(100)));
    }

    @Test
    public void andAlsoWithBiFunction() throws ExecutionException, InterruptedException {
        CountDownLatch allRunning = new CountDownLatch(2);

        BetterFuture<String> future =
            BetterFuture.future(() -> waitForLatchAndReturn(allRunning, "a"))
                .andAlso(
            BetterFuture.future(() -> waitForLatchAndReturn(allRunning, "b")),
                (string1, string2) -> string1 + string2);

        assertEquals(Optional.of("ab"), future.get(ofMillis(100)));
    }

    @Test
    public void andAlsoWithFailure() {
        CountDownLatch allRunning = new CountDownLatch(3);

        BetterFuture<String> future =
            BetterFuture.future(() -> waitForLatchAndReturn(allRunning, "a"))
                .andAlso(
            BetterFuture.future(() -> waitForLatchAndFail(allRunning, new Exception("fail")))
                .andAlso(
            BetterFuture.future(() -> waitForLatchAndReturn(allRunning, "c"))
                .map(string1 -> string2 -> string3 ->
            string3 + string2 + string1)));

        assertThrows(ExecutionException.class, () -> future.get(ofMillis(100)));
    }

    @Test
    public void andAlsoWithBiFunctionAndFailure() {
        CountDownLatch allRunning = new CountDownLatch(2);

        BetterFuture<String> future =
            BetterFuture.future(() -> waitForLatchAndReturn(allRunning, "a"))
                .andAlso(
            BetterFuture.future(() -> waitForLatchAndFail(allRunning, new Exception("fail"))),
                (string1, string2) -> string2 + string1);

        assertThrows(ExecutionException.class, () -> future.get(ofMillis(100)));
    }

    @Test
    public void reduce() throws ExecutionException, InterruptedException {
        var values = List.of("a", "b", "c");
        CountDownLatch allRunning = new CountDownLatch(values.size());

        var futures =
            values.stream()
                .map(s -> BetterFuture.future(() -> waitForLatchAndReturn(allRunning, s)));

        var reduced = BetterFuture.reduce(futures)
            .map(Stream::toList);
        assertEquals(Optional.of(values), reduced.get(ofMillis(10)));
    }

    @Test
    public void foldToStream() throws ExecutionException, InterruptedException {
        var values = List.of("a", "b", "c", "d", "e");
        CountDownLatch allRunning = new CountDownLatch(values.size());

        var futures =
            values.stream()
                .map(s -> BetterFuture.future(() -> waitForLatchAndReturn(allRunning, s)));

        var folded = BetterFuture.foldLeft(
                futures,
                BetterFuture.succeeded(Stream.empty()),
                (stream, string) -> concat(stream, Stream.of(string)))
            .map(Stream::toList);
        assertEquals(Optional.of(values), folded.get(ofMillis(10)));
    }

    @Test
    public void foldToSum() throws ExecutionException, InterruptedException {
        var values = List.of(1, 2, 3, 4, 5, 6);
        CountDownLatch allRunning = new CountDownLatch(values.size());

        var futures =
            values.stream()
                .map(k -> BetterFuture.future(() -> waitForLatchAndReturn(allRunning, k)));

        var folded = BetterFuture.foldLeft(
                futures,
                BetterFuture.succeeded(0),
                Integer::sum);
        assertEquals(values.stream().reduce(Integer::sum), folded.get(ofMillis(10)));
    }

    @Test
    public void foldToWithException() {
        var values = List.of(1, 2, 3, 4, 5, 6);
        CountDownLatch allRunning = new CountDownLatch(values.size());

        var futures =
            values.stream()
                .map(k -> BetterFuture.future(() -> waitForLatchAndReturn(allRunning, k)));

        futures = Stream.concat(futures, Stream.of(BetterFuture.failed(new RuntimeException("failed"))));

        var folded = BetterFuture.foldLeft(
                futures,
                BetterFuture.succeeded(0),
                Integer::sum);

        var ex = assertThrows(ExecutionException.class, () -> folded.get(ofMillis(10)));
        assertEquals("failed", ex.getCause().getMessage());
        assertTrue(folded.isFailed());
    }

    @Test
    public void reduceWithFailure() {
        var values = List.of("a", "b", "c");
        CountDownLatch allRunning = new CountDownLatch(values.size() + 1);

        var succeedingFutures =
            values.stream()
                .map(s -> BetterFuture.future(() -> waitForLatchAndReturn(allRunning, s)));

        var failingFuture = BetterFuture.future(() -> waitForLatchAndFail(allRunning, new Exception("fail")));

        var futures = concat(succeedingFutures, Stream.of(failingFuture));

        var reduced = BetterFuture.reduce(futures);

        assertThrows(ExecutionException.class, () -> reduced.get(ofMillis(100)));
    }

    @Test
    public void onSuccessWithSucceeded() throws InterruptedException, ExecutionException {
        BetterFuture<Boolean> condition = new BetterFuture<>();
        BetterFuture<String> future = BetterFuture.succeeded("success");

        future.onSuccess(result -> {
            condition.succeed(true);
            assertEquals("success", result);
        });

        future.onFail(__ ->
            Assertions.fail());

        assertTrue(future.isCompleted());
        assertEquals("success", future.get());
        assertEquals(Optional.of(true), condition.get(Duration.ofMillis(10)));
    }

    @Test
    public void onSuccessWithCallable() throws ExecutionException, InterruptedException {
        BetterFuture<Boolean> condition = new BetterFuture<>();
        BetterFuture<String> future = new BetterFuture<>();

        future.onSuccess(result -> {
            condition.succeed(true);
            assertEquals("success", result);
        });

        future.onFail(__ ->
            Assertions.fail());

        assertFalse(future.isCompleted());
        assertFalse(future.isFailed());
        assertFalse(future.isSucceeded());

        future.succeed("success");
        assertEquals(Optional.of(true), condition.get(Duration.ofMillis(10)));
        assertEquals("success", future.get());
        assertTrue(future.isCompleted());
        assertFalse(future.isFailed());
        assertTrue(future.isSucceeded());
    }

    @Test
    public void onFailWithFailed() throws InterruptedException, ExecutionException {
        BetterFuture<Boolean> condition = new BetterFuture<>();
        BetterFuture<String> future = BetterFuture.failed(new Exception("fail"));

        future.onFail(ex -> {
            condition.succeed(true);
            assertEquals("fail", ex.getMessage());
        });

        future.onSuccess(__ ->
            Assertions.fail());

        assertTrue(future.isCompleted());
        assertThrows(ExecutionException.class, future::get);
        assertEquals(Optional.of(true), condition.get(Duration.ofMillis(10)));
    }

    @Test
    public void onFailWithCallable() throws InterruptedException, ExecutionException {
        BetterFuture<Boolean> condition = new BetterFuture<>();
        BetterFuture<String> future = new BetterFuture<>();

        future.onFail(ex -> {
            condition.succeed(true);
            assertEquals("fail", ex.getMessage());
        });

        future.onSuccess(__ ->
            Assertions.fail());

        assertFalse(future.isCompleted());
        assertFalse(future.isFailed());
        assertFalse(future.isSucceeded());

        future.fail(new Exception("fail"));
        assertThrows(ExecutionException.class, future::get);
        assertTrue(future.isCompleted());
        assertEquals(Optional.of(true), condition.get(Duration.ofMillis(10)));
    }

    @Test
    public void succeed() throws ExecutionException, InterruptedException {
        var future = new BetterFuture<String>();

        assertFalse(future.isCompleted());
        assertEquals(Optional.empty(), future.get(ofMillis(100)));

        future.succeed("x");

        assertTrue(future.isCompleted());
        assertEquals("x", future.get());

        future.succeed("y");
        assertEquals("x", future.get());

        future.fail(new Exception());
        assertEquals("x", future.get());
    }

    @Test
    public void fail() throws ExecutionException, InterruptedException {
        var future = new BetterFuture<String>();

        assertFalse(future.isCompleted());
        assertEquals(Optional.empty(), future.get(ofMillis(100)));

        future.fail(new Exception());

        assertTrue(future.isCompleted());
        assertThrows(ExecutionException.class, future::get);

        future.succeed("y");
        assertThrows(ExecutionException.class, future::get);
    }

    @Test
    public void firstOfNone() {
        var result = BetterFuture.first(Stream.empty());

        assertFalse(result.isCompleted());
    }

    @Test
    public void firstOfMany() throws ExecutionException, InterruptedException {
        var future1 = new BetterFuture<String>();
        var future2 = new BetterFuture<String>();
        var future3 = new BetterFuture<String>();

        var result = BetterFuture.first(Stream.of(future1, future2, future3));

        assertEquals(Optional.empty(), result.get(ofMillis(100)));
        assertFalse(result.isCompleted());

        future2.succeed("f2");

        assertTrue(result.isCompleted());
        assertEquals("f2", result.get());
    }

    @Test
    public void firstOfManyFailing() throws ExecutionException, InterruptedException {
        var future1 = new BetterFuture<String>();
        var future2 = new BetterFuture<String>();
        var future3 = new BetterFuture<String>();

        var result = BetterFuture.first(Stream.of(future1, future2, future3));

        assertEquals(Optional.empty(), result.get(ofMillis(100)));
        assertFalse(result.isCompleted());

        future2.fail(new Exception("fail"));

        assertTrue(result.isCompleted());
        assertThrows(ExecutionException.class, result::get);
    }

    @Test
    public void collectEmptyStream() {
        var futures = Stream.<BetterFuture<String>>empty();

        var collected = BetterFuture.collect(futures);

        assertTrue(collected.isEmpty());
    }

    @Test
    public void collect() throws ExecutionException, InterruptedException {
        var f1 = new BetterFuture<String>();
        var f2 = new BetterFuture<String>();
        var f3 = new BetterFuture<String>();
        var futures = Stream.of(f1, f2, f3);

        var collected = BetterFuture.collect(futures);

        assertNull(collected.poll(10, MILLISECONDS));

        f2.succeed("f2");
        f1.succeed("f1");

        var r2 = collected.poll(10, MILLISECONDS);
        assertNotNull(r2);
        assertTrue(r2.isCompleted());
        assertEquals("f2", r2.get());

        var r1 = collected.poll(10, MILLISECONDS);
        assertNotNull(r1);
        assertTrue(r1.isCompleted());
        assertEquals("f1", r1.get());

        assertNull(collected.poll(10, MILLISECONDS));

        f3.succeed("f3");

        var r3 = collected.poll(10, MILLISECONDS);
        assertNotNull(r3);
        assertTrue(r3.isCompleted());
        assertEquals("f3", r3.get());

        assertNull(collected.poll(10, MILLISECONDS));
    }

    @Test
    public void collectAsync() throws ExecutionException, InterruptedException {
        var f1 = new BetterFuture<String>();
        var f2 = new BetterFuture<String>();
        var f3 = new BetterFuture<String>();
        var futures = Stream.of(f1, f2, f3);

        var collected = BetterFuture.collect(futures);

        var futureList = BetterFuture.future(() ->
                Stream.of(collected.take(), collected.take(), collected.take()))
            .andThen(BetterFuture::reduce)
            .map(Stream::toList);

        assertFalse(futureList.isCompleted());

        f2.succeed("f2");
        assertFalse(futureList.isCompleted());

        f1.succeed("f1");
        assertFalse(futureList.isCompleted());

        f3.succeed("f3");

        assertEquals(Optional.of(List.of("f2", "f1", "f3")), futureList.get(Duration.ofMillis(10)));
        assertTrue(futureList.isCompleted());
    }

    private <T> T waitForLatchAndReturn(CountDownLatch latch, T result) throws InterruptedException {
        latch.countDown();
        latch.await();
        return result;
    }

    private String waitForLatchAndFail(CountDownLatch latch, Exception failure) throws Exception {
        latch.countDown();
        latch.await();
        throw failure;
    }

}

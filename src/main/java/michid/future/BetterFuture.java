package michid.future;

import static java.util.concurrent.CompletableFuture.completedFuture;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * A {@code BetterFuture} is a handle to the result of an asynchronous computation.
 * It captures the effects of latency and failure: it is {@code completed} once the
 * computation finishes (either successfully or with a failure). Additionally, it is
 * {@code succeeded} or {@code failed} depending on the outcome of the computation.
 * When completed, it provides access to the outcome of the computation.
 * <p>
 * {@code BetterFuture} is a thin wrapper around Java's {@code CompletableFuture}.
 * While it offers some of the same functionality, it should generally be easier to
 * use and at the same time offers some additional functionality. Most prominently
 * the ability to convert a {@code Callable} into a {@code BetterFuture}, a way
 * to execute multiple asynchronous computations in parallel and few combinators
 * for collecting and reducing multiple instances of {@code BetterFuture}.
 */
public class BetterFuture<T> {
    private static final Executor DEFAULT_EXECUTOR = ForkJoinPool.commonPool();

    private final CompletableFuture<T> delegate;

    /**
     * Create a new instance that is not backed by an asynchronous computation but
     * can be completed externally by either calling {@link #succeeded(Object)} or
     * {@link #failed(Throwable)}.
     */
    public BetterFuture() {
        this(new CompletableFuture<>());
    }

    private BetterFuture(CompletableFuture<T> delegate) {
        this.delegate = delegate;
    }

    /**
     * Factory for creating an instance that already completed successfully with  a given value.
     *
     * @param value  the value to complete with
     * @return  a new instance
     */
    public static <T> BetterFuture<T> succeeded(T value) {
        return new BetterFuture<>(completedFuture(value));
    }

    /**
     * Factory for creating an instance that already completed with a failure
     *
     * @param failure  the exception to fail with
     * @return  a new instance
     */
    public static <T> BetterFuture<T> failed(Throwable failure) {
        return new BetterFuture<T>().fail(failure);
    }

    /**
     * Factory for creating a new instance from a {@code Callable}. The callable
     * is evaluated asynchronously completing the returned instance when finished.
     * The returned instance either succeeds with the value returned by the callable
     * or fails with the exception thrown by the callable.
     *
     * @param executor  the executor to use for asynchronous execution
     * @param callable  the callable to run asynchronously
     * @return  a new instance
     */
    public static <T> BetterFuture<T> future(Executor executor, Callable<T> callable) {
        CompletableFuture<T> delegate = new CompletableFuture<T>();

        executor.execute(() -> {
            try {
                delegate.complete(callable.call());
            } catch (Throwable e) {
                delegate.completeExceptionally(e);
            }
        });

        return new BetterFuture<>(delegate);
    }

    /**
     * Factory for creating a new instance from a {@code Callable}. The callable
     * is evaluated asynchronously completing the returned instance when finished.
     * The returned instance either succeeds with the value returned by the callable
     * or fails with the exception thrown by the callable.
     *
     * @param callable  the callable to run asynchronously
     * @return  a new instance
     */
    public static <T> BetterFuture<T> future(Callable<T> callable) {
        return future(DEFAULT_EXECUTOR, callable);
    }

    /**
     * Factory for creating a new instance from a given {@code Future}.
     * The returned instance completes successfully or with failed
     * once the passed {@code future} completes.
     *
     * @param executor  the executor to use for asynchronous execution
     * @param future  source future
     * @return  a new instance
     */
    public static <T> BetterFuture<T> fromFuture(Executor executor, Future<T> future) {
        return BetterFuture.future(executor, future::get);
    }

    /**
     * Factory for creating a new instance from a given {@code Future}.
     * The returned instance completes successfully or with failed
     * once the passed {@code future} completes.
     *
     * @param future  source future
     * @return  a new instance
     */
    public static <T> BetterFuture<T> fromFuture(Future<T> future) {
        return BetterFuture.future(future::get);
    }

    /**
     * @return  a {@code CompletableFuture} that completes either successfully or with
     *          a failure if after this future completed either successfully or with a
     *          failure.
     */
    public CompletableFuture<T> toCompletableFuture() {
        CompletableFuture<T> future = new CompletableFuture<>();
        onFail(future::completeExceptionally);
        onSuccess(future::complete);
        return future;
    }

    /**
     * @return  {@code true} if this instance completed (either successfully of with a failure).
     */
    public boolean isCompleted() {
        return delegate.isDone();
    }

    /**
     * @return  {@code true} if this instance completed successfully.
     */
    public boolean isSucceeded() {
        return delegate.isDone() && !delegate.isCompletedExceptionally();
    }

    /**
     * @return  {@code true} if this instance completed with a failure.
     */
    public boolean isFailed() {
        return delegate.isCompletedExceptionally();
    }

    /**
     * Complete this instance successfully with a given value.
     *
     * @param value  the value to complete this instance with
     * @return  this instance
     */
    public BetterFuture<T> succeed(T value) {
        delegate.complete(value);
        return this;
    }

    /**
     * Complete this instance with failure.
     * @param exception  the exception to complete this instance with
     * @return  this instance
     */
    public BetterFuture<T> fail(Throwable exception) {
        delegate.completeExceptionally(exception);
        return this;
    }

    /**
     * Map a function over a future in the success case: turn a {@code BetterFuture<T>}
     * into a {@code BetterFuture<R>} given a {@code Function<T, R>}.
     * <p>
     * Create a new {@code BetterFuture} that applies a function to its result if this
     * instance succeeds and otherwise fails with the exception of this instance.
     * <p>Example:
     * <pre>
     *   BetterFuture&lt;String> future = ...
     *   BetterFuture&lt;Integer> intFuture = future.map(Integer::parseInt);
     * </pre>
     *
     * @param fn  function to apply
     * @return  new {@code BetterFuture} instance
     */
    public <R> BetterFuture<R> map(Function<T, R> fn) {
        return new BetterFuture<>(delegate.thenApply(fn));
    }

    /**
     * Map a function over a future in the failure case.
     * <p>
     * Create a new {@code BetterFuture} that applies a function to its result if this
     * instance fails and otherwise completes with the value of this instance.
     *
     * @param fn  function to apply
     * @return  new {@code BetterFuture} instance
     */
    public BetterFuture<T> recover(Function <Throwable, T> fn) {
        return new BetterFuture<>(delegate.exceptionally(fn));
    }

    /**
     * Create a new {@code BetterFuture} and complete it with a given value if this
     * instance fails. Otherwise, complete it with the value of this instance.
     * <p>
     * This is equivalent to
     * <pre>
     *     future.recover(ignore -> value);
     * </pre>
     *
     * @param value  the value to complete in case of this instance failing
     * @return  new {@code BetterFuture} instance
     */
    public BetterFuture<T> recover(T value) {
        return recover(__ -> value);
    }

    /**
     * Flatmap a function over a future in the success case: turn a {@code BetterFuture<T>}
     * into a {@code BetterFuture<R>} given a {@code Function<T, BetterFuture<R>>}.
     * <p>
     * Apply a function to the value of this future when it completes successfully and return a
     * new {@code BetterFuture} that completes when the future returned from the function
     * completes.
     * <p>
     * Use {@code andThen} to execute asynchronous calls that depend on each other's result
     * sequentially. (See {@link #andAlso(BetterFuture)} for parallel execution.)
     * <p>Example:
     * <pre>
     * BetterFuture&lt;String> productReview = BetterFuture.future(
     *     API::getProduct)
     *         .andThen(product -> BetterFuture.future(() ->
     *     API.getRating(product))
     *         .map(rating ->
     *     newProductReview(product, rating)));
     * </pre>
     *
     * @param fn  function to apply
     * @return  new {@code BetterFuture} instance
     */
    public <R> BetterFuture<R> andThen(Function<T, BetterFuture<R>> fn) {
        return new BetterFuture<>(
            delegate.thenCompose(value -> fn.apply(value).delegate));
    }

    /**
     * Flatmap a function over a future in the failure case.
     * <p>
     * Apply a function to the value of this future when it completes with a failure and return a
     * new {@code BetterFuture} that completes when the future returned from the function
     * completes.
     *
     * @param fn  function to apply
     * @return  new {@code BetterFuture} instance
     */
    public BetterFuture<T> recoverWith(Function<Throwable, BetterFuture<T>> fn) {
        BetterFuture<T> result = new BetterFuture<>();
        this.onFail(throwable ->
            fn.apply(throwable)
                .onSuccess(result::succeed)
                .onFail(result::fail));
        return result;
    }

    /**
     * Use {@code andAlso} to execute asynchronous calls that do not depend on each other's result
     * in parallel. (See {@link #andThen(Function)} for sequential execution.)
     * <p>Example:
     * <pre>
     * BetterFuture<Integer> duration = BetterFuture.future(
     *     API::getSpeed)
     *         .andAlso(BetterFuture.future(
     *     API::getDistance)
     *         .map(distance -> speed ->
     *     distance / speed));
     * </pre>
     * 
     * @param future  a future of a function receiving the result of this instance for its argument
     * @return  new {@code BetterFuture} instance
     * 
     * @see #andAlso(BetterFuture, BiFunction)  
     */
    public <R> BetterFuture<R> andAlso(BetterFuture<Function<T, R>> future) {
        return future.andThen(this::map);
    }

    /**
     * Use {@code andAlso} to execute asynchronous calls that do not depend on each other's result
     * in parallel. (See {@link #andThen(Function)} for sequential execution.)
     * <p>Example:
     * <pre>
     * BetterFuture<Integer> duration = BetterFuture.future(
     *     API::getSpeed)
     *         .andAlso(BetterFuture.future(
     *     API::getDistance),
     *         (distance, speed) ->
     *     distance / speed);
     * </pre>
     *
     * @param future  future to run in parallel to this future
     * @param f  binary function for combining the results of the individual futures
     * @return  new {@code BetterFuture} instance
     * 
     * @see #andAlso(BetterFuture) 
     */
    public <S, R> BetterFuture<R> andAlso(BetterFuture<S> future, BiFunction<T, S, R> f) {
        return future.andAlso(map(t -> s -> f.apply(t, s)));
    }

    /**
     * Get the value of this future. This call block until this instance completes.
     *
     * @return  value of this future
     * @throws ExecutionException  if this instance completes with a failure
     * @throws InterruptedException  if waiting for completion is interrupted
     */
    public T get() throws ExecutionException, InterruptedException {
        return delegate.get();
    }

    /**
     * Get the value of this future. This call block until this instance completes or until it
     * times out.
     *
     * @param timeout  time to wait for this instance to complete.
     * @return  value of this future
     * @throws ExecutionException  if this instance completes with a failure
     * @throws InterruptedException  if waiting for completion is interrupted
     */
    public Optional<T> get(Duration timeout) throws ExecutionException, InterruptedException {
        try {
            return Optional.of(delegate.get(timeout.toMillis(), TimeUnit.MILLISECONDS));
        } catch (TimeoutException e) {
            return Optional.empty();
        }
    }

    /**
     * Install a callback receiving this instance as an argument once it completed.
     *
     * @param callback  the callback to call once this instance completes
     * @return  this instance
     */
    public BetterFuture<T> onComplete(Consumer<BetterFuture<T>> callback) {
        delegate.handle((__, ___) -> {
            callback.accept(this);
            return null;
        });
        return this;
    }

    /**
     * Install a callback receiving the value of this instance as an argument once it completes
     * successfully.
     *
     * @param callback  the callback to call once this instance completes
     * @return  this instance
     */
    public BetterFuture<T> onSuccess(Consumer<T> callback) {
        delegate.handle((value, __) -> {
            if (value != null) {
                callback.accept(value);
            }
            return null;
        });
        return this;
    }

    /**
     * Install a callback receiving the exception of this instance as an argument once it completes
     * with a failure.
     *
     * @param callback  the callback to call once this instance completes
     * @return  this instance
     */
    public BetterFuture<T> onFail(Consumer<Throwable> callback) {
        delegate.handle((__, throwable) -> {
            if (throwable != null) {
                callback.accept(throwable);
            }
            return null;
        });
        return this;
    }

    /**
     * Collect a stream of futures in their order of completion into a blocking queue.
     *
     * @param futures  a stream of futures
     * @return  a blocking queue containing the futures in the order of their completion.
     */
    public static <T> BlockingQueue<BetterFuture<T>> collect(Stream<BetterFuture<T>> futures) {
        LinkedBlockingQueue<BetterFuture<T>> queue = new LinkedBlockingQueue<>();
        futures.forEach(future ->
            future.onComplete(
                queue::add));

        return queue;
    }

    /**
     * Reduce a stream of futures into a future of a stream in parallel. The returned future
     * completes successfully if all futures on the stream complete successfully and fails
     * otherwise.
     *
     * @param futures  a stream of futures
     * @return  a future for a stream of all the values of the completed futures
     */
    public static <T> BetterFuture<Stream<T>> reduce(Stream<BetterFuture<T>> futures) {
        return futures
            .map(future -> future.map(Stream::of))
            .reduce(
                BetterFuture.succeeded(Stream.empty()),
                (future1, future2) ->
                    future1.andAlso(
                    future2, Stream::concat));
    }

    /**
     * Left associative parallel fold of a list of futures into a single future.
     *
     * @param futures  a stream of futures
     * @param init  initial future to start the fold with
     * @param combine  binary function for combining the values of two futures
     * @return  a future for the folded value
     */
    public static <R, T> BetterFuture<R> foldLeft(Stream<BetterFuture<T>> futures, BetterFuture<R> init, BiFunction<R, T, R> combine) {
        return futures.collect(
            Collectors.collectingAndThen(
                // Force associativity by lifting fold into the monoid of functions
                Collectors.reducing(
                    Function.<BetterFuture<R>>identity(),
                    t -> r -> r.andAlso(t, combine),
                    Function::andThen),
                endo -> endo.apply(init)));
    }

    /**
     * Find the first completed future in a stream of futures in parallel.
     *
     * @param futures  a stream of futures
     * @return  a future that completes when the first future completes
     */
    public static <T> BetterFuture<T> first(Stream<BetterFuture<T>> futures) {
        CompletableFuture<T> delegate = new CompletableFuture<>();

        futures.forEach(future ->
            future
                .onSuccess(delegate::complete)
                .onFail(delegate::completeExceptionally));

        return new BetterFuture<>(delegate);
    }

}


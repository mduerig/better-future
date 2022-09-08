# A Better Future for Java
`BetterFuture` is a thin wrapper around Java's `CompletableFuture` making it easier and more  consistent to use. By focusing on effects instead of operational aspects, reasoning becomes simpler. In essence, a future captures the notion of latency and error: a future is the promise of a value at some later time that might fail to compute.

While `BetterFuture` exposes much of the original functionality, it also adds some that is not present in `CompletableFuture`: a way to turn a `Callable` into a future, which fails the future when the `Callable` throws an exception. Furthermore, it adds ways to reduce, combine and collect multiple futures in parallel.

## Creation and Completion
A newly created `BetterFuture` might complete - either successfully or failed - at some future point in time. Once completed its result becomes available, either from a callback or from one of its accessors.

Use `BetterFuture#succeeded(T)` and `BetterFuture#failed(Throwable)` to create instances that are already completed, the first on successfully and the second one failed.

Use `BetterFuture#future(Callable<T>)` to create an instance that executes the passed `Callable` asynchronously and completes once the `Callable` finishes executing. If the `Callable` throws an exception the `BetterFuture` fails with that exception, otherwise it succeeds with the value returned from the `Callable`.

Use `new BetterFuture()` to create a new instance that can be completed manually by either calling `BetterFuture#succeed(T)` or `BetterFuture#fail(Throwable)`.

Register a callback with `BetterFuture#onSuccess(Consumer<BetterFuture<T>>)`,
`BetterFuture#onComplete(Consumer<T>)` or `BetterFuture#onFail(Consumer<Throwable>)` to get notified on completion.

Use one of the polling methods `BetterFuture#get()` or `BetterFuture#get(Duration)` to retrieve the result of a `BetterFuture`. These methods block either until completion or a timeout expired.

Use `BetterFuture#isCompleted()`, `BetterFuture#isSucceeded()` or `BetterFuture#isFailed()` to determine the completion status of a `BetterFuture`.

## Transforming, Sequencing and Recovering
`BetterFuture` offers various ways to lift functions into the future and for sequencing multiple futures.

Use `BetterFuture#map(Function<T, R>)` to convert a `BetterFuture<T>` into a `BetterFuture<R>`. E.g. lift `Integer#parseInt` into a `BetterFuture<String>` to convert it into a `BetterFuture<Integer>`:

    BetterFuture<String> future = ...
    BetterFuture>Integer> intFuture = future.map(Integer::parseInt);

Use `BetterFuture#andThen(Function<T, BetterFuture<R>>)` to sequence multiple `BetterFuture`s one after each other, where the next one depends on the result of the previous one. E.g. to retrieve a product and then a rating for that product to create a product review:

    BetterFuture<String> productReview = BetterFuture.future(
        API::getProduct)
            .andThen(product -> BetterFuture.future(() ->
        API.getRating(product))
            .map(rating ->
        newProductReview(product, rating)));

Use `BetterFuture#andAlso(BetterFuture<Function<T, R>>)` when a `BetterFuture` instance does not depend on the result of previous ones. This allows parallel execution of the asynchronous tasks. E.g. to calculate the speed from the result of two futures, one for a speed and the other a duration:

    BetterFuture<Integer> duration = BetterFuture.future(
        API::getSpeed)
            .andAlso(BetterFuture.future(
        API::getDistance)
            .map(distance -> speed ->
        distance / speed));

Use one of the `BetterFuture#recover...()` methods to recover a failed future into a succeeded one:
 - `recover(Function<Throwable,T>)` corresponds to `BetterFuture#map(Function<T, R>)` for the failure case.
 - `BetterFuture#recover(T)` is a shortcut for `future.recover(ignore --> t)`.
 - `BetterFuture#recoverWith(Function<Throwable, BetterFuture<T>>)` corresponds to `BetterFuture#andThen(Function<T, BetterFuture<R>>)` for the failure case.

## Combining, Reducing and Collecting
Use `BetterFuture#first(Stream<BetterFuture<T>>)` to find the first completing instance in a stream of `BetterFuture`s.

Use `BetterFuture#collect(Stream<BetterFuture<T>>)` to collect a stream of `BetterFuture`s into a blocking queue, ordered by the order of completion of the individual instances on the stream.

Use `BetterFuture#reduce(Stream<BetterFuture<T>>)` to reduce a stream of `BetterFuture`s into a `BetterFuture<Stream<T>>`. Reduction runs in parallel and returned `BetterFuture` fails immediately once one of the instances in the stream fail and succeeds otherwise.

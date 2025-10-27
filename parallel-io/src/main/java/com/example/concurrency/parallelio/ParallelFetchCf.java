package com.example.concurrency.parallelio;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.stream.Collectors;

/**
 * Parallel fan-out using CompletableFuture with a fixed thread pool.
 * Non-HTTP inputs are simulated to keep tests network-free.
 */
public class ParallelFetchCf {
    public static void main(String[] args) throws Exception {
        List<String> inputs = (args != null && args.length > 0) ? List.of(args) :
                List.of("simA", "simB", "simC");

        int pool = Integer.getInteger("pool", 8);
        ExecutorService exec = Executors.newFixedThreadPool(pool);
        HttpClient http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .executor(exec)
                .build();

        Instant start = Instant.now();
        List<CompletableFuture<Result>> futures = new ArrayList<>();
        for (String in : inputs) {
            futures.add(CompletableFuture.supplyAsync(() -> fetch(http, in), exec));
        }
        List<Result> results = futures.stream().map(CompletableFuture::join).collect(Collectors.toList());
        long ms = Duration.between(start, Instant.now()).toMillis();

        System.out.println("Results:");
        for (Result r : results) System.out.println(" - " + r);
        System.out.println("Total time: " + ms + " ms");
        exec.shutdown();
    }

    public record Result(String input, int status, int bytes, long millis, boolean simulated) {
        public String toString() {
            return "%s -> %s %dB (%d ms)%s".formatted(
                    input, status == 0 ? "SIM" : status, bytes, millis, simulated ? " [sim]" : "");
        }
    }

    static Result fetch(HttpClient http, String input) {
        Instant t0 = Instant.now();
        boolean simulated = false;
        try {
            if (!input.startsWith("http")) {
                simulated = true;
                Thread.sleep(150);
                return new Result(input, 0, 0, Duration.between(t0, Instant.now()).toMillis(), true);
            }
            HttpRequest req = HttpRequest.newBuilder(URI.create(input)).GET().timeout(Duration.ofSeconds(5)).build();
            HttpResponse<byte[]> resp = http.send(req, HttpResponse.BodyHandlers.ofByteArray());
            return new Result(input, resp.statusCode(), resp.body() == null ? 0 : resp.body().length,
                    Duration.between(t0, Instant.now()).toMillis(), false);
        } catch (Exception e) {
            return new Result(input, -1, 0, Duration.between(t0, Instant.now()).toMillis(), simulated);
        }
    }
}

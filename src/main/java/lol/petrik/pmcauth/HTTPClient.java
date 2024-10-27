package lol.petrik.pmcauth;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

public class HTTPClient {
  public static HttpClient client;

  public HTTPClient() {
    client = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .build();
  }

  public CompletableFuture<String> get(String url) {
    return client.sendAsync(HttpRequest.newBuilder()
            .uri(URI.create(url))
            .GET()
            .build(), HttpResponse.BodyHandlers.ofString())
        .thenApply(HttpResponse::body);
  }

  public CompletableFuture<String> post(String url, String body) {
    return client.sendAsync(HttpRequest.newBuilder()
            .uri(URI.create(url))
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build(), HttpResponse.BodyHandlers.ofString())
        .thenApply(HttpResponse::body);
  }
}

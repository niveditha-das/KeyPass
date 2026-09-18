package com.keypass.carsim;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

/** A thin, dependency-light wrapper around the KeyPass REST API for the simulator's own use. */
public class ApiClient {

    private final String baseUrl;
    private final HttpClient http;
    private final ObjectMapper mapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    public ApiClient(String baseUrl) {
        this.baseUrl = baseUrl;
        this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    }

    public record ApiResponse(int status, JsonNode body) {
        public boolean isSuccess() {
            return status >= 200 && status < 300;
        }
    }

    public ApiResponse post(String path, Object body, String bearerToken) {
        return send("POST", path, body, bearerToken, null);
    }

    public ApiResponse post(String path, Object body, String bearerToken, String vehicleApiKey) {
        return send("POST", path, body, bearerToken, vehicleApiKey);
    }

    public ApiResponse get(String path, String bearerToken) {
        return send("GET", path, null, bearerToken, null);
    }

    public ApiResponse get(String path, String bearerToken, String vehicleApiKey) {
        return send("GET", path, null, bearerToken, vehicleApiKey);
    }

    private ApiResponse send(String method, String path, Object body, String bearerToken, String vehicleApiKey) {
        try {
            String json = body == null ? "" : mapper.writeValueAsString(body);
            if (System.getenv("KEYPASS_SIM_DEBUG") != null) {
                System.out.println("--> " + method + " " + path + " " + json);
            }
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + path))
                    .timeout(Duration.ofSeconds(10))
                    .header("Content-Type", "application/json");
            if (bearerToken != null) {
                builder.header("Authorization", "Bearer " + bearerToken);
            }
            if (vehicleApiKey != null) {
                builder.header("X-Vehicle-Key", vehicleApiKey);
            }
            builder.method(method, body == null
                    ? HttpRequest.BodyPublishers.noBody()
                    : HttpRequest.BodyPublishers.ofString(json));

            HttpResponse<String> response = http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            JsonNode node = response.body() == null || response.body().isBlank()
                    ? mapper.createObjectNode()
                    : mapper.readTree(response.body());
            return new ApiResponse(response.statusCode(), node);
        } catch (Exception e) {
            throw new RuntimeException("Request failed: " + method + " " + path, e);
        }
    }

    public static Map<String, Object> map(Object... kvs) {
        var m = new java.util.LinkedHashMap<String, Object>();
        for (int i = 0; i < kvs.length; i += 2) {
            m.put((String) kvs[i], kvs[i + 1]);
        }
        return m;
    }
}

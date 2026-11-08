package com.vehicletracker.simulator.device;

import com.vehicletracker.simulator.Fix;
import com.vehicletracker.simulator.codec.OsmAndCodec;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

final class OsmAndConnection implements DeviceConnection {

    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .proxy(HttpClient.Builder.NO_PROXY)
            .build();

    private final String baseUrl;
    private final String uniqueId;

    OsmAndConnection(String host, int port, String uniqueId) {
        this.baseUrl = "http://" + host + ":" + port;
        this.uniqueId = uniqueId;
    }

    @Override
    public void connect() {
        // stateless HTTP: nothing to open
    }

    @Override
    public void send(Fix fix) throws IOException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + OsmAndCodec.query(uniqueId, fix)))
                .timeout(Duration.ofSeconds(10))
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();
        try {
            HttpResponse<Void> response = CLIENT.send(request, HttpResponse.BodyHandlers.discarding());
            if (response.statusCode() != 200) {
                throw new IOException("HTTP " + response.statusCode());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted", e);
        }
    }

    @Override
    public boolean isConnected() {
        return true;
    }

    @Override
    public void close() {
        // nothing to close
    }
}

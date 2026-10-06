package by.whatsappka.platform.realtime;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.stereotype.Component;

/** Число подтверждённых WebSocket-соединений прямо сейчас. */
@Component
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class RealtimeMetrics {

    public RealtimeMetrics(StompConnections connections, MeterRegistry metrics) {
        Gauge.builder("whatsappka.ws.connections.active", connections, StompConnections::activeCount).register(metrics);
    }
}

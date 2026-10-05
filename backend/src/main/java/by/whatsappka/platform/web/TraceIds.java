package by.whatsappka.platform.web;

import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import org.slf4j.MDC;

public final class TraceIds {

    public static final String HEADER = "X-Trace-Id";
    public static final String ATTRIBUTE = "whatsappka.traceId";
    public static final String MDC_KEY = "traceId";

    private TraceIds() {
    }

    public static String resolveIncoming(String header) {
        if (header != null) {
            try {
                return UUID.fromString(header.trim()).toString();
            } catch (IllegalArgumentException ignored) {
                // Клиент прислал не UUID. Подставляем свой, чтобы не тащить произвольный текст в логи.
            }
        }
        return UUID.randomUUID().toString();
    }

    public static String current(HttpServletRequest request) {
        Object value = request.getAttribute(ATTRIBUTE);
        if (value instanceof String traceId && !traceId.isBlank()) {
            return traceId;
        }
        String fromMdc = MDC.get(MDC_KEY);
        if (fromMdc != null && !fromMdc.isBlank()) {
            return fromMdc;
        }
        return UUID.randomUUID().toString();
    }
}

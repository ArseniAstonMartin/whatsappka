package by.whatsappka.platform.web;

/**
 * Заголовки ответа API (NFR-SEC-03). Политика CSP не разрешает произвольные скрипты и inline-обработчики:
 * пользовательский текст выводится как текст, а не как HTML, поэтому исполнять нечего.
 */
public final class SecurityHeaders {

    public static final String CONTENT_SECURITY_POLICY =
            "default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline' https://fonts.googleapis.com; "
                    + "font-src 'self' https://fonts.gstatic.com data:; img-src 'self' data: blob:; media-src 'self' blob:; "
                    + "connect-src 'self' ws: wss:; object-src 'none'; base-uri 'self'; form-action 'self'; "
                    + "frame-ancestors 'none'";

    public static final String PERMISSIONS_POLICY = "camera=(), microphone=(), geolocation=(), payment=()";

    public static final long HSTS_MAX_AGE_SECONDS = 31_536_000L;

    private SecurityHeaders() {
    }
}

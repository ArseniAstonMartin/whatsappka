package by.whatsappka.admin;

import by.whatsappka.platform.web.ApiV1Controller;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/** Агрегированная статистика. Всё под /admin/** — только ADMIN по правилу безопасности. */
@ApiV1Controller
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class AdminStatsController {

    private final AdminStatsService stats;
    private final Clock clock;

    public AdminStatsController(AdminStatsService stats, Clock clock) {
        this.stats = stats;
        this.clock = clock;
    }

    @GetMapping("/admin/stats")
    public AdminStatsService.Stats stats(
            @RequestParam(name = "from", required = false) String from,
            @RequestParam(name = "to", required = false) String to
    ) {
        LocalDate today = LocalDate.ofInstant(clock.instant(), ZoneOffset.UTC);
        return stats.stats(AdminStatsService.period(from, to, today));
    }
}

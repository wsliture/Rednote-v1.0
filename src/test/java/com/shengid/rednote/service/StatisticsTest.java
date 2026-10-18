package com.shengid.rednote.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.shengid.rednote.repository.*;
import java.time.*;
import org.junit.jupiter.api.Test;

class StatisticsTest {
  @Test
  void shanghaiMidnightAndSevenDaysAreIndependentOfSystemTimezone() {
    var q = mock(Queries.class);
    var identity = mock(IdentityService.class);
    var clock = Clock.fixed(Instant.parse("2026-10-18T16:00:00Z"), ZoneOffset.UTC);
    var service =
        new AdminService(
            identity, mock(AppUserRepository.class), q, mock(ViewService.class), clock);
    var dashboard = service.dashboard();
    assertEquals(7, dashboard.trends().size());
    assertEquals("2026-10-19", dashboard.trends().get(6).date());
    assertEquals("2026-10-13", dashboard.trends().get(0).date());
    verify(q)
        .newUsers(Instant.parse("2026-10-18T16:00:00Z"), Instant.parse("2026-10-19T16:00:00Z"));
    assertTrue(dashboard.trends().stream().allMatch(t -> t.users() == 0 && t.notes() == 0));
  }
}

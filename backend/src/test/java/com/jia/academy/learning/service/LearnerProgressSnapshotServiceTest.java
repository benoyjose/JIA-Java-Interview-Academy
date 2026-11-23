package com.jia.academy.learning.service;

import com.jia.academy.security.CurrentUser;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.List;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class LearnerProgressSnapshotServiceTest {
    @Test void clampsRequestedPageSizeAndReturnsServerDerivedAggregate() {
        JdbcTemplate jdbc=mock(JdbcTemplate.class);
        CurrentUser user=mock(CurrentUser.class);
        UUID academy=UUID.randomUUID();
        when(user.academyIds()).thenReturn(List.of(academy));
        when(user.id()).thenReturn(UUID.randomUUID());
        when(jdbc.query(anyString(),any(org.springframework.jdbc.core.RowMapper.class),any(),any(),any(),any(),any()))
            .thenReturn(List.of());
        when(jdbc.query(anyString(),any(org.springframework.jdbc.core.RowMapper.class),any(),any()))
            .thenReturn(List.of(new LearnerProgressSnapshotService.Aggregate(5,2,
                new java.math.BigDecimal("41.20"))));
        var result=new LearnerProgressSnapshotService(jdbc).load(user,999,null);
        assertEquals(5,result.aggregate().lessonsStarted());
        assertEquals(2,result.aggregate().lessonsCompleted());
        verify(jdbc).query(contains("LIMIT ?"),any(org.springframework.jdbc.core.RowMapper.class),
            any(),any(),any(),any(),eq(101));
    }
}

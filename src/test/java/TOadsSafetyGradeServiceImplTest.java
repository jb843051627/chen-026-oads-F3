import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.fc.v2.common.exception.safety.SafetyRuleException;
import com.fc.v2.mapper.auto.TOadsSafetyRecordMapper;
import com.fc.v2.mapper.auto.TOadsSpotMapper;
import com.fc.v2.model.auto.TOadsRuleLine;
import com.fc.v2.model.auto.TOadsSafetyRecord;
import com.fc.v2.model.auto.TOadsSpot;
import com.fc.v2.model.custom.OadsGradeOutcome;
import com.fc.v2.model.custom.OadsRuleVerdict;
import com.fc.v2.service.ITOadsRuleLineService;
import com.fc.v2.service.impl.TOadsSafetyGradeServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 牌面安全分档固化口径逐条核对（mapper 与裁决引擎全 Mock，不落库）：
 * 固化与留痕同成同败、逐字同源、两问对不齐整笔打回、孤儿逐条列出、批量单点不拦整批。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class TOadsSafetyGradeServiceImplTest {

    @Mock
    private TOadsSpotMapper spotMapper;
    @Mock
    private TOadsSafetyRecordMapper recordMapper;
    @Mock
    private ITOadsRuleLineService ruleLineService;

    private TOadsSafetyGradeServiceImpl service;

    private static Date at(String s) throws Exception {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").parse(s);
    }

    private static TOadsSpot spot(long id, String code) {
        TOadsSpot s = new TOadsSpot();
        s.setId(id);
        s.setSpotNo("SP" + id);
        s.setSafeRuleCode(code);
        s.setDelFlag(0);
        return s;
    }

    private static TOadsRuleLine line(String code, String effStart) throws Exception {
        TOadsRuleLine r = new TOadsRuleLine();
        r.setId(1L);
        r.setRuleCode(code);
        r.setEffStart(at(effStart));
        return r;
    }

    @BeforeEach
    public void setUp() {
        service = new TOadsSafetyGradeServiceImpl();
        ReflectionTestUtils.setField(service, "oadsSpotMapper", spotMapper);
        ReflectionTestUtils.setField(service, "oadsSafetyRecordMapper", recordMapper);
        ReflectionTestUtils.setField(service, "oadsRuleLineService", ruleLineService);
        when(spotMapper.update(isNull(), any())).thenReturn(1);
        when(recordMapper.insert(any())).thenReturn(1);
    }

    @Test
    public void gradedSpotFreezesLevelAndSamePairInOneTransaction() throws Exception {
        TOadsSpot s = spot(1L, null);
        when(spotMapper.selectById(1L)).thenReturn(s);
        Date now = at("2026-10-05 03:00:00");
        Date eff = at("2025-01-01 00:00:00");
        OadsRuleVerdict v = OadsRuleVerdict.of(3, line("R-A", "2025-01-01 00:00:00"), new BigDecimal("9.00"));
        when(ruleLineService.verdictTop(new BigDecimal("8.996"), now)).thenReturn(v);

        OadsGradeOutcome out = service.gradeTop(1L, new BigDecimal("8.996"), now, "QUARTER");

        assertEquals(3, out.getLevel());
        assertEquals("R-A", out.getRuleCode());
        assertEquals(eff, out.getEffStart());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<UpdateWrapper<TOadsSpot>> uwCap = ArgumentCaptor.forClass(UpdateWrapper.class);
        verify(spotMapper).update(isNull(), uwCap.capture());
        String sqlSet = uwCap.getValue().getSqlSet();
        assertTrue(sqlSet.contains("safe_level"));
        assertTrue(sqlSet.contains("safe_rule_code"));
        assertTrue(sqlSet.contains("safe_eff_start"));
        Collection<Object> vals = uwCap.getValue().getParamNameValuePairs().values();
        assertTrue(vals.contains(3), "档位固化成第3档");
        assertTrue(vals.contains("R-A"), "代号逐字固化");
        assertTrue(vals.contains(eff), "起用那一一日逐字固化");

        ArgumentCaptor<TOadsSafetyRecord> recCap = ArgumentCaptor.forClass(TOadsSafetyRecord.class);
        verify(recordMapper).insert(recCap.capture());
        TOadsSafetyRecord rec = recCap.getValue();
        assertEquals(1L, rec.getSpotId());
        assertEquals("SP1", rec.getSpotNo());
        assertEquals(3, rec.getSafeLevel());
        assertEquals("R-A", rec.getRuleCode(), "留痕与点位行逐字同源");
        assertEquals(eff, rec.getEffStart());
        assertEquals(new BigDecimal("9.00"), rec.getConvScore());
        assertEquals(now, rec.getJudgedAt());
        assertEquals("2026Q4", rec.getQuarterCode());
        assertEquals("QUARTER", rec.getCreateBy());
    }

    @Test
    public void ungradedFreezesZeroAndLeavesBothCellsBlank() throws Exception {
        TOadsSpot s = spot(2L, "R-OLD");
        when(spotMapper.selectById(2L)).thenReturn(s);
        Date now = at("2026-10-05 03:00:00");
        when(ruleLineService.verdictTop(any(), eq(now)))
                .thenReturn(OadsRuleVerdict.ungraded(new BigDecimal("120.00"),
                        OadsRuleVerdict.MSG_OUT_OF_RANGE));

        OadsGradeOutcome out = service.gradeTop(2L, new BigDecimal("120"), now, null);

        assertEquals(0, out.getLevel());
        assertNull(out.getRuleCode(), "不分档代号留空");
        assertNull(out.getEffStart(), "不分档起用日一并留空");
        assertTrue(out.getMessage().contains("量程"));

        ArgumentCaptor<TOadsSafetyRecord> recCap = ArgumentCaptor.forClass(TOadsSafetyRecord.class);
        verify(recordMapper).insert(recCap.capture());
        assertEquals(0, recCap.getValue().getSafeLevel());
        assertNull(recCap.getValue().getRuleCode(), "留痕同样留空，不许借邻近规则凑");
        assertNull(recCap.getValue().getEffStart());
    }

    @Test
    public void twoEntriesDisagreeRejectsEverything() throws Exception {
        TOadsSpot s = spot(3L, "R-A");
        when(spotMapper.selectById(3L)).thenReturn(s);
        Date now = at("2026-10-05 03:00:00");
        when(ruleLineService.verdictByCode("R-A", new BigDecimal("50"), now))
                .thenReturn(OadsRuleVerdict.of(2, line("R-A", "2025-01-01 00:00:00"), new BigDecimal("50.00")));
        when(ruleLineService.verdictTop(new BigDecimal("50"), now))
                .thenReturn(OadsRuleVerdict.of(4, line("R-B", "2025-01-01 00:00:00"), new BigDecimal("50.00")));

        SafetyRuleException ex = assertThrows(SafetyRuleException.class,
                () -> service.gradeByRuleCode(3L, "R-A", new BigDecimal("50"), now, null));
        assertTrue(ex.getMessage().contains("两问对不齐"));
        verify(spotMapper, never()).update(any(), any());
        verify(recordMapper, never()).insert(any());
    }

    @Test
    public void twoEntriesAgreeFreezesByCode() throws Exception {
        TOadsSpot s = spot(4L, "R-A");
        when(spotMapper.selectById(4L)).thenReturn(s);
        Date now = at("2026-10-05 03:00:00");
        OadsRuleVerdict v = OadsRuleVerdict.of(3, line("R-A", "2025-01-01 00:00:00"), new BigDecimal("9.00"));
        when(ruleLineService.verdictByCode("R-A", new BigDecimal("9.00"), now)).thenReturn(v);
        when(ruleLineService.verdictTop(new BigDecimal("9.00"), now)).thenReturn(v);

        OadsGradeOutcome out = service.gradeByRuleCode(4L, "R-A", new BigDecimal("9.00"), now, null);
        assertEquals(3, out.getLevel());
        assertEquals("R-A", out.getRuleCode());
        verify(recordMapper).insert(any());
    }

    @Test
    public void spotGoneOrUpdateMissedLeavesNoTrail() throws Exception {
        when(spotMapper.selectById(9L)).thenReturn(null);
        assertThrows(SafetyRuleException.class,
                () -> service.gradeTop(9L, new BigDecimal("10"), new Date(), null));
        verify(spotMapper, never()).update(any(), any());
        verify(recordMapper, never()).insert(any());

        // 点位还在、但固化那一下没落进行（并发被删）：不许只留痕
        when(spotMapper.selectById(8L)).thenReturn(spot(8L, null));
        when(spotMapper.update(any(), any())).thenReturn(0);
        when(ruleLineService.verdictTop(any(), any()))
                .thenReturn(OadsRuleVerdict.of(1, line("R-Z", "2025-01-01 00:00:00"),
                        new BigDecimal("1.00")));
        assertThrows(SafetyRuleException.class,
                () -> service.gradeTop(8L, new BigDecimal("10"), new Date(), null));
        verify(recordMapper, never()).insert(any());
    }

    @Test
    public void orphanSpotsAreListedOneByOne() throws Exception {
        Date now = at("2026-10-05 03:00:00");
        TOadsSpot live = spot(1L, "R-A");
        TOadsSpot withdrawn = spot(2L, "R-OLD");
        TOadsSpot expired = spot(3L, "R-PAST");
        when(spotMapper.selectList(any())).thenReturn(new ArrayList<TOadsSpot>(
                Arrays.asList(live, withdrawn, expired)));
        when(ruleLineService.effectiveVersion("R-A", now)).thenReturn(line("R-A", "2025-01-01 00:00:00"));
        when(ruleLineService.effectiveVersion("R-OLD", now)).thenReturn(null);
        when(ruleLineService.effectiveVersion("R-PAST", now)).thenReturn(null);

        List<TOadsSpot> orphans = service.listOrphanSpots(now);
        assertEquals(2, orphans.size(), "代号已撤下与交班已过的都逐条列出来");
        assertEquals("SP2", orphans.get(0).getSpotNo());
        assertEquals("SP3", orphans.get(1).getSpotNo());
    }

    @Test
    public void quarterBatchOneBadSpotDoesNotStopOthers() throws Exception {
        // 自注入代理在单测里用实例自身顶上：逐个调用逻辑一致（无 Spring 时事务为空操作）
        ReflectionTestUtils.setField(service, "self", service);
        Date now = at("2026-10-05 03:00:00");
        when(spotMapper.selectById(1L)).thenReturn(spot(1L, null));
        when(spotMapper.selectById(2L)).thenReturn(null);
        OadsRuleVerdict v2 = OadsRuleVerdict.of(2, line("R-A", "2025-01-01 00:00:00"), new BigDecimal("5.00"));
        when(ruleLineService.verdictTop(new BigDecimal("5.00"), now)).thenReturn(v2);

        Map<Long, BigDecimal> scores = new LinkedHashMap<Long, BigDecimal>();
        scores.put(1L, new BigDecimal("5.00"));
        scores.put(2L, new BigDecimal("5.00"));
        List<OadsGradeOutcome> outs = service.gradeQuarter(scores, now, "QUARTER");

        assertEquals(2, outs.size());
        assertEquals(2, outs.get(0).getLevel());
        assertEquals(0, outs.get(1).getLevel(), "站不住的点位不分档、带说法，不拦剩下的");
        assertNotNull(outs.get(1).getMessage());
        verify(recordMapper).insert(any());
    }
}

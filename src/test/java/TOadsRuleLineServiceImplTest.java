import com.fc.v2.mapper.auto.TOadsRuleLineMapper;
import com.fc.v2.model.auto.TOadsRuleLine;
import com.fc.v2.model.custom.OadsRuleBoard;
import com.fc.v2.model.custom.OadsRuleVerdict;
import com.fc.v2.service.impl.TOadsRuleLineServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * 牌面安全分档唯一裁决处口径逐条核对（mapper Mock，不落库）。
 * 上一季度的事故：折算分 8.996 对够线限值 9.00，取数进了位、比对没进位，错落第2档。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class TOadsRuleLineServiceImplTest {

    @Mock
    private TOadsRuleLineMapper ruleLineMapper;

    private TOadsRuleLineServiceImpl service;

    private final List<TOadsRuleLine> rows = new ArrayList<TOadsRuleLine>();

    private static Date at(String s) throws Exception {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").parse(s);
    }

    /** 一条干净规则：阈值 4/9/18，2025 全年起、交班留空（长期有效） */
    private static TOadsRuleLine rule(long id, String code, String th1, String th2, String th3,
                                     String start, String end, int priority, int status) throws Exception {
        TOadsRuleLine r = new TOadsRuleLine();
        r.setId(id);
        r.setRuleCode(code);
        r.setRuleName("线" + code);
        if (th1 != null) {
            r.setTh1Max(new BigDecimal(th1));
        }
        if (th2 != null) {
            r.setTh2Max(new BigDecimal(th2));
        }
        if (th3 != null) {
            r.setTh3Max(new BigDecimal(th3));
        }
        r.setEffStart(start == null ? null : at(start));
        r.setEffEnd(end == null ? null : at(end));
        r.setPriority(priority);
        r.setStatus(status);
        r.setDelFlag(0);
        return r;
    }

    @BeforeEach
    public void setUp() {
        service = new TOadsRuleLineServiceImpl();
        org.springframework.test.util.ReflectionTestUtils.setField(service, "oadsRuleLineMapper", ruleLineMapper);
        when(ruleLineMapper.selectList(any())).thenAnswer(inv -> new ArrayList<TOadsRuleLine>(rows));
        when(ruleLineMapper.selectById(any())).thenAnswer(inv -> {
            Long id = inv.getArgument(0);
            for (TOadsRuleLine r : rows) {
                if (id.equals(r.getId())) {
                    return r;
                }
            }
            return null;
        });
    }

    // ------------------------------------------------ 落档与压线

    @Test
    public void bandsAndBoundaries() throws Exception {
        rows.add(rule(1L, "R-A", "4", "9", "18", "2025-01-01 00:00:00", null, 10, 0));
        Date now = at("2026-10-05 03:00:00");
        // 规则书原例：3.00→1，9.00→3（压线落号更大的档），8.90→2，9.10→3
        assertEquals(1, service.evaluateTop(new BigDecimal("3.00"), now));
        assertEquals(2, service.evaluateTop(new BigDecimal("8.90"), now));
        assertEquals(3, service.evaluateTop(new BigDecimal("9.00"), now), "正压够线限值：不进第2档，落第3档");
        assertEquals(3, service.evaluateTop(new BigDecimal("9.10"), now));
        assertEquals(2, service.evaluateTop(new BigDecimal("4.00"), now), "正压起算限值：落第2档");
        assertEquals(1, service.evaluateTop(new BigDecimal("3.99"), now));
        assertEquals(4, service.evaluateTop(new BigDecimal("18.00"), now), "正压封顶限值：落第4档");
        assertEquals(4, service.evaluateTop(new BigDecimal("99.99"), now));
    }

    @Test
    public void lastQuarterRoundingAccidentMustNotRecur() throws Exception {
        rows.add(rule(1L, "R-A", "4", "9", "18", "2025-01-01 00:00:00", null, 10, 0));
        Date now = at("2026-10-05 03:00:00");
        // 事故原值：8.996 进位成 9.00 后不再小于 9.00，必须落第3档
        assertEquals(3, service.evaluateTop(new BigDecimal("8.996"), now),
                "取数与比对都进位：8.996→9.00，不小于够线限值9.00，落第3档");
        // 没跨过线的零头仍在第2档
        assertEquals(2, service.evaluateTop(new BigDecimal("8.994"), now), "8.994→8.99，仍在第2档");
        OadsRuleVerdict v = service.verdictTop(new BigDecimal("8.996"), now);
        assertEquals(new BigDecimal("9.00"), v.getRoundedScore(), "交代里带回的是取齐后的折算分");
    }

    @Test
    public void outOfRangeScoreIsUngraded() throws Exception {
        rows.add(rule(1L, "R-A", "1", "2", "100", "2025-01-01 00:00:00", null, 10, 0));
        Date now = at("2026-10-05 03:00:00");
        assertEquals(0, service.evaluateTop(new BigDecimal("-0.01"), now), "负的折算分按不分档");
        assertEquals(0, service.evaluateTop(new BigDecimal("100.01"), now), "大过100按不分档");
        assertEquals(0, service.evaluateTop((BigDecimal) null, now), "没给折算分按不分档");
        // 恰在量程两端仍正常判：0 落第1档，100 正压封顶落第4档
        assertEquals(1, service.evaluateTop(BigDecimal.ZERO, now));
        assertEquals(4, service.evaluateTop(new BigDecimal("100"), now));
        assertTrue(service.verdictTop(new BigDecimal("100.01"), now).getMessage().contains("量程"));
    }

    // ------------------------------------------------ 脏配置点名剔除

    @Test
    public void dirtyRulesAreRejectedAndNamedByCell() throws Exception {
        rows.add(rule(1L, "MISS", "4", null, "18", "2025-01-01 00:00:00", null, 10, 0));
        rows.add(rule(2L, "FLAT", "9", "9", "18", "2025-01-01 00:00:00", null, 10, 0));
        rows.add(rule(3L, "OVER", "4", "9", "100.50", "2025-01-01 00:00:00", null, 10, 0));
        rows.add(rule(4L, "R-A", "4", "9", "18", "2025-01-01 00:00:00", null, 10, 0));
        Date now = at("2026-10-05 03:00:00");

        OadsRuleBoard board = service.board(now);
        assertEquals(1, board.getCount(), "三条脏配置不进条数");
        assertEquals(1, board.getAvailable().size(), "清单与条数同一次计算");
        assertEquals("R-A", board.getAvailable().get(0).getRuleCode());
        assertEquals(3, board.getDirty().size());

        assertReasons(board, "MISS", "够线限值(th2_max)那一格没有值");
        assertReasons(board, "FLAT", "够线限值(th2_max)没有大于它的前一格起算限值(th1_max)");
        assertReasons(board, "OVER", "封顶限值(th3_max)大过100");

        assertEquals(3, service.evaluateTop(new BigDecimal("9.00"), now), "脏的不算，拿干净的R-A贴区间");
        OadsRuleVerdict miss = service.verdictByCode("MISS", new BigDecimal("9.00"), now);
        assertEquals(0, miss.getLevel());
        assertTrue(miss.getMessage().contains("够线限值(th2_max)"), "不能只讲数值有问题，要点名哪一格");
    }

    @Test
    public void eachMissingLimitCellIsNamedIndividually() throws Exception {
        rows.add(rule(1L, "MISS2", null, "9", null, "2025-01-01 00:00:00", null, 10, 0));
        OadsRuleBoard board = service.board(at("2026-10-05 03:00:00"));
        List<String> reasons = board.getDirty().get(0).getReasons();
        assertTrue(reasons.contains("起算限值(th1_max)那一格没有值"));
        assertTrue(reasons.contains("封顶限值(th3_max)那一格没有值"));
    }

    // ------------------------------------------------ 撤下与生效窗

    @Test
    public void withdrawnRuleStaysInTableButOutOfPlay() throws Exception {
        TOadsRuleLine off = rule(1L, "R-OFF", "4", "9", "18", "2025-01-01 00:00:00", null, 10, 1);
        rows.add(off);
        Date now = at("2026-10-05 03:00:00");
        assertEquals(0, service.countAvailable(now));
        assertTrue(service.listAvailable(now).isEmpty());
        assertFalse(service.usable(1L, now));
        assertEquals(0, service.evaluate("R-OFF", new BigDecimal("3.00"), now));
        assertTrue(service.verdictByCode("R-OFF", new BigDecimal("3.00"), now)
                .getMessage().contains("取不到生效版本"));
        // 仍是在册一行：按主键原值查得到，不收口
        assertSame(off, service.selectTOadsRuleLineById(1L));
    }

    @Test
    public void windowIsStartInclusiveEndExclusiveAndBlankEndMeansOpen() throws Exception {
        rows.add(rule(1L, "R-A", "4", "9", "18",
                "2025-01-01 00:00:00", "2025-07-01 00:00:00", 10, 0));
        rows.add(rule(2L, "R-B", "4", "9", "18",
                "2025-07-01 00:00:00", null, 10, 0));
        // 正压起用当口：在
        assertTrue(service.usable(1L, at("2025-01-01 00:00:00")), "起的一端含当口");
        // 起用前一刻：不在
        assertFalse(service.usable(1L, at("2024-12-31 23:59:59")));
        // 正压交班当日：旧规则已止（止端不含），新规则已起
        assertFalse(service.usable(1L, at("2025-07-01 00:00:00")), "止的那一端不含");
        assertTrue(service.usable(2L, at("2025-07-01 00:00:00")));
        // 交班空着：长期有效
        assertTrue(service.usable(2L, at("2099-01-01 00:00:00")), "交班留空的兼容分支不能删");
    }

    // ------------------------------------------------ 并列采信与 as-of

    @Test
    public void priorityFirstThenCodeLexicalDesc() throws Exception {
        rows.add(rule(1L, "R-LOW", "4", "9", "18", "2025-01-01 00:00:00", null, 5, 0));
        rows.add(rule(2L, "R-HIGH", "40", "60", "90", "2025-01-01 00:00:00", null, 9, 0));
        Date now = at("2026-10-05 03:00:00");
        // 先后序大的先说：50 分在 R-HIGH 落第2档，若错拿 R-LOW 会落第4档
        assertEquals(2, service.evaluateTop(new BigDecimal("50"), now));
        assertEquals("R-HIGH", service.verdictTop(new BigDecimal("50"), now).ruleCode());

        rows.clear();
        rows.add(rule(11L, "R-A", "4", "9", "18", "2025-01-01 00:00:00", null, 9, 0));
        rows.add(rule(12L, "R-B", "40", "60", "90", "2025-01-01 00:00:00", null, 9, 0));
        assertEquals("R-B", service.verdictTop(new BigDecimal("50"), now).ruleCode(),
                "先后序并列：代号字面序排在后面的为准");
    }

    @Test
    public void backfillUsesVersionEffectiveAtBatchSubmitTime() throws Exception {
        // 同一代号两版：旧版 4/9/18 交到 2025 元旦；新版 40/60/90 从 2025 元旦起
        rows.add(rule(1L, "R-A", "4", "9", "18", "2024-01-01 00:00:00", "2025-01-01 00:00:00", 10, 0));
        rows.add(rule(2L, "R-A", "40", "60", "90", "2025-01-01 00:00:00", null, 10, 0));
        // 前年那一批按提交当时（2024-06）生效的旧版判：50 分落第4档
        Date oldBatch = at("2024-06-01 00:00:00");
        assertEquals(4, service.evaluate("R-A", new BigDecimal("50"), oldBatch),
                "不许拿新版本判旧批，旧版 50 分封顶外落第4档");
        assertEquals(4, service.evaluateTop(new BigDecimal("50"), oldBatch));
        // 此刻按新版判：50 分落第2档
        Date now = at("2026-10-05 03:00:00");
        assertEquals(2, service.evaluate("R-A", new BigDecimal("50"), now));
        assertEquals(2, service.evaluateTop(new BigDecimal("50"), now));
        assertEquals(1L, service.verdictByCode("R-A", new BigDecimal("50"), oldBatch).getRule().getId());
        assertEquals(2L, service.verdictByCode("R-A", new BigDecimal("50"), now).getRule().getId());
    }

    @Test
    public void twoEntriesMustGiveSameLevelAtSameMoment() throws Exception {
        rows.add(rule(1L, "R-A", "4", "9", "18", "2025-01-01 00:00:00", null, 10, 0));
        rows.add(rule(2L, "R-B", "4", "9", "18", "2025-01-01 00:00:00", null, 5, 0));
        Date now = at("2026-10-05 03:00:00");
        for (String s : new String[]{"3.00", "4.00", "8.996", "9.00", "18.00", "100"}) {
            BigDecimal score = new BigDecimal(s);
            assertEquals(service.evaluateTop(score, now), service.evaluate("R-A", score, now),
                    "按标识取值与整表取优对 " + s + " 必须同档");
        }
    }

    @Test
    public void boardCountEqualsListSizeAndNullAtIsEmpty() {
        assertEquals(0, service.countAvailable(null));
        assertTrue(service.listAvailable(null).isEmpty());
        assertEquals(0, service.evaluateTop(new BigDecimal("50"), null));
    }

    private static void assertReasons(OadsRuleBoard board, String code, String expected) {
        for (OadsRuleBoard.DirtyRule d : board.getDirty()) {
            if (code.equals(d.getRuleCode())) {
                boolean hit = false;
                for (String reason : d.getReasons()) {
                    if (reason.contains(expected)) {
                        hit = true;
                    }
                }
                assertTrue(hit, code + " 应点名[" + expected + "]，实际：" + d.getReasons());
                return;
            }
        }
        fail("脏配置清单里没有 " + code);
    }
}

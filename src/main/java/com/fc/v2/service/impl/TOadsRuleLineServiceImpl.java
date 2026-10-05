package com.fc.v2.service.impl;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;

import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fc.v2.mapper.auto.TOadsRuleLineMapper;
import com.fc.v2.model.auto.TOadsRuleLine;
import com.fc.v2.model.custom.OadsRuleBoard;
import com.fc.v2.model.custom.OadsRuleVerdict;
import com.fc.v2.service.ITOadsRuleLineService;

/**
 * 牌面安全归层判据线 Service——全市统一分档口径的<strong>唯一裁决处</strong>。
 *
 * <p>本轮取哪条规则、没有可用规则时给不给说法、多条并列采信谁、折算分压在某个上限时
 * 落进哪一档，答案只从本类点出：页面与固化服务都不许再算第二遍，更不许先定档位再回头
 * 挑一条规则来圆。区级只在库里配置规则，不改写本类逻辑。
 *
 * <p>口径要点（与《牌面安全分档规则表》逐条对齐）：
 * <ul>
 *   <li>生效窗：起用那一一日不晚于本次询问（含当口）、交班那一一日晚于本次询问（止端不含）；
 *       交班空着的早年人工规则一律当长期有效；线的情形须为 0 在位，已撤下不参与。</li>
 *   <li>旧批按各自提交当时生效的版本判：时刻由入参 at 点出，本类不自取时钟。</li>
 *   <li>脏配置剔除：三个定值任一缺值、后一个没有严格大于前一个、封顶限值大过 100，
 *       本轮不予采信；提示点名到具体格，不许只讲"数值有问题"。</li>
 *   <li>定值与折算分一律先按两位小数、四舍五入取齐，取齐之后才贴区间。</li>
 *   <li>压线落号更大的档：score&lt;th1 为 1 档，&lt;th2 为 2 档，&lt;th3 为 3 档，余为 4 档。</li>
 *   <li>多条并届：先后序数值大的先说，并列再比代号字面序，排在后面的为准。</li>
 * </ul>
 *
 * @author fuce
 * @date 2026-09-14
 */
@Service
public class TOadsRuleLineServiceImpl implements ITOadsRuleLineService {

    /** 线的情形：0 在位（可用） */
    private static final int STATUS_ON = 0;

    /** 折算分量程上下限 */
    private static final BigDecimal SCORE_MIN = BigDecimal.ZERO;
    private static final BigDecimal SCORE_MAX = new BigDecimal("100");

    /** 并列次序：先后序降序；并列时代号字面序降序（排在后面的为准）；再并列用主键兜底，保证结果确定 */
    private static final Comparator<TOadsRuleLine> SPEAK_ORDER = new Comparator<TOadsRuleLine>() {
        @Override
        public int compare(TOadsRuleLine a, TOadsRuleLine b) {
            int pa = a.getPriority() == null ? 0 : a.getPriority();
            int pb = b.getPriority() == null ? 0 : b.getPriority();
            if (pa != pb) {
                return pb - pa;
            }
            String ca = a.getRuleCode() == null ? "" : a.getRuleCode();
            String cb = b.getRuleCode() == null ? "" : b.getRuleCode();
            int c = cb.compareTo(ca);
            if (c != 0) {
                return c;
            }
            long ia = a.getId() == null ? 0L : a.getId();
            long ib = b.getId() == null ? 0L : b.getId();
            return Long.compare(ib, ia);
        }
    };

    @javax.annotation.Resource
    private TOadsRuleLineMapper oadsRuleLineMapper;

    // ------------------------------------------------ 原值查询（原状，不收口）

    @Override
    public TOadsRuleLine selectTOadsRuleLineById(Long id) {
        return this.oadsRuleLineMapper.selectById(id);
    }

    // ------------------------------------------------ 两个老入口（形状不变，答案改由引擎给出）

    @Override
    public int evaluate(String ruleCode, BigDecimal input, Date at) {
        return verdictByCode(ruleCode, input, at).getLevel();
    }

    @Override
    public int evaluateTop(BigDecimal input, Date at) {
        return verdictTop(input, at).getLevel();
    }

    // ------------------------------------------------ 完整裁决（唯一引擎的两个入口）

    @Override
    public OadsRuleVerdict verdictByCode(String ruleCode, BigDecimal input, Date at) {
        if (ruleCode == null || ruleCode.trim().isEmpty() || at == null) {
            return OadsRuleVerdict.ungraded(null, String.format(OadsRuleVerdict.MSG_CODE_NO_VERSION,
                    ruleCode == null ? "" : ruleCode));
        }
        BigDecimal rounded = roundScore(input);
        if (rounded == null) {
            return OadsRuleVerdict.ungraded(null, OadsRuleVerdict.MSG_NO_SCORE);
        }
        if (outOfRange(input)) {
            return OadsRuleVerdict.ungraded(rounded, OadsRuleVerdict.MSG_OUT_OF_RANGE);
        }
        // 候选只按代号收，生效窗、撤下、脏配置三道筛与整表取优完全相同，
        // 两入口的差别只剩这一道代号过滤，结构上杜绝同点两问两档
        List<TOadsRuleLine> sameCode = new ArrayList<TOadsRuleLine>();
        List<String> dirtyReasons = null;
        for (TOadsRuleLine r : loadAllRows()) {
            if (!ruleCode.equals(r.getRuleCode()) || !inWindow(r, at)) {
                continue;
            }
            List<String> reasons = configFaults(r);
            if (reasons.isEmpty()) {
                sameCode.add(r);
            } else if (dirtyReasons == null) {
                dirtyReasons = reasons;
            }
        }
        if (sameCode.isEmpty()) {
            // 有在窗版本但全是脏配置：点名不予采信；压根没有在窗版本：取不到生效版本
            if (dirtyReasons != null) {
                return OadsRuleVerdict.ungraded(rounded,
                        "判据线[" + ruleCode + "]本轮不予采信：" + joinReasons(dirtyReasons));
            }
            return OadsRuleVerdict.ungraded(rounded,
                    String.format(OadsRuleVerdict.MSG_CODE_NO_VERSION, ruleCode));
        }
        sameCode.sort(SPEAK_ORDER);
        TOadsRuleLine picked = sameCode.get(0);
        return OadsRuleVerdict.of(levelOf(picked, rounded), picked, rounded);
    }

    @Override
    public OadsRuleVerdict verdictTop(BigDecimal input, Date at) {
        if (at == null) {
            return OadsRuleVerdict.ungraded(null, OadsRuleVerdict.MSG_NO_AVAILABLE);
        }
        BigDecimal rounded = roundScore(input);
        if (rounded == null) {
            return OadsRuleVerdict.ungraded(null, OadsRuleVerdict.MSG_NO_SCORE);
        }
        if (outOfRange(input)) {
            return OadsRuleVerdict.ungraded(rounded, OadsRuleVerdict.MSG_OUT_OF_RANGE);
        }
        // 可用清单、条数与本裁决同一次计算
        List<TOadsRuleLine> available = board(at).getAvailable();
        if (available.isEmpty()) {
            return OadsRuleVerdict.ungraded(rounded, OadsRuleVerdict.MSG_NO_AVAILABLE);
        }
        TOadsRuleLine picked = available.get(0);
        return OadsRuleVerdict.of(levelOf(picked, rounded), picked, rounded);
    }

    // ------------------------------------------------ 看板（清单/条数/脏配置同一次计算）

    @Override
    public OadsRuleBoard board(Date at) {
        OadsRuleBoard board = new OadsRuleBoard();
        board.setAt(at);
        if (at == null) {
            board.setCount(0);
            return board;
        }
        List<TOadsRuleLine> available = new ArrayList<TOadsRuleLine>();
        for (TOadsRuleLine r : loadAllRows()) {
            // 已撤下与不在生效窗的：在册但不进清单（按主键仍查得到），也不算脏配置
            if (!inWindow(r, at)) {
                continue;
            }
            List<String> reasons = configFaults(r);
            if (reasons.isEmpty()) {
                available.add(r);
            } else {
                board.getDirty().add(new OadsRuleBoard.DirtyRule(r.getId(), r.getRuleCode(), reasons));
            }
        }
        available.sort(SPEAK_ORDER);
        board.setAvailable(available);
        board.setCount(available.size());
        return board;
    }

    @Override
    public List<TOadsRuleLine> listAvailable(Date at) {
        return board(at).getAvailable();
    }

    @Override
    public boolean usable(Long id, Date at) {
        if (id == null || at == null) {
            return false;
        }
        for (TOadsRuleLine r : board(at).getAvailable()) {
            if (id.equals(r.getId())) {
                return true;
            }
        }
        return false;
    }

    @Override
    public int countAvailable(Date at) {
        return board(at).getCount();
    }

    @Override
    public TOadsRuleLine effectiveVersion(String ruleCode, Date at) {
        if (ruleCode == null || ruleCode.trim().isEmpty() || at == null) {
            return null;
        }
        List<TOadsRuleLine> inWindow = new ArrayList<TOadsRuleLine>();
        for (TOadsRuleLine r : loadAllRows()) {
            if (ruleCode.equals(r.getRuleCode()) && inWindow(r, at)) {
                inWindow.add(r);
            }
        }
        if (inWindow.isEmpty()) {
            return null;
        }
        inWindow.sort(SPEAK_ORDER);
        return inWindow.get(0);
    }

    // ------------------------------------------------ 口径零件（私有，旁处不得另抄一份）

    /** 全表在册行：删除标记为 0（或老数据留空）。生效窗在内存里比，不走 JDBC 时刻参数绑定。 */
    private List<TOadsRuleLine> loadAllRows() {
        QueryWrapper<TOadsRuleLine> qw = new QueryWrapper<TOadsRuleLine>();
        qw.and(w -> w.eq("del_flag", 0).or().isNull("del_flag"));
        List<TOadsRuleLine> rows = this.oadsRuleLineMapper.selectList(qw);
        return rows == null ? new ArrayList<TOadsRuleLine>() : rows;
    }

    /**
     * 在册且在窗：线的情形为 0 在位；起用那一一日不晚于 at（含当口）；
     * 交班那一一日晚于 at（止端不含），交班空着的早年人工规则按长期有效认（兼容分支，不得删）。
     */
    private boolean inWindow(TOadsRuleLine r, Date at) {
        if (r.getStatus() == null || r.getStatus() != STATUS_ON) {
            return false;
        }
        if (r.getEffStart() == null || r.getEffStart().after(at)) {
            return false;
        }
        if (r.getEffEnd() != null && !r.getEffEnd().after(at)) {
            return false;
        }
        return true;
    }

    /**
     * 脏配置点名，逐条写明毛病出在哪一格：
     * 三个定值任一缺值；后一个没有严格大于前一个；封顶限值大过 100。
     * 返回空清单即配置干净，可参与本轮。
     */
    private List<String> configFaults(TOadsRuleLine r) {
        List<String> faults = new ArrayList<String>();
        BigDecimal th1 = r.getTh1Max();
        BigDecimal th2 = r.getTh2Max();
        BigDecimal th3 = r.getTh3Max();
        if (th1 == null) {
            faults.add("起算限值(th1_max)那一格没有值");
        }
        if (th2 == null) {
            faults.add("够线限值(th2_max)那一格没有值");
        }
        if (th3 == null) {
            faults.add("封顶限值(th3_max)那一格没有值");
        }
        // 只在两格都有值时才比，缺值已经各自点名，不重复扣帽子
        if (th1 != null && th2 != null && th2.compareTo(th1) <= 0) {
            faults.add("够线限值(th2_max)没有大于它的前一格起算限值(th1_max)");
        }
        if (th2 != null && th3 != null && th3.compareTo(th2) <= 0) {
            faults.add("封顶限值(th3_max)没有大于它的前一格够线限值(th2_max)");
        }
        if (th3 != null && th3.compareTo(SCORE_MAX) > 0) {
            faults.add("封顶限值(th3_max)大过100，免得把脏值算成高档");
        }
        return faults;
    }

    /** 两位小数、四舍五入取齐；定值与折算分同此一把尺，取齐之后才贴区间 */
    private static BigDecimal align2(BigDecimal v) {
        return v.setScale(2, RoundingMode.HALF_UP);
    }

    /** 折算分取齐；压根没值返回 null（调用方按不分档处理） */
    private static BigDecimal roundScore(BigDecimal input) {
        return input == null ? null : align2(input);
    }

    /** 量程 0 到 100，出格的输入按不分档处理（按原报折算分量，不把取齐抹掉的越界当真） */
    private static boolean outOfRange(BigDecimal input) {
        return input.compareTo(SCORE_MIN) < 0 || input.compareTo(SCORE_MAX) > 0;
    }

    /**
     * 贴区间：阈值同样先两位取齐再比。恰好压在上限上的不进号更小的档，落号更大的档。
     * 以 4/9/18 为例：3.00→1，8.90→2，9.00→3（不是 2），9.10→3，18.00→4。
     */
    private static int levelOf(TOadsRuleLine r, BigDecimal score) {
        BigDecimal th1 = align2(r.getTh1Max());
        BigDecimal th2 = align2(r.getTh2Max());
        BigDecimal th3 = align2(r.getTh3Max());
        if (score.compareTo(th1) < 0) {
            return 1;
        }
        if (score.compareTo(th2) < 0) {
            return 2;
        }
        if (score.compareTo(th3) < 0) {
            return 3;
        }
        return 4;
    }

    private static String joinReasons(List<String> reasons) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < reasons.size(); i++) {
            if (i > 0) {
                sb.append("；");
            }
            sb.append(reasons.get(i));
        }
        return sb.toString();
    }
}

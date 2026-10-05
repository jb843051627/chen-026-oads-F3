package com.fc.v2.service;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

import com.fc.v2.model.auto.TOadsRuleLine;
import com.fc.v2.model.custom.OadsRuleBoard;
import com.fc.v2.model.custom.OadsRuleVerdict;

/**
 * 牌面安全归层判据线 Service接口。
 *
 * <p>全市统一实现的分档口径只落在本接口的实现这一处，区级只做配置。
 * 按标识取值（{@link #evaluate}）与整表取优（{@link #evaluateTop}）共用同一套
 * 生效窗、脏配置剔除、两位取齐与贴区间逻辑，同一点位同一时刻两问必须同档。
 *
 * @author fuce
 * @date 2026-09-14
 */
public interface ITOadsRuleLineService {

    /**
     * 原值查询，收什么回什么一概不改：已撤下（status=1）的在册行按主键照样查得到，
     * 不经过本轮可用口径过滤。今后要按路段、按季度另取维度，请另写一套承接，不动它的模样。
     */
    TOadsRuleLine selectTOadsRuleLineById(Long id);

    /**
     * 单规则求值（原方法，形状不变）：按 ruleCode 在 at 时刻生效的版本判档（1..4）。
     * 无生效版本 / 输入越界或缺失 / 参数缺失 / 该代号只剩脏配置，一律返回 0（不分档）。
     */
    int evaluate(String ruleCode, BigDecimal input, Date at);

    /** 多规则求值（原方法，形状不变）：at 时刻可用规则中先后序最前者的档位；无可用规则返回 0 */
    int evaluateTop(BigDecimal input, Date at);

    /**
     * 按标识取值的完整裁决：落档 + 本轮说话那条规则 + 取齐后的折算分 + 不分档时的说法。
     * 与 {@link #verdictTop} 走同一个引擎，只在候选集合上差一个代号过滤。
     */
    OadsRuleVerdict verdictByCode(String ruleCode, BigDecimal input, Date at);

    /**
     * 整表取优的完整裁决：at 时刻在册、在窗、可用（非脏配置）的规则里，
     * 先比先后序（数值大的先说），并列再比代号字面序（排在后面的为准），取唯一一条贴区间。
     */
    OadsRuleVerdict verdictTop(BigDecimal input, Date at);

    /**
     * 一次计算出看板：可用清单、清单上方条数、不予采信脏配置的点名，出自同一轮。
     * 已撤下与不在生效窗的在册行不进这份清单（仍可经 {@link #selectTOadsRuleLineById} 查到）。
     */
    OadsRuleBoard board(Date at);

    /** at 时刻可用规则（先后序降序、并列时代号字面序排在后面的在前） */
    List<TOadsRuleLine> listAvailable(Date at);

    /** 单条规则在 at 时刻是否可用（批量导入/报表等旁路复用，口径须与定位/列表一致） */
    boolean usable(Long id, Date at);

    /** at 时刻可用规则条数（与看板同一次计算，等于可用清单行数） */
    int countAvailable(Date at);

    /**
     * 回到 at 那一刻，这个代号能不能取到生效版本（在册、在窗、未撤下；不看配置脏不脏——
     * 脏配置是"不予采信"，不是"取不到版本"）。供孤儿点位清单与两入口一致性核对使用。
     * 同代号多个在窗版本时，采信次序（先后序→代号字面序）与裁决同一把尺。
     */
    TOadsRuleLine effectiveVersion(String ruleCode, Date at);
}

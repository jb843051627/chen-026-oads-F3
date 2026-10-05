package com.fc.v2.model.custom;

import com.fc.v2.model.auto.TOadsRuleLine;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 牌面安全分档一次裁决的交代。
 *
 * <p>本轮取哪条规则、没有可用规则时给什么说法、多条并列采信谁、折算分压在哪个上限上，
 * 答案只从服务端唯一裁决处点出。按标识取值与整表取优两个入口共用同一套区间比对，
 * 结构上保证同一点位同一时刻两问同档。
 *
 * @author fuce
 * @date 2026-10-05
 */
@ApiModel(value = "OadsRuleVerdict", description = "牌面安全分档裁决结果")
public class OadsRuleVerdict implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 折算分超出量程（0 到 100 之外）：按不分档处理 */
    public static final String MSG_OUT_OF_RANGE = "折算分超出量程0到100，按不分档处理";
    /** 折算分压根没给：按不分档处理 */
    public static final String MSG_NO_SCORE = "折算分没有值，按不分档处理";
    /** 整表取优：此刻一条可用规则也没有 */
    public static final String MSG_NO_AVAILABLE = "此刻没有一条可用的判据线，按不分档处理";
    /** 按标识取值：页面挂着代号，回到本次询问那一刻却取不到生效版本 */
    public static final String MSG_CODE_NO_VERSION = "判据线[%s]在本次询问时刻取不到生效版本";

    /** 落档 0不分档 1..4 */
    @ApiModelProperty(value = "落档 0不分档 1..4")
    private int level;

    /** 本轮说话的那条规则；不分档时为 null，固化时代号与起用日逐字取自它 */
    @ApiModelProperty(value = "本轮说话的那条规则；不分档为null")
    private TOadsRuleLine rule;

    /** 实际贴区间用的折算分（已两位小数、四舍五入取齐） */
    @ApiModelProperty(value = "取齐后的折算分")
    private BigDecimal roundedScore;

    /** 没有可用规则/取不到版本/脏配置时的说法原文 */
    @ApiModelProperty(value = "说法原文")
    private String message;

    public static OadsRuleVerdict ungraded(BigDecimal roundedScore, String message) {
        OadsRuleVerdict v = new OadsRuleVerdict();
        v.level = 0;
        v.roundedScore = roundedScore;
        v.message = message;
        return v;
    }

    public static OadsRuleVerdict of(int level, TOadsRuleLine rule, BigDecimal roundedScore) {
        OadsRuleVerdict v = new OadsRuleVerdict();
        v.level = level;
        v.rule = rule;
        v.roundedScore = roundedScore;
        return v;
    }

    /** 本轮说话规则的代号；不分档为 null（回写时一并留空） */
    public String ruleCode() {
        return rule == null ? null : rule.getRuleCode();
    }

    /** 本轮说话规则的起用那一一日；不分档为 null（回写时一并留空） */
    public java.util.Date effStart() {
        return rule == null ? null : rule.getEffStart();
    }

    public int getLevel() {
        return level;
    }

    public void setLevel(int level) {
        this.level = level;
    }

    public TOadsRuleLine getRule() {
        return rule;
    }

    public void setRule(TOadsRuleLine rule) {
        this.rule = rule;
    }

    public BigDecimal getRoundedScore() {
        return roundedScore;
    }

    public void setRoundedScore(BigDecimal roundedScore) {
        this.roundedScore = roundedScore;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}

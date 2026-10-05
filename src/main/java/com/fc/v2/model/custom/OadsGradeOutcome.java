package com.fc.v2.model.custom;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

/**
 * 一处点位一次安全分档固化的交代。
 *
 * <p>固化进点位那一行的三列（档位、判据线代号、起用那一一日）与追加的留痕行同生在一个
 * 事务里，内容逐字取自本次裁决，不许取自邻近规则或上一季度。不分档（level=0）时
 * 代号与起用日两格一并留空。
 *
 * @author fuce
 * @date 2026-10-05
 */
@ApiModel(value = "OadsGradeOutcome", description = "点位安全分档固化结果")
public class OadsGradeOutcome implements Serializable {
    private static final long serialVersionUID = 1L;

    @ApiModelProperty(value = "点位主键")
    private Long spotId;

    @ApiModelProperty(value = "点位码")
    private String spotNo;

    /** 落档 0不分档 1..4 */
    @ApiModelProperty(value = "落档 0不分档 1..4")
    private int level;

    /** 本次照哪条判据线（与点位行、留痕行逐字相同）；不分档为 null */
    @ApiModelProperty(value = "本次照哪条判据线；不分档留空")
    private String ruleCode;

    /** 该判据线起用那一一日（逐字固化）；不分档为 null */
    @ApiModelProperty(value = "该判据线起用那一一日；不分档留空")
    private Date effStart;

    /** 实际贴区间的折算分（两位小数取齐后） */
    @ApiModelProperty(value = "取齐后的折算分")
    private BigDecimal roundedScore;

    /** 本次询问/固化时刻 */
    @ApiModelProperty(value = "本次询问时刻")
    private Date judgedAt;

    /** 不分档或不予采信时的说法 */
    @ApiModelProperty(value = "说法原文")
    private String message;

    public Long getSpotId() {
        return spotId;
    }

    public void setSpotId(Long spotId) {
        this.spotId = spotId;
    }

    public String getSpotNo() {
        return spotNo;
    }

    public void setSpotNo(String spotNo) {
        this.spotNo = spotNo;
    }

    public int getLevel() {
        return level;
    }

    public void setLevel(int level) {
        this.level = level;
    }

    public String getRuleCode() {
        return ruleCode;
    }

    public void setRuleCode(String ruleCode) {
        this.ruleCode = ruleCode;
    }

    public Date getEffStart() {
        return effStart;
    }

    public void setEffStart(Date effStart) {
        this.effStart = effStart;
    }

    public BigDecimal getRoundedScore() {
        return roundedScore;
    }

    public void setRoundedScore(BigDecimal roundedScore) {
        this.roundedScore = roundedScore;
    }

    public Date getJudgedAt() {
        return judgedAt;
    }

    public void setJudgedAt(Date judgedAt) {
        this.judgedAt = judgedAt;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}

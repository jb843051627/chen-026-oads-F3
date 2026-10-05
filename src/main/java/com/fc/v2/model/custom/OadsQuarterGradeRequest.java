package com.fc.v2.model.custom;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 每季度安全提醒一批点位的请求体：点位主键到折算分的对照，外加本次询问时刻。
 * 追补前年那一批时 at 传那一批各自提交当时的时刻，按当时生效版本判。
 *
 * @author fuce
 * @date 2026-10-05
 */
@ApiModel(value = "OadsQuarterGradeRequest", description = "季度安全分档批量固化请求")
public class OadsQuarterGradeRequest implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 本次询问时刻，字符串形如 2026-10-05 03:00:00（由调用方显式点出） */
    @ApiModelProperty(value = "本次询问时刻 yyyy-MM-dd HH:mm:ss")
    private String at;

    /** 操作人（留痕创建者） */
    @ApiModelProperty(value = "操作人")
    private String operator;

    /** 点位主键（字符串形式给出，JSON 对像键只能是字符串）到折算分的对照 */
    @ApiModelProperty(value = "点位主键到折算分的对照")
    private Map<String, BigDecimal> scores = new LinkedHashMap<String, BigDecimal>();

    public String getAt() {
        return at;
    }

    public void setAt(String at) {
        this.at = at;
    }

    public String getOperator() {
        return operator;
    }

    public void setOperator(String operator) {
        this.operator = operator;
    }

    public Map<String, BigDecimal> getScores() {
        return scores;
    }

    public void setScores(Map<String, BigDecimal> scores) {
        this.scores = scores;
    }
}

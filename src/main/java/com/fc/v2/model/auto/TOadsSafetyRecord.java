package com.fc.v2.model.auto;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

/**
 * 牌面安全分档判定留痕对象 t_oads_safety_record
 *
 * <p>一次判定一行，只许追加不许改。固化进点位那一行的三列与本行同生（同一事务）。
 *
 * @author fuce
 * @date 2026-10-05
 */
@TableName("t_oads_safety_record")
@ApiModel(value = "TOadsSafetyRecord", description = "牌面安全分档判定留痕")
public class TOadsSafetyRecord implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 主键 */
    @TableId(type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    @ApiModelProperty(value = "主键")
    private Long id;

    /** 点位底册行 */
    @TableField("spot_id")
    @ApiModelProperty(value = "点位底册行")
    private Long spotId;

    /** 点位码（判定当时自点位主档冗余） */
    @TableField("spot_no")
    @ApiModelProperty(value = "点位码")
    private String spotNo;

    /** 本次照哪条判据线（与固化进点位的那一对逐字相同）；不分档留空 */
    @TableField("rule_code")
    @ApiModelProperty(value = "本次照哪条判据线；不分档留空")
    private String ruleCode;

    /** 该判据线起用那一一日（逐字固化）；不分档留空 */
    @TableField("eff_start")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @ApiModelProperty(value = "该判据线起用那一一日；不分档留空")
    private Date effStart;

    /** 本次落档 0不分档 1..4 */
    @TableField("safe_level")
    @ApiModelProperty(value = "本次落档 0不分档 1..4")
    private Integer safeLevel;

    /** 本次比对用的折算分（已按两位小数四舍五入取齐） */
    @TableField("conv_score")
    @ApiModelProperty(value = "本次比对用的折算分（两位小数取齐）")
    private BigDecimal convScore;

    /** 本次询问时刻（按此刻生效版本判，旧批按各自提交当时版本判） */
    @TableField("judged_at")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @ApiModelProperty(value = "本次询问时刻")
    private Date judgedAt;

    /** 本次所属季度码 如2026Q4 */
    @TableField("quarter_code")
    @ApiModelProperty(value = "本次所属季度码")
    private String quarterCode;

    /** 创建者 */
    @TableField(value = "create_by", fill = FieldFill.INSERT)
    @ApiModelProperty(value = "创建者")
    private String createBy;

    /** 创建时间 */
    @TableField(value = "create_time", fill = FieldFill.INSERT)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @ApiModelProperty(value = "创建时间")
    private Date createTime;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

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

    public Integer getSafeLevel() {
        return safeLevel;
    }

    public void setSafeLevel(Integer safeLevel) {
        this.safeLevel = safeLevel;
    }

    public BigDecimal getConvScore() {
        return convScore;
    }

    public void setConvScore(BigDecimal convScore) {
        this.convScore = convScore;
    }

    public Date getJudgedAt() {
        return judgedAt;
    }

    public void setJudgedAt(Date judgedAt) {
        this.judgedAt = judgedAt;
    }

    public String getQuarterCode() {
        return quarterCode;
    }

    public void setQuarterCode(String quarterCode) {
        this.quarterCode = quarterCode;
    }

    public String getCreateBy() {
        return createBy;
    }

    public void setCreateBy(String createBy) {
        this.createBy = createBy;
    }

    public Date getCreateTime() {
        return createTime;
    }

    public void setCreateTime(Date createTime) {
        this.createTime = createTime;
    }
}

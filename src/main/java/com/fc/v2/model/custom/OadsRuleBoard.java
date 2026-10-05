package com.fc.v2.model.custom;

import com.fc.v2.model.auto.TOadsRuleLine;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 牌面安全判据线看板：一次计算的三处出口。
 *
 * <p>页面上那份可用清单、清单上方那个条数、逐条判定（含脏配置为什么不予采信），
 * 必须出自同一次计算——本对象就是那一次计算的留档，不许各起一轮。
 * 已撤下（status=1）的在册行不进这份清单，按主键仍可经原值查询查到。
 *
 * @author fuce
 * @date 2026-10-05
 */
@ApiModel(value = "OadsRuleBoard", description = "牌面安全判据线看板（清单/条数/脏配置同源）")
public class OadsRuleBoard implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 本次询问时刻（java.util.Date，由调用方点出，看板不自带时钟） */
    @ApiModelProperty(value = "本次询问时刻")
    private java.util.Date at;

    /** 可用清单：先后序降序、并列时代号字面序排在后面的在前 */
    @ApiModelProperty(value = "可用清单")
    private List<TOadsRuleLine> available = new ArrayList<TOadsRuleLine>();

    /** 条数：必须等于可用清单的行数 */
    @ApiModelProperty(value = "可用条数")
    private int count;

    /** 脏配置点名：在在册在窗但本轮不予采信的规则，逐条写明毛病出在哪一格 */
    @ApiModelProperty(value = "不予采信的脏配置及点名原因")
    private List<DirtyRule> dirty = new ArrayList<DirtyRule>();

    @ApiModel(value = "OadsRuleBoard.DirtyRule", description = "不予采信的脏配置")
    public static class DirtyRule implements Serializable {
        private static final long serialVersionUID = 1L;

        @ApiModelProperty(value = "主键（仍是在册数据，可按主键核对）")
        private Long id;
        @ApiModelProperty(value = "判据线代号")
        private String ruleCode;
        @ApiModelProperty(value = "点名原因：缺的是哪一格、哪一格没大于前一格、哪一格大过100")
        private List<String> reasons = new ArrayList<String>();

        public DirtyRule() {
        }

        public DirtyRule(Long id, String ruleCode, List<String> reasons) {
            this.id = id;
            this.ruleCode = ruleCode;
            this.reasons = reasons;
        }

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }

        public String getRuleCode() {
            return ruleCode;
        }

        public void setRuleCode(String ruleCode) {
            this.ruleCode = ruleCode;
        }

        public List<String> getReasons() {
            return reasons;
        }

        public void setReasons(List<String> reasons) {
            this.reasons = reasons;
        }
    }

    public java.util.Date getAt() {
        return at;
    }

    public void setAt(java.util.Date at) {
        this.at = at;
    }

    public List<TOadsRuleLine> getAvailable() {
        return available;
    }

    public void setAvailable(List<TOadsRuleLine> available) {
        this.available = available;
    }

    public int getCount() {
        return count;
    }

    public void setCount(int count) {
        this.count = count;
    }

    public List<DirtyRule> getDirty() {
        return dirty;
    }

    public void setDirty(List<DirtyRule> dirty) {
        this.dirty = dirty;
    }
}

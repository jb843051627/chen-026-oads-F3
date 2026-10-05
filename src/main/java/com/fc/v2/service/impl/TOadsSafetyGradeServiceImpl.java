package com.fc.v2.service.impl;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

import javax.annotation.Resource;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
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
import com.fc.v2.service.ITOadsSafetyGradeService;
import com.fc.v2.util.OadsWorkdayUtil;

/**
 * 牌面安全分档固化 Service。
 *
 * <p>本类不算档——档位只从 {@link ITOadsRuleLineService} 这唯一裁决处取。取到裁决后，
 * 点位行三列固化与留痕插入同成一个事务；固化进点位的代号与起用日逐字照抄裁决给出的那一对。
 * 一次询问给两个档位本身就是缺陷：两个入口各问一遍，对不齐就整笔打回，一行不写。
 *
 * @author fuce
 * @date 2026-10-05
 */
@Service
public class TOadsSafetyGradeServiceImpl implements ITOadsSafetyGradeService {

    /** 不分档：固化时代号与起用日两格一并留空 */
    private static final int LEVEL_NONE = 0;

    @Resource
    private TOadsSpotMapper oadsSpotMapper;
    @Resource
    private TOadsSafetyRecordMapper oadsSafetyRecordMapper;
    @Resource
    private ITOadsRuleLineService oadsRuleLineService;

    /** 自注入代理：季度批量里每处点位各开一个事务，单点站不住不拦整批 */
    @Autowired
    @Lazy
    private ITOadsSafetyGradeService self;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OadsGradeOutcome gradeByRuleCode(Long spotId, String ruleCode, BigDecimal score, Date at, String operator) {
        if (ruleCode == null || ruleCode.trim().isEmpty()) {
            throw new SafetyRuleException("按标识取值须点出判据线代号");
        }
        TOadsSpot spot = requireSpot(spotId);
        OadsRuleVerdict byCode = oadsRuleLineService.verdictByCode(ruleCode, score, at);
        OadsRuleVerdict byTop = oadsRuleLineService.verdictTop(score, at);
        // 同一时刻两个入口必须同档；对不齐即为缺陷，不许先定档再回头挑规则圆场
        if (byCode.getLevel() != byTop.getLevel()) {
            throw new SafetyRuleException("判据线[" + ruleCode + "]按标识取值落第" + byCode.getLevel()
                    + "档，整表取优落第" + byTop.getLevel() + "档，两问对不齐，本笔不固化");
        }
        return persist(spot, byCode, at, operator);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OadsGradeOutcome gradeTop(Long spotId, BigDecimal score, Date at, String operator) {
        TOadsSpot spot = requireSpot(spotId);
        OadsRuleVerdict verdict = oadsRuleLineService.verdictTop(score, at);
        return persist(spot, verdict, at, operator);
    }

    @Override
    public List<OadsGradeOutcome> gradeQuarter(Map<Long, BigDecimal> scores, Date at, String operator) {
        if (at == null) {
            throw new SafetyRuleException("须点出本次询问时刻；追补旧批传各批提交当时的时刻");
        }
        List<OadsGradeOutcome> outcomes = new ArrayList<OadsGradeOutcome>();
        if (scores == null) {
            return outcomes;
        }
        for (Map.Entry<Long, BigDecimal> entry : scores.entrySet()) {
            // 经代理调用：每处点位一个事务，固化+留痕同成同败；单点异常不拦剩下的点位
            try {
                outcomes.add(self.gradeTop(entry.getKey(), entry.getValue(), at, operator));
            } catch (RuntimeException e) {
                OadsGradeOutcome skip = new OadsGradeOutcome();
                skip.setSpotId(entry.getKey());
                skip.setJudgedAt(at);
                skip.setLevel(LEVEL_NONE);
                skip.setMessage(e.getMessage());
                outcomes.add(skip);
            }
        }
        return outcomes;
    }

    @Override
    public List<TOadsSpot> listOrphanSpots(Date at) {
        if (at == null) {
            throw new SafetyRuleException("须点出回到哪一刻核生效版本");
        }
        List<TOadsSpot> orphans = new ArrayList<TOadsSpot>();
        List<TOadsSpot> spots = oadsSpotMapper.selectList(new QueryWrapper<TOadsSpot>()
                .eq("del_flag", 0).isNotNull("safe_rule_code")
                .and(w -> w.isNotNull("safe_rule_code").ne("safe_rule_code", "")));
        for (TOadsSpot spot : spots) {
            TOadsRuleLine version = oadsRuleLineService.effectiveVersion(spot.getSafeRuleCode(), at);
            if (version == null) {
                orphans.add(spot);
            }
        }
        return orphans;
    }

    // ------------------------------------------------ 同成一个事务的固化与留痕

    /**
     * 把裁决落进点位行并追加留痕：三步共用裁决给出的那一对代号/起用日，
     * 任一步失败整体回滚——不许只挪档位不留痕，也不许留痕照抄别处的值。
     */
    private OadsGradeOutcome persist(TOadsSpot spot, OadsRuleVerdict verdict, Date at, String operator) {
        int level = verdict.getLevel();
        String ruleCode = verdict.ruleCode();
        Date effStart = verdict.effStart();

        UpdateWrapper<TOadsSpot> uw = new UpdateWrapper<TOadsSpot>();
        uw.eq("id", spot.getId()).eq("del_flag", 0)
                .set("safe_level", level)
                .set("safe_rule_code", ruleCode)
                .set("safe_eff_start", effStart)
                .set("update_by", operator)
                .set("update_time", at);
        int rows = oadsSpotMapper.update(null, uw);
        if (rows == 0) {
            throw new SafetyRuleException("点位[" + spot.getSpotNo() + "]已不在册，固化打回");
        }

        TOadsSafetyRecord record = new TOadsSafetyRecord();
        record.setSpotId(spot.getId());
        record.setSpotNo(spot.getSpotNo());
        // 逐字照抄本次裁决那一对，不取邻近规则、不取上一季度
        record.setRuleCode(ruleCode);
        record.setEffStart(effStart);
        record.setSafeLevel(level);
        record.setConvScore(verdict.getRoundedScore());
        record.setJudgedAt(at);
        record.setQuarterCode(OadsWorkdayUtil.quarterCode(at));
        record.setCreateBy(operator);
        record.setCreateTime(at);
        oadsSafetyRecordMapper.insert(record);

        OadsGradeOutcome outcome = new OadsGradeOutcome();
        outcome.setSpotId(spot.getId());
        outcome.setSpotNo(spot.getSpotNo());
        outcome.setLevel(level);
        outcome.setRuleCode(ruleCode);
        outcome.setEffStart(effStart);
        outcome.setRoundedScore(verdict.getRoundedScore());
        outcome.setJudgedAt(at);
        outcome.setMessage(verdict.getMessage());
        return outcome;
    }

    private TOadsSpot requireSpot(Long spotId) {
        if (spotId == null) {
            throw new SafetyRuleException("须点出点位主键");
        }
        TOadsSpot spot = oadsSpotMapper.selectById(spotId);
        if (spot == null || (spot.getDelFlag() != null && spot.getDelFlag() != 0)) {
            throw new SafetyRuleException("点位底册上查无此行：" + spotId);
        }
        return spot;
    }
}

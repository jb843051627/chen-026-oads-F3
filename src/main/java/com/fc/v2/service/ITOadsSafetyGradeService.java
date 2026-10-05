package com.fc.v2.service;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;
import java.util.Map;

import com.fc.v2.model.auto.TOadsSpot;
import com.fc.v2.model.custom.OadsGradeOutcome;

/**
 * 牌面安全分档固化 Service：把唯一裁决处给出的档位落进点位行，并同趟留痕。
 *
 * <p>固化那一步与留痕那一步同成一个事务：点位行的档位、判据线代号、起用那一一日三列与
 * 留痕表新行逐字同源，取自邻近规则或上一季度的一律算不一致。追补旧批时把各批提交当时的
 * 时刻作 at 传入，按当时生效版本判，不许拿新版本判旧批。
 *
 * @author fuce
 * @date 2026-10-05
 */
public interface ITOadsSafetyGradeService {

    /**
     * 按标识取值入口：照页面挂着的判据线代号裁决并固化。
     * 同一时刻再走一遍整表取优，两问档不一致即为缺陷，抛异常、一行都不写。
     */
    OadsGradeOutcome gradeByRuleCode(Long spotId, String ruleCode, BigDecimal score, Date at, String operator);

    /**
     * 整表取优入口：at 时刻可用规则中先后序最前者裁决并固化。
     * 每季度安全提醒按此入口逐点跑；追补旧批 at 传各批提交当时的时刻。
     */
    OadsGradeOutcome gradeTop(Long spotId, BigDecimal score, Date at, String operator);

    /**
     * 一批点位逐点整表取优：一处点位一个事务（固化+留痕同成同败），
     * 单点站不住不拦整批，该点的说法收在返回行里。scores 为点位主键到折算分的对照。
     */
    List<OadsGradeOutcome> gradeQuarter(Map<Long, BigDecimal> scores, Date at, String operator);

    /**
     * 孤儿点位清单：页面上挂着判据线代号、回到 at 那一刻却取不到生效版本
     * （代号已撤下、交班已过或从没立过）的在册点位，逐条列出。
     */
    List<TOadsSpot> listOrphanSpots(Date at);
}

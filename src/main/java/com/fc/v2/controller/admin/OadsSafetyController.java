package com.fc.v2.controller.admin;

import java.math.BigDecimal;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;

import com.fc.v2.common.base.BaseController;
import com.fc.v2.common.domain.AjaxResult;
import com.fc.v2.common.log.Log;
import com.fc.v2.model.custom.OadsQuarterGradeRequest;
import com.fc.v2.service.ITOadsRuleLineService;
import com.fc.v2.service.ITOadsSafetyGradeService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

/**
 * 牌面安全分档 Controller（quarterly-safety 形状）。
 *
 * <p>本类不承载任何分档口径：档位、采信规则、脏配置剔除全部在服务端唯一裁决处，
 * 这里只把询问时刻、折算分与点位递进去，把同一次计算的结果原样递出来。
 *
 * @author fuce
 * @date 2026-10-05
 */
@Api(value = "牌面安全分档")
@Controller
@RequestMapping("/oadsSafety")
public class OadsSafetyController extends BaseController {

    @Autowired
    private ITOadsRuleLineService oadsRuleLineService;
    @Autowired
    private ITOadsSafetyGradeService oadsSafetyGradeService;

    /** 时刻解析只用这一把尺（yyyy-MM-dd HH:mm:ss），页面与服务端按墙钟字符串对齐 */
    private static final String TS_PATTERN = "yyyy-MM-dd HH:mm:ss";

    @Log(title = "牌面安全判据线看板", action = "board")
    @ApiOperation(value = "可用清单/条数/脏配置（同一次计算）", notes = "可用清单/条数/脏配置（同一次计算）")
    @GetMapping("/rule/board")
    @RequiresPermissions("oads:ruleLine:board")
    @ResponseBody
    public AjaxResult board(@RequestParam("at") @DateTimeFormat(pattern = TS_PATTERN) Date at) {
        return AjaxResult.successData(200, oadsRuleLineService.board(at));
    }

    @Log(title = "判据线原值查询", action = "view")
    @ApiOperation(value = "按主键原值查询（已撤下的在册行照样查得到）", notes = "按主键原值查询")
    @GetMapping("/rule/view/{id}")
    @RequiresPermissions("oads:ruleLine:view")
    @ResponseBody
    public AjaxResult viewRule(@PathVariable("id") Long id) {
        return AjaxResult.successData(200, oadsRuleLineService.selectTOadsRuleLineById(id));
    }

    @Log(title = "点位安全分档（按标识取值）", action = "gradeByCode")
    @ApiOperation(value = "按页面挂着的判据线代号码固化，并与整表取优对档", notes = "按标识取值固化")
    @PostMapping("/gradeByCode")
    @RequiresPermissions("oads:safety:grade")
    @ResponseBody
    public AjaxResult gradeByCode(@RequestParam("spotId") Long spotId,
                                  @RequestParam("ruleCode") String ruleCode,
                                  @RequestParam("score") BigDecimal score,
                                  @RequestParam("at") @DateTimeFormat(pattern = TS_PATTERN) Date at,
                                  @RequestParam(value = "operator", required = false) String operator) {
        return AjaxResult.successData(200,
                oadsSafetyGradeService.gradeByRuleCode(spotId, ruleCode, score, at, operator));
    }

    @Log(title = "点位安全分档（整表取优）", action = "gradeTop")
    @ApiOperation(value = "按 at 时刻先后序最前者裁决并固化", notes = "整表取优固化")
    @PostMapping("/gradeTop")
    @RequiresPermissions("oads:safety:grade")
    @ResponseBody
    public AjaxResult gradeTop(@RequestParam("spotId") Long spotId,
                               @RequestParam("score") BigDecimal score,
                               @RequestParam("at") @DateTimeFormat(pattern = TS_PATTERN) Date at,
                               @RequestParam(value = "operator", required = false) String operator) {
        return AjaxResult.successData(200,
                oadsSafetyGradeService.gradeTop(spotId, score, at, operator));
    }

    @Log(title = "季度安全提醒批量分档", action = "gradeQuarter")
    @ApiOperation(value = "一批点位逐点整表取优，单点站不住不拦整批", notes = "季度批量固化")
    @PostMapping("/gradeQuarter")
    @RequiresPermissions("oads:safety:grade")
    @ResponseBody
    public AjaxResult gradeQuarter(@RequestBody OadsQuarterGradeRequest request) throws ParseException {
        if (request == null || request.getAt() == null || request.getAt().trim().isEmpty()) {
            return AjaxResult.error("须点出本次询问时刻");
        }
        Date at = new SimpleDateFormat(TS_PATTERN).parse(request.getAt().trim());
        Map<Long, BigDecimal> scores = new LinkedHashMap<Long, BigDecimal>();
        for (Map.Entry<String, BigDecimal> e : request.getScores().entrySet()) {
            scores.put(Long.valueOf(e.getKey()), e.getValue());
        }
        return AjaxResult.successData(200,
                oadsSafetyGradeService.gradeQuarter(scores, at, request.getOperator()));
    }

    @Log(title = "取不到生效版本的孤儿点位清单", action = "orphans")
    @ApiOperation(value = "挂着代号、回到 at 那一刻却取不到生效版本的在册点位", notes = "孤儿点位清单")
    @GetMapping("/orphans")
    @RequiresPermissions("oads:safety:grade")
    @ResponseBody
    public AjaxResult orphans(@RequestParam("at") @DateTimeFormat(pattern = TS_PATTERN) Date at) {
        return AjaxResult.successData(200, oadsSafetyGradeService.listOrphanSpots(at));
    }
}

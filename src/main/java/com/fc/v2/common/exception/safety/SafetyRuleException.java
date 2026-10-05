package com.fc.v2.common.exception.safety;

/**
 * 牌面安全分档口径违例：点位不存在、两个入口对同一处点位给出不同档等缺陷情形。
 *
 * <p>消息原样回到页面，作为这一笔没落成的交代。
 *
 * @author fuce
 * @date 2026-10-05
 */
public class SafetyRuleException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public SafetyRuleException(String message) {
        super(message);
    }
}

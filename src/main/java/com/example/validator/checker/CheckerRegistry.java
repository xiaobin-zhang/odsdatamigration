package com.example.validator.checker;

import com.example.validator.common.CheckType;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Checker 插件注册表。
 *
 * <p>职责：收集 Spring 容器中的所有 {@link ValidationChecker}，并按 {@link CheckType} 提供查找能力。</p>
 *
 * @author Codex
 * @since 2026-06-03
 */
@Component
public class CheckerRegistry {
    private final Map<CheckType, ValidationChecker> checkerMap = new EnumMap<CheckType, ValidationChecker>(CheckType.class);

    /**
     * 创建 Checker 注册表。
     *
     * @param checkers Spring 自动注入的 Checker 插件列表
     */
    public CheckerRegistry(List<ValidationChecker> checkers) {
        // Checker 通过 Spring Bean 自动注册，任务生成器只依赖注册表。
        // 后续新增校验类型时，只需要新增实现类并声明为 Bean，不需要改执行主流程。
        for (ValidationChecker checker : checkers) {
            checkerMap.put(checker.getType(), checker);
        }
    }

    /**
     * 获取指定类型的 Checker。
     *
     * @param type Checker 类型
     * @return Checker 插件实例
     */
    public ValidationChecker getRequired(CheckType type) {
        ValidationChecker checker = checkerMap.get(type);
        if (checker == null) {
            throw new IllegalArgumentException("未找到 Checker 插件: " + type);
        }
        return checker;
    }

    /**
     * 获取全部已注册 Checker。
     *
     * @return Checker 类型到插件实例的映射
     */
    public Map<CheckType, ValidationChecker> getCheckerMap() {
        return checkerMap;
    }
}

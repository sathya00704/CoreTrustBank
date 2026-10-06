package com.coretrust.bank.common.util;

import com.coretrust.bank.common.exception.BusinessException;
import com.coretrust.bank.common.exception.ErrorCode;
import org.springframework.data.domain.Sort;

import java.util.Set;
import java.util.stream.Collectors;

public final class SortValidator {

    private SortValidator() {
    }

    public static void requireAllowed(Sort sort, Set<String> allowed) {
        for (Sort.Order order : sort) {
            if (!allowed.contains(order.getProperty())) {
                throw new BusinessException(ErrorCode.VALIDATION_FAILED,
                        "Sorting by '%s' is not allowed. Allowed: %s".formatted(order.getProperty(),
                                allowed.stream().sorted().collect(Collectors.joining(", "))));
            }
        }
    }
}
package com.qiujie.dto.support;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 分页参数校验单测（C-04：pageNum 默认 1；pageSize 默认 10、区间 [1,100]，越界/非法 → 400）。
 * 纯 Bean Validation，不依赖 Spring/DB；口径对齐 Mock validate.pageSizeInvalid（文案「每页条数须为 1-100」）。
 * 注意：本机无 JDK/Maven，无法执行；收敛到服务器阶段运行。
 */
class PageQueryConstraintTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void setUp() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void tearDown() {
        factory.close();
    }

    private Set<ConstraintViolation<PageQuery>> validate(PageQuery query) {
        return validator.validate(query);
    }

    @Test
    void defaultsAreValid() {
        PageQuery query = new PageQuery();
        assertEquals(1, query.getPageNum());
        assertEquals(10, query.getPageSize());
        assertTrue(validate(query).isEmpty());
    }

    @Test
    void boundariesAreValid() {
        PageQuery lower = new PageQuery();
        lower.setPageNum(1);
        lower.setPageSize(1);
        assertTrue(validate(lower).isEmpty());

        PageQuery upper = new PageQuery();
        upper.setPageSize(100);
        assertTrue(validate(upper).isEmpty());
    }

    @Test
    void pageSizeOutOfRangeIsRejected() {
        PageQuery zero = new PageQuery();
        zero.setPageSize(0);
        assertEquals(1, validate(zero).size());
        assertEquals("每页条数须为 1-100", validate(zero).iterator().next().getMessage());

        PageQuery tooLarge = new PageQuery();
        tooLarge.setPageSize(101);
        assertEquals(1, validate(tooLarge).size());
        assertEquals("每页条数须为 1-100", validate(tooLarge).iterator().next().getMessage());
    }

    @Test
    void pageNumBelowOneIsRejected() {
        PageQuery zero = new PageQuery();
        zero.setPageNum(0);
        assertEquals(1, validate(zero).size());

        PageQuery negative = new PageQuery();
        negative.setPageNum(-3);
        assertEquals(1, validate(negative).size());
    }

    @Test
    void nullValuesFallBackToDefaultsWithoutViolation() {
        PageQuery query = new PageQuery();
        query.setPageNum(null);
        query.setPageSize(null);
        // 缺省值由控制器/Service 补齐；校验注解对 null 不报错（与契约「缺失走默认值」一致）
        assertTrue(validate(query).isEmpty());
    }
}

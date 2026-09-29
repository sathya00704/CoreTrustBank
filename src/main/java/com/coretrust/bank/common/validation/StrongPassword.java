package com.coretrust.bank.common.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.lang.annotation.*;

/** Password policy in one place: 8–64 chars, upper, lower, digit and special character. */
@Documented
@Constraint(validatedBy = {})
@Target({ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@NotBlank
@Size(min = 8, max = 64)
@Pattern(regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^A-Za-z0-9]).+$",
        message = "must contain upper and lower case letters, a digit and a special character")
public @interface StrongPassword {
    String message() default "must be a strong password";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
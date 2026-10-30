package com.example.caprichometro.domain.validation

interface Validator<T> {
    fun setNext(next: Validator<T>): Validator<T>
    fun validate(input: T): ValidationResult
}

abstract class BaseValidator<T> : Validator<T> {
    private var nextValidator: Validator<T>? = null

    override fun setNext(next: Validator<T>): Validator<T> {
        this.nextValidator = next
        return next
    }

    override fun validate(input: T): ValidationResult {
        return nextValidator?.validate(input) ?: ValidationResult.Success
    }
}

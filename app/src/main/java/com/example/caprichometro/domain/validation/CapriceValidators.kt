package com.example.caprichometro.domain.validation

class NotBlankValidator(private val fieldName: String) : BaseValidator<String>() {
    override fun validate(input: String): ValidationResult {
        if (input.trim().isEmpty()) {
            return ValidationResult.Error("$fieldName no puede estar vacío")
        }
        return super.validate(input)
    }
}

class MinLengthValidator(
    private val fieldName: String,
    private val minLength: Int = 5
) : BaseValidator<String>() {
    override fun validate(input: String): ValidationResult {
        if (input.trim().length < minLength) {
            return ValidationResult.Error("$fieldName debe tener al menos $minLength caracteres")
        }
        return super.validate(input)
    }
}

class DifferentOptionsValidator : BaseValidator<Pair<String, String>>() {
    override fun validate(input: Pair<String, String>): ValidationResult {
        val (option1, option2) = input
        if (option1.trim().equals(option2.trim(), ignoreCase = true)) {
            return ValidationResult.Error("Las dos opciones de respuesta deben ser diferentes")
        }
        return super.validate(input)
    }
}

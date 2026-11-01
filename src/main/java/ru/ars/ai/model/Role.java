package ru.ars.ai.model;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum Role {

    USER("user"), ASSISTANT("assistant"), SYSTEM("system");

    /** Отображаемое имя роли, используется в шаблонах. */
    private final String role;

}
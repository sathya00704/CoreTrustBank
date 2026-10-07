package com.coretrust.bank.statement.dto;

public record StatementFile(String fileName, String contentType, byte[] content) {
}
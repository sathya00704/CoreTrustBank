package com.coretrust.bank.caseupdate.event;

public record CaseUpdateEvent(String customerName, String customerEmail, String title, String message) {
}
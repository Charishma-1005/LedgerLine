package com.ledgerline.identity.application;

public record AdminDashboardStats(long totalUsers, long enabledUsers, long disabledUsers,
                                  long totalAccounts, long activeAccounts, long closedAccounts) { }

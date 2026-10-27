package com.vs6.syncsight.promotions.shared.sync;

// What the page needs to show refresh progress
public record RefreshStatus(boolean running, String stage, int done, int total, String lastError) {}
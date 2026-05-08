package com.finalproject.manager;

import com.finalproject.manager.quota.QuotaRequest;

@FunctionalInterface
public interface QuotaListener {
    void onQuotaEvent(QuotaRequest request, String kind);
}

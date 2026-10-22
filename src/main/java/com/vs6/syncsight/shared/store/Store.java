package com.vs6.syncsight.shared.store;

public record Store(int storeId, String storeName, String localIp) {

    // Only IPs ending in .237 or .238 have a local site
    public boolean hasLocalSite() {
        if (localIp == null) return false;
        String ip = localIp.trim();
        return ip.endsWith(".237") || ip.endsWith(".238")|| ip.endsWith(".37")|| ip.endsWith(".38");
    }

    public String localUrl() {
        return "http://visualstore." + localIp.trim() + ".nip.io/vs";
    }
}
package com.dev.gateway.cache;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

import java.util.List;
import java.util.Map;

public final class DdosIpCache {
    public final static List<String> IP_WHITE_LIST = Lists.newArrayList();

    public final static Map<String,Integer> IP_BLACK_LIST = Maps.newHashMap();

    public final static List<String> PREFIXIP_WHITE_LIST = Lists.newArrayList();

    public final static Map<String,Integer> PREFIXIP_BLACK_LIST = Maps.newHashMap();

    public final static List<String> IP_BLACK_LIST_ACCESSLOG = Lists.newArrayList();

    public final static List<String> PREFIXIP_BLACK_LIST_ACCESSLOG = Lists.newArrayList();

    public final static Map<String,String> SLIDING_WINDOW_IPTRACKER = Maps.newHashMap();
}

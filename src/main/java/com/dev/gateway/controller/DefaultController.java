package com.dev.gateway.controller;

import com.alibaba.fastjson.JSONObject;
import com.dev.gateway.cache.DdosIpCache;
import org.springframework.web.bind.annotation.*;

@RestController
public class DefaultController {

    @RequestMapping("/default")
    public String defaultMethod() {
        return "404";
    }

//    @RequestMapping("/dev/debug")
//    public String devDebug() {
//        return "debug";
//    }

    @GetMapping("/broken-pipe")
    public String clientBrokenPipe()throws Exception{
        Thread.sleep(1000*10);
        return "success";
    }

    @GetMapping("/ddos-info")
    public JSONObject ddosInfo() {
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("ip_black_list", DdosIpCache.IP_BLACK_LIST);
        jsonObject.put("ip_black_list_accesslog",DdosIpCache.IP_BLACK_LIST_ACCESSLOG);
        jsonObject.put("prefixip_black_list",DdosIpCache.PREFIXIP_BLACK_LIST);
        jsonObject.put("prefixip_black_list_accesslog",DdosIpCache.PREFIXIP_BLACK_LIST_ACCESSLOG);
        jsonObject.put("white_list",DdosIpCache.IP_WHITE_LIST);
        jsonObject.put("prefixip_white_list",DdosIpCache.PREFIXIP_WHITE_LIST);
        return jsonObject;
    }



    @PostMapping("/white-list/{ip}")
    public String whiteList(@PathVariable("ip") String ip) {
        DdosIpCache.IP_WHITE_LIST.add(ip);
        return "ok";
    }

    @PostMapping("/prefixip-white-list/{ip}")
    public String prefixipWhiteList(@PathVariable("ip") String ip) {
        DdosIpCache.PREFIXIP_WHITE_LIST.add(ip);
        return "ok";
    }

}

package com.wanghongchun.rpc.impl;

import com.wanghongchun.rpc.server.RpcService;
import com.wanghongchun.rpc.sv.HelloService;

/**
 * @Description:
 * @author: wanghongchun
 * @date: 2018/12/21
 */
@RpcService(HelloService.class)
public class HelloImpl implements HelloService {
    @Override
    public String hello(String name) {
        return "Hello! " + name;
    }
}

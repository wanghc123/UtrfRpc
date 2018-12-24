package com.wanghongchun.rpc.client.proxy;

import com.wanghongchun.rpc.client.MessageSendHandler;
import com.wanghongchun.rpc.client.RPCFuture;
import com.wanghongchun.rpc.client.RpcClientHandler;
import com.wanghongchun.rpc.client.RpcServerLoader;
import com.wanghongchun.rpc.protocol.RpcRequest;
import com.wanghongchun.rpc.server.RpcServer;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.socket.nio.NioSocketChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.util.UUID;

/**
 * @Description:
 * @author: wanghongchun
 * @date: 2018/12/24
 */
public class MessageSendProxy<T> implements InvocationHandler {
    private static final Logger logger = LoggerFactory.getLogger(RpcServer.class);
    private Class<T> cls;

    public MessageSendProxy(Class<T> cls) {
        this.cls = cls;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        if (Object.class == method.getDeclaringClass()) {
            String name = method.getName();
            if ("equals".equals(name)) {
                return proxy == args[0];
            } else if ("hashCode".equals(name)) {
                return System.identityHashCode(proxy);
            } else if ("toString".equals(name)) {
                return proxy.getClass().getName() + "@" +
                        Integer.toHexString(System.identityHashCode(proxy)) +
                        ", with InvocationHandler " + this;
            } else {
                throw new IllegalStateException(String.valueOf(method));
            }
        }

        RpcRequest request = new RpcRequest();
        request.setRequestId(UUID.randomUUID().toString());
        request.setClassName(method.getDeclaringClass().getName());
        request.setMethodName(method.getName());
        request.setParameterTypes(method.getParameterTypes());
        request.setParameters(args);
        // Debug
        logger.debug(method.getDeclaringClass().getName());
        logger.debug(method.getName());
        for (int i = 0; i < method.getParameterTypes().length; ++i) {
            logger.debug(method.getParameterTypes()[i].getName());
        }
        for (int i = 0; i < args.length; ++i) {
            logger.debug(args[i].toString());
        }
        RpcClientHandler handler;
        Bootstrap b = new Bootstrap();
        b.group(eventLoopGroup)
                .channel(NioSocketChannel.class)
                .handler(new RpcClientInitializer());

        ChannelFuture channelFuture = b.connect(remotePeer);
        channelFuture.addListener(new ChannelFutureListener() {
            @Override
            public void operationComplete(final ChannelFuture channelFuture) throws Exception {
                if (channelFuture.isSuccess()) {
                    logger.debug("Successfully connect to remote server. remote peer = " + remotePeer);
                    handler = channelFuture.channel().pipeline().get(RpcClientHandler.class);
                   // addHandler(handler);
                }
            }
        });
        RpcClientHandler handler = new RpcClientHandler();
        RPCFuture rpcFuture = handler.sendRequest(request);
        return rpcFuture.get();
    }
}
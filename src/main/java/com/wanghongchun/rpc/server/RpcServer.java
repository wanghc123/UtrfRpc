package com.wanghongchun.rpc.server;

import com.wanghongchun.rpc.common.NamedThreadFactory;
import com.wanghongchun.rpc.common.RpcThreadPool;
import com.wanghongchun.rpc.protocol.RpcDecoder;
import com.wanghongchun.rpc.protocol.RpcEncoder;
import com.wanghongchun.rpc.protocol.RpcRequest;
import com.wanghongchun.rpc.protocol.RpcResponse;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.handler.codec.LengthFieldPrepender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import javax.imageio.spi.ServiceRegistry;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * @Description:
 * @author: wanghongchun
 * @date: 2018/12/21
 */
@Component
public class RpcServer implements ApplicationContextAware, InitializingBean {
    private static final Logger logger = LoggerFactory.getLogger(RpcServer.class);
    @Value("${netty.Server.ip}")
    private String nettyIp;
    @Value("${netty.Server.port}")
    private Integer nettyPort;
    private String serverAddress;
    private ServiceRegistry serviceRegistry;
    private static ThreadPoolExecutor threadPoolExecutor;
    private EventLoopGroup bossGroup = null;
    private EventLoopGroup workerGroup = null;
    private Map<String,Object> serviceMap = new HashMap<String,Object>();
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        Map<String, Object> beansWithAnnotation = applicationContext.getBeansWithAnnotation(RpcService.class);
        for(Map.Entry<String,Object> entry :beansWithAnnotation.entrySet()){
            String interfaceName = entry.getValue().getClass()
                    .getAnnotation(RpcService.class).value().getName();
            serviceMap.put(interfaceName,entry.getValue());
        }
    }
    @Override
    public void afterPropertiesSet() throws Exception {
        start();
    }

    public void stop() {
        if (bossGroup != null) {
            bossGroup.shutdownGracefully();
        }
        if (workerGroup != null) {
            workerGroup.shutdownGracefully();
        }
    }

    public static void submit(Runnable task) {
        if (threadPoolExecutor == null) {
            synchronized (RpcServer.class) {
                if (threadPoolExecutor == null) {
                    threadPoolExecutor = RpcThreadPool.getExecutor(16,1);
                }
            }
        }
        threadPoolExecutor.submit(task);
    }

    public RpcServer addService(String interfaceName, Object serviceBean) {
        if (!serviceMap.containsKey(interfaceName)) {
            logger.info("Loading service: {}", interfaceName);
            serviceMap.put(interfaceName, serviceBean);
        }

        return this;
    }

    public void start() throws Exception {
        if (bossGroup == null && workerGroup == null) {
            bossGroup = new NioEventLoopGroup();
            workerGroup = new NioEventLoopGroup();
            ServerBootstrap bootstrap = new ServerBootstrap();
            bootstrap.group(bossGroup, workerGroup).channel(NioServerSocketChannel.class)
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        public void initChannel(SocketChannel channel) throws Exception {
                            channel.pipeline()
                                    .addLast(new LengthFieldBasedFrameDecoder(65536, 0, 4, 0, 0))
                                    .addLast(new LengthFieldPrepender(4))
                                    .addLast(new RpcDecoder(RpcRequest.class))
                                    .addLast(new RpcEncoder(RpcResponse.class))
                                    .addLast(new RpcHandler(serviceMap));
                        }
                    })
                    .option(ChannelOption.SO_BACKLOG, 128)
                    .childOption(ChannelOption.SO_KEEPALIVE, true);

       /*     String[] array = serverAddress.split(":");
            String host = array[0];
            int port = Integer.parseInt(array[1]);*/

            ChannelFuture future = bootstrap.bind(nettyIp, nettyPort).sync();
            logger.info("Server started on port {}", nettyPort);

           /* if (serviceRegistry != null) {
                serviceRegistry.register(serverAddress);
            }*/

            future.channel().closeFuture().sync();
        }
    }
}

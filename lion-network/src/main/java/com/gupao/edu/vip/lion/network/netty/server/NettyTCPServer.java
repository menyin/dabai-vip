
package com.gupao.edu.vip.lion.network.netty.server;

import com.gupao.edu.vip.lion.api.service.BaseService;
import com.gupao.edu.vip.lion.api.service.Listener;
import com.gupao.edu.vip.lion.api.service.Server;
import com.gupao.edu.vip.lion.api.service.ServiceException;
import com.gupao.edu.vip.lion.network.netty.codec.PacketDecoder;
import com.gupao.edu.vip.lion.network.netty.codec.PacketEncoder;
import com.gupao.edu.vip.lion.tools.common.Strings;
import com.gupao.edu.vip.lion.tools.thread.ThreadNames;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.channel.*;
import io.netty.channel.epoll.EpollEventLoopGroup;
import io.netty.channel.epoll.EpollServerSocketChannel;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.util.concurrent.DefaultThreadFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.nio.channels.spi.SelectorProvider;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicReference;

import static com.gupao.edu.vip.lion.tools.Utils.useNettyEpoll;


public abstract class NettyTCPServer extends BaseService implements Server {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    public enum State {Created, Initialized, Starting, Started, Shutdown}
    //cny_note ？这里为什么要保证serverState 的原子性？ 似乎不会有其它线程来操作它
    //主要在于在Nio通道操作过程都是在线程池环境下操作，而这个NettyTCPServer基类的具体子类服务实例是有可能在这些通道操作下去停止，此时要更新这个状态就要保证安全（原子性）
    protected final AtomicReference<State> serverState = new AtomicReference<>(State.Created);

    protected final int port;
    protected final String host;
    protected EventLoopGroup bossGroup;
    protected EventLoopGroup workerGroup;

    public NettyTCPServer(int port) {
        this.port = port;
        this.host = null;
    }

    public NettyTCPServer(int port, String host) {
        this.port = port;
        this.host = host;
    }

    public void init() {
        if (!serverState.compareAndSet(State.Created, State.Initialized)) {
            throw new ServiceException("Server already init");
        }
    }

    @Override
    public boolean isRunning() {
        return serverState.get() == State.Started;
    }

    @Override
    public void stop(Listener listener) {
        if (!serverState.compareAndSet(State.Started, State.Shutdown)) {
            if (listener != null) listener.onFailure(new ServiceException("server was already shutdown."));
            logger.error("{} was already shutdown.", this.getClass().getSimpleName());
            return;
        }
        logger.info("try shutdown {}...", this.getClass().getSimpleName());
        if (bossGroup != null) bossGroup.shutdownGracefully().syncUninterruptibly();//要先关闭接收连接的main reactor
        if (workerGroup != null) workerGroup.shutdownGracefully().syncUninterruptibly();//再关闭处理业务的sub reactor
        logger.info("{} shutdown success.", this.getClass().getSimpleName());
        if (listener != null) {
            listener.onSuccess(port);
        }
    }

    @Override
    public void start(final Listener listener) {//listener规定为final保证在子线程内使用时不会扩大listener作用域
        if (!serverState.compareAndSet(State.Initialized, State.Starting)) { //cny_note 其实必须保证子类重写的init方法里有super.init()
            throw new ServiceException("Server already started or have not init");
        }
        //？？epoll是什么东西，好像和什么空轮询问题相关，EpollServerSocketChannel和NioServerSocketChannel的区别
        //空轮询应该就是每个Nio的selector都要轮询一遍，才知道那个数据已经准备好了
        //Netty可在任何系统上运行，但对于不同的系统会有不同的折中，Linux系统中的epoll具有高可扩展的I/O的事件通知，Linux上的JDK的NIO则是基于epoll，
        //当使用epoll取代NIO时，可以使用Netty中的EpollEventLoopGroup取代NioEventLoopGroup，使用EpollServerSocketChannel.class取代NioServerSocketChannel.class。
        //可以去了解下linux的epoll  个人认为操作系统的Nio还是selector就是一个标致，需要我们自己轮询。而epoll却提供了真正的事件，即事件通知。
        if (useNettyEpoll()) {
            createEpollServer(listener);
        } else {
            createNioServer(listener);
        }
    }

    private void createServer(Listener listener, EventLoopGroup boss, EventLoopGroup work, ChannelFactory<? extends ServerChannel> channelFactory) {
        /***
         * NioEventLoopGroup 是用来处理I/O操作的多线程事件循环器，
         * Netty提供了许多不同的EventLoopGroup的实现用来处理不同传输协议。
         * 在一个服务端的应用会有2个NioEventLoopGroup会被使用。
         * 第一个经常被叫做‘boss’，用来接收进来的连接。
         * 第二个经常被叫做‘worker’，用来处理已经被接收的连接，
         * 一旦‘boss’接收到连接，就会把连接信息注册到‘worker’上。
         * 如何知道多少个线程已经被使用，如何映射到已经创建的Channels上都需要依赖于EventLoopGroup的实现，
         * 并且可以通过构造函数来配置他们的关系。
         */
        this.bossGroup = boss;
        this.workerGroup = work;

        try {
            /**
             * ServerBootstrap 是一个启动NIO服务的辅助启动类
             * 你可以在这个服务中直接使用Channel
             */
            ServerBootstrap b = new ServerBootstrap();

            /**
             * 这一步是必须的，如果没有设置group将会报java.lang.IllegalStateException: group not set异常
             */
            b.group(bossGroup, workerGroup);

            /***
             * ServerSocketChannel以NIO的selector为基础进行实现的，用来接收新的连接
             * 这里告诉Channel如何获取新的连接.
             */
            b.channelFactory(channelFactory);//cny_note 和channel()作用一致，channel()内部也是用默认的channelFactory生产NioChannel


            /***
             * 这里的事件处理类经常会被用来处理一个最近的已经接收的Channel。
             * ChannelInitializer是一个特殊的处理类，
             * 他的目的是帮助使用者配置一个新的Channel。
             * 也许你想通过增加一些处理类比如NettyServerHandler来配置一个新的Channel
             * 或者其对应的ChannelPipeline来实现你的网络程序。
             * 当你的程序变的复杂时，可能你会增加更多的处理类到pipeline上，
             * 然后提取这些匿名类到最顶层的类上。
             */
            b.childHandler(new ChannelInitializer<Channel>() { // (4)
                @Override
                public void initChannel(Channel ch) throws Exception {//每连上一个链接调用一次
                    initPipeline(ch.pipeline());
                }
            });

            initOptions(b);

            /***
             * 绑定端口并启动去接收进来的连接
             */
            InetSocketAddress address = Strings.isBlank(host) ? new InetSocketAddress(port) : new InetSocketAddress(host, port);
            b.bind(address).addListener(future -> {
                if (future.isSuccess()) {
                    serverState.set(State.Started);
                    logger.info("server start success on:{}", port);
                    if (listener != null) listener.onSuccess(port);
                } else {
                    logger.error("server start failure on:{}", port, future.cause());
                    if (listener != null) listener.onFailure(future.cause());//cny_note 接收future处理的异常
                }
            });
        } catch (Exception e) {
            logger.error("server start exception", e);
            if (listener != null) listener.onFailure(e);
            throw new ServiceException("server start exception, port=" + port, e);
        }
    }

    private void createNioServer(Listener listener) {
        EventLoopGroup bossGroup = getBossGroup();
        EventLoopGroup workerGroup = getWorkerGroup();

        if (bossGroup == null) { //cny_note 这样设计，主要是通过getBossGroup()里让子类可以重写设置bossGroup和workGroup
            NioEventLoopGroup nioEventLoopGroup = new NioEventLoopGroup(getBossThreadNum(), getBossThreadFactory(), getSelectorProvider());//？？这个构造函数需学习下
            nioEventLoopGroup.setIoRatio(100);//？？这个IoRatio详解：设置I/O任务和非I/O任务的执行时间比
            bossGroup = nioEventLoopGroup;
        }

        if (workerGroup == null) {
            NioEventLoopGroup nioEventLoopGroup = new NioEventLoopGroup(getWorkThreadNum(), getWorkThreadFactory(), getSelectorProvider());//？？这个构造函数学习下
            nioEventLoopGroup.setIoRatio(getIoRate());
            workerGroup = nioEventLoopGroup;
        }

        createServer(listener, bossGroup, workerGroup, getChannelFactory());
    }

    private void createEpollServer(Listener listener) {
        EventLoopGroup bossGroup = getBossGroup();
        EventLoopGroup workerGroup = getWorkerGroup();

        if (bossGroup == null) {
            EpollEventLoopGroup epollEventLoopGroup = new EpollEventLoopGroup(getBossThreadNum(), getBossThreadFactory());
            epollEventLoopGroup.setIoRatio(100);
            bossGroup = epollEventLoopGroup;
        }

        if (workerGroup == null) {
            EpollEventLoopGroup epollEventLoopGroup = new EpollEventLoopGroup(getWorkThreadNum(), getWorkThreadFactory());
            epollEventLoopGroup.setIoRatio(getIoRate());
            workerGroup = epollEventLoopGroup;
        }

        createServer(listener, bossGroup, workerGroup, EpollServerSocketChannel::new);
    }

    /***
     * option()是提供给NioServerSocketChannel用来接收进来的连接。
     * childOption()是提供给由父管道ServerChannel接收到的连接，
     * 在这个例子中也是NioServerSocketChannel。
     */
    protected void initOptions(ServerBootstrap b) {
        //b.childOption(ChannelOption.SO_KEEPALIVE, false);// 使用应用层心跳

        /**
         * 在Netty 4中实现了一个新的ByteBuf内存池，它是一个纯Java版本的 jemalloc （Facebook也在用）。
         * 现在，Netty不会再因为用零填充缓冲区而浪费内存带宽了。不过，由于它不依赖于GC，开发人员需要小心内存泄漏。
         * 如果忘记在处理程序中释放缓冲区，那么内存使用率会无限地增长。
         * Netty默认不使用内存池，需要在创建客户端或者服务端的时候进行指定
         * ？？
         */
        b.option(ChannelOption.ALLOCATOR, PooledByteBufAllocator.DEFAULT);//？？内存分配方式采用：堆外的内存池
        b.childOption(ChannelOption.ALLOCATOR, PooledByteBufAllocator.DEFAULT);
    }


    public abstract ChannelHandler getChannelHandler();

    protected ChannelHandler getDecoder() {
        return new PacketDecoder();
    }

    protected ChannelHandler getEncoder() {
        return PacketEncoder.INSTANCE;//每连上一个链接调用一次, 所有用单利//cny_note ？这里用单例，为什么getDecoder()里为什么不用单例
    }

    /**
     * 每连上一个链接调用一次
     *
     * @param pipeline
     */
    protected void initPipeline(ChannelPipeline pipeline) {
        pipeline.addLast("decoder", getDecoder());
        pipeline.addLast("encoder", getEncoder());
        pipeline.addLast("handler", getChannelHandler());
    }

    /**
     * netty 默认的Executor为ThreadPerTaskExecutor
     * 线程池的使用在SingleThreadEventExecutor#doStartThread
     * <p>
     * eventLoop.execute(runnable);
     * 是比较重要的一个方法。在没有启动真正线程时，
     * 它会启动线程并将待执行任务放入执行队列里面。
     * 启动真正线程(startThread())会判断是否该线程已经启动，
     * 如果已经启动则会直接跳过，达到线程复用的目的
     * cny_note 自定义线程池的名称，但还是使用netty默认的线程池工厂DefaultThreadFactory
     * cny_note 单独定义这个方法，应该是为了让子类可以自由重写定义
     */
    protected ThreadFactory getBossThreadFactory() {
        return new DefaultThreadFactory(getBossThreadName());
    }

    protected ThreadFactory getWorkThreadFactory() {
        return new DefaultThreadFactory(getWorkThreadName());
    }

    /**
     * 这里定义protected，目的为了让子类重写，但不被外类所访问
     * @return
     */
    protected int getBossThreadNum() {
        return 1;
    }

    /**
     * ？这里为什么默认设置为0
     * @return
     */
    protected int getWorkThreadNum() {
        return 0;
    }

    protected String getBossThreadName() {
        return ThreadNames.T_BOSS;
    }

    protected String getWorkThreadName() {
        return ThreadNames.T_WORKER;
    }

    protected int getIoRate() {
        return 70;
    }
    /**
     * 这里定义public，目的为了让子类重写，并且能被外类所访问
     * @return
     */
    public EventLoopGroup getBossGroup() {
        return bossGroup;
    }

    public EventLoopGroup getWorkerGroup() {
        return workerGroup;
    }

    /**
     * cny_note 让子类可以重写，因为ServerChannel有多种协议的实现
     * @return
     */
    public ChannelFactory<? extends ServerChannel> getChannelFactory() {
        return NioServerSocketChannel::new;
    }

    /**
     * cny_note 单独定义这个方法，应该是为了让子类可以自由重写定义
     * @return
     */
    public SelectorProvider getSelectorProvider() {
        return SelectorProvider.provider();
    }
}

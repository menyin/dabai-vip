
package com.gupao.edu.vip.lion.client.push;

import com.gupao.edu.vip.lion.api.Constants;
import com.gupao.edu.vip.lion.api.push.*;
import com.gupao.edu.vip.lion.api.router.ClientLocation;
import com.gupao.edu.vip.lion.client.LionClient;
import com.gupao.edu.vip.lion.common.message.gateway.GatewayPushMessage;
import com.gupao.edu.vip.lion.common.push.GatewayPushResult;
import com.gupao.edu.vip.lion.common.router.RemoteRouter;
import com.gupao.edu.vip.lion.tools.Jsons;
import com.gupao.edu.vip.lion.tools.common.TimeLine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.atomic.AtomicReference;

/**
 * cny_note 此类的大致逻辑流程：
 *          1.从#send方法开始发送请求，并且当前请求对象用status属性记录请求。
 *          2.请求是由netty的通道（connection）执行的，也就是netty线程去执行。
 *          3.当netty开始执行请求，我们会通过线程池定时执行一个超时任务，达到时间就响应超时结果给客户端。
 *          4.在超时任务的超时时间内，如果netty的请求有出错或返回正确结果，则会马上取消超时任务，并且返回netty执行的结果给客户端。
 * cny_note FutureTask学习详见blog《Java并发编程：Callable、Future和FutureTask》 https://www.cnblogs.com/dolphin0520/p/3949310.html
 *                           csdn《FutureTask介绍及使用》 https://blog.csdn.net/qq1137623160/article/details/79772505
 *          个人认为FutureTask是一个【将不带返回值的任务 run 转换为带返回值的任务 Callable】的东西
 *          此类执行逻辑的理解主要要学习 FutureTask的使用原理，详见demo
 *
 */
public final class PushRequest extends FutureTask<PushResult> {
    private static final Logger LOGGER = LoggerFactory.getLogger(PushRequest.class);

    private static final Callable<PushResult> NONE = () -> new PushResult(PushResult.CODE_FAILURE);

    private enum Status {init, success, failure, offline, timeout} //cny_note 初始状态是init，请求最后的结果是其它4种之一

    private final AtomicReference<Status> status = new AtomicReference<>(Status.init);
    private final TimeLine timeLine = new TimeLine("Push-Time-Line");

    private final LionClient lionClient;

    private AckModel ackModel;
    private Set<String> tags;
    private String condition;
    private PushCallback callback;
    private String userId;
    private byte[] content;
    private int timeout; //cny_note 请求开始后的超时任务的延迟时间，超时任务主要是在服务器无响应或其它原因导致当前请求未得到结果时，反馈给客户端超时报文
    private ClientLocation location;
    private int sessionId;
    private String taskId;
    private Future<?> future;//cny_note 超时任务的future
    private PushResult result;

    /**
     * cny_note 该方法是真正底层发送消息到远程的服务节点
     * @param remoteRouter
     */
    private void sendToConnServer(RemoteRouter remoteRouter) {
        timeLine.addTimePoint("lookup-remote");

        if (remoteRouter != null) {
            location = remoteRouter.getRouteValue();
        }

        if (remoteRouter == null || remoteRouter.isOffline()) {
            //1.1没有查到说明用户已经下线
            offline();
            return;
        }

        timeLine.addTimePoint("check-gateway-conn");
        //2.通过网关连接，把消息发送到所在机器
        boolean success = lionClient.getGatewayConnectionFactory().send( //cny_note #send里会根据目标IP+Port建立Connection
                location.getHostAndPort(),
                connection -> GatewayPushMessage  //cny_note 这里构造的GatewayPushMessage就是下面的pushMessage对象
                        .build(connection)
                        .setUserId(userId)
                        .setContent(content)
                        .setClientType(location.getClientType())
                        .setTimeout(timeout - 500) //？？为什么要减去500
                        .setTags(tags)
                        .addFlag(ackModel.flag)
                ,
                pushMessage -> {//cny_note 相当于把这个lamda传递到GatewayConnectionFactory里面执行
                    timeLine.addTimePoint("send-to-gateway-begin");
                    pushMessage.sendRaw(f -> {
                        timeLine.addTimePoint("send-to-gateway-end");
                        if (f.isSuccess()) {
                            LOGGER.debug("send to gateway server success, location={}, conn={}", location, f.channel());
                        } else {
                            LOGGER.error("send to gateway server failure, location={}, conn={}", location, f.channel(), f.cause());
                            failure();
                        }
                    });
                    PushRequest.this.content = null;//释放内存
                    sessionId = pushMessage.getSessionId();
                    future = lionClient.getPushRequestBus().put(sessionId, PushRequest.this);//cny_note 针对这个请求开启一个超时任务
                }
        );

        if (!success) {
            LOGGER.error("get gateway connection failure, location={}", location);
            failure();
        }
    }

    private void submit(Status status) {
        if (this.status.compareAndSet(Status.init, status)) {//防止重复调用
            boolean isTimeoutEnd = status == Status.timeout;//任务是否超时结束

            if (future != null && !isTimeoutEnd) {//是超时结束任务不用再取消一次
                future.cancel(true);//取消超时任务 cny_note true表可以取消正在执行的任务
            }

            this.timeLine.end();//结束时间流统计
            super.set(getResult());//设置同步调用的返回结果 //cny_note 调用 返回的是FutureTask实例future，而futre.get()是阻塞获取到结果，这个结果就是在这里设置

            if (callback != null) {//回调callback
                if (isTimeoutEnd) {//超时结束时，当前线程已经是线程池里的线程，直接调用callback
                    callback.onResult(getResult()); //cny_note onResult里再做差异化处理
                } else {//非超时结束时，当前线程为Netty线程池，要异步执行callback  ？？为什么要异步执行，即为什么要将任务从netty线程交给线程池处理？ 是为了做监控吗？
                    lionClient.getPushRequestBus().asyncCall(this);//会执行run方法
                }
            }
        }
        LOGGER.info("push request {} end, {}, {}, {}", status, userId, location, timeLine);
    }

    /**
     * run方法会有两个地方的线程调用
     * 1. 任务超时时会调用，见PushRequestBus.I.put(sessionId, PushRequest.this);
     * 2. 异步执行callback的时候，见PushRequestBus.I.asyncCall(this);
     */
    @Override
    public void run() {
        //判断任务是否超时，如果超时了此时状态是init，否则应该是其他状态, 因为从submit方法过来的状态都不是init
        if (status.get() == Status.init) { //cny_note PushRequst请求开始时会用线程池启动一个超时任务，如果超时则设置当前请求的状态是status=timeout
            timeout();
        } else {
            callback.onResult(getResult());
        }
    }

    @Override
    public boolean cancel(boolean mayInterruptIfRunning) {
        throw new UnsupportedOperationException();
    }

    public FutureTask<PushResult> send(RemoteRouter router) {
        timeLine.begin();
        sendToConnServer(router);
        return this;
    }

    public FutureTask<PushResult> broadcast() {
        timeLine.begin();

        boolean success = lionClient.getGatewayConnectionFactory()
                .broadcast(
                        connection -> GatewayPushMessage
                                .build(connection)
                                .setUserId(userId)
                                .setContent(content)
                                .setTags(tags)
                                .setCondition(condition)
                                .setTaskId(taskId)
                                .addFlag(ackModel.flag),

                        pushMessage -> {
                            pushMessage.sendRaw(f -> {
                                if (f.isSuccess()) {
                                    LOGGER.debug("send broadcast to gateway server success, userId={}, conn={}", userId, f.channel());
                                } else {
                                    failure();
                                    LOGGER.error("send broadcast to gateway server failure, userId={}, conn={}", userId, f.channel(), f.cause());
                                }
                            });

                            if (pushMessage.taskId == null) {
                                sessionId = pushMessage.getSessionId();
                                future = lionClient.getPushRequestBus().put(sessionId, PushRequest.this);
                            } else {
                                success();
                            }
                        }
                );

        if (!success) {
            LOGGER.error("get gateway connection failure when broadcast.");
            failure();
        }

        return this;
    }

    private void offline() {
        lionClient.getCachedRemoteRouterManager().invalidateLocalCache(userId); //cny_note 其实实际上也不会出缓存的远程路由列表失效，因为查询的时候已经检查过了
        submit(Status.offline);
    }

    private void timeout() {
        //超时要把request从队列中移除，其他情况是XXHandler中移除的
        if (lionClient.getPushRequestBus().getAndRemove(sessionId) != null) {
            submit(Status.timeout);
        }
    }

    private void success() {
        submit(Status.success);
    }

    private void failure() {
        submit(Status.failure);
    }

    public void onFailure() {
        failure();
    }

    public void onRedirect() {
        timeLine.addTimePoint("redirect");
        LOGGER.warn("user route has changed, userId={}, location={}", userId, location);
        //1. 清理一下缓存，确保查询的路由是正确的
        lionClient.getCachedRemoteRouterManager().invalidateLocalCache(userId);
        if (status.get() == Status.init) {//init表示任务还没有完成，还可以重新发送
            //2. 取消前一次任务, 否则会有两次回调
            if (lionClient.getPushRequestBus().getAndRemove(sessionId) != null) {
                if (future != null && !future.isCancelled()) {
                    future.cancel(true);
                }
            }
            //3. 取最新的路由重发一次
            send(lionClient.getCachedRemoteRouterManager().lookup(userId, location.getClientType()));
        }
    }

    public FutureTask<PushResult> onOffline() {
        offline();
        return this;
    }

    public void onSuccess(GatewayPushResult result) { //cny_note 在netty 通道接收到推送服务的反馈后会调用该方法
        if (result != null) timeLine.addTimePoints(result.timePoints);
        submit(Status.success);
    }

    public long getTimeout() {
        return timeout;
    }

    public PushRequest(LionClient lionClient) {
        super(NONE);
        this.lionClient = lionClient;
    }

    public static PushRequest build(LionClient lionClient, PushContext ctx) {
        byte[] content = ctx.getContext();
        PushMsg msg = ctx.getPushMsg(); //cny_note 以pushMsg为主
        if (msg != null) {
            String json = Jsons.toJson(msg); //cny_note 序列化
            if (json != null) {
                content = json.getBytes(Constants.UTF_8);
            }
        }

        Objects.requireNonNull(content, "push content can not be null.");//cny_note 检测对象为null就抛异常

        return new PushRequest(lionClient)
                .setAckModel(ctx.getAckModel())
                .setUserId(ctx.getUserId())
                .setTags(ctx.getTags())
                .setCondition(ctx.getCondition())
                .setTaskId(ctx.getTaskId())
                .setContent(content)
                .setTimeout(ctx.getTimeout())
                .setCallback(ctx.getCallback());

    }

    private PushResult getResult() {
        if (result == null) {
            result = new PushResult(status.get().ordinal()) //cny_note ordinal()返回枚举的序号
                    .setUserId(userId)
                    .setLocation(location)
                    .setTimeLine(timeLine.getTimePoints());
        }
        return result;
    }

    public PushRequest setCallback(PushCallback callback) {
        this.callback = callback;
        return this;
    }

    public PushRequest setUserId(String userId) {
        this.userId = userId;
        return this;
    }

    public PushRequest setContent(byte[] content) {
        this.content = content;
        return this;
    }

    public PushRequest setTimeout(int timeout) {
        this.timeout = timeout;
        return this;
    }

    public PushRequest setAckModel(AckModel ackModel) {
        this.ackModel = ackModel;
        return this;
    }

    public PushRequest setTags(Set<String> tags) {
        this.tags = tags;
        return this;
    }

    public PushRequest setCondition(String condition) {
        this.condition = condition;
        return this;
    }

    public PushRequest setTaskId(String taskId) {
        this.taskId = taskId;
        return this;
    }

    @Override
    public String toString() {
        return "PushRequest{" +
                "content='" + (content == null ? -1 : content.length) + '\'' +
                ", userId='" + userId + '\'' +
                ", timeout=" + timeout +
                ", location=" + location +
                '}';
    }
}

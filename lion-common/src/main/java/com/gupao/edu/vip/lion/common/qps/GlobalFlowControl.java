
package com.gupao.edu.vip.lion.common.qps;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * cny_note 大致原理：通过方法checkQps()检测指定时间内流量计数是否符合标准（限制有时间、时间内流量数、总流量数）
 * cny_note 指定duration纳秒内（默认1秒）请求的数量limit
 * cny_note 其中 maxLimit为最大的请求数量（默认是Integer.MAX_VALUE）
 */
public final class GlobalFlowControl implements FlowControl {
    private final int limit;
    private final int maxLimit;
    private final long duration;
    private final AtomicInteger count = new AtomicInteger();//在duration时间内的总请求数
    private final AtomicInteger total = new AtomicInteger();//在服务启动后的到当前时间内的总请求数
    private final long start0 = System.nanoTime();
    private volatile long start;

    public GlobalFlowControl(int qps) {
        this(qps, Integer.MAX_VALUE, 1000);
    }

    public GlobalFlowControl(int limit, int maxLimit, int duration) {
        this.limit = limit;
        this.maxLimit = maxLimit;
        this.duration = TimeUnit.MILLISECONDS.toNanos(duration);//cny_note duration的毫秒转化为纳秒
    }

    @Override
    public void reset() {
        count.set(0);
        start = System.nanoTime();
    }

    @Override
    public int total() {
        return total.get();
    }

    /**
     * cny_note 检测每秒内的qps，算是一个实时的qps
     *
     * @return
     */
    @Override
    public boolean checkQps() {
        if (count.incrementAndGet() < limit) {
            total.incrementAndGet();//cny_note 此处注意incrementAndGet和getAndIncrement的区别
            return true;
        }

        if (maxLimit > 0 && total.get() > maxLimit) throw new OverFlowException(true);//cny_note 总流量达到上限则报错

        if (System.nanoTime() - start > duration) {//超过1个时间单位就重置时间
            reset();
            total.incrementAndGet();
            return true;
        }
        return false;
    }

    /**
     * 获得限流时间的剩余时间delay
     * 当请求超过qps后，客户端可以设置delay延迟之后再请求，这样就可以缓解请求压力，降低qps
     *
     * @return
     */
    @Override
    public long getDelay() {
        return duration - (System.nanoTime() - start);
    }

    /**
     * cny_note 从当前类实例创建开始到调用此方法期间的平均qps = total / 时间
     *
     * @return
     */
    @Override
    public int qps() {
        return (int) (TimeUnit.SECONDS.toNanos(total.get()) / (System.nanoTime() - start0));
    }

    @Override
    public String report() {
        return String.format("total:%d, count:%d, qps:%d", total.get(), count.get(), qps());
    }
}

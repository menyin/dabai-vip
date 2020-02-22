package com.gupao.edu.vip.lion.tools.common;

/**
 * cny_note 时间线，存储了时间点对象，本质上是一个单向的链表
 *          时间点：存储了开发人员在代码中定义的时间点名称，以及当前时间点和下个时间点对象
 */
public final class TimeLine {
    private final TimePoint root = new TimePoint("root");
    private final String name;
    private int pointCount;
    private TimePoint current = root;

    public TimeLine() {
        name = "TimeLine";
    }

    public TimeLine(String name) {
        this.name = name;
    }

    public void begin(String name) {
        addTimePoint(name);
    }

    public void begin() {
        addTimePoint("begin");
    }

    public void addTimePoint(String name) {
        //这条代码的逻辑是这样，先将当前节点的next指向新建的节点，然后将当前节点变量current又指向新的节点
        current = current.next = new TimePoint(name);
        pointCount++;
    }

    public void addTimePoints(Object[] points) {
        if (points != null) {
            for (int i = 0; i < points.length; i++) {
                current = current.next = new TimePoint((String) points[i], ((Number) points[++i]).longValue());
                pointCount++;
            }
        }
    }

    public TimeLine end(String name) {
        addTimePoint(name);
        return this;
    }

    public TimeLine end() {
        addTimePoint("end");
        return this;
    }

    public TimeLine successEnd() {
        addTimePoint("success-end");
        return this;
    }

    public TimeLine failureEnd() {
        addTimePoint("failure-end");
        return this;
    }

    public TimeLine timeoutEnd() {
        addTimePoint("timeout-end");
        return this;
    }

    public void clean() {
        root.next = null;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder(name);
        if (root.next != null) {
            sb.append('[').append(current.time - root.next.time).append(']').append("(ms)");
        }
        sb.append('{');
        TimePoint next = root;
        while ((next = next.next) != null) {
            sb.append(next.toString());
        }
        sb.append('}');
        return sb.toString();
    }

    public Object[] getTimePoints() {
        Object[] arrays = new Object[2 * pointCount];
        int i = 0;
        TimePoint next = root;
        while ((next = next.next) != null) {
            arrays[i++] = next.name;
            arrays[i++] = next.time;
        }
        return arrays;
    }

    private static class TimePoint { //？？这里为什么用static
        private final String name;
        private final long time;
        private transient TimePoint next;

        public TimePoint(String name) {
            this.name = name;
            this.time = System.currentTimeMillis();
        }

        public TimePoint(String name, long time) {
            this.name = name;
            this.time = time;
        }

        public void setNext(TimePoint next) {
            this.next = next;
        }

        @Override
        public String toString() {
            if (next == null) return name;
            return name + " --（" + (next.time - time) + "ms) --> ";
        }
    }
}

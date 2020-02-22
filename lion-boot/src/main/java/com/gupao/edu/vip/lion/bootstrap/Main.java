
package com.gupao.edu.vip.lion.bootstrap;

import com.gupao.edu.vip.lion.tools.log.Logs;

public class Main {

    /**
     *
     */
    public static void main(String[] args) {
        Logs.init();
        Logs.Console.info("launch Lion server...");
        ServerLauncher launcher = new ServerLauncher();
        launcher.init();
        launcher.start();
        addHook(launcher);
    }

    /**
     * 注意点
     * 1.不要ShutdownHook Thread 里调用System.exit()方法，否则会造成死循环。
     * 2.如果有非守护线程，只有所有的非守护线程都结束了才会执行hook
     * 3.Thread默认都是非守护线程，创建的时候要注意
     * 4.注意线程抛出的异常，如果没有被捕获都会跑到Thread.dispatchUncaughtException
     *
     * cny_note 详见 https://blog.csdn.net/u013332124/article/details/84647915
     *  java进程的关闭：kill有-9和-15两种参数，默认是-15。用-9参数的kill不会等shutdownHook线程执行完就退出。
     *  如果是-15参数，系统就发送一个关闭信号给进程，然后等待进程关闭。在这个过程中，目标进程可以释放手中的资源，以及进行一些关闭操作。
     * @param launcher
     */
    private static void addHook(ServerLauncher launcher) {
        Runtime.getRuntime().addShutdownHook(
                new Thread(() -> {

                    try {
                        launcher.stop();
                    } catch (Exception e) {
                        Logs.Console.error("Lion server stop ex", e);
                    }
                    Logs.Console.info("jvm exit, all service stopped.");

                }, "lion-shutdown-hook-thread")
        );
    }
}
